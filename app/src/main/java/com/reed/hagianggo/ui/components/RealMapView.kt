package com.reed.hagianggo.ui.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.label
import com.reed.hagianggo.ui.theme.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

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

// Bộ nhớ đệm Icon Marker để tránh tạo lại Bitmap gây giật lag
private val markerDrawableCache = HashMap<String, BitmapDrawable>()
private var myLocationDrawableCache: BitmapDrawable? = null

@Composable
fun RealMapView(
    places: List<Place>,
    currentLat: Double?,
    currentLng: Double?,
    trackPoints: List<Pair<Double, Double>> = emptyList(),
    isMapFullscreen: Boolean = false,
    onToggleFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var isTopoMode by remember { mutableStateOf(false) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }

    // Biến ghi nhớ danh sách places đã add vào overlays để tránh xóa tạo lại liên tục
    var lastPlacesKey by remember { mutableStateOf("") }
    var myLocationMarkerRef by remember { mutableStateOf<Marker?>(null) }
    var polylineRef by remember { mutableStateOf<Polyline?>(null) }

    // Cấu hình OSMDroid tối ưu hiệu năng
    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.filesDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
            cacheMapTileCount = 40.toShort()
            tileDownloadThreads = 4
            tileFileSystemCacheMaxBytes = 350L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 300L * 1024 * 1024
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TouchableMapView(ctx).apply {
                    mapViewInstance = this
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setTileSource(if (isTopoMode) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isFlingEnabled = true
                    isTilesScaledToDpi = true
                    isVerticalMapRepetitionEnabled = false
                    isHorizontalMapRepetitionEnabled = false
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)

                    minZoomLevel = 8.0
                    maxZoomLevel = 19.0
                    controller.setZoom(10.8)
                    controller.setCenter(GeoPoint(23.16, 105.20)) // Trung tâm vòng cung Hà Giang

                    // Khởi tạo overlays lần đầu
                    val currentKey = places.joinToString(",") { it.id }
                    lastPlacesKey = currentKey

                    overlays.clear()

                    // 1. Thêm các place marker có gắn tên trực tiếp
                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(this, ctx, place) { p ->
                            selectedPlace = p
                        }
                        overlays.add(marker)
                    }

                    // 2. Thêm Polyline vệt đường
                    val polyline = Polyline().apply {
                        outlinePaint.color = android.graphics.Color.parseColor("#EA580C")
                        outlinePaint.strokeWidth = 9f
                        if (trackPoints.size >= 2) {
                            setPoints(trackPoints.map { GeoPoint(it.first, it.second) })
                        }
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
                // Chỉ đổi tile source khi người dùng bấm nút đổi layer
                val currentTileSource = if (isTopoMode) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
                if (mapView.tileProvider.tileSource != currentTileSource) {
                    mapView.setTileSource(currentTileSource)
                }

                // Cập nhật danh sách place markers CHỈ KHI danh sách thực sự thay đổi
                val currentKey = places.joinToString(",") { it.id }
                if (currentKey != lastPlacesKey) {
                    lastPlacesKey = currentKey

                    // Lọc giữ lại myLocationMarker và polyline, chỉ rebuild place markers
                    val nonPlaceOverlays = mapView.overlays.filter { it == myLocationMarkerRef || it == polylineRef }
                    mapView.overlays.clear()

                    places.forEach { place ->
                        val marker = createOrGetPlaceMarker(mapView, context, place) { p ->
                            selectedPlace = p
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.overlays.addAll(nonPlaceOverlays)
                    mapView.invalidate()
                }

                // Cập nhật vị trí GPS người dùng (không xóa overlay, chỉ set position mượt mà)
                myLocationMarkerRef?.let { marker ->
                    if (currentLat != null && currentLng != null) {
                        marker.position = GeoPoint(currentLat, currentLng)
                        marker.isEnabled = true
                    } else {
                        marker.isEnabled = false
                    }
                }

                // Cập nhật vệt đường polyline (nếu có điểm mới)
                polylineRef?.let { poly ->
                    if (trackPoints.size >= 2) {
                        poly.setPoints(trackPoints.map { GeoPoint(it.first, it.second) })
                    }
                }
            }
        )

        // Các nút điều khiển nổi (Floating Control Buttons)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nút định vị vị trí hiện tại của tôi
            SmallFloatingActionButton(
                onClick = {
                    if (currentLat != null && currentLng != null) {
                        mapViewInstance?.controller?.animateTo(GeoPoint(currentLat, currentLng), 14.5, 800L)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = if (currentLat != null) HighlandGreen else Color.Gray,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Vị trí của tôi", modifier = Modifier.size(20.dp))
            }

            // Nút bao trọn toàn bộ vòng lặp Hà Giang
            SmallFloatingActionButton(
                onClick = {
                    mapViewInstance?.controller?.animateTo(GeoPoint(23.16, 105.20), 10.8, 800L)
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = SunsetOrange,
                shape = CircleShape
            ) {
                Icon(Icons.Default.CropFree, contentDescription = "Toàn cảnh Hà Giang", modifier = Modifier.size(20.dp))
            }

            // Nút chuyển đổi kiểu bản đồ (Tiêu chuẩn ⟷ Địa hình Topo)
            SmallFloatingActionButton(
                onClick = { isTopoMode = !isTopoMode },
                containerColor = if (isTopoMode) HighlandGreen else MaterialTheme.colorScheme.surface,
                contentColor = if (isTopoMode) Color.White else MaterialTheme.colorScheme.onSurface,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Đổi kiểu bản đồ", modifier = Modifier.size(20.dp))
            }

            // Nút Phóng to toàn màn hình / Thu nhỏ
            if (onToggleFullscreen != null) {
                SmallFloatingActionButton(
                    onClick = onToggleFullscreen,
                    containerColor = if (isMapFullscreen) SunsetOrange else MaterialTheme.colorScheme.surface,
                    contentColor = if (isMapFullscreen) Color.White else MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape
                ) {
                    Icon(
                        if (isMapFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isMapFullscreen) "Thu nhỏ" else "Toàn màn hình",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Card chi tiết địa điểm khi chạm vào marker trên bản đồ
        AnimatedVisibility(
            visible = selectedPlace != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            selectedPlace?.let { place ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = place.category.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            IconButton(
                                onClick = { selectedPlace = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (place.practicalTip.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 ${place.practicalTip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    LocationMath.openNavigationIntent(context, place.lat, place.lng, place.name)
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Dẫn đường", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            if (place.phone.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        LocationMath.openDialerIntent(context, place.phone)
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, tint = HighlandGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
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
    val cachedDrawable = markerDrawableCache[place.id] ?: run {
        val bitmap = createLabeledPinBitmap(place)
        val drawable = BitmapDrawable(context.resources, bitmap)
        markerDrawableCache[place.id] = drawable
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
