package com.reed.hagianggo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import java.util.Locale

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
    var refreshSpinCount by remember { mutableIntStateOf(0) }
    val refreshRotation by animateFloatAsState(
        targetValue = refreshSpinCount * 180f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
        label = "RefreshSpin"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = AppBackground,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Tiêu đề & Cụm vị trí
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ElectricOrange,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LÀM GÌ BÂY GIỜ?",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            ShadcnBadge(text = "OFFLINE AI", variant = ShadcnBadgeVariant.Orange)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gợi ý điểm dừng tối ưu theo quỹ giờ, thời tiết & cự ly đèo",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(SurfaceWhite)
                            .springClickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 2. Chọn Cụm Chặng (hoặc GPS tự động)
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Đang xét tại: ${activeZone?.name ?: "Tự động theo GPS"}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                val isAuto = query.zoneId == null
                                WhatNowPillOption(
                                    text = "Tự động (GPS)",
                                    isSelected = isAuto,
                                    onClick = { onZoneManualSelected(null) }
                                )
                            }
                            items(zones.sortedBy { it.seq }, key = { it.id }) { zone ->
                                val isSelected = query.zoneId == zone.id
                                WhatNowPillOption(
                                    text = "${zone.seq}. ${zone.name.split("-")[0].trim()}",
                                    isSelected = isSelected,
                                    onClick = { onZoneManualSelected(zone.id) }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Bộ 3 Bộ lọc 1 chạm (Thời gian • Thời tiết • Nhu cầu)
            item {
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "1. QUỸ THỜI GIAN ĐANG CÓ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AvailableDuration.entries.forEach { dur ->
                                val isSelected = query.availableDuration == dur
                                val bg by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeSoft else SurfaceMuted,
                                    animationSpec = tween(160),
                                    label = "DurBg"
                                )
                                val border by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeBorder else BorderSubtle,
                                    animationSpec = tween(160),
                                    label = "DurBorder"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
                                        .springClickable { onQueryChanged(query.copy(availableDuration = dur)) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dur.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (isSelected) ElectricOrangeHover else TextSecondary
                                    )
                                }
                            }
                        }

                        Text(
                            text = "2. THỜI TIẾT TRÊN ĐÈO LÚC NÀY",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CurrentWeather.entries.forEach { w ->
                                val isSelected = query.weather == w
                                val bg by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeSoft else SurfaceMuted,
                                    animationSpec = tween(160),
                                    label = "WeatherBg"
                                )
                                val border by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeBorder else BorderSubtle,
                                    animationSpec = tween(160),
                                    label = "WeatherBorder"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(BorderStroke(1.dp, border), RoundedCornerShape(12.dp))
                                        .springClickable { onQueryChanged(query.copy(weather = w)) }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (w == CurrentWeather.SUNNY) Icons.Default.WbSunny else Icons.Default.Cloud,
                                            contentDescription = null,
                                            tint = if (isSelected) ElectricOrangeHover else TextMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = w.title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            color = if (isSelected) ElectricOrangeHover else TextSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "3. BẠN ĐANG MUỐN GÌ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextMuted
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(CurrentNeed.entries, key = { it.code }) { need ->
                                val isSelected = query.need == need
                                val bg by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeSoft else SurfaceMuted,
                                    animationSpec = tween(160),
                                    label = "NeedBg"
                                )
                                val border by animateColorAsState(
                                    targetValue = if (isSelected) ElectricOrangeBorder else BorderSubtle,
                                    animationSpec = tween(160),
                                    label = "NeedBorder"
                                )
                                val icon = when (need) {
                                    CurrentNeed.EAT -> Icons.Default.Restaurant
                                    CurrentNeed.CAFE_REST -> Icons.Default.LocalCafe
                                    CurrentNeed.VIEW -> Icons.Default.PhotoCamera
                                    CurrentNeed.RAIN_SHELTER -> Icons.Default.Umbrella
                                    CurrentNeed.FUEL_SOS -> Icons.Default.LocalGasStation
                                    CurrentNeed.ANY -> Icons.Default.Explore
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(bg)
                                        .border(BorderStroke(1.dp, border), CircleShape)
                                        .springClickable { onQueryChanged(query.copy(need = need)) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = if (isSelected) ElectricOrangeHover else TextMuted
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            need.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isSelected) ElectricOrangeHover else TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Cảnh báo giờ tối đèo (nếu có)
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

            // 5. Tiêu đề kết quả & Nút Đổi gợi ý khác
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOP ${suggestions.size} GỢI Ý PHÙ HỢP NHẤT",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextMuted
                    )
                    Surface(
                        modifier = Modifier.springClickable {
                            refreshSpinCount++
                            onRefreshAlternative()
                        },
                        shape = CircleShape,
                        color = ElectricOrangeSoft,
                        border = BorderStroke(1.dp, ElectricOrangeBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer { rotationZ = refreshRotation },
                                tint = ElectricOrangeHover
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Đổi gợi ý",
                                fontSize = 11.5.sp,
                                color = ElectricOrangeHover,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 6. Danh sách gợi ý với animateItem
            if (suggestions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Không có địa điểm nào khớp với bộ lọc trên.\nHãy thử nới lỏng quỹ thời gian hoặc chọn nhu cầu 'Linh hoạt' nhé!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(suggestions, key = { it.place.id }) { item ->
                    SuggestionCard(
                        result = item,
                        onNavigate = {
                            LocationMath.openNavigationIntent(context, item.place.lat, item.place.lng, item.place.name)
                        },
                        onCall = {
                            LocationMath.openDialerIntent(context, item.place.phone)
                        },
                        onToggleBookmark = { onToggleBookmark(item.place.id) },
                        onToggleVisited = { onToggleVisited(item.place.id) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(220),
                            fadeOutSpec = tween(160),
                            placementSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
                        )
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun WhatNowPillOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) ElectricOrangeSoft else SurfaceMuted,
        animationSpec = tween(160),
        label = "ZoneOptBg"
    )
    val border by animateColorAsState(
        targetValue = if (isSelected) ElectricOrangeBorder else BorderSubtle,
        animationSpec = tween(160),
        label = "ZoneOptBorder"
    )
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, border), CircleShape)
            .springClickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            color = if (isSelected) ElectricOrangeHover else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
fun SuggestionCard(
    result: SuggestionResult,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVisited: () -> Unit,
    modifier: Modifier = Modifier
) {
    val place = result.place
    val bookmarkScale by animateFloatAsState(
        targetValue = if (place.isBookmarked) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "SugBookmarkBounce"
    )
    val visitedScale by animateFloatAsState(
        targetValue = if (place.isVisited) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "SugVisitedBounce"
    )

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShadcnBadge(
                            text = "${String.format(Locale.US, "%.1f", result.distanceKm)} km",
                            variant = ShadcnBadgeVariant.Emerald
                        )
                        Text(
                            text = "~${result.travelTimeMin}p chạy đèo",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TextSecondary
                        )
                        Text(
                            text = "• ${place.altitudeMeters}m",
                            style = TelemetryTextStyle.copy(fontSize = 11.sp),
                            color = TextMuted
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (place.isBookmarked) ElectricOrangeSoft else SurfaceMuted)
                            .springClickable(onClick = onToggleBookmark),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (place.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Lưu",
                            tint = if (place.isBookmarked) ElectricOrange else TextMuted,
                            modifier = Modifier
                                .size(17.dp)
                                .graphicsLayer {
                                    scaleX = bookmarkScale
                                    scaleY = bookmarkScale
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (place.isVisited) EmeraldGreenSoft else SurfaceMuted)
                            .springClickable(onClick = onToggleVisited),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (place.isVisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "Đã đến",
                            tint = if (place.isVisited) EmeraldGreen else TextMuted,
                            modifier = Modifier
                                .size(17.dp)
                                .graphicsLayer {
                                    scaleX = visitedScale
                                    scaleY = visitedScale
                                }
                        )
                    }
                }
            }

            if (place.addressHint.isNotBlank()) {
                Text(
                    text = "📍 ${place.addressHint}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
            }

            if (place.practicalTip.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceMuted)
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "💡 ${place.practicalTip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("DẪN ĐƯỜNG", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (place.phone.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Gọi điện", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
