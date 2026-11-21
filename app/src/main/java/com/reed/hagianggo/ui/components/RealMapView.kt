package com.reed.hagianggo.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

// MapView ngăn không cho Container cha (LazyColumn) cuộn cướp thao tác khi người dùng đang lướt bản đồ,
// đồng thời phát hiện cử chỉ kéo tay để nhả chế độ bám camera khi đang dẫn đường.
class TouchableMapView(context: Context) : MapView(context) {
    var onUserMapDrag: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private var downX = 0f
    private var downY = 0f
    private var reportedDragForGesture = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                reportedDragForGesture = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!reportedDragForGesture) {
                    reportedDragForGesture = true
                    onUserMapDrag?.invoke()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!reportedDragForGesture &&
                    (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop)
                ) {
                    reportedDragForGesture = true
                    onUserMapDrag?.invoke()
                }
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                reportedDragForGesture = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

enum class MapLayerStyle(val label: String) {
    STANDARD_OSM("Chuẩn OpenStreetMap"),
    OPEN_TOPO("Địa hình Live (OpenTopo)"),
    OFFLINE_TOPO("Địa hình Offline (filesDir)")
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
    preferredLayerStyle: MapLayerStyle? = null,
    onLayerStyleChanged: ((MapLayerStyle) -> Unit)? = null,
    activeNavigation: ActiveNavigationState? = null,
    currentBearingDeg: Float = 0f,
    overviewRouteTriggerSeq: Int = 0,
    recenterTriggerSeq: Int = 0,
    onStartInAppNav: ((Place) -> Unit)? = null,
    onUserPanMap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var localLayerOverride by remember { mutableStateOf<MapLayerStyle?>(null) }
    var layerToastLabel by remember { mutableStateOf<String?>(null) }
    var layerToastSeq by remember { mutableIntStateOf(0) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var hasMounted by remember { mutableStateOf(false) }
    var pendingFlyToUser by remember { mutableStateOf(false) }
    val isInNavigation = activeNavigation != null

    val latestOnUserPan by rememberUpdatedState(onUserPanMap)

    val hasInternet = OfflineMapManager.hasValidatedInternet(context)
    val effectiveLayerStyle = preferredLayerStyle
        ?: localLayerOverride
        ?: if (!hasInternet && offlineMapStatus?.isReadyOffline == true) {
            MapLayerStyle.OFFLINE_TOPO
        } else {
            MapLayerStyle.STANDARD_OSM
        }

    // Tự ẩn nhãn thông báo đổi kiểu bản đồ sau 2.2 giây
    LaunchedEffect(layerToastSeq) {
        if (layerToastSeq > 0) {
            delay(2200L)
            layerToastLabel = null
        }
    }

    // Đóng thẻ chi tiết explore khi chuyển sang dẫn đường trực tiếp
    LaunchedEffect(isInNavigation) {
        if (isInNavigation) {
            selectedPlace = null
        }
    }

    // Làm mới bộ nhớ đệm RAM ngay khi dựng xong gói Offline để hiển thị tức thì
    LaunchedEffect(offlineMapStatus?.isReadyOffline) {
        if (offlineMapStatus?.isReadyOffline == true) {
            mapViewInstance?.tileProvider?.clearTileCache()
            mapViewInstance?.invalidate()
        }
    }

    // Tự động bay tới vị trí người dùng ngay khi GPS vừa bắt được tọa độ sau khi bật Vị trí
    LaunchedEffect(currentLat, currentLng, isGpsEnabled) {
        if (!isInNavigation && pendingFlyToUser && isGpsEnabled && currentLat != null && currentLng != null) {
            pendingFlyToUser = false
            val targetZoom = 14.5
            val adjLat = offsetLatForSheet(currentLat, targetZoom, visibleMapHeightFraction)
            mapViewInstance?.controller?.animateTo(GeoPoint(adjLat, currentLng), targetZoom, 750L)
        }
    }

    // Biến ghi nhớ danh sách places đã add vào overlays (gồm cả lat/lng) để tự động cập nhật ghim khi đổi tọa độ
    var lastPlacesKey by remember { mutableStateOf("") }
    var loopRouteRef by remember { mutableStateOf<Polyline?>(null) }
    var navPassedPolylineRef by remember { mutableStateOf<Polyline?>(null) }
    var navRemainingCasingRef by remember { mutableStateOf<Polyline?>(null) }
    var navRemainingPolylineRef by remember { mutableStateOf<Polyline?>(null) }
    var navDestinationMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var myLocationMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }
    var smoothedBearingDeg by remember { mutableFloatStateOf(0f) }
    var lastFittedPreviewRouteStamp by remember { mutableLongStateOf(0L) }
    var lastHandledOverviewSeq by remember { mutableIntStateOf(0) }
    var lastNavCameraAnimateMs by remember { mutableLongStateOf(0L) }
    var lastNavFollowModeApplied by remember { mutableStateOf<NavCameraFollowMode?>(null) }
    var rotationOverlayRef by remember { mutableStateOf<RotationGestureOverlay?>(null) }
    var mapRotationDeg by remember { mutableFloatStateOf(0f) }

