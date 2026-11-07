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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config by viewModel.tripConfig.collectAsState()
    val zones by viewModel.zones.collectAsState()
    val places by viewModel.places.collectAsState()
    val itinerarySlots by viewModel.itinerarySlots.collectAsState()
    val activeZone by viewModel.activeZone.collectAsState()
    val isGpsEnabled by viewModel.isGpsEnabled.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isOutsideHaGiang by viewModel.isOutsideHaGiang.collectAsState()
    val distanceToHaGiangKm by viewModel.distanceToHaGiangKm.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()

    var selectedDay by remember { mutableIntStateOf(1) }
    var showZonePicker by remember { mutableStateOf(false) }

    // Tính thời gian hoàng hôn trên đèo Hà Giang (~17:15)
    val now = remember { Calendar.getInstance() }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute
    val sunsetMinutes = 17 * 60 + 15 // 17:15
    val minutesUntilSunset = sunsetMinutes - currentTotalMinutes
    val isNearDark = currentTotalMinutes >= (16 * 60 + 30) // sau 16:30
    val isNight = currentTotalMinutes >= sunsetMinutes

    // Places map
    val placesById = remember(places) { places.associateBy { it.id } }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Hero Action: "LÀM GÌ BÂY GIỜ?" (shadcn light callout)
            item {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openWhatNow() },
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = ElectricOrangeBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ElectricOrangeSoft)
                                .border(BorderStroke(1.dp, ElectricOrangeBorder), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = ElectricOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LÀM GÌ BÂY GIỜ?",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ShadcnBadge(text = "AI GỢI Ý", variant = ShadcnBadgeVariant.Orange)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Đang ở ${activeZone?.name ?: "Hà Giang"} • Chạm để tìm điểm tiếp theo",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            tint = TextSubtle,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 2. Live Ride Mode Toggle Banner
            item {
                if (!liveRideState.isTracking) {
                    ShadcnCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.startRide() },
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = EmeraldGreenBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EmeraldGreenSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.TwoWheeler,
                                    contentDescription = null,
                                    tint = EmeraldGreenText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CHẾ ĐỘ PHƯỢT (LIVE RIDE)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldGreenText
                                )
                                Text(
                                    text = "Đo độ cao mét, tốc độ GPS & tự động check-in",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = EmeraldGreenText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    ShadcnCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openCockpit() },
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = if (liveRideState.isPaused) AmberWarningBorder else EmeraldGreenBorder
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (liveRideState.isPaused) AmberWarning else EmeraldGreen)
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (liveRideState.isPaused) "CHẶNG ĐANG TẠM DỪNG" else "ĐANG GHI HÀNH TRÌNH",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (liveRideState.isPaused) AmberWarningText else EmeraldGreenText
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "• ${String.format(java.util.Locale.US, "%.1f", liveRideState.totalDistanceKm)} km",
                                        color = TextMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${liveRideState.currentSpeedKmh.toInt()} km/h • ${liveRideState.currentAltitude.toInt()}m cao độ",
                                    style = TelemetryTextStyle.copy(fontSize = 13.sp),
                                    color = TextPrimary
                                )
                            }

                            ShadcnBadge(
                                text = "MỞ HUD",
                                variant = ShadcnBadgeVariant.Emerald
                            )
                        }
                    }
                }
            }

            // 3. Location & Zone Status Pill
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (currentLocation != null) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                                contentDescription = null,
                                tint = if (isOutsideHaGiang) AmberWarningText else if (currentLocation != null) EmeraldGreenText else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isOutsideHaGiang) "GPS: Ngoài Hà Giang (~${distanceToHaGiangKm?.toInt() ?: 0} km)"
                                    else if (currentLocation != null) "Vị trí GPS: Hoạt động"
                                    else "Chưa có tín hiệu GPS",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isOutsideHaGiang) AmberWarningText else if (currentLocation != null) EmeraldGreenText else TextSecondary
                                )
                                Text(
                                    text = if (isOutsideHaGiang) "Khu vực: ${activeZone?.name ?: "TP. Hà Giang"} (Xem trước)"
                                    else "Khu vực: ${activeZone?.name ?: "Chưa xác định"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        ShadcnButton(
                            onClick = { showZonePicker = true },
                            variant = ShadcnButtonVariant.Secondary,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đổi vùng", fontSize = 11.sp)
                        }
                    }
                }
            }

            // 4. Mountain Sunset Warning Banner (Pastel Amber/Rose Alert)
            item {
                val alertVariant = when {
                    isNight -> ShadcnBadgeVariant.Rose
                    isNearDark -> ShadcnBadgeVariant.Amber
                    else -> ShadcnBadgeVariant.Default
                }

                ShadcnAlert(
                    title = when {
                        isNight -> "ĐÃ TỐI TRÊN ĐÈO HÀ GIANG!"
                        isNearDark -> "CẢNH BÁO HOÀNG HÔN SẮP TẮT (${minutesUntilSunset} phút nữa)"
                        else -> "Thời gian an toàn đến hoàng hôn (17:15)"
                    },
                    description = when {
                        isNight -> "Nên về homestay ngay. Đèo núi đêm tối om, cua gấp và nhiều sương mù rất nguy hiểm!"
                        isNearDark -> "Nên giảm tốc độ và chốt điểm nghỉ/ăn tối trước 17:15 để tránh chạy đèo đêm."
                        else -> "Còn khoảng ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p ánh sáng ban ngày. Hãy căn giờ vượt đèo hợp lý."
                    },
                    variant = alertVariant,
                    icon = if (isNight || isNearDark) Icons.Default.Warning else Icons.Default.WbSunny
                )
            }

            // 5. Day Tabs (Shadcn segmented tabs)
            item {
                val daysList = (1..config.durationDays).toList()
                ShadcnSegmentedTabs(
                    items = daysList,
                    selectedItem = selectedDay,
                    onItemSelected = { selectedDay = it },
                    modifier = Modifier.fillMaxWidth()
                ) { day, isSelected ->
                    Text(
                        text = "Ngày $day",
                        color = if (isSelected) TextPrimary else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            // 6. Day Route Summary Hint
            item {
                val dayRouteHint = when (selectedDay) {
                    1 -> "📍 Chặng Ngày 1: TP. Hà Giang ➔ Dốc Bắc Sum ➔ Cổng Trời Quản Bạ ➔ Rừng Thông Yên Minh ➔ Phố Cáo ➔ Phố Cổ Đồng Văn (~140km)"
                    2 -> "📍 Chặng Ngày 2: Đồng Văn ➔ Cột Cờ Lũng Cú ➔ Làng Lô Lô Chải ➔ Đèo Mã Pí Lèng ➔ Hẻm Tu Sản / Sông Nho Quế ➔ Mèo Vạc (~80km)"
                    3 -> "📍 Chặng Ngày 3: Mèo Vạc ➔ Cua Chữ M ➔ Thác Du Già ➔ Đường Thượng ➔ Về lại TP. Hà Giang (~150km)"
                    else -> "📍 Chặng Ngày $selectedDay: Khám phá thêm các bản làng, chợ phiên và trở về an toàn."
                }
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Text(
                        text = dayRouteHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // 7. Timeline of Activities for Selected Day
            val daySlots = itinerarySlots.filter { it.dayNumber == selectedDay }

            listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING).forEach { slot ->
                item(key = "header_${slot.name}") {
                    TimeSlotHeader(slot)
                }

                val slotsInThisTime = daySlots.filter { it.slot == slot }
                val placesForSlot = slotsInThisTime.flatMap { item ->
                    item.suggestedPlaceIds.mapNotNull { pid -> placesById[pid]?.let { p -> item to p } }
                }

                if (placesForSlot.isEmpty()) {
                    item(key = "empty_${slot.name}") {
                        Text(
                            text = "Chưa có điểm nào được ghim trong ${slot.label.lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                } else {
                    items(placesForSlot, key = { (slotItem, place) -> "${slotItem.id}_${place.id}" }) { (slotItem, place) ->
                        ItineraryPlaceCard(
                            place = place,
                            customNote = slotItem.title,
                            onNavigate = {
                                LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                            },
                            onToggleVisited = { viewModel.toggleVisited(place.id) },
                            onCall = {
                                if (place.phoneNumber.isNotBlank()) {
                                    LocationMath.openDialerIntent(context, place.phoneNumber)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal chọn zone override tay khi offline/ở thung lũng
    if (showZonePicker) {
        AlertDialog(
            onDismissRequest = { showZonePicker = false },
            containerColor = SurfaceWhite,
            title = {
                Text("Chọn cụm chặng hiện tại", color = TextPrimary, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Tự động theo GPS", color = EmeraldGreenText, fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Default.AutoMode, contentDescription = null, tint = EmeraldGreen) },
                            colors = ListItemDefaults.colors(containerColor = SurfaceWhite),
                            modifier = Modifier.clickable {
                                viewModel.setManualZoneOverride(null)
                                showZonePicker = false
                            }
                        )
                        HorizontalDivider(color = BorderSubtle)
                    }
                    items(zones) { zone ->
                        ListItem(
                            headlineContent = { Text(zone.name, color = TextPrimary) },
                            supportingContent = { Text("Độ cao ~${zone.elevationMeters}m", color = TextSecondary) },
                            leadingContent = { Icon(Icons.Default.Place, contentDescription = null, tint = ElectricOrange) },
                            colors = ListItemDefaults.colors(containerColor = SurfaceWhite),
                            modifier = Modifier.clickable {
                                viewModel.setManualZoneOverride(zone.id)
                                showZonePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                ShadcnButton(
                    onClick = { showZonePicker = false },
                    variant = ShadcnButtonVariant.Ghost
                ) {
                    Text("Đóng", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun TimeSlotHeader(slot: TimeSlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, color) = when (slot) {
            TimeSlot.MORNING -> Icons.Default.WbTwilight to ElectricOrange
            TimeSlot.NOON -> Icons.Default.WbSunny to AmberWarningText
            TimeSlot.AFTERNOON -> Icons.Default.CloudQueue to SkyTelemetry
            TimeSlot.EVENING -> Icons.Default.NightsStay to TextMuted
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${slot.label.uppercase()} (${slot.timeRange})",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextSecondary
        )
    }
}

@Composable
fun ItineraryPlaceCard(
    place: Place,
    customNote: String,
    onNavigate: () -> Unit,
    onToggleVisited: () -> Unit,
    onCall: () -> Unit
) {
    ShadcnCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = if (place.isVisited) SurfaceMuted.copy(alpha = 0.5f) else SurfaceWhite,
        borderColor = if (place.isVisited) BorderSubtle.copy(alpha = 0.6f) else BorderSubtle
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleVisited,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        if (place.isVisited) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Trạng thái",
                        tint = if (place.isVisited) EmeraldGreen else BorderMedium,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        ),
                        color = if (place.isVisited) TextMuted else TextPrimary
                    )
                    Text(
                        text = "${place.category.label} • Khoảng ${place.suggestedDurationMin} phút",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (place.phoneNumber.isNotBlank()) {
                    IconButton(onClick = onCall, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = "Gọi", tint = EmeraldGreenText, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (place.practicalTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 ${place.practicalTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            if (customNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "📝 Ghi chú: $customNote",
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricOrangeHover
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ShadcnButton(
                onClick = onNavigate,
                variant = ShadcnButtonVariant.Primary,
                modifier = Modifier.fillMaxWidth().height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Dẫn đường ngay", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}
