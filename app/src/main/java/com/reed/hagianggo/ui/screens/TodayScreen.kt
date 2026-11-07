package com.reed.hagianggo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
        modifier = modifier.fillMaxSize()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Big "LÀM GÌ BÂY GIỜ?" Action Button
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openWhatNow() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SunsetOrange),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Explore,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "LÀM GÌ BÂY GIỜ?",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Đang ở ${activeZone?.name ?: "Hà Giang"} • Chạm để gợi ý ngay",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }

                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Location & Zone Status Pill
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                tint = if (isOutsideHaGiang) AmberGlow else if (currentLocation != null) HighlandGreen else SlateBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isOutsideHaGiang) "GPS: Ngoài Hà Giang (~${distanceToHaGiangKm?.toInt() ?: 0} km)"
                                    else if (currentLocation != null) "Vị trí GPS: Hoạt động"
                                    else "Chưa có tín hiệu GPS",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isOutsideHaGiang) AmberGlow else if (currentLocation != null) HighlandGreen else SlateBlue
                                )
                                Text(
                                    text = if (isOutsideHaGiang) "Khu vực: ${activeZone?.name ?: "TP. Hà Giang"} (Xem trước)"
                                    else "Khu vực: ${activeZone?.name ?: "Chưa xác định"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = { showZonePicker = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đổi vùng", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 3. Mountain Sunset Warning Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isNight -> AlertRed.copy(alpha = 0.15f)
                            isNearDark -> AmberGlow.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = when {
                        isNight -> androidx.compose.foundation.BorderStroke(1.5.dp, AlertRed)
                        isNearDark -> androidx.compose.foundation.BorderStroke(1.5.dp, AmberGlow)
                        else -> null
                    }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isNight || isNearDark) Icons.Default.Warning else Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = if (isNight) AlertRed else if (isNearDark) AmberGlow else SunsetOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isNight -> "ĐÃ TỐI TRÊN ĐÈO HÀ GIANG!"
                                    isNearDark -> "CẢNH BÁO HOÀNG HÔN SẮP TẮT (${minutesUntilSunset} phút nữa)"
                                    else -> "Thời gian an toàn đến hoàng hôn (17:15)"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isNight) AlertRed else if (isNearDark) AmberGlow else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    isNight -> "Nên về homestay ngay. Đèo núi đêm tối om, cua gấp và nhiều sương mù rất nguy hiểm!"
                                    isNearDark -> "Nên giảm tốc độ và chốt điểm nghỉ/ăn tối trước 17:15 để tránh chạy đèo đêm."
                                    else -> "Còn khoảng ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p ánh sáng ban ngày. Hãy căn giờ vượt đèo hợp lý."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 4. Day Tabs (Day 1, Day 2, Day 3, ...)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..config.durationDays).forEach { day ->
                        val isSelected = selectedDay == day
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDay = day },
                            label = {
                                Text(
                                    text = "Ngày $day",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // 5. Day Route Summary Hint
            item {
                val dayRouteHint = when (selectedDay) {
                    1 -> "📍 Chặng Ngày 1: TP. Hà Giang ➔ Dốc Bắc Sum ➔ Cổng Trời Quản Bạ ➔ Rừng Thông Yên Minh ➔ Phố Cáo ➔ Phố Cổ Đồng Văn (~140km)"
                    2 -> "📍 Chặng Ngày 2: Đồng Văn ➔ Cột Cờ Lũng Cú ➔ Làng Lô Lô Chải ➔ Đèo Mã Pí Lèng ➔ Hẻm Tu Sản / Sông Nho Quế ➔ Mèo Vạc (~80km)"
                    3 -> "📍 Chặng Ngày 3: Mèo Vạc ➔ Cua Chữ M ➔ Thác Du Già ➔ Đường Thượng ➔ Về lại TP. Hà Giang (~150km)"
                    else -> "📍 Chặng Ngày $selectedDay: Khám phá thêm các bản làng, chợ phiên và trở về an toàn."
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = dayRouteHint,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 6. Timeline of Activities for Selected Day
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
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
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
            title = { Text("Chọn cụm chặng hiện tại") },
            text = {
                LazyColumn {
                    item {
                        ListItem(
                            headlineContent = { Text("Tự động theo GPS") },
                            leadingContent = { Icon(Icons.Default.AutoMode, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.setManualZoneOverride(null)
                                showZonePicker = false
                            }
                        )
                        HorizontalDivider()
                    }
                    items(zones) { zone ->
                        ListItem(
                            headlineContent = { Text(zone.name) },
                            supportingContent = { Text("Độ cao ~${zone.elevationMeters}m") },
                            leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                            modifier = Modifier.clickable {
                                viewModel.setManualZoneOverride(zone.id)
                                showZonePicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showZonePicker = false }) {
                    Text("Đóng")
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
            TimeSlot.MORNING -> Icons.Default.WbTwilight to SunsetOrange
            TimeSlot.NOON -> Icons.Default.WbSunny to FuelGold
            TimeSlot.AFTERNOON -> Icons.Default.CloudQueue to SlateBlue
            TimeSlot.EVENING -> Icons.Default.NightsStay to MistBlue
        }
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${slot.label.uppercase()} (${slot.timeRange})",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (place.isVisited) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleVisited,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        if (place.isVisited) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = "Trạng thái",
                        tint = if (place.isVisited) HighlandGreen else MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                    )
                    Text(
                        text = "${place.category.label} • Khoảng ${place.suggestedDurationMin} phút",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (place.phoneNumber.isNotBlank()) {
                    IconButton(onClick = onCall, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Phone, contentDescription = "Gọi", tint = HighlandGreen)
                    }
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

            if (customNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📝 Ghi chú: $customNote",
                    style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = SunsetOrange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onNavigate,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
            ) {
                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Dẫn đường ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
