package com.reed.hagianggo.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.OfflineMapManager
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

// MapView ngăn không cho Container cha (LazyColumn) cuộn cướp thao tác khi người dùng đang lướt bản đồ
class TouchableMapView(context: Context) : MapView(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

private val LOOP_OVERVIEW_CENTER = GeoPoint(23.0950, 105.1900)
private const val LOOP_OVERVIEW_ZOOM = 9.45

// Hành lang tuyến đường thực tế Hà Giang Loop (QL4C -> ĐT176 -> Du Già -> Đường Thượng -> Km0)
private val HA_GIANG_LOOP_WAYPOINTS = listOf(
    GeoPoint(22.827330, 104.984169), // Km0 TP. Hà Giang
    GeoPoint(22.841302, 104.995193), // Cửa ngõ QL4C
    GeoPoint(22.988169, 104.935900), // Dốc Bắc Sum
    GeoPoint(23.049316, 104.993021), // Cổng Trời Quản Bạ
    GeoPoint(23.059509, 104.996916), // Núi Đôi Quản Bạ
    GeoPoint(23.064631, 105.004454), // TT. Tam Sơn
    GeoPoint(23.069913, 105.034891), // Phở Tráng Kìm
    GeoPoint(23.083986, 105.037507), // Cầu Cán Tỷ
    GeoPoint(23.104330, 105.038522), // Cây Nghiến Cô Đơn
    GeoPoint(23.121011, 105.109948), // Rừng Thông Yên Minh
    GeoPoint(23.118139, 105.145654), // TT. Yên Minh
    GeoPoint(23.168925, 105.194145), // Dốc Thẩm Mã
    GeoPoint(23.181364, 105.198267), // Phố Cáo
    GeoPoint(23.227137, 105.202360), // Sủng Là - Nhà của Pao
    GeoPoint(23.256248, 105.262089), // Dinh Vua Mèo Sà Phìn
    GeoPoint(23.363461, 105.316334), // Cột Cờ Quốc gia Lũng Cú
    GeoPoint(23.279671, 105.360784), // Phố Cổ Đồng Văn
    GeoPoint(23.240621, 105.412083), // Đỉnh Đèo Mã Pì Lèng
    GeoPoint(23.206305, 105.416669), // Làng Pả Vi
    GeoPoint(23.164376, 105.408771), // TT. Mèo Vạc
    GeoPoint(23.104663, 105.260078), // Cua Chữ M
    GeoPoint(23.065592, 105.237738), // Ngã Ba Mậu Duệ
    GeoPoint(22.933029, 105.226014), // Xã Du Già
    GeoPoint(22.979511, 105.185435), // Đèo Đường Thượng
    GeoPoint(23.049316, 104.993021), // Cổng Trời / Thuận Hòa
    GeoPoint(22.827330, 104.984169)  // Về lại Km0 TP. Hà Giang
)

// Bộ nhớ đệm Icon Marker để tránh tạo lại Bitmap gây giật lag
private val markerDrawableCache = HashMap<String, BitmapDrawable>()
private var myLocationDrawableCache: BitmapDrawable? = null

private fun placesOverlayKey(places: List<Place>): String =
    places.joinToString(";") { "${it.id}:${it.lat}:${it.lng}:${it.name}:${it.category.code}" }

private fun markerCacheKey(place: Place): String =
    "${place.id}|${place.name}|${place.category.code}"

private fun offsetLatForSheet(
    baseLat: Double,
    zoom: Double,
    visibleMapHeightFraction: Float
): Double {
    if (visibleMapHeightFraction >= 0.90f) return baseLat
    val topHeaderFraction = 0.10
    val openCenterY = (topHeaderFraction + visibleMapHeightFraction.toDouble().coerceIn(0.22, 0.90)) / 2.0
    val fullScreenLatSpan = 1.42 * 2.0.pow(10.0 - zoom)
    val deltaSouth = (0.50 - openCenterY) * fullScreenLatSpan
    return baseLat - deltaSouth
}

private fun computeFocusCamera(
    focusedZone: Zone?,
    places: List<Place>,
    isMapFullscreen: Boolean = false,
    visibleMapHeightFraction: Float = 1.0f
): Pair<GeoPoint, Double> {
    val sheetZoomAdjust = when {
        isMapFullscreen -> 0.85
        visibleMapHeightFraction >= 0.70f -> 0.22
        visibleMapHeightFraction >= 0.42f -> -0.05
        else -> -0.35
    }
    if (focusedZone == null) {
        val zoom = LOOP_OVERVIEW_ZOOM + sheetZoomAdjust
        val adjLat = offsetLatForSheet(LOOP_OVERVIEW_CENTER.latitude, zoom, visibleMapHeightFraction)
        return GeoPoint(adjLat, LOOP_OVERVIEW_CENTER.longitude) to zoom
    }
    if (places.isEmpty()) {
        val zoom = 13.2 + sheetZoomAdjust
        val adjLat = offsetLatForSheet(focusedZone.centerLat, zoom, visibleMapHeightFraction)
        return GeoPoint(adjLat, focusedZone.centerLng) to zoom
    }
    val minLat = places.minOf { it.lat }
    val maxLat = places.maxOf { it.lat }
    val minLng = places.minOf { it.lng }
    val maxLng = places.maxOf { it.lng }
    val latSpan = (maxLat - minLat).coerceAtLeast(0.012)
    val lngSpan = (maxLng - minLng).coerceAtLeast(0.015)
    val midLat = (minLat + maxLat) / 2.0 + latSpan * 0.06
    val midLng = (minLng + maxLng) / 2.0

    val paddedLat = latSpan * 1.55
    val paddedLng = lngSpan * 1.65
    val zoomLat = 10.0 + (ln(0.41 / paddedLat) / ln(2.0))
    val zoomLng = 10.0 + (ln(0.44 / paddedLng) / ln(2.0))
    val zoom = (minOf(zoomLat, zoomLng) + sheetZoomAdjust).coerceIn(9.2, 15.0)
    val adjLat = offsetLatForSheet(midLat, zoom, visibleMapHeightFraction)
    return GeoPoint(adjLat, midLng) to zoom
}

@Composable
fun RealMapView(
    places: List<Place>,
    currentLat: Double?,
    currentLng: Double?,
    isGpsEnabled: Boolean = true,
    onRequestMyLocation: (() -> Unit)? = null,
    focusedZone: Zone? = null,
    focusedPlace: Place? = null,
    visibleMapHeightFraction: Float = 1.0f,
    controlsTopPadding: Dp = 12.dp,
    cardBottomPadding: Dp = 12.dp,
    edgeToEdge: Boolean = false,
    trackPoints: List<Pair<Double, Double>> = emptyList(),
    isMapFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    offlineMapStatus: OfflineMapStatus? = null,
    onOpenOfflineMapPack: (() -> Unit)? = null,
    onOpenSos: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var isTopoMode by remember { mutableStateOf(false) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var hasMounted by remember { mutableStateOf(false) }
    var pendingFlyToUser by remember { mutableStateOf(false) }

    // Tự động bay tới vị trí người dùng ngay khi GPS vừa bắt được tọa độ sau khi bật Vị trí
    LaunchedEffect(currentLat, currentLng, isGpsEnabled) {
        if (pendingFlyToUser && isGpsEnabled && currentLat != null && currentLng != null) {
            pendingFlyToUser = false
            val targetZoom = 14.5
            val adjLat = offsetLatForSheet(currentLat, targetZoom, visibleMapHeightFraction)
            mapViewInstance?.controller?.animateTo(GeoPoint(adjLat, currentLng), targetZoom, 750L)
        }
    }

    // Biến ghi nhớ danh sách places đã add vào overlays (gồm cả lat/lng) để tự động cập nhật ghim khi đổi tọa độ
    var lastPlacesKey by remember { mutableStateOf("") }
    var loopRouteRef by remember { mutableStateOf<Polyline?>(null) }
    var myLocationMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }

    // Khi người dùng chạm vào 1 địa điểm trong danh sách tấm trượt, bay bản đồ mượt tới địa điểm đó
    LaunchedEffect(focusedPlace?.id) {
        if (focusedPlace != null) {
            selectedPlace = focusedPlace
            val targetZoom = 14.6
            val adjLat = offsetLatForSheet(focusedPlace.lat, targetZoom, visibleMapHeightFraction)
            mapViewInstance?.controller?.animateTo(
                GeoPoint(adjLat, focusedPlace.lng),
                targetZoom,
                650L
            )
        }
    }

    // Tự đóng thẻ chi tiết nếu địa điểm đang chọn không nằm trong vùng lọc hiện tại
    LaunchedEffect(places) {
        val sel = selectedPlace
        if (sel != null) {
            selectedPlace = places.firstOrNull { it.id == sel.id }
        }
    }

    // Tự động di chuyển & căn khung bản đồ mượt mà khi người dùng lọc theo Chặng hoặc kéo nấc tấm trượt
    LaunchedEffect(focusedZone?.id, visibleMapHeightFraction) {
        if (!hasMounted) {
            hasMounted = true
            return@LaunchedEffect
        }
        val fp = focusedPlace
        if (fp != null && selectedPlace?.id == fp.id) {
            val targetZoom = 14.6
            val adjLat = offsetLatForSheet(fp.lat, targetZoom, visibleMapHeightFraction)
            mapViewInstance?.controller?.animateTo(GeoPoint(adjLat, fp.lng), targetZoom, 500L)
        } else {
            val (targetCenter, targetZoom) = computeFocusCamera(
                focusedZone = focusedZone,
                places = places,
                isMapFullscreen = isMapFullscreen,
                visibleMapHeightFraction = visibleMapHeightFraction
            )
            mapViewInstance?.controller?.animateTo(targetCenter, targetZoom, 550L)
        }
    }

    // Cấu hình OSMDroid lưu vĩnh viễn vào filesDir (không bao giờ bị trình dọn rác xóa)
    remember(context) {
        OfflineMapManager.configureOsmDroid(context)
        true
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(if (edgeToEdge) RectangleShape else RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                OfflineMapManager.configureOsmDroid(ctx)
                TouchableMapView(ctx).apply {
                    mapViewInstance = this
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setUseDataConnection(OfflineMapManager.hasValidatedInternet(ctx))
                    setTileSource(if (isTopoMode) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isFlingEnabled = true
                    isTilesScaledToDpi = true
                    isVerticalMapRepetitionEnabled = false
                    isHorizontalMapRepetitionEnabled = false
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    minZoomLevel = 8.0
                    maxZoomLevel = 19.0
                    val (initialCenter, initialZoom) = computeFocusCamera(
                        focusedZone = focusedZone,
                        places = places,
                        isMapFullscreen = isMapFullscreen,
                        visibleMapHeightFraction = visibleMapHeightFraction
                    )
                    controller.setZoom(initialZoom)
                    controller.setCenter(initialCenter)

                    // Khởi tạo overlays lần đầu
                    val currentKey = placesOverlayKey(places)
                    lastPlacesKey = currentKey

                    overlays.clear()

                    // 0. Đường dẫn vòng cung Hà Giang Loop (nằm dưới các ghim địa điểm)
                    val loopLine = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.argb(120, 234, 88, 12) // ElectricOrange mờ thanh lịch
                        outlinePaint.strokeWidth = 5.5f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        outlinePaint.pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
                        setPoints(HA_GIANG_LOOP_WAYPOINTS)
                        setOnClickListener { _, _, _ -> false }
                    }
                    loopRouteRef = loopLine
                    overlays.add(loopLine)

                    // 1. Thêm các place marker có gắn tên trực tiếp
                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(this, ctx, place) { p ->
                            selectedPlace = p
                            controller.animateTo(GeoPoint(p.lat, p.lng))
                        }
                        overlays.add(marker)
                    }

                    // 2. Thêm Polyline vệt đường Live GPS
                    val polyline = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.parseColor("#EA580C")
                        outlinePaint.strokeWidth = 9f
                        if (trackPoints.size >= 2) {
                            setPoints(trackPoints.map { GeoPoint(it.first, it.second) })
                        }
                        setOnClickListener { _, _, _ -> false }
                    }
                    polylineRef = polyline
                    overlays.add(polyline)

                    // 3. Thêm Live My Location Marker
                    val myLocMarker = Marker(this).apply {
                        title = "Vị trí của bạn"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = getMyLocationDrawable(ctx)
                        if (currentLat != null && currentLng != null) {
                            position = GeoPoint(currentLat, currentLng)
                            isEnabled = true
                        } else {
                            isEnabled = false
                        }
                    }
                    myLocationMarkerRef = myLocMarker
                    overlays.add(myLocMarker)

                    invalidate()
                }
            },
            update = { mapView ->
                // Tự động ngắt kết nối mạng của MapView khi rớt mạng / sóng 2G ảo để đọc cache 0ms không treo
                val hasInternet = OfflineMapManager.hasValidatedInternet(context)
                if (mapView.useDataConnection() != hasInternet) {
                    mapView.setUseDataConnection(hasInternet)
                }

                // Chỉ đổi tile source khi người dùng bấm nút đổi layer
                val currentTileSource = if (isTopoMode) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
                if (mapView.tileProvider.tileSource != currentTileSource) {
                    mapView.setTileSource(currentTileSource)
                }

                // Cập nhật danh sách place markers khi danh sách hoặc tọa độ thay đổi
                val currentKey = placesOverlayKey(places)
                if (currentKey != lastPlacesKey) {
                    lastPlacesKey = currentKey

                    mapView.overlays.clear()
                    loopRouteRef?.let { mapView.overlays.add(it) }

                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(mapView, context, place) { p ->
                            selectedPlace = p
                            mapView.controller.animateTo(GeoPoint(p.lat, p.lng))
                        }
                        mapView.overlays.add(marker)
                    }
                    polylineRef?.let { mapView.overlays.add(it) }
                    myLocationMarkerRef?.let { mapView.overlays.add(it) }
                    mapView.invalidate()
                }

                // Cập nhật vị trí GPS người dùng (không xóa overlay, chỉ set position mượt mà)
                myLocationMarkerRef?.let { marker ->
                    if (isGpsEnabled && currentLat != null && currentLng != null) {
                        marker.position = GeoPoint(currentLat, currentLng)
                        marker.isEnabled = true
                    } else {
                        marker.isEnabled = false
                    }
                    mapView.invalidate()
                }

                // Cập nhật vệt đường polyline (nếu có điểm mới)
                polylineRef?.let { poly ->
                    if (trackPoints.size >= 2) {
                        poly.setPoints(trackPoints.map { GeoPoint(it.first, it.second) })
                    }
                }
            }
        )

        // Các nút điều khiển nổi kính mờ (AllTrails Floating Map Controls)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = controlsTopPadding, end = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Nút định vị vị trí hiện tại của tôi
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                    .clip(CircleShape)
                    .background(SurfaceGlass)
                    .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                    .springClickable {
                        onRequestMyLocation?.invoke()
                        if (isGpsEnabled && currentLat != null && currentLng != null) {
                            val targetZoom = 14.5
                            val adjLat = offsetLatForSheet(currentLat, targetZoom, visibleMapHeightFraction)
                            mapViewInstance?.controller?.animateTo(GeoPoint(adjLat, currentLng), targetZoom, 800L)
                        } else {
                            pendingFlyToUser = true
                            if (!isGpsEnabled) {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                } catch (_: Exception) {}
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Vị trí của tôi",
                    tint = if (isGpsEnabled && currentLat != null) EmeraldGreenText else TextMuted,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Nút bao trọn toàn bộ vòng lặp Hà Giang
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                    .clip(CircleShape)
                    .background(SurfaceGlass)
                    .border(BorderStroke(1.dp, BorderSubtle), CircleShape)
                    .springClickable {
                        selectedPlace = null
                        val (overviewCenter, overviewZoom) = computeFocusCamera(
                            focusedZone = null,
                            places = emptyList(),
                            isMapFullscreen = isMapFullscreen,
                            visibleMapHeightFraction = visibleMapHeightFraction
                        )
                        mapViewInstance?.controller?.animateTo(overviewCenter, overviewZoom, 700L)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CropFree,
                    contentDescription = "Toàn cảnh Hà Giang",
                    tint = ElectricOrange,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Nút chuyển đổi kiểu bản đồ (Tiêu chuẩn ⟷ Địa hình Topo)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                    .clip(CircleShape)
                    .background(if (isTopoMode) EmeraldGreenSoft else SurfaceGlass)
                    .border(BorderStroke(1.dp, if (isTopoMode) EmeraldGreenBorder else BorderSubtle), CircleShape)
                    .springClickable { isTopoMode = !isTopoMode },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = "Đổi kiểu bản đồ",
                    tint = if (isTopoMode) EmeraldGreenText else TextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Nút Tải / Kiểm tra Bản đồ Offline vĩnh viễn (filesDir)
            if (onOpenOfflineMapPack != null) {
                val isReady = offlineMapStatus?.isReadyOffline == true
                val isDownloading = offlineMapStatus?.isDownloading == true
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                        .clip(CircleShape)
                        .background(if (isReady) EmeraldGreenSoft else SurfaceGlass)
                        .border(
                            BorderStroke(
                                1.dp,
                                when {
                                    isReady -> EmeraldGreenBorder
                                    isDownloading -> ElectricOrange
                                    else -> BorderSubtle
                                }
                            ),
                            CircleShape
                        )
                        .springClickable(onClick = onOpenOfflineMapPack),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.2.dp,
                            color = ElectricOrange
                        )
                    } else {
                        Icon(
                            if (isReady) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                            contentDescription = "Bản đồ Offline",
                            tint = if (isReady) EmeraldGreenText else ElectricOrangeHover,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Nút Cứu hộ Đèo núi SOS 1 chạm trên bản đồ
            if (onOpenSos != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                        .clip(CircleShape)
                        .background(RoseDestructiveSoft)
                        .border(BorderStroke(1.dp, RoseDestructiveBorder), CircleShape)
                        .springClickable(onClick = onOpenSos),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Sos,
                        contentDescription = "Cứu hộ SOS",
                        tint = RoseDestructiveText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Nút Phóng to toàn màn hình / Thu nhỏ (nếu truyền callback)
            if (onToggleFullscreen != null) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                        .clip(CircleShape)
                        .background(if (isMapFullscreen) ElectricOrangeSoft else SurfaceGlass)
                        .border(BorderStroke(1.dp, if (isMapFullscreen) ElectricOrangeBorder else BorderSubtle), CircleShape)
                        .springClickable(onClick = onToggleFullscreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isMapFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMapFullscreen) "Thu nhỏ" else "Toàn màn hình",
                        tint = if (isMapFullscreen) ElectricOrangeHover else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Card chi tiết địa điểm nổi kiểu Airbnb khi chạm vào marker trên bản đồ
        AnimatedVisibility(
            visible = selectedPlace != null,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = cardBottomPadding)
        ) {
            selectedPlace?.let { place ->
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ShadcnBadge(
                                        text = place.category.label,
                                        variant = ShadcnBadgeVariant.Orange
                                    )
                                    if (place.altitudeMeters > 0) {
                                        Text(
                                            text = "• ${place.altitudeMeters}m",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = "• ${String.format(Locale.US, "%.5f, %.5f", place.lat, place.lng)}",
                                        style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                        color = TextMuted
                                    )
                                }
                            }
                            IconButton(
                                onClick = { selectedPlace = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (place.addressHint.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "📍 ${place.addressHint}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary
                            )
                        }

                        if (place.practicalTip.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 ${place.practicalTip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShadcnButton(
                                onClick = {
                                    LocationMath.openNavigationIntent(context, place.lat, place.lng, place.name)
                                },
                                variant = ShadcnButtonVariant.Primary,
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Dẫn đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }

                            if (place.phone.isNotBlank()) {
                                ShadcnButton(
                                    onClick = {
                                        LocationMath.openDialerIntent(context, place.phone)
                                    },
                                    variant = ShadcnButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f).height(38.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Gọi điện", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun createOrGetPlaceMarker(
    mapView: MapView,
    context: Context,
    place: Place,
    onPlaceClick: (Place) -> Unit
): Marker {
    val cacheKey = markerCacheKey(place)
    val cachedDrawable = markerDrawableCache[cacheKey] ?: run {
        val bitmap = createLabeledPinBitmap(place)
        val drawable = BitmapDrawable(context.resources, bitmap)
        markerDrawableCache[cacheKey] = drawable
        drawable
    }

    return Marker(mapView).apply {
        position = GeoPoint(place.lat, place.lng)
        title = place.name
        snippet = place.category.label
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = cachedDrawable
        setOnMarkerClickListener { _, _ ->
            onPlaceClick(place)
            true
        }
    }
}

/**
 * Tạo huy hiệu ghim (Marker Pill) hiển thị trực tiếp TÊN ĐỊA ĐIỂM + CHẤM MÀU DANH MỤC
 * Giúp người dùng nhìn vào bản đồ là biết ngay vị trí đó là gì.
 */
private fun createLabeledPinBitmap(place: Place): Bitmap {
    val text = place.name
    val category = place.category

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = android.graphics.Color.parseColor("#0F172A") // Slate Dark
    }

    val textWidth = textPaint.measureText(text)
    val textHeight = 28f

    val paddingH = 14f
    val paddingV = 8f
    val dotRadius = 8f
    val dotMarginRight = 8f

    val pillWidth = paddingH + (dotRadius * 2) + dotMarginRight + textWidth + paddingH
    val pillHeight = paddingV * 2 + textHeight
    val pointerHeight = 10f
    val totalHeight = pillHeight + pointerHeight

    val bitmap = Bitmap.createBitmap(pillWidth.toInt().coerceAtLeast(60), totalHeight.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val colorInt = when (category) {
        PlaceCategory.FUEL -> android.graphics.Color.parseColor("#D97706") // Fuel Gold
        PlaceCategory.MECHANIC_SOS -> android.graphics.Color.parseColor("#DC2626") // Alert Red
        PlaceCategory.CHECKIN_VIEW -> android.graphics.Color.parseColor("#0D9488") // Highland Green
        PlaceCategory.EAT -> android.graphics.Color.parseColor("#EA580C") // Sunset Orange
        PlaceCategory.CAFE_REST -> android.graphics.Color.parseColor("#F59E0B") // Amber
        PlaceCategory.HOMESTAY -> android.graphics.Color.parseColor("#475569") // Slate
    }

    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // 1. Thân nhãn bo góc chữ nhật
    val rect = RectF(2f, 2f, pillWidth - 2f, pillHeight)
    canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
    canvas.drawRoundRect(rect, 12f, 12f, borderPaint)

    // 2. Mũi tên tam giác chỉ đúng tọa độ dưới đáy
    val arrowPath = android.graphics.Path().apply {
        val centerX = pillWidth / 2f
        moveTo(centerX - 7f, pillHeight - 1f)
        lineTo(centerX + 7f, pillHeight - 1f)
        lineTo(centerX, totalHeight - 1f)
        close()
    }
    canvas.drawPath(arrowPath, bgPaint)
    val arrowBorderPaint = Paint(borderPaint).apply { strokeWidth = 2.5f }
    canvas.drawLine(pillWidth / 2f - 7f, pillHeight, pillWidth / 2f, totalHeight - 1f, arrowBorderPaint)
    canvas.drawLine(pillWidth / 2f + 7f, pillHeight, pillWidth / 2f, totalHeight - 1f, arrowBorderPaint)

    // 3. Chấm tròn danh mục màu ở bên trái
    val dotCenterY = pillHeight / 2f
    val dotCenterX = paddingH + dotRadius
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, dotPaint)
    val innerWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    }
    canvas.drawCircle(dotCenterX, dotCenterY, 3f, innerWhitePaint)

    // 4. Vẽ tên địa điểm
    val textX = dotCenterX + dotRadius + dotMarginRight
    val textY = dotCenterY + (textHeight / 2f) - 4f
    canvas.drawText(text, textX, textY, textPaint)

    return bitmap
}

private fun getMyLocationDrawable(context: Context): BitmapDrawable {
    return myLocationDrawableCache ?: run {
        val size = 56
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Vòng phát sóng mờ
        paint.color = android.graphics.Color.parseColor("#38BDF8")
        paint.alpha = 90
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)

        // Viền trắng
        paint.alpha = 255
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f, 13f, paint)

        // Chấm xanh lam tâm
        paint.color = android.graphics.Color.parseColor("#0284C7")
        canvas.drawCircle(size / 2f, size / 2f, 9f, paint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        myLocationDrawableCache = drawable
        drawable
    }
}
