package com.reed.hagianggo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatNowBottomSheet(
    query: WhatNowQuery,
    zones: List<Zone>,
    activeZone: Zone?,
    suggestions: List<SuggestionResult>,
    onQueryChanged: (WhatNowQuery) -> Unit,
    onZoneManualSelected: (String?) -> Unit,
    onRefreshAlternative: () -> Unit,
    onToggleBookmark: (String) -> Unit,
    onToggleVisited: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tiêu đề & Cụm vị trí
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Làm gì bây giờ?",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        Text(
                            text = "Gợi ý thông minh tức thì theo vị trí đèo",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.Gray)
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
            }

            // Hàng 1: Chọn Cụm Chặng (hoặc GPS tự động)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = SunsetOrange)
                                Text(
                                    text = "Đang ở: ${activeZone?.name ?: "Tự động theo GPS"}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }

                        // Danh sách chọn chặng nhanh
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                FilterChip(
                                    selected = query.zoneId == null,
                                    onClick = { onZoneManualSelected(null) },
                                    label = { Text("Tự động (GPS)") }
                                )
                            }
                            items(zones.sortedBy { it.seq }) { zone ->
                                FilterChip(
                                    selected = query.zoneId == zone.id,
                                    onClick = { onZoneManualSelected(zone.id) },
                                    label = { Text("${zone.seq}. ${zone.name.split("-")[0].trim()}") }
                                )
                            }
                        }
                    }
                }
            }

            // Hàng 2: Bộ 3 Bộ lọc 1 chạm (Thời gian • Thời tiết • Nhu cầu)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Thời gian
                    Text(
                        text = "1. Quỹ thời gian đang có:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AvailableDuration.entries.forEach { dur ->
                            val isSelected = query.availableDuration == dur
                            OutlinedButton(
                                onClick = { onQueryChanged(query.copy(availableDuration = dur)) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = if (isSelected) 2.dp else 1.dp
                                )
                            ) {
                                Text(
                                    text = dur.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Thời tiết
                    Text(
                        text = "2. Thời tiết lúc này:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CurrentWeather.entries.forEach { w ->
                            val isSelected = query.weather == w
                            OutlinedButton(
                                onClick = { onQueryChanged(query.copy(weather = w)) },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    width = if (isSelected) 2.dp else 1.dp
                                )
                            ) {
                                Text(
                                    text = w.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Nhu cầu
                    Text(
                        text = "3. Bạn đang muốn gì:",
                        style = MaterialTheme.typography.labelLarge
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CurrentNeed.entries) { need ->
                            val isSelected = query.need == need
                            FilterChip(
                                selected = isSelected,
                                onClick = { onQueryChanged(query.copy(need = need)) },
                                label = { Text(need.title) },
                                leadingIcon = {
                                    when (need) {
                                        CurrentNeed.EAT -> Icon(Icons.Default.Restaurant, null, Modifier.size(16.dp))
                                        CurrentNeed.CAFE_REST -> Icon(Icons.Default.LocalCafe, null, Modifier.size(16.dp))
                                        CurrentNeed.VIEW -> Icon(Icons.Default.PhotoCamera, null, Modifier.size(16.dp))
                                        CurrentNeed.RAIN_SHELTER -> Icon(Icons.Default.Umbrella, null, Modifier.size(16.dp))
                                        CurrentNeed.FUEL_SOS -> Icon(Icons.Default.LocalGasStation, null, Modifier.size(16.dp))
                                        CurrentNeed.ANY -> Icon(Icons.Default.Explore, null, Modifier.size(16.dp))
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Cảnh báo giờ tối đèo (nếu có)
            val firstWarning = suggestions.firstOrNull { it.isSunsetWarning }
            if (firstWarning != null && firstWarning.sunsetWarningMessage.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AlertRedLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.size(28.dp))
                            Text(
                                text = firstWarning.sunsetWarningMessage,
                                style = MaterialTheme.typography.bodyMedium.copy(color = AlertRed, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }

            // Tiêu đề kết quả & Nút Đổi gợi ý khác
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top ${suggestions.size} gợi ý tốt nhất:",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(onClick = onRefreshAlternative) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Đổi gợi ý khác")
                    }
                }
            }

            // Danh sách gợi ý
            if (suggestions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không có địa điểm nào khớp với bộ lọc trên.\nHãy thử nới lỏng quỹ thời gian hoặc chọn nhu cầu 'Linh hoạt' nhé!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                items(suggestions) { item ->
                    SuggestionCard(
                        result = item,
                        onNavigate = {
                            LocationMath.openNavigationIntent(context, item.place.lat, item.place.lng, item.place.name)
                        },
                        onCall = {
                            LocationMath.openDialerIntent(context, item.place.phone)
                        },
                        onToggleBookmark = { onToggleBookmark(item.place.id) },
                        onToggleVisited = { onToggleVisited(item.place.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SuggestionCard(
    result: SuggestionResult,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVisited: () -> Unit
) {
    val place = result.place

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tên & Huy hiệu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        )
                    }
                    Text(
                        text = "${place.category.title} • ~${result.distanceKm} km (~${result.travelTimeMin} phút chạy đèo)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SunsetOrange,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Row {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (place.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Lưu",
                            tint = if (place.isBookmarked) SunsetOrange else Color.Gray
                        )
                    }
                    IconButton(onClick = onToggleVisited) {
                        Icon(
                            imageVector = if (place.isVisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Đã đến",
                            tint = if (place.isVisited) HighlandGreen else Color.Gray
                        )
                    }
                }
            }

            // Mẹo sống còn (Vital practical tip)
            if (place.practicalTip.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FuelGoldLight, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💡 Mẹo: ${place.practicalTip}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SunsetOrangeDark,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // Nút bấm lớn ngoài trời (>= 50dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DẪN ĐƯỜNG", fontWeight = FontWeight.Bold)
                }

                if (place.phone.isNotBlank()) {
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = HighlandGreen)
                        Spacer(Modifier.width(4.dp))
                        Text("Gọi điện")
                    }
                }
            }
        }
    }
}
