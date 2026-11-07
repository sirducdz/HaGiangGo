package com.reed.hagianggo.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
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
        containerColor = SurfaceWhite,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Tiêu đề & Cụm vị trí
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "LÀM GÌ BÂY GIỜ?",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ShadcnBadge(text = "AI GỢI Ý", variant = ShadcnBadgeVariant.Orange)
                        }
                        Text(
                            text = "Gợi ý thông minh tức thì theo vị trí đèo thực tế",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Hàng 1: Chọn Cụm Chặng (hoặc GPS tự động)
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Vị trí: ${activeZone?.name ?: "Tự động theo GPS"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                        }

                        // Danh sách chọn chặng nhanh
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                val isAuto = query.zoneId == null
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isAuto) ElectricOrangeSoft else SurfaceMuted)
                                        .border(BorderStroke(1.dp, if (isAuto) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                                        .clickable { onZoneManualSelected(null) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Tự động (GPS)", fontSize = 11.sp, color = if (isAuto) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            items(zones.sortedBy { it.seq }) { zone ->
                                val isSelected = query.zoneId == zone.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) ElectricOrangeSoft else SurfaceMuted)
                                        .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                                        .clickable { onZoneManualSelected(zone.id) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("${zone.seq}. ${zone.name.split("-")[0].trim()}", fontSize = 11.sp, color = if (isSelected) ElectricOrangeHover else TextSecondary, fontWeight = FontWeight.SemiBold)
                                }
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
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AvailableDuration.entries.forEach { dur ->
                            val isSelected = query.availableDuration == dur
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricOrangeSoft else SurfaceWhite)
                                    .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { onQueryChanged(query.copy(availableDuration = dur)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dur.title,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (isSelected) ElectricOrangeHover else TextSecondary
                                )
                            }
                        }
                    }

                    // Thời tiết
                    Text(
                        text = "2. Thời tiết lúc này:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CurrentWeather.entries.forEach { w ->
                            val isSelected = query.weather == w
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricOrangeSoft else SurfaceWhite)
                                    .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { onQueryChanged(query.copy(weather = w)) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = w.title,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                    color = if (isSelected) ElectricOrangeHover else TextSecondary
                                )
                            }
                        }
                    }

                    // Nhu cầu
                    Text(
                        text = "3. Bạn đang muốn gì:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(CurrentNeed.entries) { need ->
                            val isSelected = query.need == need
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ElectricOrangeSoft else SurfaceWhite)
                                    .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(8.dp))
                                    .clickable { onQueryChanged(query.copy(need = need)) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (need) {
                                        CurrentNeed.EAT -> Icons.Default.Restaurant
                                        CurrentNeed.CAFE_REST -> Icons.Default.LocalCafe
                                        CurrentNeed.VIEW -> Icons.Default.PhotoCamera
                                        CurrentNeed.RAIN_SHELTER -> Icons.Default.Umbrella
                                        CurrentNeed.FUEL_SOS -> Icons.Default.LocalGasStation
                                        CurrentNeed.ANY -> Icons.Default.Explore
                                    }
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) ElectricOrangeHover else TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        need.title,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) ElectricOrangeHover else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cảnh báo giờ tối đèo (nếu có)
            val firstWarning = suggestions.firstOrNull { it.isSunsetWarning }
            if (firstWarning != null && firstWarning.sunsetWarningMessage.isNotBlank()) {
                item {
                    ShadcnAlert(
                        title = "CẢNH BÁO TỐI ĐÈO",
                        description = firstWarning.sunsetWarningMessage,
                        variant = ShadcnBadgeVariant.Rose,
                        icon = Icons.Default.Warning
                    )
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
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SurfaceMuted)
                            .clickable(onClick = onRefreshAlternative)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = ElectricOrange)
                        Spacer(Modifier.width(4.dp))
                        Text("Đổi gợi ý", fontSize = 11.sp, color = ElectricOrange, fontWeight = FontWeight.SemiBold)
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
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tên & Huy hiệu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ShadcnBadge(
                            text = "${result.distanceKm} km",
                            variant = ShadcnBadgeVariant.Emerald
                        )
                        Text(
                            text = "~${result.travelTimeMin} phút chạy đèo",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Row {
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (place.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Lưu",
                            tint = if (place.isBookmarked) ElectricOrange else TextSubtle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleVisited, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (place.isVisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Đã đến",
                            tint = if (place.isVisited) EmeraldGreen else TextSubtle,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Mẹo sống còn (Vital practical tip)
            if (place.practicalTip.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceMuted)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "💡 Mẹo: ${place.practicalTip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Nút bấm thao tác
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(40.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DẪN ĐƯỜNG", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                if (place.phone.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Gọi điện", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
