package com.reed.hagianggo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.util.Locale

private enum class MapSheetDetent(val label: String) {
    PEEK("Bản đồ"),
    HALF("Cân bằng"),
    EXPANDED("Danh sách")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val zones by viewModel.zones.collectAsState()
    val places by viewModel.places.collectAsState()
    val config by viewModel.tripConfig.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()

    var selectedZoneId by remember { mutableStateOf<String?>(null) }
    var focusedPlace by remember { mutableStateOf<Place?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRealMap by remember { mutableStateOf(true) }
    var sheetDetent by remember { mutableStateOf(MapSheetDetent.HALF) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }

    val selectedZone = remember(zones, selectedZoneId) {
        zones.firstOrNull { it.id == selectedZoneId }
    }
    val displayedPlaces = remember(places, selectedZoneId) {
        if (selectedZoneId == null) places
        else places.filter { it.zoneId == selectedZoneId }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        val navBarClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 86.dp
        val statusClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp

        val peekHeight = navBarClearance + 114.dp
        val halfHeight = (maxHeight * 0.52f).coerceAtLeast(peekHeight + 90.dp)
        val expandedHeight = (maxHeight - statusClearance).coerceAtLeast(halfHeight + 80.dp)

        val visibleMapFraction = when (sheetDetent) {
            MapSheetDetent.PEEK -> 0.76f
            MapSheetDetent.HALF -> 0.48f
            MapSheetDetent.EXPANDED -> 0.25f
        }

        val baseTargetHeight = when (sheetDetent) {
            MapSheetDetent.PEEK -> peekHeight
            MapSheetDetent.HALF -> halfHeight
            MapSheetDetent.EXPANDED -> expandedHeight
        }

        val dragOffsetDp = with(density) { (-dragOffsetPx).toDp() }
        val rawHeight = (baseTargetHeight + dragOffsetDp).coerceIn(peekHeight - 16.dp, expandedHeight + 12.dp)
        val animatedSheetHeight by animateDpAsState(
            targetValue = rawHeight,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = 440f
            ),
            label = "TripSheetHeightSpring"
        )

        // =====================================================================
        // 1. FULL-BLEED MAP CANVAS PHÍA SAU (ALLTRAILS / AIRBNB SPATIAL STAGE)
        // =====================================================================
        if (showRealMap) {
            RealMapView(
                places = displayedPlaces,
                currentLat = currentLocation?.first,
                currentLng = currentLocation?.second,
                focusedZone = selectedZone,
                focusedPlace = focusedPlace,
                visibleMapHeightFraction = visibleMapFraction,
                controlsTopPadding = 74.dp,
                cardBottomPadding = (animatedSheetHeight + 12.dp).coerceAtMost(maxHeight - 180.dp),
                edgeToEdge = true,
                trackPoints = liveRideState.recentPoints,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFFEEF2F6), AppBackground)
                        )
                    )
                    .padding(
                        top = statusClearance + 6.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                HaGiangLoopMap(
                    zones = zones,
                    places = places,
                    activeZoneId = selectedZoneId,
                    currentLat = currentLocation?.first,
                    currentLng = currentLocation?.second,
                    trackPoints = liveRideState.recentPoints,
                    onZoneSelected = { zone ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedZoneId = if (selectedZoneId == zone.id) null else zone.id
                        focusedPlace = null
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // =====================================================================
        // 2. TẤM KÉO TRƯỢT 3 NẤC VẬT LÝ LÒ XO (3-DETENT FLUID SHEET)
        // =====================================================================
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(animatedSheetHeight)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                    ambientColor = SoftShadowColor,
                    spotColor = SoftShadowColor
                ),
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Vùng Tay nắm kéo trượt 120Hz + Nút chuyển nhanh 3 nấc
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetPx += delta
                            },
                            onDragStopped = { velocity ->
                                val finalHeight = baseTargetHeight + with(density) { (-dragOffsetPx).toDp() }
                                dragOffsetPx = 0f
                                val nextDetent = when {
                                    velocity < -900f -> when (sheetDetent) {
                                        MapSheetDetent.PEEK -> MapSheetDetent.HALF
                                        else -> MapSheetDetent.EXPANDED
                                    }
                                    velocity > 900f -> when (sheetDetent) {
                                        MapSheetDetent.EXPANDED -> MapSheetDetent.HALF
                                        else -> MapSheetDetent.PEEK
                                    }
                                    else -> {
                                        listOf(
                                            MapSheetDetent.PEEK to peekHeight,
                                            MapSheetDetent.HALF to halfHeight,
                                            MapSheetDetent.EXPANDED to expandedHeight
                                        ).minByOrNull { (_, h) -> kotlin.math.abs((finalHeight - h).value) }?.first
                                            ?: MapSheetDetent.HALF
                                    }
                                }
                                if (nextDetent != sheetDetent) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    sheetDetent = nextDetent
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thanh nắm pill có thể chạm để xoay vòng nấc mở
                    Box(
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .width(42.dp)
                            .height(4.5.dp)
                            .clip(CircleShape)
                            .background(BorderMedium)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sheetDetent = when (sheetDetent) {
                                    MapSheetDetent.PEEK -> MapSheetDetent.HALF
                                    MapSheetDetent.HALF -> MapSheetDetent.EXPANDED
                                    MapSheetDetent.EXPANDED -> MapSheetDetent.PEEK
                                }
                            }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hàng công cụ: Chuyển chế độ Bản đồ + Nấc Sheet + Nút Thêm điểm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShadcnSegmentedTabs(
                            items = listOf(true, false),
                            selectedItem = showRealMap,
                            onItemSelected = { showRealMap = it },
                            modifier = Modifier.width(214.dp)
                        ) { isReal, isSelected ->
                            Text(
                                text = if (isReal) "🗺️ Bản đồ Thật" else "🔄 8 Chặng",
                                color = if (isSelected) TextPrimary else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Nút chuyển nhanh nấc mở tấm trượt
                            Surface(
                                modifier = Modifier.springClickable {
                                    sheetDetent = when (sheetDetent) {
                                        MapSheetDetent.PEEK -> MapSheetDetent.HALF
                                        MapSheetDetent.HALF -> MapSheetDetent.EXPANDED
                                        MapSheetDetent.EXPANDED -> MapSheetDetent.PEEK
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceMuted,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (sheetDetent) {
                                            MapSheetDetent.PEEK -> Icons.Default.KeyboardArrowUp
                                            MapSheetDetent.HALF -> Icons.Default.UnfoldMore
                                            MapSheetDetent.EXPANDED -> Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = sheetDetent.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Nút Thêm địa điểm mới nổi bật
                            Surface(
                                modifier = Modifier.springClickable {
                                    viewModel.openAddPlaceDialog(selectedZoneId)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = ElectricOrange
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Thêm địa điểm",
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Thêm",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Hàng Chip lọc 8 Cụm Chặng (Luôn hiển thị ngay cả ở nấc PEEK để điều khiển bản đồ tức thì!)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        item {
                            ZoneFilterPill(
                                label = "Tất cả (${places.size})",
                                isSelected = selectedZoneId == null,
                                onClick = {
                                    selectedZoneId = null
                                    focusedPlace = null
                                }
                            )
                        }
                        items(zones, key = { it.id }) { zone ->
                            val zoneCount = places.count { it.zoneId == zone.id }
                            ZoneFilterPill(
                                label = "${zone.seq}. ${zone.name} ($zoneCount)",
                                isSelected = selectedZoneId == zone.id,
                                onClick = {
                                    selectedZoneId = if (selectedZoneId == zone.id) null else zone.id
                                    focusedPlace = null
                                }
                            )
                        }
                    }
                }

                HorizontalDivider(color = BorderSubtle.copy(alpha = 0.7f))

                // Danh sách Bento Cấu hình + Các Thẻ Địa Điểm (Cuộn mượt bên dưới)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 10.dp,
                        bottom = navBarClearance + 20.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Khối Bento Tóm tắt Chuyến đi + Live Ride + Ngân sách Rolling Odometer
                    item(key = "bento_trip_strip") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Bento 1: Lộ trình & Xe
                            ShadcnCard(
                                modifier = Modifier.weight(1.15f),
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = SurfaceMuted,
                                borderColor = BorderSubtle,
                                shadowElevation = 0.dp
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("PHƯƠNG TIỆN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(3 to "3N2Đ", 4 to "4N3Đ").forEach { (days, label) ->
                                                val sel = config.durationDays == days
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (sel) ElectricOrange else SurfaceWhite)
                                                        .springClickable {
                                                            viewModel.updateTripConfig(config.withDurationDays(days))
                                                        }
                                                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (sel) Color.White else TextSecondary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            VehicleType.MOTORBIKE to "🏍️ Xe máy",
                                            VehicleType.CAR to "🚗 Ô tô"
                                        ).forEach { (vType, vLabel) ->
                                            val isVeh = config.vehicle == vType
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isVeh) SurfaceWhite else Color.Transparent)
                                                    .border(
                                                        BorderStroke(1.dp, if (isVeh) ElectricOrangeBorder else BorderSubtle),
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .springClickable {
                                                        viewModel.updateTripConfig(config.copy(vehicle = vType))
                                                    }
                                                    .padding(vertical = 5.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = vLabel,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isVeh) TextPrimary else TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Bento 2: Ngân sách Rolling Odometer + Live HUD
                            Column(
                                modifier = Modifier.weight(0.85f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ShadcnCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = SurfaceMuted,
                                    borderColor = BorderSubtle,
                                    shadowElevation = 0.dp,
                                    onClick = { showBudgetDialog = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("ĐÃ CHI", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextMuted)
                                            RollingNumberText(
                                                targetValue = totalSpent.toFloat(),
                                                suffix = " đ",
                                                useThousandSeparator = true,
                                                style = TelemetryTextStyle.copy(fontSize = 13.sp),
                                                color = TextPrimary
                                            )
                                        }
                                        Icon(Icons.Default.Edit, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(13.dp))
                                    }
                                }

                                ShadcnButton(
                                    onClick = {
                                        if (!liveRideState.isTracking) viewModel.startRide()
                                        viewModel.openCockpit()
                                    },
                                    variant = if (liveRideState.isTracking) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Primary,
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        if (liveRideState.isTracking) Icons.Default.Speed else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (liveRideState.isTracking) EmeraldGreenText else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (liveRideState.isTracking) "Mở HUD Live" else "Bật HUD Đèo",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (liveRideState.isTracking) EmeraldGreenText else Color.White
                                    )
                                }
                            }
                        }
                    }

                    // Tiêu đề danh sách
                    item(key = "list_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedZone == null) "KHÁM PHÁ TOÀN VÒNG LẶP (${displayedPlaces.size} ĐIỂM)"
                                else "${selectedZone.name.uppercase()} (${displayedPlaces.size} ĐIỂM)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextMuted
                            )
                            Text(
                                text = "Chạm thẻ để bay tới ghim",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricOrangeHover
                            )
                        }
                    }

                    if (displayedPlaces.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Chưa có địa điểm nào trong cụm này.\nBấm + Thêm để lưu địa điểm mới!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(displayedPlaces, key = { it.id }) { place ->
                            PlaceRowCard(
                                place = place,
                                isFocused = focusedPlace?.id == place.id,
                                onCardClick = {
                                    showRealMap = true
                                    focusedPlace = place
                                    if (sheetDetent == MapSheetDetent.EXPANDED) {
                                        sheetDetent = MapSheetDetent.HALF
                                    }
                                },
                                onNavigate = {
                                    LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                                },
                                onCall = {
                                    LocationMath.openDialerIntent(context, place.phoneNumber)
                                },
                                onToggleBookmark = { viewModel.toggleBookmark(place.id) },
                                onToggleVisited = { viewModel.toggleVisited(place.id) },
                                onEdit = { viewModel.openEditPlaceDialog(place) },
                                onDelete = { viewModel.deletePlace(place.id) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        var budgetInput by remember { mutableStateOf(config.totalBudget.toString()) }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            containerColor = SurfaceWhite,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    "Ngân sách dự kiến chuyến đi",
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tổng ngân sách (VNĐ)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                ShadcnButton(
                    onClick = {
                        val newBudget = budgetInput.toLongOrNull() ?: config.totalBudget
                        viewModel.updateTripConfig(config.copy(totalBudget = newBudget))
                        showBudgetDialog = false
                    },
                    variant = ShadcnButtonVariant.Primary
                ) {
                    Text("Lưu cài đặt")
                }
            },
            dismissButton = {
                ShadcnButton(
                    onClick = { showBudgetDialog = false },
                    variant = ShadcnButtonVariant.Ghost
                ) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ZoneFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ElectricOrange else SurfaceMuted,
        animationSpec = tween(180),
        label = "ZonePillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else TextSecondary,
        animationSpec = tween(180),
        label = "ZonePillText"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(
                BorderStroke(1.dp, if (isSelected) ElectricOrangeHover else BorderSubtle),
                CircleShape
            )
            .springClickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 6.5.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun PlaceRowCard(
    place: Place,
    isFocused: Boolean = false,
    onCardClick: (() -> Unit)? = null,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVisited: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }
    val bookmarkScale by animateFloatAsState(
        targetValue = if (place.isBookmarked) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "BookmarkBounce"
    )

    val (icon, iconTint, softBg) = when (place.category) {
        PlaceCategory.FUEL -> Triple(Icons.Default.LocalGasStation, AmberWarningText, AmberWarningSoft)
        PlaceCategory.MECHANIC_SOS -> Triple(Icons.Default.Build, RoseDestructiveText, RoseDestructiveSoft)
        PlaceCategory.CHECKIN_VIEW -> Triple(Icons.Default.Landscape, EmeraldGreenText, EmeraldGreenSoft)
        PlaceCategory.EAT -> Triple(Icons.Default.Restaurant, ElectricOrangeHover, ElectricOrangeSoft)
        PlaceCategory.CAFE_REST -> Triple(Icons.Default.LocalCafe, AmberWarningText, AmberWarningSoft)
        PlaceCategory.HOMESTAY -> Triple(Icons.Default.Hotel, SkyTelemetry, SkyTelemetrySoft)
    }

    ShadcnCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (place.isVisited) SurfaceMuted.copy(alpha = 0.65f) else SurfaceWhite,
        borderColor = when {
            isFocused -> ElectricOrange
            place.isVisited -> EmeraldGreenBorder
            else -> BorderSubtle
        },
        borderWidth = if (isFocused) 1.5.dp else 1.dp,
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(softBg)
                        .border(BorderStroke(1.dp, iconTint.copy(alpha = 0.22f)), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            ),
                            color = if (place.isVisited) TextMuted else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (place.isShelterInRain) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Umbrella,
                                contentDescription = "Trú mưa tốt",
                                tint = SkyTelemetry,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val badgeVariant = when (place.category) {
                            PlaceCategory.FUEL -> ShadcnBadgeVariant.Amber
                            PlaceCategory.MECHANIC_SOS -> ShadcnBadgeVariant.Rose
                            PlaceCategory.CHECKIN_VIEW -> ShadcnBadgeVariant.Emerald
                            PlaceCategory.EAT -> ShadcnBadgeVariant.Orange
                            PlaceCategory.CAFE_REST -> ShadcnBadgeVariant.Amber
                            PlaceCategory.HOMESTAY -> ShadcnBadgeVariant.Sky
                        }
                        ShadcnBadge(text = place.category.label, variant = badgeVariant)
                        if (place.altitudeMeters > 0) {
                            Text(
                                text = "▲ ${place.altitudeMeters}m",
                                style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                color = TextMuted
                            )
                        }
                        if (place.isVisited) {
                            ShadcnBadge(text = "✓ ĐÃ ĐI", variant = ShadcnBadgeVariant.Emerald)
                        }
                    }
                }

                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) ElectricOrange else TextSubtle,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = bookmarkScale
                                scaleY = bookmarkScale
                            }
                    )
                }

                Box {
                    IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn", tint = TextSubtle, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier
                            .background(SurfaceWhite)
                            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (place.isVisited) "Đánh dấu chưa đi" else "Đánh dấu đã đi", color = TextPrimary) },
                            leadingIcon = {
                                Icon(
                                    if (place.isVisited) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = EmeraldGreen
                                )
                            },
                            onClick = {
                                onToggleVisited()
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary) },
                            onClick = {
                                onEdit()
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa địa điểm", color = RoseDestructive) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = RoseDestructive) },
                            onClick = {
                                onDelete()
                                expandedMenu = false
                            }
                        )
                    }
                }
            }

            if (place.addressHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Dẫn đường", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (place.phoneNumber.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(15.dp), tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Gọi điện", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
