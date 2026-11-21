package com.reed.hagianggo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryPlacePickerSheet(
    dayNumber: Int,
    initialSlot: TimeSlot,
    places: List<Place>,
    zones: List<Zone>,
    existingDayPlaceIds: Set<String>,
    onSelectPlace: (placeId: String, targetSlot: TimeSlot) -> Unit,
    onCreateCustomPlace: (targetSlot: TimeSlot) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    var selectedCategory by remember { mutableStateOf<PlaceCategory?>(null) }
    val zonesById = remember(zones) { zones.associateBy { it.id } }

    val filteredPlaces = remember(places, searchQuery, selectedCategory, zonesById) {
        val q = searchQuery.trim().lowercase()
        places.filter { p ->
            val catMatch = selectedCategory == null || p.category == selectedCategory
            val zoneName = zonesById[p.zoneId]?.name?.lowercase() ?: ""
            val queryMatch = q.isEmpty() ||
                p.name.lowercase().contains(q) ||
                p.addressHint.lowercase().contains(q) ||
                zoneName.contains(q)
            catMatch && queryMatch
        }.sortedWith(
            compareBy<Place> { it.id in existingDayPlaceIds }
                .thenBy { zonesById[it.zoneId]?.seq ?: 99 }
                .thenBy { it.name }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = AppBackground,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "THÊM ĐIỂM VÀO NGÀY $dayNumber",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                    Text(
                        text = "Chọn từ 48+ điểm có sẵn hoặc tự tạo homestay/quán mới",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ElectricOrangeSoft)
                        .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                        .springClickable { onCreateCustomPlace(selectedSlot) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "+ Tạo mới",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricOrangeHover
                    )
                }
            }

            // Chọn buổi cần chèn vào
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chèn vào:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING).forEach { slot ->
                    val isSelected = selectedSlot == slot
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) ElectricOrange else SurfaceWhite)
                            .border(
                                BorderStroke(1.dp, if (isSelected) ElectricOrangeHover else BorderSubtle),
                                RoundedCornerShape(9.dp)
                            )
                            .springClickable { selectedSlot = slot }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = slot.label,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm tên điểm, bản làng, đèo, cây xăng...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = ElectricOrange,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Bộ lọc danh mục
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filterOptions: List<Pair<PlaceCategory?, String>> = listOf(
                    null to "Tất cả",
                    PlaceCategory.CHECKIN_VIEW to "Ngắm cảnh",
                    PlaceCategory.EAT to "Ăn uống",
                    PlaceCategory.CAFE_REST to "Cà phê",
                    PlaceCategory.HOMESTAY to "Homestay",
                    PlaceCategory.FUEL to "Cây xăng"
                )
                filterOptions.forEach { (cat, label) ->
                    val isSel = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSel) SkyTelemetry else SurfaceWhite)
                            .border(
                                BorderStroke(1.dp, if (isSel) SkyTelemetry else BorderSubtle),
                                CircleShape
                            )
                            .springClickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSel) Color.White else TextSecondary
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredPlaces, key = { it.id }) { place ->
                    val alreadyInDay = place.id in existingDayPlaceIds
                    val zoneName = zonesById[place.zoneId]?.name ?: "Hà Giang"
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = if (alreadyInDay) EmeraldGreenBorder else BorderSubtle
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${place.category.label} • $zoneName • ▲ ${place.altitudeMeters}m • Ở ${place.durationMin}p",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (alreadyInDay) {
                                ShadcnBadge(text = "Đã có", variant = ShadcnBadgeVariant.Emerald)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(ElectricOrange)
                                        .springClickable { onSelectPlace(place.id, selectedSlot) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "+ Thêm",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoveStopDialog(
    placeName: String,
    currentDay: Int,
    currentSlot: TimeSlot,
    totalDays: Int,
    onConfirm: (targetDay: Int, targetSlot: TimeSlot) -> Unit,
    onDismiss: () -> Unit
) {
    var targetDay by remember { mutableIntStateOf(currentDay) }
    var targetSlot by remember { mutableStateOf(currentSlot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceWhite,
        title = {
            Column {
                Text(
                    text = "Chuyển điểm sang lịch khác",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = placeName,
                    style = MaterialTheme.typography.bodySmall,
                    color = ElectricOrangeHover,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chọn ngày:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..totalDays).forEach { day ->
                        val isSel = targetDay == day
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) ElectricOrange else SurfaceMuted)
                                .border(
                                    BorderStroke(1.dp, if (isSel) ElectricOrangeHover else BorderSubtle),
                                    RoundedCornerShape(10.dp)
                                )
                                .springClickable { targetDay = day }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ngày $day",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else TextPrimary
                            )
                        }
                    }
                }

                Text("Chọn buổi trong ngày:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING).forEach { slot ->
                        val isSel = targetSlot == slot
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) SkyTelemetry else SurfaceMuted)
                                .border(
                                    BorderStroke(1.dp, if (isSel) SkyTelemetry else BorderSubtle),
                                    RoundedCornerShape(10.dp)
                                )
                                .springClickable { targetSlot = slot }
                                .padding(horizontal = 11.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = slot.label,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            ShadcnButton(
                onClick = { onConfirm(targetDay, targetSlot) },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("Chuyển điểm")
            }
        },
        dismissButton = {
            ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) {
                Text("Hủy", color = TextSecondary)
            }
        }
    )
}
