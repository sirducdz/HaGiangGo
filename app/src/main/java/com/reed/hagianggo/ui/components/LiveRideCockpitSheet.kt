package com.reed.hagianggo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRideCockpitSheet(
    state: LiveRideState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Tính thời gian hoàng hôn
    val now = remember { Calendar.getInstance() }
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val sunsetMinutes = 17 * 60 + 15
    val minutesUntilSunset = sunsetMinutes - currentMinutes
    val isNearDark = currentMinutes >= (16 * 60 + 30)

    val hours = state.elapsedTimeSeconds / 3600
    val minutes = (state.elapsedTimeSeconds % 3600) / 60
    val seconds = state.elapsedTimeSeconds % 60
    val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceWhite,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isPaused) AmberWarning else EmeraldGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (state.isPaused) "TẠM DỪNG CHẶNG" else "LIVE RIDE TELEMETRY",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (state.isPaused) AmberWarningText else EmeraldGreenText
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Thu nhỏ", tint = TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Giant Gauges: Vận tốc & Cao độ (phong cách HUD đồng hồ xe)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Đồng hồ Vận tốc
                ShadcnCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "VẬN TỐC",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Text(
                            text = state.currentSpeedKmh.toInt().toString(),
                            style = TelemetryTextStyle.copy(fontSize = 54.sp),
                            color = ElectricOrange
                        )
                        Text(
                            text = "km/h",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // Đồng hồ Độ cao (Altitude)
                ShadcnCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Landscape, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CAO ĐỘ",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = state.currentAltitude.toInt().toString(),
                            style = TelemetryTextStyle.copy(fontSize = 54.sp),
                            color = EmeraldGreenText
                        )
                        Text(
                            text = "mét / biển",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stats Strip: Quãng đường • Thời gian • Max Speed
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                backgroundColor = SurfaceMuted,
                borderColor = BorderSubtle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("QUÃNG ĐƯỜNG", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", state.totalDistanceKm)} km",
                            style = TelemetryTextStyle.copy(fontSize = 15.sp),
                            color = TextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("THỜI GIAN", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = timeFormatted,
                            style = TelemetryTextStyle.copy(fontSize = 15.sp),
                            color = TextPrimary
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TỐC ĐỘ MAX", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${state.maxSpeedKmh.toInt()} km/h",
                            style = TelemetryTextStyle.copy(fontSize = 15.sp),
                            color = ElectricOrangeHover
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sunset Countdown Banner
            ShadcnAlert(
                title = if (minutesUntilSunset > 0) "Hoàng hôn đèo (17:15): Còn ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p"
                else "ĐÃ TỐI TRÊN ĐÈO! NÊN VỀ ĐIỂM NGHỈ",
                description = "Đèo Hà Giang không có đèn chiếu sáng, nhiều sương mù và dốc quanh co rất nguy hiểm.",
                variant = if (isNearDark) ShadcnBadgeVariant.Rose else ShadcnBadgeVariant.Amber,
                icon = if (isNearDark) Icons.Default.Warning else Icons.Default.WbTwilight
            )

            // Auto Check-in Toast / Badge
            if (!state.lastVisitedPlaceName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = EmeraldGreenSoft,
                    borderColor = EmeraldGreenBorder
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vừa tự động check-in: ${state.lastVisitedPlaceName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EmeraldGreenText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Điểm kế tiếp phía trước (Next Milestone)
            if (state.nextMilestonePlace != null) {
                val nextPlace = state.nextMilestonePlace
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NearMe, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ĐIỂM GẦN NHẤT PHÍA TRƯỚC",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricOrangeHover
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nextPlace.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${nextPlace.category.label} • Còn ~${String.format(java.util.Locale.US, "%.1f", state.distanceToNextKm)} km",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            ShadcnButton(
                                onClick = {
                                    LocationMath.openNavigationIntent(context, nextPlace.lat, nextPlace.lng, nextPlace.name)
                                },
                                variant = ShadcnButtonVariant.Primary,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dẫn đường", fontSize = 11.sp)
                            }
                        }

                        if (nextPlace.practicalTip.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 ${nextPlace.practicalTip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Tạm dừng / Tiếp tục & Kết thúc
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isPaused) {
                    ShadcnButton(
                        onClick = onResume,
                        variant = ShadcnButtonVariant.Primary,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tiếp tục", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    ShadcnButton(
                        onClick = onPause,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = AmberWarningText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tạm dừng", fontWeight = FontWeight.SemiBold, color = AmberWarningText)
                    }
                }

                ShadcnButton(
                    onClick = onStop,
                    variant = ShadcnButtonVariant.Destructive,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kết thúc chặng", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
