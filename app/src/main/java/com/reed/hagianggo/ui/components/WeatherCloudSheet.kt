package com.reed.hagianggo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherCloudSheet(
    zones: List<Zone>,
    activeZone: Zone?,
    forecasts: Map<String, ZoneWeatherForecast>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onApplyToWhatNow: (Zone, ZoneWeatherForecast) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFmt = remember { SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()) }

    val latestSyncTime = remember(forecasts) {
        forecasts.values.maxOfOrNull { it.fetchedAt } ?: 0L
    }

    val bestCloudZonePair = remember(zones, forecasts) {
        zones.filter { it.elevationMeters >= 950 }
            .mapNotNull { z -> forecasts[z.id]?.let { z to it } }
            .maxByOrNull { it.second.cloudHuntScore }
    }

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
                .heightIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "weather_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "THỜI TIẾT ĐÈO CAO & SĂN MÂY",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = TextPrimary
                        )
                        Text(
                            text = if (latestSyncTime > 0L) {
                                "Đã lưu Offline lúc ${timeFmt.format(Date(latestSyncTime))}"
                            } else {
                                "Đang hiển thị nền nhiệt theo độ cao • Bấm Đồng bộ khi có mạng"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    ShadcnButton(
                        onClick = onSyncNow,
                        enabled = !isSyncing,
                        variant = ShadcnButtonVariant.Primary,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = SurfaceWhite
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(15.dp))
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isSyncing) "Đang tải..." else "Đồng bộ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Điểm săn biển mây lý tưởng nhất sáng sớm
            bestCloudZonePair?.let { (bestZone, bestForecast) ->
                item(key = "best_cloud_banner") {
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        backgroundColor = SkyTelemetrySoft,
                        borderColor = SkyTelemetryBorder
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShadcnBadge(text = "☁️ ĐIỂM SĂN MÂY SÁNG SỚM ĐẸP NHẤT", variant = ShadcnBadgeVariant.Sky)
                                Text(
                                    text = "${bestForecast.cloudHuntScore}% CƠ HỘI",
                                    style = TelemetryTextStyle.copy(fontSize = 14.sp),
                                    color = SkyTelemetry
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${bestZone.name} (▲ ${bestZone.elevationMeters}m)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Khung giờ vàng săn biển mây: 05:30 – 07:45 sáng • Nhiệt độ thấp nhất ~${bestForecast.minTempC}°C.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            items(zones, key = { it.id }) { zone ->
                val fc = forecasts[zone.id] ?: return@items
                val isCurrentZone = activeZone?.id == zone.id

                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = when {
                        fc.isFogHazard -> RoseDestructiveBorder
                        isCurrentZone -> ElectricOrange
                        else -> BorderSubtle
                    },
                    borderWidth = if (isCurrentZone) 1.5.dp else 1.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${zone.seq}. ${zone.name}",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    if (isCurrentZone) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        ShadcnBadge(text = "ĐANG Ở ĐÂY", variant = ShadcnBadgeVariant.Orange)
                                    }
                                }
                                Text(
                                    text = "▲ Độ cao ${zone.elevationMeters}m • ${fc.summaryLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${fc.currentTempC}°C",
                                    style = TelemetryTextStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Black),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${fc.minTempC}° – ${fc.maxTempC}°C",
                                    style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ShadcnBadge(
                                    text = "☁️ Mây ${fc.cloudHuntScore}%",
                                    variant = if (fc.cloudHuntScore >= 70) ShadcnBadgeVariant.Emerald else ShadcnBadgeVariant.Sky
                                )
                                ShadcnBadge(
                                    text = "🌧️ ${fc.rainProbPercent}%",
                                    variant = if (fc.rainProbPercent >= 55) ShadcnBadgeVariant.Amber else ShadcnBadgeVariant.Default
                                )
                                if (fc.isFogHazard) {
                                    ShadcnBadge(
                                        text = "🌫️ Mù dày",
                                        variant = ShadcnBadgeVariant.Rose
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(ElectricOrangeSoft)
                                    .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                                    .springClickable { onApplyToWhatNow(zone, fc) }
                                    .padding(horizontal = 10.dp, vertical = 5.5.dp)
                            ) {
                                Text(
                                    text = "Gợi ý ➔",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricOrangeHover,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        if (fc.dailyForecasts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceMuted)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                fc.dailyForecasts.take(4).forEach { dayItem ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = dayItem.dateLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = TextMuted
                                        )
                                        Text(
                                            text = "${dayItem.minTempC}°-${dayItem.maxTempC}°",
                                            style = TelemetryTextStyle.copy(fontSize = 11.5.sp),
                                            color = TextPrimary
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
}
