package com.reed.hagianggo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zones by viewModel.zones.collectAsState()
    val places by viewModel.places.collectAsState()
    val config by viewModel.tripConfig.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val activeZone by viewModel.activeZone.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()

    var selectedZoneId by remember { mutableStateOf<String?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showRealMap by remember { mutableStateOf(true) }
    var isMapFullscreen by remember { mutableStateOf(false) }

    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }

    // Lọc places theo zone đang chọn (hoặc tất cả nếu null)
    val selectedZone = remember(zones, selectedZoneId) {
        zones.firstOrNull { it.id == selectedZoneId }
    }
    val displayedPlaces = remember(places, selectedZoneId) {
        if (selectedZoneId == null) places
        else places.filter { it.zoneId == selectedZoneId }
    }

    if (isMapFullscreen && showRealMap) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(AppBackground)
        ) {
            RealMapView(
                places = displayedPlaces,
                currentLat = currentLocation?.first,
                currentLng = currentLocation?.second,
                focusedZone = selectedZone,
                trackPoints = liveRideState.recentPoints,
                isMapFullscreen = true,
                onToggleFullscreen = { isMapFullscreen = false },
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddPlaceDialog(selectedZoneId) },
                containerColor = ElectricOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm địa điểm")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Interactive Map Card (shadcn light style with 1px border)
            item {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Map Switcher phong cách shadcn segmented
                            ShadcnSegmentedTabs(
                                items = listOf(true, false),
                                selectedItem = showRealMap,
                                onItemSelected = { showRealMap = it },
                                modifier = Modifier.width(260.dp)
                            ) { isReal, isSelected ->
                                Text(
                                    text = if (isReal) "🗺️ Bản đồ Thật" else "🔄 Sơ đồ 8 Chặng",
                                    color = if (isSelected) TextPrimary else TextMuted,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }

                            if (selectedZoneId != null) {
                                TextButton(
                                    onClick = { selectedZoneId = null },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Xem tất cả", fontSize = 11.sp, color = ElectricOrange)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (showRealMap) {
                            RealMapView(
                                places = displayedPlaces,
                                currentLat = currentLocation?.first,
                                currentLng = currentLocation?.second,
                                focusedZone = selectedZone,
                                trackPoints = liveRideState.recentPoints,
                                isMapFullscreen = false,
                                onToggleFullscreen = { isMapFullscreen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                            )
                        } else {
                            HaGiangLoopMap(
                                zones = zones,
                                places = places,
                                activeZoneId = selectedZoneId,
                                currentLat = currentLocation?.first,
                                currentLng = currentLocation?.second,
                                trackPoints = liveRideState.recentPoints,
                                onZoneSelected = { zone ->
                                    selectedZoneId = if (selectedZoneId == zone.id) null else zone.id
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 2. Live Ride Telemetry Card
            item {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = if (liveRideState.isTracking) EmeraldGreenBorder else BorderSubtle
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (liveRideState.isTracking) EmeraldGreen else BorderMedium)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (liveRideState.isTracking) "LIVE RIDE TRACKING" else "CHẾ ĐỘ THEO DÕI ĐÈO",
                                    color = TextMuted,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = if (liveRideState.isTracking) "${liveRideState.recentPoints.size} điểm • ${String.format(Locale.US, "%.1f", liveRideState.currentSpeedKmh)} km/h"
                                    else "Sẵn sàng ghi lại lộ trình GPS",
                                    color = if (liveRideState.isTracking) EmeraldGreenText else TextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }

                        ShadcnButton(
                            onClick = {
                                if (liveRideState.isTracking) {
                                    viewModel.openCockpit()
                                } else {
                                    viewModel.startRide()
                                    viewModel.openCockpit()
                                }
                            },
                            variant = if (liveRideState.isTracking) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Primary,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                if (liveRideState.isTracking) Icons.Default.Speed else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (liveRideState.isTracking) "HUD Cockpit" else "Bật Live",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // 3. Trip Config Overview Card
            item {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LỘ TRÌNH & PHƯƠNG TIỆN",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )

                            // Bộ chọn số ngày
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(3 to "3N2Đ", 4 to "4N3Đ").forEach { (days, label) ->
                                    val isSelected = config.durationDays == days
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                            .border(
                                                BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { viewModel.updateTripConfig(config.withDurationDays(days)) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) ElectricOrangeHover else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Phương tiện và Ngân sách
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Nút chọn phương tiện
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceMuted)
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (config.vehicle == VehicleType.MOTORBIKE) SurfaceWhite else Color.Transparent)
                                        .then(
                                            if (config.vehicle == VehicleType.MOTORBIKE)
                                                Modifier.border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                                            else Modifier
                                        )
                                        .clickable { viewModel.updateTripConfig(config.copy(vehicle = VehicleType.MOTORBIKE)) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.TwoWheeler,
                                            contentDescription = "Xe máy",
                                            tint = if (config.vehicle == VehicleType.MOTORBIKE) ElectricOrange else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Xe máy",
                                            fontSize = 11.sp,
                                            color = if (config.vehicle == VehicleType.MOTORBIKE) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (config.vehicle == VehicleType.CAR) SurfaceWhite else Color.Transparent)
                                        .then(
                                            if (config.vehicle == VehicleType.CAR)
                                                Modifier.border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                                            else Modifier
                                        )
                                        .clickable { viewModel.updateTripConfig(config.copy(vehicle = VehicleType.CAR)) }
                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.DirectionsCar,
                                            contentDescription = "Ô tô",
                                            tint = if (config.vehicle == VehicleType.CAR) ElectricOrange else TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "Ô tô",
                                            fontSize = 11.sp,
                                            color = if (config.vehicle == VehicleType.CAR) TextPrimary else TextSecondary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            // Ngân sách tóm tắt
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceMuted)
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { showBudgetDialog = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = ElectricOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${currencyFormatter.format(totalSpent)} đ",
                                    color = TextPrimary,
                                    style = TelemetryTextStyle.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }

            // 4. Zone Horizontal Filter Chips (shadcn badge pills)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedZoneId == null) "TẤT CẢ ĐỊA ĐIỂM (${displayedPlaces.size})"
                            else "ĐỊA ĐIỂM TẠI ${zones.firstOrNull { it.id == selectedZoneId }?.name?.uppercase()} (${displayedPlaces.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        if (selectedZoneId != null) {
                            Text(
                                text = "Bỏ lọc",
                                color = ElectricOrange,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable { selectedZoneId = null }
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            val isAll = selectedZoneId == null
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAll) ElectricOrangeSoft else SurfaceWhite)
                                    .border(
                                        BorderStroke(1.dp, if (isAll) ElectricOrangeBorder else BorderSubtle),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedZoneId = null }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "Tất cả (${places.size})",
                                    color = if (isAll) ElectricOrangeHover else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        items(zones, key = { it.id }) { zone ->
                            val isSelected = selectedZoneId == zone.id
                            val zoneCount = places.count { it.zoneId == zone.id }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) ElectricOrangeSoft else SurfaceWhite)
                                    .border(
                                        BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        selectedZoneId = if (isSelected) null else zone.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${zone.name} ($zoneCount)",
                                    color = if (isSelected) ElectricOrangeHover else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // 5. Place items list
            if (displayedPlaces.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có địa điểm nào trong cụm này.\nBấm + để thêm địa điểm mới!",
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
                            .padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        var budgetInput by remember { mutableStateOf(config.totalBudget.toString()) }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            containerColor = SurfaceWhite,
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
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = BorderMedium,
                        focusedLabelColor = ElectricOrange,
                        unfocusedLabelColor = TextSecondary
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
fun PlaceRowCard(
    place: Place,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVisited: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }

    ShadcnCard(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (place.isVisited) SurfaceMuted.copy(alpha = 0.5f) else SurfaceWhite,
        borderColor = if (place.isVisited) BorderSubtle.copy(alpha = 0.6f) else BorderSubtle
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon in subtle 1px border box
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceMuted)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (place.category) {
                        PlaceCategory.FUEL -> Icons.Default.LocalGasStation
                        PlaceCategory.MECHANIC_SOS -> Icons.Default.Build
                        PlaceCategory.CHECKIN_VIEW -> Icons.Default.Landscape
                        PlaceCategory.EAT -> Icons.Default.Restaurant
                        PlaceCategory.CAFE_REST -> Icons.Default.LocalCafe
                        PlaceCategory.HOMESTAY -> Icons.Default.Hotel
                    }
                    val iconTint = when (place.category) {
                        PlaceCategory.FUEL -> AmberWarningText
                        PlaceCategory.MECHANIC_SOS -> RoseDestructiveText
                        PlaceCategory.CHECKIN_VIEW -> EmeraldGreenText
                        PlaceCategory.EAT -> ElectricOrangeHover
                        PlaceCategory.CAFE_REST -> AmberWarningText
                        PlaceCategory.HOMESTAY -> SkyTelemetry
                    }
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
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

                    Spacer(modifier = Modifier.height(2.dp))

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
                        ShadcnBadge(
                            text = place.category.label,
                            variant = badgeVariant
                        )
                        if (place.altitudeMeters > 0) {
                            Text(
                                text = "${place.altitudeMeters}m",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        if (place.isVisited) {
                            ShadcnBadge(
                                text = "ĐÃ ĐI",
                                variant = ShadcnBadgeVariant.Emerald
                            )
                        }
                    }
                }

                // Bookmark toggle
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) ElectricOrange else TextSubtle,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn", tint = TextSubtle, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false },
                        modifier = Modifier.background(SurfaceWhite).border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp))
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

            // Action Buttons in shadcn button variants
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Dẫn đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                if (place.phoneNumber.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.weight(1f).height(36.dp),
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