    val navRoute = activeNavigation?.route
    val desiredNavBearing = remember(
        navRoute?.computedAtMillis,
        activeNavigation?.matchedSegmentIndex,
        currentBearingDeg
    ) {
        if (currentBearingDeg != 0f) {
            currentBearingDeg
        } else if (navRoute != null && navRoute.polyline.size >= 2) {
            val seg = activeNavigation.matchedSegmentIndex.coerceIn(0, navRoute.polyline.lastIndex - 1)
            val ahead = (seg + 3).coerceAtMost(navRoute.polyline.lastIndex)
            LocationMath.calculateBearingDeg(
                navRoute.polyline[seg].first,
                navRoute.polyline[seg].second,
                navRoute.polyline[ahead].first,
                navRoute.polyline[ahead].second
            )
        } else {
            0f
        }
    }

    LaunchedEffect(desiredNavBearing, isInNavigation) {
        if (isInNavigation) {
            smoothedBearingDeg = LocationMath.smoothBearingDeg(smoothedBearingDeg, desiredNavBearing, 0.45f)
        } else {
            smoothedBearingDeg = 0f
        }
    }

    // Khi vừa tải xong tuyến xem trước từ Km0 hoặc người dùng bấm "Toàn tuyến", tự động thu phóng vừa khít cả tuyến
    LaunchedEffect(navRoute?.computedAtMillis, activeNavigation?.isUsingZonePreviewOrigin, overviewRouteTriggerSeq) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        val route = navRoute ?: return@LaunchedEffect
        if (route.polyline.size < 2) return@LaunchedEffect

        val overviewRequested = overviewRouteTriggerSeq != lastHandledOverviewSeq
        val newPreviewLoaded = (activeNavigation?.isUsingZonePreviewOrigin == true) &&
            lastFittedPreviewRouteStamp != route.computedAtMillis

