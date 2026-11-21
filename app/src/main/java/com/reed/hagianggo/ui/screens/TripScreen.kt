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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
    val locationTelemetry by viewModel.locationTelemetry.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val isOutsideHaGiang by viewModel.isOutsideHaGiang.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()
    val fuelAdvisory by viewModel.fuelAdvisory.collectAsState()
    val offlineMapStatus by viewModel.offlineMapStatus.collectAsState()
    val preferredMapLayer by viewModel.preferredMapLayer.collectAsState()
    val activeNavigation by viewModel.activeNavigation.collectAsState()
    val navMapPreference by viewModel.navMapPreference.collectAsState()

    var selectedZoneId by remember { mutableStateOf<String?>(null) }
    var focusedPlace by remember { mutableStateOf<Place?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showTripSettingsSheet by remember { mutableStateOf(false) }
    var showRealMap by remember { mutableStateOf(true) }
    var sheetDetent by remember { mutableStateOf(MapSheetDetent.HALF) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var overviewRouteTriggerSeq by remember { mutableIntStateOf(0) }
    var recenterTriggerSeq by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeNavigation != null) {
        if (activeNavigation != null) {
            showRealMap = true
        }
    }

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
                isGpsEnabled = isGpsEnabled,
                onRequestMyLocation = { viewModel.requestImmediateMyLocation() },
                focusedZone = selectedZone,
                focusedPlace = focusedPlace,
                visibleMapHeightFraction = visibleMapFraction,
                controlsTopPadding = 74.dp,
                cardBottomPadding = (animatedSheetHeight + 12.dp).coerceAtMost(maxHeight - 180.dp),
                edgeToEdge = true,
                trackPoints = liveRideState.recentPoints,
                offlineMapStatus = offlineMapStatus,
                onOpenOfflineMapPack = { viewModel.openOfflineMapDialog() },
                onOpenSos = { viewModel.openSosSheet() },
                preferredLayerStyle = preferredMapLayer,
                onLayerStyleChanged = { viewModel.setPreferredMapLayer(it) },
                activeNavigation = activeNavigation,
                currentBearingDeg = locationTelemetry?.bearingDeg ?: 0f,
                overviewRouteTriggerSeq = overviewRouteTriggerSeq,
                recenterTriggerSeq = recenterTriggerSeq,
                onStartInAppNav = { place -> viewModel.requestNavigationTo(place) },
                onUserPanMap = { viewModel.setNavFreePan() },
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
        // 2. TẤM KÉO TRƯỢT 3 NẤC VẬT LÝ LÒ XO (ẩn khi đang Dẫn đường trong app)
        // =====================================================================
        if (activeNavigation == null) {
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

                    // Hàng công cụ: Chuyển chế độ Bản đồ + Nấc Sheet + Cài đặt + Nút Thêm điểm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShadcnSegmentedTabs(
                            items = listOf(true, false),
                            selectedItem = showRealMap,
                            onItemSelected = { showRealMap = it },
                            modifier = Modifier.width(176.dp)
                        ) { isReal, isSelected ->
                            Text(
                                text = if (isReal) "Bản đồ" else "8 Chặng",
                                color = if (isSelected) TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Nút chuyển nhanh nấc mở tấm trượt
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .springClickable {
                                        sheetDetent = when (sheetDetent) {
                                            MapSheetDetent.PEEK -> MapSheetDetent.HALF
                                            MapSheetDetent.HALF -> MapSheetDetent.EXPANDED
                                            MapSheetDetent.EXPANDED -> MapSheetDetent.PEEK
                                        }
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceMuted,
                                border = BorderStroke(0.8.dp, BorderHairline)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (sheetDetent) {
                                            MapSheetDetent.PEEK -> Icons.Default.KeyboardArrowUp
                                            MapSheetDetent.HALF -> Icons.Default.UnfoldMore
                                            MapSheetDetent.EXPANDED -> Icons.Default.KeyboardArrowDown
                                        },
                                        contentDescription = sheetDetent.label,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Nút Cài đặt chuyến đi & Bản đồ (kèm chấm xanh khi đã sẵn sàng Offline)
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .springClickable { showTripSettingsSheet = true },
                                shape = RoundedCornerShape(10.dp),
                                color = SurfaceMuted,
                                border = BorderStroke(0.8.dp, BorderHairline)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "Cài đặt chuyến đi",
                                        tint = TextPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (offlineMapStatus.isReadyOffline) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(4.dp)
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldGreen)
                                        )
                                    }
                                }
                            }

                            // Nút Thêm địa điểm mới
                            Surface(
                                modifier = Modifier
                                    .height(34.dp)
                                    .springClickable {
                                        viewModel.openAddPlaceDialog(selectedZoneId)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = ElectricOrange
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
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
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
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

                // Danh sách Địa điểm ngay tầm mắt (Cấu hình gọn trong nút Cài đặt)
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
                    // Chỉ nhắc gọn khi chưa tải gói Bản đồ Offline
                    if (!offlineMapStatus.isReadyOffline) {
                        item(key = "offline_pack_strip") {
                            ShadcnCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = ElectricOrangeSoft,
                                borderColor = ElectricOrangeBorder.copy(alpha = 0.6f),
                                shadowElevation = 0.dp,
                                onClick = { viewModel.openOfflineMapDialog() }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            Icons.Default.CloudDownload,
                                            contentDescription = null,
                                            tint = ElectricOrangeHover,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (offlineMapStatus.isDownloading) {
                                                "Đang tải bản đồ Offline (${offlineMapStatus.downloadedTiles}/${offlineMapStatus.totalTiles})..."
                                            } else {
                                                "Lưu gói Bản đồ Offline để xem khi mất sóng"
                                            },
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    ShadcnBadge(text = "Tải ngay", variant = ShadcnBadgeVariant.Orange)
                                }
                            }
                        }
                    }

                    // Tiêu đề danh sách kiểu Apple Section Header
                    item(key = "list_header") {
                        val durDisplay = when (config.duration) {
                            TripDuration.DAYS_2_NIGHTS_1 -> "2N1Đ"
                            TripDuration.DAYS_3_NIGHTS_2 -> "3N2Đ"
                            TripDuration.DAYS_4_NIGHTS_3 -> "4N3Đ"
                        }
                        AppleSectionHeader(
                            title = if (selectedZone == null) "Toàn vòng lặp • ${displayedPlaces.size} điểm"
                            else "${selectedZone.name} • ${displayedPlaces.size} điểm",
                            trailingText = "$durDisplay • ${config.vehicle.title} ⚙",
                            onTrailingClick = { showTripSettingsSheet = true }
                        )
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
                                    viewModel.requestNavigationTo(place)
                                },
                                onOpenGoogleMaps = {
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

        // =====================================================================
        // 3. LỚP PHỦ DẪN ĐƯỜNG TRỰC TIẾP TRONG APP (TURN-BY-TURN HUD)
        // =====================================================================
        activeNavigation?.let { navState ->
            val currentLayer = preferredMapLayer
                ?: if (!com.reed.hagianggo.domain.OfflineMapManager.hasValidatedInternet(context) &&
                    offlineMapStatus.isReadyOffline
                ) {
                    MapLayerStyle.OFFLINE_TOPO
                } else {
                    MapLayerStyle.STANDARD_OSM
                }
            val nextScheduled = remember(navState.destination.id, navState.hasArrived) {
                if (navState.hasArrived) {
                    viewModel.findNextUnvisitedScheduledPlace(navState.destination.id)
                } else {
                    null
                }
            }
            val displaySpeedKmh = if (liveRideState.isTracking && liveRideState.currentSpeedKmh > 0.5f) {
                liveRideState.currentSpeedKmh
            } else {
                locationTelemetry?.speedKmh ?: 0f
            }

            NavigationOverlayHud(
                navState = navState,
                currentSpeedKmh = displaySpeedKmh,
                isOutsideHaGiang = isOutsideHaGiang,
                fuelAdvisory = fuelAdvisory,
                nextScheduledPlace = nextScheduled,
                currentLayerStyle = currentLayer,
                onToggleFollowMode = { viewModel.toggleNavFollowMode() },
                onRecenter = {
                    viewModel.recenterNavigation()
                    recenterTriggerSeq += 1
                },
                onOverviewRoute = {
                    viewModel.setNavFreePan()
                    overviewRouteTriggerSeq += 1
                },
                onToggleVoice = { viewModel.toggleNavVoice() },
                onToggleOriginMode = { viewModel.toggleNavOriginMode() },
                onToggleMapLayer = {
                    val offlineReady = offlineMapStatus.isReadyOffline
                    val nextStyle = when (currentLayer) {
                        MapLayerStyle.STANDARD_OSM -> MapLayerStyle.OPEN_TOPO
                        MapLayerStyle.OPEN_TOPO -> if (offlineReady) MapLayerStyle.OFFLINE_TOPO else MapLayerStyle.STANDARD_OSM
                        MapLayerStyle.OFFLINE_TOPO -> MapLayerStyle.STANDARD_OSM
                    }
                    viewModel.setPreferredMapLayer(nextStyle)
                },
                onOpenSos = { viewModel.openSosSheet() },
                onOpenGoogleMaps = {
                    val dest = navState.destination
                    LocationMath.openNavigationIntent(context, dest.lat, dest.lng, dest.name)
                },
                onForceReroute = { viewModel.forceRerouteNow() },
                onNavigateNext = { nextPlace -> viewModel.startInAppNavigation(nextPlace) },
                onStopNavigation = { viewModel.stopInAppNavigation() }
            )
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

    if (showTripSettingsSheet) {
        TripSettingsBottomSheet(
            config = config,
            totalSpent = totalSpent,
            isTrackingRide = liveRideState.isTracking,
            offlineMapStatus = offlineMapStatus,
            navMapPreference = navMapPreference,
            onUpdateDuration = { days -> viewModel.updateTripConfig(config.withDurationDays(days)) },
            onUpdateVehicle = { vehicle -> viewModel.updateTripConfig(config.copy(vehicle = vehicle)) },
            onSelectNavPref = { pref -> viewModel.setNavMapPreference(pref) },
            onOpenBudget = {
                showTripSettingsSheet = false
                showBudgetDialog = true
            },
            onOpenHud = {
                showTripSettingsSheet = false
                if (!liveRideState.isTracking) viewModel.startRide()
                viewModel.openCockpit()
            },
            onOpenOfflinePack = {
                showTripSettingsSheet = false
                viewModel.openOfflineMapDialog()
            },
            onOpenRecap = {
                showTripSettingsSheet = false
                viewModel.openRecapSheet()
            },
            onDismiss = { showTripSettingsSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSettingsBottomSheet(
    config: TripConfig,
    totalSpent: Long,
    isTrackingRide: Boolean,
    offlineMapStatus: OfflineMapStatus,
    navMapPreference: NavigationMapPreference,
    onUpdateDuration: (Int) -> Unit,
    onUpdateVehicle: (VehicleType) -> Unit,
    onSelectNavPref: (NavigationMapPreference) -> Unit,
    onOpenBudget: () -> Unit,
    onOpenHud: () -> Unit,
    onOpenOfflinePack: () -> Unit,
    onOpenRecap: () -> Unit,
    onDismiss: () -> Unit
) {
    val currencyFormatter = remember { java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppBackground,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Cài đặt Chuyến đi & Bản đồ",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )

            // Nhóm 1: Lộ trình & Phương tiện
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = BorderHairline
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Thời lượng chuyến đi", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(3 to "3N2Đ", 4 to "4N3Đ").forEach { (days, label) ->
                                val sel = config.durationDays == days
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (sel) ElectricOrange else SurfaceMuted)
                                        .springClickable { onUpdateDuration(days) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (sel) Color.White else TextSecondary)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = BorderHairline)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Phương tiện di chuyển", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        ShadcnSegmentedTabs(
                            items = VehicleType.entries,
                            selectedItem = config.vehicle,
                            onItemSelected = onUpdateVehicle,
                            modifier = Modifier.fillMaxWidth()
                        ) { vType, isSel ->
                            Text(
                                text = vType.title,
                                color = if (isSel) TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    HorizontalDivider(color = BorderHairline)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bản đồ dẫn đường mặc định", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = TextPrimary)
                        ShadcnSegmentedTabs(
                            items = NavigationMapPreference.entries,
                            selectedItem = navMapPreference,
                            onItemSelected = onSelectNavPref,
                            modifier = Modifier.fillMaxWidth()
                        ) { pref, isSel ->
                            Text(
                                text = pref.shortLabel,
                                color = if (isSel) TextPrimary else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Nhóm 2: iOS Inset Grouped Rows cho Công cụ & Dữ liệu Offline
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = BorderHairline
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TripSettingsActionRow(
                        icon = Icons.Default.OfflinePin,
                        iconTint = if (offlineMapStatus.isReadyOffline) EmeraldGreenText else ElectricOrangeHover,
                        iconBg = if (offlineMapStatus.isReadyOffline) EmeraldGreenSoft else ElectricOrangeSoft,
                        title = "Gói Bản đồ Địa hình Offline",
                        subtitle = if (offlineMapStatus.isReadyOffline) {
                            "Đã sẵn sàng (${String.format(Locale.US, "%.1f", offlineMapStatus.storageSizeMb)} MB trong filesDir)"
                        } else {
                            "Chưa tải gói đường đèo Offline"
                        },
                        onClick = onOpenOfflinePack
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = BorderHairline)
                    TripSettingsActionRow(
                        icon = Icons.Default.TwoWheeler,
                        iconTint = EmeraldGreenText,
                        iconBg = EmeraldGreenSoft,
                        title = if (isTrackingRide) "Mở HUD Chế độ Phượt (Đang chạy)" else "Bật Chế độ Phượt (HUD)",
                        subtitle = "Đo tốc độ, cao độ thực & tự động check-in",
                        onClick = onOpenHud
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = BorderHairline)
                    TripSettingsActionRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = SkyTelemetry,
                        iconBg = SkyTelemetrySoft,
                        title = "Ngân sách dự kiến chuyến đi",
                        subtitle = "Định mức ${currencyFormatter.format(config.totalBudget)} đ • Đã chi ${currencyFormatter.format(totalSpent)} đ",
                        onClick = onOpenBudget
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 54.dp), color = BorderHairline)
                    TripSettingsActionRow(
                        icon = Icons.Default.AutoAwesomeMotion,
                        iconTint = AmberWarningText,
                        iconBg = AmberWarningSoft,
                        title = "Poster Tổng kết Hành trình",
                        subtitle = "Xuất ảnh PNG vệt đường & cột mốc đã đi",
                        onClick = onOpenRecap
                    )
                }
            }
        }
    }
}

@Composable
private fun TripSettingsActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppleIconSquircle(
            icon = icon,
            tint = iconTint,
            backgroundColor = iconBg,
            size = 32.dp,
            iconSize = 17.dp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSubtle, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ZoneFilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ElectricOrange else SurfaceWhite,
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
                BorderStroke(0.8.dp, if (isSelected) ElectricOrange else BorderHairline),
                CircleShape
            )
            .springClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
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
    onOpenGoogleMaps: () -> Unit = {},
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
        backgroundColor = if (place.isVisited) SurfaceMuted.copy(alpha = 0.6f) else SurfaceWhite,
        borderColor = when {
            isFocused -> ElectricOrange
            place.isVisited -> EmeraldGreenBorder
            else -> BorderHairline
        },
        borderWidth = if (isFocused) 1.4.dp else 0.8.dp,
        onClick = onCardClick
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppleIconSquircle(
                    icon = icon,
                    tint = iconTint,
                    backgroundColor = softBg,
                    size = 40.dp,
                    iconSize = 19.dp,
                    cornerRadius = 11.dp
                )

                Spacer(modifier = Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = (-0.2).sp,
                                textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            ),
                            color = if (place.isVisited) TextMuted else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (place.isShelterInRain) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Umbrella,
                                contentDescription = "Trú mưa tốt",
                                tint = SkyTelemetry,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val altPart = if (place.altitudeMeters > 0) " • ${place.altitudeMeters}m" else ""
                        Text(
                            text = "${place.category.label}$altPart",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = TextMuted,
                            maxLines = 1
                        )
                        if (place.isVisited) {
                            Text(
                                text = "• Đã đi",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = EmeraldGreenText
                            )
                        }
                    }
                }

                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) ElectricOrange else TextSubtle,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = bookmarkScale
                                scaleY = bookmarkScale
                            }
                    )
                }

                Box {
                    IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn", tint = TextSubtle, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier
                            .background(SurfaceWhite)
                            .border(BorderStroke(0.8.dp, BorderHairline), RoundedCornerShape(12.dp))
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

            val descText = place.practicalTip.ifBlank { place.addressHint }
            if (descText.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chỉ đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                }

                ShadcnButton(
                    onClick = onOpenGoogleMaps,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.width(84.dp).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Map, contentDescription = "Mở Google Maps", modifier = Modifier.size(14.dp), tint = SkyTelemetry)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("G.Maps", fontWeight = FontWeight.Medium, fontSize = 11.5.sp, maxLines = 1)
                }

                if (place.phoneNumber.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.weight(0.75f).height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gọi", fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
