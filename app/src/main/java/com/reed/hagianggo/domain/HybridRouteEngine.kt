package com.reed.hagianggo.domain

import android.content.Context
import com.reed.hagianggo.model.ManeuverType
import com.reed.hagianggo.model.NavigationRoute
import com.reed.hagianggo.model.NavigationStep
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.RouteSource
import com.reed.hagianggo.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Động cơ định tuyến Hybrid 3 lớp cho Hà Giang Go:
 * 1. Online OSRM (`router.project-osrm.org`): chi tiết từng ngã rẽ / đường nhỏ toàn quốc, kèm steps Turn-by-Turn.
 * 2. Bộ đệm ổ đĩa (`filesDir/nav_routes_cache.json`): giữ lại tới 20 tuyến đã tải khi rớt sóng giữa đèo.
 * 3. Offline Graph Router (`OfflineRoadGraphRouter`): chạy Dijkstra trực tiếp trên 6.791 điểm đường đèo Hà Giang (< 20ms).
 */
class HybridRouteEngine(context: Context) {

    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, "nav_routes_cache.json")
    private val cacheLock = Any()

    companion object {
        private const val MAX_CACHED_ROUTES = 20
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val READ_TIMEOUT_MS = 4500
    }

    suspend fun computeRoute(
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String,
        preferOnline: Boolean = true
    ): NavigationRoute = withContext(Dispatchers.IO) {
        val cacheKey = buildCacheKey(originLat, originLng, destination.id)

        // Lớp 1: Online OSRM khi có Internet xác thực
        if (preferOnline && OfflineMapManager.hasValidatedInternet(appContext)) {
            val onlineRoute = tryFetchOsrmRoute(originLat, originLng, destination, vehicle, originLabel)
            if (onlineRoute != null) {
                saveRouteToCache(cacheKey, onlineRoute)
                return@withContext onlineRoute
            }
        }

        // Lớp 2: Tuyến OSRM đã cache trong filesDir (hữu ích khi rớt mạng giữa đèo)
        loadCachedRoute(cacheKey, destination, originLabel)?.let { cached ->
            return@withContext cached
        }

        // Lớp 3: Bộ định tuyến Đồ thị Hà Giang Loop 100% Offline (< 20ms)
        OfflineRoadGraphRouter.computeRoute(
            context = appContext,
            originLat = originLat,
            originLng = originLng,
            destination = destination,
            vehicle = vehicle,
            originLabel = originLabel
        )
    }

    private fun buildCacheKey(lat: Double, lng: Double, destId: String): String {
        val rLat = String.format(Locale.US, "%.3f", lat)
        val rLng = String.format(Locale.US, "%.3f", lng)
        return "$rLat,$rLng->$destId"
    }

    private fun tryFetchOsrmRoute(
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String
    ): NavigationRoute? {
        var conn: HttpURLConnection? = null
        return try {
            val oLon = String.format(Locale.US, "%.6f", originLng)
            val oLat = String.format(Locale.US, "%.6f", originLat)
            val dLon = String.format(Locale.US, "%.6f", destination.lng)
            val dLat = String.format(Locale.US, "%.6f", destination.lat)
            val endpoint = "https://router.project-osrm.org/route/v1/driving/" +
                "$oLon,$oLat;$dLon,$dLat?overview=full&geometries=geojson&steps=true"

            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", appContext.packageName)
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode !in 200..299) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseOsrmResponse(body, originLat, originLng, destination, vehicle, originLabel)
        } catch (_: Exception) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun parseOsrmResponse(
        jsonBody: String,
        originLat: Double,
        originLng: Double,
        destination: Place,
        vehicle: VehicleType,
        originLabel: String
    ): NavigationRoute? {
        val root = JSONObject(jsonBody)
        if (root.optString("code") != "Ok") return null
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null

        val routeObj = routes.getJSONObject(0)
        val geometry = routeObj.optJSONObject("geometry") ?: return null
        val coords = geometry.optJSONArray("coordinates") ?: return null
        if (coords.length() < 2) return null

        val polyline = ArrayList<Pair<Double, Double>>(coords.length() + 1)
        for (i in 0 until coords.length()) {
            val pt = coords.getJSONArray(i)
            // Lưu ý: GeoJSON luôn trả [longitude, latitude]
            val lng = pt.getDouble(0)
            val lat = pt.getDouble(1)
            polyline.add(lat to lng)
        }

        val osrmDistM = routeObj.optDouble("distance", 0.0)
        val osrmDurSec = routeObj.optDouble("duration", 0.0).roundToInt()

        // Hiệu chỉnh ETA theo tốc độ thực tế đường đèo (tránh OSRM dự báo tốc độ cao như đồng bằng)
        val distKm = osrmDistM / 1000.0
        val mountainAvgKmh = if (vehicle == VehicleType.MOTORBIKE) 28.0 else 34.0
        val mountainMinSec = ((distKm / mountainAvgKmh) * 3600.0).roundToInt()
        val calibratedDurationSec = if (distKm <= 220.0 || vehicle == VehicleType.MOTORBIKE) {
            maxOf(osrmDurSec, mountainMinSec).coerceAtLeast(60)
        } else {
            osrmDurSec.coerceAtLeast(60)
        }

        val parsedSteps = ArrayList<NavigationStep>()
        val legs = routeObj.optJSONArray("legs")
        var minPolySearchIdx = 0
        if (legs != null && legs.length() > 0) {
            val stepsArr = legs.getJSONObject(0).optJSONArray("steps")
            if (stepsArr != null) {
                for (i in 0 until stepsArr.length()) {
                    val sObj = stepsArr.getJSONObject(i)
                    val maneuverObj = sObj.optJSONObject("maneuver") ?: continue
                    val mTypeStr = maneuverObj.optString("type", "turn")
                    val mModStr = maneuverObj.optString("modifier", "")
                    val exitNum = maneuverObj.optInt("exit", 0)
                    val mLoc = maneuverObj.optJSONArray("location")
                    val mLng = if (mLoc != null && mLoc.length() >= 2) mLoc.getDouble(0) else polyline.last().second
                    val mLat = if (mLoc != null && mLoc.length() >= 2) mLoc.getDouble(1) else polyline.last().first

                    val roadName = sObj.optString("name", "").trim()
                    val maneuver = mapOsrmManeuver(mTypeStr, mModStr)
                    val instructionVi = formatVietnameseInstruction(
                        type = mTypeStr,
                        modifier = mModStr,
                        maneuver = maneuver,
                        roadName = roadName,
                        exitNum = exitNum,
                        destName = destination.name
                    )

                    val polyIdx = if (maneuver == ManeuverType.ARRIVE) {
                        polyline.lastIndex
                    } else if (i == 0) {
                        0
                    } else {
                        findClosestForwardIndex(polyline, minPolySearchIdx, mLat, mLng)
                    }
                    minPolySearchIdx = polyIdx

                    parsedSteps.add(
                        NavigationStep(
                            index = parsedSteps.size,
                            instructionVi = instructionVi,
                            roadName = roadName,
                            maneuver = maneuver,
                            distanceMeters = sObj.optDouble("distance", 0.0),
                            durationSeconds = sObj.optDouble("duration", 0.0).roundToInt(),
                            maneuverLat = mLat,
                            maneuverLng = mLng,
                            routePointIndex = polyIdx
                        )
                    )
                }
            }
        }

        if (parsedSteps.isEmpty()) {
            parsedSteps.add(
                NavigationStep(
                    index = 0,
                    instructionVi = "Khởi hành tới ${destination.name}",
                    roadName = "",
                    maneuver = ManeuverType.DEPART,
                    distanceMeters = osrmDistM,
                    durationSeconds = calibratedDurationSec,
                    maneuverLat = polyline.first().first,
                    maneuverLng = polyline.first().second,
                    routePointIndex = 0
                )
            )
            parsedSteps.add(
                NavigationStep(
                    index = 1,
                    instructionVi = "Đã đến ${destination.name}",
                    roadName = destination.name,
                    maneuver = ManeuverType.ARRIVE,
                    distanceMeters = 0.0,
                    durationSeconds = 0,
                    maneuverLat = destination.lat,
                    maneuverLng = destination.lng,
                    routePointIndex = polyline.lastIndex
                )
            )
        }

        return NavigationRoute(
            originLat = originLat,
            originLng = originLng,
            destination = destination,
            polyline = polyline,
            steps = parsedSteps,
            totalDistanceMeters = osrmDistM,
            totalDurationSeconds = calibratedDurationSec,
            source = RouteSource.OSRM_ONLINE,
            originLabel = originLabel
        )
    }

    private fun findClosestForwardIndex(
        polyline: List<Pair<Double, Double>>,
        startIdx: Int,
        targetLat: Double,
        targetLng: Double
    ): Int {
        var bestIdx = startIdx.coerceIn(0, polyline.lastIndex)
        var bestDist = Double.MAX_VALUE
        for (i in bestIdx until polyline.size) {
            val pt = polyline[i]
            val d = LocationMath.calculateDistanceKm(pt.first, pt.second, targetLat, targetLng)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            } else if (d > bestDist + 0.35 && bestDist < 0.08) {
                // Đã khớp rất sát (<80m) và đang đi xa dần điểm maneuver
                break
            }
        }
        return bestIdx
    }

    private fun mapOsrmManeuver(type: String, modifier: String): ManeuverType {
        return when (type.lowercase(Locale.US)) {
            "depart" -> ManeuverType.DEPART
            "arrive" -> ManeuverType.ARRIVE
            "roundabout", "rotary", "exit roundabout", "exit rotary" -> ManeuverType.ROUNDABOUT
            "merge" -> ManeuverType.MERGE
            else -> when (modifier.lowercase(Locale.US)) {
                "uturn" -> ManeuverType.UTURN
                "sharp left" -> ManeuverType.SHARP_LEFT
                "left" -> ManeuverType.LEFT
                "slight left" -> ManeuverType.SLIGHT_LEFT
                "sharp right" -> ManeuverType.SHARP_RIGHT
                "right" -> ManeuverType.RIGHT
                "slight right" -> ManeuverType.SLIGHT_RIGHT
                "straight" -> ManeuverType.STRAIGHT
                else -> ManeuverType.STRAIGHT
            }
        }
    }

    private fun formatVietnameseInstruction(
        type: String,
        modifier: String,
        maneuver: ManeuverType,
        roadName: String,
        exitNum: Int,
        destName: String
    ): String {
        val roadSuffix = if (roadName.isNotBlank()) " vào $roadName" else ""
        return when (maneuver) {
            ManeuverType.DEPART -> if (roadName.isNotBlank()) "Xuất phát theo $roadName" else "Bắt đầu di chuyển"
            ManeuverType.ARRIVE -> "Đã đến $destName"
            ManeuverType.ROUNDABOUT -> if (exitNum > 0) "Vào vòng xuyến, chọn lối ra thứ $exitNum$roadSuffix" else "Qua vòng xuyến$roadSuffix"
            ManeuverType.UTURN -> "Quay đầu$roadSuffix"
            ManeuverType.SHARP_LEFT -> "Rẽ gắt sang trái$roadSuffix"
            ManeuverType.LEFT -> "Rẽ trái$roadSuffix"
            ManeuverType.SLIGHT_LEFT -> "Đi chếch sang trái$roadSuffix"
            ManeuverType.SHARP_RIGHT -> "Rẽ gắt sang phải$roadSuffix"
            ManeuverType.RIGHT -> "Rẽ phải$roadSuffix"
            ManeuverType.SLIGHT_RIGHT -> "Đi chếch sang phải$roadSuffix"
            ManeuverType.MERGE -> "Nhập làn$roadSuffix"
            ManeuverType.STRAIGHT -> when (type.lowercase(Locale.US)) {
                "continue", "new name" -> if (roadName.isNotBlank()) "Tiếp tục theo $roadName" else "Đi thẳng"
                else -> if (roadName.isNotBlank()) "Đi thẳng theo $roadName" else "Đi thẳng"
            }
        }
    }

    // ====== Bộ đệm tuyến đường lưu trong filesDir (JSON gọn nhẹ, tối đa 20 tuyến) ======

    private fun saveRouteToCache(cacheKey: String, route: NavigationRoute) {
        synchronized(cacheLock) {
            try {
                val arr = if (cacheFile.exists()) {
                    JSONArray(cacheFile.readText())
                } else {
                    JSONArray()
                }
                val filtered = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("cacheKey") != cacheKey) {
                        filtered.add(item)
                    }
                }
                filtered.add(0, serializeRoute(cacheKey, route))
                val trimmed = JSONArray()
                for (item in filtered.take(MAX_CACHED_ROUTES)) {
                    trimmed.put(item)
                }
                cacheFile.writeText(trimmed.toString())
            } catch (_: Exception) {
                // Không để lỗi I/O làm gián đoạn dẫn đường
            }
        }
    }

    private fun loadCachedRoute(
        cacheKey: String,
        destination: Place,
        originLabel: String
    ): NavigationRoute? {
        synchronized(cacheLock) {
            return try {
                if (!cacheFile.exists()) return null
                val arr = JSONArray(cacheFile.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    if (obj.optString("cacheKey") == cacheKey) {
                        return deserializeRoute(obj, destination, originLabel)
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun serializeRoute(cacheKey: String, route: NavigationRoute): JSONObject {
        val polyArr = JSONArray()
        for (pt in route.polyline) {
            polyArr.put(JSONArray().apply {
                put(pt.first)
                put(pt.second)
            })
        }
        val stepsArr = JSONArray()
        for (s in route.steps) {
            stepsArr.put(JSONObject().apply {
                put("index", s.index)
                put("instructionVi", s.instructionVi)
                put("roadName", s.roadName)
                put("maneuver", s.maneuver.name)
                put("distanceMeters", s.distanceMeters)
                put("durationSeconds", s.durationSeconds)
                put("maneuverLat", s.maneuverLat)
                put("maneuverLng", s.maneuverLng)
                put("routePointIndex", s.routePointIndex)
            })
        }
        return JSONObject().apply {
            put("cacheKey", cacheKey)
            put("originLat", route.originLat)
            put("originLng", route.originLng)
            put("totalDistanceMeters", route.totalDistanceMeters)
            put("totalDurationSeconds", route.totalDurationSeconds)
            put("computedAtMillis", route.computedAtMillis)
            put("polyline", polyArr)
            put("steps", stepsArr)
        }
    }

    private fun deserializeRoute(
        obj: JSONObject,
        destination: Place,
        originLabel: String
    ): NavigationRoute? {
        val polyArr = obj.optJSONArray("polyline") ?: return null
        if (polyArr.length() < 2) return null
        val polyline = ArrayList<Pair<Double, Double>>(polyArr.length())
        for (i in 0 until polyArr.length()) {
            val pt = polyArr.getJSONArray(i)
            polyline.add(pt.getDouble(0) to pt.getDouble(1))
        }
        val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
        val steps = ArrayList<NavigationStep>(stepsArr.length())
        for (i in 0 until stepsArr.length()) {
            val s = stepsArr.getJSONObject(i)
            val maneuver = try {
                ManeuverType.valueOf(s.optString("maneuver", "STRAIGHT"))
            } catch (_: Exception) {
                ManeuverType.STRAIGHT
            }
            steps.add(
                NavigationStep(
                    index = s.optInt("index", i),
                    instructionVi = s.optString("instructionVi", "Đi thẳng"),
                    roadName = s.optString("roadName", ""),
                    maneuver = maneuver,
                    distanceMeters = s.optDouble("distanceMeters", 0.0),
                    durationSeconds = s.optInt("durationSeconds", 0),
                    maneuverLat = s.optDouble("maneuverLat", destination.lat),
                    maneuverLng = s.optDouble("maneuverLng", destination.lng),
                    routePointIndex = s.optInt("routePointIndex", 0).coerceIn(0, polyline.lastIndex)
                )
            )
        }
        return NavigationRoute(
            originLat = obj.optDouble("originLat", polyline.first().first),
            originLng = obj.optDouble("originLng", polyline.first().second),
            destination = destination,
            polyline = polyline,
            steps = steps,
            totalDistanceMeters = obj.optDouble("totalDistanceMeters", 0.0),
            totalDurationSeconds = obj.optInt("totalDurationSeconds", 60),
            source = RouteSource.OSRM_CACHED,
            originLabel = originLabel,
            computedAtMillis = obj.optLong("computedAtMillis", System.currentTimeMillis())
        )
    }
}