        if (overviewRequested || newPreviewLoaded) {
            lastHandledOverviewSeq = overviewRouteTriggerSeq
            lastFittedPreviewRouteStamp = route.computedAtMillis
            val geoPts = route.polyline.map { GeoPoint(it.first, it.second) }
            try {
                mv.setMapOrientation(0f, true)
                mapRotationDeg = 0f
                val bbox = BoundingBox.fromGeoPoints(geoPts)
                mv.zoomToBoundingBox(bbox, true, 120)
            } catch (_: Exception) {}
        }
    }

    // Bám camera & xoay bản đồ theo hướng xe chạy khi đang Dẫn đường
    LaunchedEffect(
        isInNavigation,
        activeNavigation?.followMode,
        activeNavigation?.matchedSegmentIndex,
        currentLat,
        currentLng,
        smoothedBearingDeg,
        recenterTriggerSeq
    ) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        val nav = activeNavigation
        if (nav == null) {
            if (lastNavFollowModeApplied != null && mv.mapOrientation != 0f) {
                mv.setMapOrientation(0f, true)
                mapRotationDeg = 0f
            }
            lastNavFollowModeApplied = null
            return@LaunchedEffect
        }

        val follow = nav.followMode
        if (follow == NavCameraFollowMode.FREE_PAN) {
            lastNavFollowModeApplied = follow
            return@LaunchedEffect
        }

        val targetLatLng = when {
            nav.isUsingZonePreviewOrigin && nav.route != null -> nav.route.originLat to nav.route.originLng
            currentLat != null && currentLng != null -> currentLat to currentLng
            nav.route != null -> nav.route.originLat to nav.route.originLng
            else -> null
        } ?: return@LaunchedEffect

        val targetZoom = if (follow == NavCameraFollowMode.HEADING_UP) 16.1 else 15.2
        val targetOrientation = if (follow == NavCameraFollowMode.HEADING_UP) -smoothedBearingDeg else 0f
        if (abs(LocationMath.normalizeAngleDeltaDeg(mv.mapOrientation, targetOrientation)) >= 1.5f) {
            mv.setMapOrientation(targetOrientation, true)
            mapRotationDeg = targetOrientation
        }

        // Dịch tâm nhìn lên phía trước một chút theo hướng chạy để thấy nhiều đoạn đường sắp tới
        val lookAheadDeg = if (follow == NavCameraFollowMode.HEADING_UP) smoothedBearingDeg.toDouble() else 0.0
        val lookAheadOffsetDeg = if (follow == NavCameraFollowMode.HEADING_UP) 0.00095 else 0.0
        val rad = Math.toRadians(lookAheadDeg)
        val camLat = targetLatLng.first + lookAheadOffsetDeg * kotlin.math.cos(rad)
        val camLng = targetLatLng.second + lookAheadOffsetDeg * kotlin.math.sin(rad)

        val modeJustChanged = lastNavFollowModeApplied != follow
        lastNavFollowModeApplied = follow
        val now = System.currentTimeMillis()
        if (modeJustChanged || recenterTriggerSeq > 0 && now - lastNavCameraAnimateMs >= 250L || now - lastNavCameraAnimateMs >= 650L) {
            lastNavCameraAnimateMs = now
            val useZoom = if (modeJustChanged || mv.zoomLevelDouble < 14.0) targetZoom else mv.zoomLevelDouble.coerceIn(14.5, 17.5)
            mv.controller.animateTo(GeoPoint(camLat, camLng), useZoom, if (modeJustChanged) 520L else 380L)
        }
    }

    // Khi người dùng chạm vào 1 địa điểm trong danh sách tấm trượt, bay bản đồ mượt tới địa điểm đó
    LaunchedEffect(focusedPlace?.id) {
        if (!isInNavigation && focusedPlace != null) {
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
        if (isInNavigation) return@LaunchedEffect
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
                val detailedLoopPts = OfflineMapManager.getDetailedLoopGeoPoints(ctx).ifEmpty { HA_GIANG_LOOP_WAYPOINTS }
                TouchableMapView(ctx).apply {
                    mapViewInstance = this
                    onUserMapDrag = { latestOnUserPan?.invoke() }
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    val initialTileSource = when (effectiveLayerStyle) {
                        MapLayerStyle.STANDARD_OSM -> TileSourceFactory.MAPNIK
                        MapLayerStyle.OPEN_TOPO -> TileSourceFactory.OpenTopo
                        MapLayerStyle.OFFLINE_TOPO -> OfflineMapManager.HA_GIANG_OFFLINE_TILE_SOURCE
                    }
                    val shouldUseData = effectiveLayerStyle != MapLayerStyle.OFFLINE_TOPO && OfflineMapManager.hasValidatedInternet(ctx)
                    setUseDataConnection(shouldUseData)
                    setTileSource(initialTileSource)
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

                    // 0. Đường dẫn vòng cung Hà Giang Loop thực tế (1.780 khúc cua đèo uốn lượn)
                    val loopLine = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.argb(135, 234, 88, 12)
                        outlinePaint.strokeWidth = 6.0f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        setPoints(detailedLoopPts)
                        setOnClickListener { _, _, _ -> false }
                    }
                    loopRouteRef = loopLine
                    overlays.add(loopLine)

                    // 0B. Tuyến Dẫn đường đã đi qua (xám mờ) & đoạn còn lại (viền trắng + lõi nổi bật)
                    val navPassedLine = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.parseColor("#94A3B8")
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isVisible = false
                        setOnClickListener { _, _, _ -> false }
                    }
                    navPassedPolylineRef = navPassedLine
                    overlays.add(navPassedLine)

                    val navRemainingCasing = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.argb(235, 255, 255, 255)
                        outlinePaint.strokeWidth = 17f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isVisible = false
                        setOnClickListener { _, _, _ -> false }
                    }
                    navRemainingCasingRef = navRemainingCasing
                    overlays.add(navRemainingCasing)

                    val navRemainingLine = Polyline().apply {
                        infoWindow = null
                        outlinePaint.color = android.graphics.Color.parseColor("#0284C7")
                        outlinePaint.strokeWidth = 10.5f
                        outlinePaint.style = Paint.Style.STROKE
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isVisible = false
                        setOnClickListener { _, _, _ -> false }
                    }
                    navRemainingPolylineRef = navRemainingLine
                    overlays.add(navRemainingLine)

                    // 1. Thêm các place marker có gắn tên trực tiếp
                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(this, ctx, place) { p ->
                            if (activeNavigation == null) {
                                selectedPlace = p
                                controller.animateTo(GeoPoint(p.lat, p.lng))
                            }
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

                    // 2B. Ghim Điểm đích dẫn đường nổi bật ở cuối tuyến
                    val navDestMarker = Marker(this).apply {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        isEnabled = false
                    }
                    navDestinationMarkerRef = navDestMarker
                    overlays.add(navDestMarker)

                    // 3. Thêm Live My Location / Navigation Arrow Marker
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

                    val rotOverlay = object : RotationGestureOverlay(this) {
                        override fun onRotate(deltaAngle: Float) {
                            super.onRotate(deltaAngle)
                            mapRotationDeg = this@apply.mapOrientation
                        }
                    }.apply {
                        isEnabled = true
                    }
                    rotationOverlayRef = rotOverlay
                    overlays.add(rotOverlay)

                    invalidate()
                }
            },
            update = { mapView ->
                val targetTileSource = when (effectiveLayerStyle) {
                    MapLayerStyle.STANDARD_OSM -> TileSourceFactory.MAPNIK
                    MapLayerStyle.OPEN_TOPO -> TileSourceFactory.OpenTopo
                    MapLayerStyle.OFFLINE_TOPO -> OfflineMapManager.HA_GIANG_OFFLINE_TILE_SOURCE
                }
                val shouldUseData = effectiveLayerStyle != MapLayerStyle.OFFLINE_TOPO && OfflineMapManager.hasValidatedInternet(context)
                if (mapView.useDataConnection() != shouldUseData) {
                    mapView.setUseDataConnection(shouldUseData)
                }

                if (mapView.tileProvider.tileSource != targetTileSource) {
                    mapView.setTileSource(targetTileSource)
                    mapView.invalidate()
                }

                // Làm dịu tuyến nền Hà Giang Loop khi đang vẽ tuyến dẫn đường chính
                loopRouteRef?.let { loop ->
                    loop.outlinePaint.color = if (activeNavigation != null) {
                        android.graphics.Color.argb(55, 234, 88, 12)
                    } else {
                        android.graphics.Color.argb(135, 234, 88, 12)
                    }
                    loop.outlinePaint.strokeWidth = if (activeNavigation != null) 4.5f else 6.0f
                }

                // Cập nhật danh sách place markers khi danh sách hoặc tọa độ thay đổi
                val currentKey = placesOverlayKey(places)
                if (currentKey != lastPlacesKey) {
                    lastPlacesKey = currentKey

                    mapView.overlays.clear()
                    loopRouteRef?.let { mapView.overlays.add(it) }
                    navPassedPolylineRef?.let { mapView.overlays.add(it) }
                    navRemainingCasingRef?.let { mapView.overlays.add(it) }
                    navRemainingPolylineRef?.let { mapView.overlays.add(it) }

                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(mapView, context, place) { p ->
                            if (activeNavigation == null) {
                                selectedPlace = p
                                mapView.controller.animateTo(GeoPoint(p.lat, p.lng))
                            }
                        }
                        mapView.overlays.add(marker)
                    }
                    polylineRef?.let { mapView.overlays.add(it) }
                    navDestinationMarkerRef?.let { mapView.overlays.add(it) }
                    myLocationMarkerRef?.let { mapView.overlays.add(it) }
                    rotationOverlayRef?.let { mapView.overlays.add(it) }
                }

                // Cập nhật tuyến dẫn đường đã đi & còn lại + ghim đích
                val route = activeNavigation?.route
                if (activeNavigation != null && route != null && route.polyline.size >= 2) {
                    val splitIdx = activeNavigation.matchedSegmentIndex.coerceIn(0, route.polyline.lastIndex)
                    val passedGeo = if (splitIdx > 0) {
                        route.polyline.subList(0, (splitIdx + 1).coerceAtMost(route.polyline.size))
                            .map { GeoPoint(it.first, it.second) }
                    } else {
                        emptyList()
                    }
                    val remainingGeo = route.polyline.subList(splitIdx, route.polyline.size)
                        .map { GeoPoint(it.first, it.second) }

                    navPassedPolylineRef?.apply {
                        if (passedGeo.size >= 2) {
                            setPoints(passedGeo)
                            isVisible = true
                        } else {
                            setPoints(emptyList())
                            isVisible = false
                        }
                    }
                    navRemainingCasingRef?.apply {
                        setPoints(remainingGeo)
                        isVisible = remainingGeo.size >= 2
                    }
                    navRemainingPolylineRef?.apply {
                        outlinePaint.color = if (route.source == RouteSource.OFFLINE_LOOP_GRAPH) {
                            android.graphics.Color.parseColor("#EA580C")
                        } else {
                            android.graphics.Color.parseColor("#0284C7")
                        }
                        setPoints(remainingGeo)
                        isVisible = remainingGeo.size >= 2
                    }
                    navDestinationMarkerRef?.apply {
                        val dest = activeNavigation.destination
                        position = GeoPoint(dest.lat, dest.lng)
                        title = dest.name
                        icon = getPlaceCachedDrawable(context, dest)
                        isEnabled = true
                    }
                } else {
                    navPassedPolylineRef?.apply {
                        setPoints(emptyList())
                        isVisible = false
                    }
                    navRemainingCasingRef?.apply {
                        setPoints(emptyList())
                        isVisible = false
                    }
                    navRemainingPolylineRef?.apply {
                        setPoints(emptyList())
                        isVisible = false
                    }
                    navDestinationMarkerRef?.isEnabled = false
                }

                // Giữ sáng màn hình & cập nhật mũi tên / chấm định vị người dùng
                mapView.keepScreenOn = (activeNavigation != null)
                myLocationMarkerRef?.let { marker ->
                    if (activeNavigation != null) {
                        marker.icon = getNavigationArrowDrawable(context)
                        val arrowPt = when {
                            activeNavigation.isUsingZonePreviewOrigin && route != null -> {
                                GeoPoint(route.originLat, route.originLng)
                            }
                            currentLat != null && currentLng != null -> {
                                GeoPoint(currentLat, currentLng)
                            }
                            route != null -> {
                                GeoPoint(route.originLat, route.originLng)
                            }
                            else -> null
                        }
                        if (arrowPt != null) {
                            marker.position = arrowPt
                            marker.isEnabled = true
                        } else {
                            marker.isEnabled = false
                        }
                        if (activeNavigation.followMode == NavCameraFollowMode.HEADING_UP) {
                            marker.isFlat = false
                            marker.rotation = 0f
                        } else {
                            marker.isFlat = true
                            marker.rotation = -smoothedBearingDeg
                        }
                    } else {
                        marker.icon = getMyLocationDrawable(context)
                        marker.isFlat = false
                        marker.rotation = 0f
                        if (isGpsEnabled && currentLat != null && currentLng != null) {
                            marker.position = GeoPoint(currentLat, currentLng)
                            marker.isEnabled = true
                        } else {
                            marker.isEnabled = false
                        }
                    }
                }

                // Cập nhật vệt đường polyline Live Ride (nếu có điểm mới)
                polylineRef?.let { poly ->
                    if (trackPoints.size >= 2) {
                        poly.setPoints(trackPoints.map { GeoPoint(it.first, it.second) })
                    }
                }

                mapView.invalidate()
            }
        )

        // Các nút điều khiển nổi kính mờ (chỉ hiện ở chế độ khám phá, vì chế độ Dẫn đường có HUD riêng)
        if (!isInNavigation) {
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
                            mapViewInstance?.setMapOrientation(0f, true)
                            mapRotationDeg = 0f
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

                // Nút chuyển đổi kiểu bản đồ (Chuẩn OSM ➔ Địa hình Live ➔ Địa hình Offline)
                val isNonStandardLayer = effectiveLayerStyle != MapLayerStyle.STANDARD_OSM
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                        .clip(CircleShape)
                        .background(
                            when (effectiveLayerStyle) {
                                MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenSoft
                                MapLayerStyle.OPEN_TOPO -> SkyTelemetrySoft
                                MapLayerStyle.STANDARD_OSM -> SurfaceGlass
                            }
                        )
                        .border(
                            BorderStroke(
                                1.dp,
                                when (effectiveLayerStyle) {
                                    MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenBorder
                                    MapLayerStyle.OPEN_TOPO -> SkyTelemetryBorder
                                    MapLayerStyle.STANDARD_OSM -> BorderSubtle
                                }
                            ),
                            CircleShape
                        )
                        .springClickable {
                            val offlineReady = offlineMapStatus?.isReadyOffline == true
                            val nextStyle = when (effectiveLayerStyle) {
                                MapLayerStyle.STANDARD_OSM -> MapLayerStyle.OPEN_TOPO
                                MapLayerStyle.OPEN_TOPO -> if (offlineReady) MapLayerStyle.OFFLINE_TOPO else MapLayerStyle.STANDARD_OSM
                                MapLayerStyle.OFFLINE_TOPO -> MapLayerStyle.STANDARD_OSM
                            }
                            localLayerOverride = nextStyle
                            onLayerStyleChanged?.invoke(nextStyle)
                            layerToastLabel = "🗺️ ${nextStyle.label}"
                            layerToastSeq += 1
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Layers,
                        contentDescription = "Đổi kiểu bản đồ",
                        tint = when {
                            effectiveLayerStyle == MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenText
                            isNonStandardLayer -> SkyTelemetry
                            else -> TextSecondary
                        },
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
        }

        // Huy hiệu nhỏ tự ẩn sau 2.2s khi đổi lớp bản đồ
        AnimatedVisibility(
            visible = layerToastLabel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = controlsTopPadding, start = 14.dp)
        ) {
            layerToastLabel?.let { lbl ->
                Surface(
                    shape = CircleShape,
                    color = SurfaceGlass,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = lbl,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Nút La bàn xoay về hướng Bắc khi người dùng xoay bản đồ bằng 2 ngón tay
        AnimatedVisibility(
            visible = !isInNavigation &&
                layerToastLabel == null &&
                abs(LocationMath.normalizeAngleDeltaDeg(0f, mapRotationDeg)) > 2.5f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = controlsTopPadding, start = 14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                    .clip(CircleShape)
                    .background(SurfaceGlass)
                    .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                    .springClickable {
                        mapViewInstance?.setMapOrientation(0f, true)
                        mapRotationDeg = 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Xoay lại hướng Bắc",
                    tint = ElectricOrangeHover,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer { rotationZ = mapRotationDeg }
                )
            }
        }

        // Card chi tiết địa điểm nổi kiểu Airbnb khi chạm vào marker trên bản đồ (ẩn khi đang Dẫn đường)
        AnimatedVisibility(
            visible = selectedPlace != null && !isInNavigation,
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
                                    onStartInAppNav?.invoke(place)
                                        ?: LocationMath.openNavigationIntent(context, place.lat, place.lng, place.name)
                                },
                                variant = ShadcnButtonVariant.Primary,
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Dẫn đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }

                            ShadcnButton(
                                onClick = {
                                    LocationMath.openNavigationIntent(context, place.lat, place.lng, place.name)
                                },
                                variant = ShadcnButtonVariant.Secondary,
                                modifier = Modifier.width(86.dp).height(38.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = "Mở Google Maps", tint = SkyTelemetry, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("G.Maps", fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp, maxLines = 1)
                            }

                            if (place.phone.isNotBlank()) {
                                ShadcnButton(
                                    onClick = {
                                        LocationMath.openDialerIntent(context, place.phone)
                                    },
                                    variant = ShadcnButtonVariant.Secondary,
                                    modifier = Modifier.weight(0.9f).height(38.dp)
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

private fun getPlaceCachedDrawable(context: Context, place: Place): BitmapDrawable {
    val cacheKey = markerCacheKey(place)
    return markerDrawableCache[cacheKey] ?: run {
        val bitmap = createLabeledPinBitmap(place)
        val drawable = BitmapDrawable(context.resources, bitmap)
        markerDrawableCache[cacheKey] = drawable
        drawable
    }
}

private fun createOrGetPlaceMarker(
    mapView: MapView,
    context: Context,
    place: Place,
    onPlaceClick: (Place) -> Unit
): Marker {
    val cachedDrawable = getPlaceCachedDrawable(context, place)

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

private object NavArrowDrawableCache {
    var drawable: BitmapDrawable? = null
}

private fun getNavigationArrowDrawable(context: Context): BitmapDrawable {
    return NavArrowDrawableCache.drawable ?: run {
        val size = 68
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#38BDF8")
            alpha = 80
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size / 2f - 3f, haloPaint)

        val borderPath = android.graphics.Path().apply {
            moveTo(cx, 8f)
            lineTo(cx + 20f, size - 13f)
            lineTo(cx, size - 22f)
            lineTo(cx - 20f, size - 13f)
            close()
        }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(borderPath, whitePaint)

        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#0284C7")
            style = Paint.Style.FILL
        }
        canvas.drawPath(borderPath, arrowPaint)

        val drawable = BitmapDrawable(context.resources, bitmap)
        NavArrowDrawableCache.drawable = drawable
        drawable
    }
}
