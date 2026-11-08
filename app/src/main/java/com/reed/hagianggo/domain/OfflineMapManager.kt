package com.reed.hagianggo.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.reed.hagianggo.model.OfflineMapStatus
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.Zone
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

/**
 * Quản lý Bản đồ Offline Hà Giang Loop (Cách 2: Tải 1 chạm lưu vĩnh viễn vào context.filesDir).
 * - Ghi trực tiếp vào SqlTileWriter (filesDir/osmdroid/tiles/cache.db) để trình dọn rác hệ thống không bao giờ xóa.
 * - Tự động nhận diện mất mạng / sóng 2G ảo để ngắt kết nối mạng của MapView, đọc cache 0ms.
 */
class OfflineMapManager(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("hagiang_offline_map_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(OfflineMapStatus())
    val status: StateFlow<OfflineMapStatus> = _status.asStateFlow()

    private var downloadJob: Job? = null

    init {
        configureOsmDroid(appContext)
        refreshStatus()
    }

    companion object {
        private const val USER_AGENT = "HaGiangGo/1.2 (Android; Offline Mountain Safety Companion)"
        private const val ONE_YEAR_MS = 365L * 24L * 3600L * 1000L

        // Tọa độ khung cung đường Hà Giang Loop
        private val LOOP_CORRIDOR = listOf(
            22.827330 to 104.984169, // Km0 TP. Hà Giang
            22.841302 to 104.995193, // QL4C
            22.988169 to 104.935900, // Dốc Bắc Sum
            23.049316 to 104.993021, // Cổng Trời Quản Bạ
            23.064631 to 105.004454, // TT. Tam Sơn
            23.083986 to 105.037507, // Cán Tỷ
            23.121011 to 105.109948, // Rừng Thông Yên Minh
            23.118139 to 105.145654, // TT. Yên Minh
            23.168925 to 105.194145, // Dốc Thẩm Mã
            23.181364 to 105.198267, // Phố Cáo
            23.227137 to 105.202360, // Sủng Là
            23.256248 to 105.262089, // Dinh Vua Mèo
            23.363461 to 105.316334, // Cột Cờ Lũng Cú
            23.279671 to 105.360784, // Phố Cổ Đồng Văn
            23.240621 to 105.412083, // Đèo Mã Pì Lèng
            23.206305 to 105.416669, // Pả Vi
            23.164376 to 105.408771, // TT. Mèo Vạc
            23.104663 to 105.260078, // Cua Chữ M
            23.065592 to 105.237738, // Ngã Ba Mậu Duệ
            22.933029 to 105.226014, // Du Già
            22.979511 to 105.185435, // Đường Thượng
            23.049316 to 104.993021, // Thuận Hòa
            22.827330 to 104.984169  // Km0
        )

        fun configureOsmDroid(context: Context) {
            val baseDir = File(context.filesDir, "osmdroid").apply { mkdirs() }
            val tileDir = File(baseDir, "tiles").apply { mkdirs() }
            Configuration.getInstance().apply {
                userAgentValue = USER_AGENT
                osmdroidBasePath = baseDir
                osmdroidTileCache = tileDir
                cacheMapTileCount = 48.toShort()
                tileDownloadThreads = 3
                tileFileSystemCacheMaxBytes = 600L * 1024L * 1024L
                tileFileSystemCacheTrimBytes = 550L * 1024L * 1024L
                expirationOverrideDuration = ONE_YEAR_MS
            }
        }

        fun hasValidatedInternet(context: Context): Boolean {
            return try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    ?: return false
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } catch (_: Exception) {
                false
            }
        }
    }

    fun refreshStatus() {
        if (_status.value.isDownloading) return
        val tileDir = File(appContext.filesDir, "osmdroid/tiles")
        val sizeBytes = tileDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        val sizeMb = sizeBytes / (1024.0 * 1024.0)
        val savedDownloaded = prefs.getInt("downloaded_tiles", 0)
        val savedTotal = prefs.getInt("total_tiles", 0)
        val isComplete = prefs.getBoolean("pack_complete", false) && sizeMb >= 2.0

        val msg = when {
            isComplete -> "Đã lưu vĩnh viễn gói Hà Giang Loop (${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB)"
            sizeMb >= 3.0 -> "Đã lưu bộ đệm ${String.format(java.util.Locale.US, "%.1f", sizeMb)} MB • Tải trọn bộ để đi đèo mất sóng"
            else -> "Chưa tải gói bản đồ Offline (~24 MB cho toàn tuyến)"
        }

        _status.value = OfflineMapStatus(
            isDownloading = false,
            downloadedTiles = savedDownloaded,
            totalTiles = savedTotal,
            storageSizeMb = sizeMb,
            isReadyOffline = isComplete,
            statusMessage = msg,
            errorMessage = null
        )
    }

    fun startDownload(zones: List<Zone>, places: List<Place>) {
        if (_status.value.isDownloading) return
        if (!hasValidatedInternet(appContext)) {
            _status.value = _status.value.copy(
                errorMessage = "Cần kết nối Wi-Fi hoặc 4G ổn định để tải gói bản đồ Hà Giang.",
                statusMessage = "Không có kết nối Internet để tải bản đồ"
            )
            return
        }

        downloadJob = scope.launch {
            try {
                configureOsmDroid(appContext)
                val targetTiles = buildHaGiangTileList(zones, places)
                val total = targetTiles.size
                _status.value = OfflineMapStatus(
                    isDownloading = true,
                    downloadedTiles = 0,
                    totalTiles = total,
                    storageSizeMb = measureTileStorageMb(),
                    isReadyOffline = false,
                    statusMessage = "Đang chuẩn bị $total mảnh bản đồ cung đường Hà Giang..."
                )

                val writer = SqlTileWriter()
                val tileSource = TileSourceFactory.MAPNIK
                val completedCounter = AtomicInteger(0)
                val failedCounter = AtomicInteger(0)
                val semaphore = Semaphore(2) // Tôn trọng chuẩn fair-use của OpenStreetMap (tối đa 2 luồng song song)
                val expiry = System.currentTimeMillis() + ONE_YEAR_MS

                try {
                    coroutineScope {
                        targetTiles.map { tileIndex ->
                            async {
                                semaphore.withPermit {
                                    ensureActive()
                                    val alreadyCached = try {
                                        writer.exists(tileSource, tileIndex)
                                    } catch (_: Exception) {
                                        false
                                    }
                                    if (!alreadyCached) {
                                        val bytes = fetchTileBytes(tileIndex)
                                        if (bytes != null && bytes.isNotEmpty()) {
                                            ByteArrayInputStream(bytes).use { stream ->
                                                writer.saveFile(tileSource, tileIndex, stream, expiry)
                                            }
                                        } else {
                                            failedCounter.incrementAndGet()
                                        }
                                    }
                                    val done = completedCounter.incrementAndGet()
                                    if (done % 18 == 0 || done == total) {
                                        val mb = measureTileStorageMb()
                                        _status.value = OfflineMapStatus(
                                            isDownloading = true,
                                            downloadedTiles = done,
                                            totalTiles = total,
                                            storageSizeMb = mb,
                                            isReadyOffline = false,
                                            statusMessage = "Đang tải $done/$total mảnh (${String.format(java.util.Locale.US, "%.1f", mb)} MB)..."
                                        )
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                } finally {
                    try {
                        writer.onDetach()
                    } catch (_: Exception) {}
                }

                val finalMb = measureTileStorageMb()
                val succeeded = total - failedCounter.get()
                val isSuccess = succeeded >= (total * 0.85).toInt()
                prefs.edit()
                    .putInt("downloaded_tiles", succeeded)
                    .putInt("total_tiles", total)
                    .putBoolean("pack_complete", isSuccess)
                    .apply()

                _status.value = OfflineMapStatus(
                    isDownloading = false,
                    downloadedTiles = succeeded,
                    totalTiles = total,
                    storageSizeMb = finalMb,
                    isReadyOffline = isSuccess,
                    statusMessage = if (isSuccess) {
                        "Hoàn tất! Đã lưu vĩnh viễn ${String.format(java.util.Locale.US, "%.1f", finalMb)} MB bản đồ Offline"
                    } else {
                        "Đã tải $succeeded/$total mảnh (${String.format(java.util.Locale.US, "%.1f", finalMb)} MB) • Bấm tải tiếp để hoàn thiện"
                    },
                    errorMessage = if (isSuccess) null else "Một số mảnh bị gián đoạn mạng, dữ liệu đã tải vẫn được giữ nguyên."
                )
            } catch (_: CancellationException) {
                val mb = measureTileStorageMb()
                _status.value = _status.value.copy(
                    isDownloading = false,
                    storageSizeMb = mb,
                    statusMessage = "Đã tạm dừng tải (${String.format(java.util.Locale.US, "%.1f", mb)} MB đã được giữ lại)"
                )
            } catch (e: Exception) {
                val mb = measureTileStorageMb()
                _status.value = _status.value.copy(
                    isDownloading = false,
                    storageSizeMb = mb,
                    errorMessage = "Lỗi mạng khi tải bản đồ: ${e.localizedMessage ?: "Vui lòng thử lại"}"
                )
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    private fun measureTileStorageMb(): Double {
        val tileDir = File(appContext.filesDir, "osmdroid/tiles")
        val bytes = tileDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return bytes / (1024.0 * 1024.0)
    }

    private fun fetchTileBytes(tileIndex: Long): ByteArray? {
        val z = MapTileIndex.getZoom(tileIndex)
        val x = MapTileIndex.getX(tileIndex)
        val y = MapTileIndex.getY(tileIndex)
        val urls = listOf(
            "https://tile.openstreetmap.org/$z/$x/$y.png",
            "https://a.tile.openstreetmap.fr/hot/$z/$x/$y.png"
        )
        for (urlStr in urls) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "image/png,image/*;q=0.8")
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    return conn.inputStream.use { it.readBytes() }
                }
            } catch (_: Exception) {
                // Thử mirror dự phòng
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun buildHaGiangTileList(zones: List<Zone>, places: List<Place>): List<Long> {
        val tileSet = LinkedHashSet<Long>()

        // 1. Zoom 9..12: Toàn bộ khung tỉnh vùng cao Hà Giang Loop
        val minLat = 22.74
        val maxLat = 23.40
        val minLng = 104.86
        val maxLng = 105.48
        for (zoom in 9..12) {
            val xMin = lonToTileX(minLng, zoom)
            val xMax = lonToTileX(maxLng, zoom)
            val yMin = latToTileY(maxLat, zoom) // Lưu ý: trục Y tăng từ Bắc xuống Nam
            val yMax = latToTileY(minLat, zoom)
            for (x in xMin..xMax) {
                for (y in yMin..yMax) {
                    tileSet.add(MapTileIndex.getTileIndex(zoom, x, y))
                }
            }
        }

        // Nội suy các điểm dọc theo hành lang QL4C - ĐT176 - Du Già mỗi ~2km
        val corridorPoints = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until LOOP_CORRIDOR.lastIndex) {
            val (lat1, lng1) = LOOP_CORRIDOR[i]
            val (lat2, lng2) = LOOP_CORRIDOR[i + 1]
            val distKm = LocationMath.calculateDistanceKm(lat1, lng1, lat2, lng2)
            val steps = max(2, (distKm / 2.2).toInt())
            for (s in 0..steps) {
                val t = s.toDouble() / steps.toDouble()
                corridorPoints.add((lat1 + (lat2 - lat1) * t) to (lng1 + (lng2 - lng1) * t))
            }
        }

        // 2. Zoom 13 & 14: Bám sát toàn tuyến đường đèo + các điểm dừng chân
        for (zoom in 13..14) {
            val pad = if (zoom == 13) 1 else 1
            for ((lat, lng) in corridorPoints) {
                addTilesAround(tileSet, lat, lng, zoom, pad)
            }
            for (place in places) {
                addTilesAround(tileSet, place.lat, place.lng, zoom, 1)
            }
        }

        // 3. Zoom 15: Sắc nét chi tiết tại 8 trung tâm thị trấn/đèo lớn & tất cả địa điểm ghim
        for (zone in zones) {
            addTilesAround(tileSet, zone.centerLat, zone.centerLng, 15, 1)
        }
        for (place in places) {
            addTilesAround(tileSet, place.lat, place.lng, 15, 1)
        }

        return tileSet.toList()
    }

    private fun addTilesAround(set: MutableSet<Long>, lat: Double, lng: Double, zoom: Int, radius: Int) {
        val n = 1 shl zoom
        val cx = lonToTileX(lng, zoom)
        val cy = latToTileY(lat, zoom)
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val tx = (cx + dx).coerceIn(0, n - 1)
                val ty = (cy + dy).coerceIn(0, n - 1)
                set.add(MapTileIndex.getTileIndex(zoom, tx, ty))
            }
        }
    }

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return (((lon + 180.0) / 360.0) * n).toInt().coerceIn(0, n - 1)
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = Math.toRadians(lat.coerceIn(-85.0511, 85.0511))
        val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
        return y.toInt().coerceIn(0, n - 1)
    }
}
