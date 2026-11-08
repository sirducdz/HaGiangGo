package com.reed.hagianggo.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveRideCockpitSheet(
    state: LiveRideState,
    fuelState: FuelState = FuelState(),
    fuelAdvisory: FuelAdvisory? = null,
    onRefuelFull: () -> Unit = {},
    onOpenSos: () -> Unit = {},
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val now = remember { Calendar.getInstance() }
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val sunsetMinutes = 17 * 60 + 15
    val minutesUntilSunset = sunsetMinutes - currentMinutes
    val isNearDark = currentMinutes >= (16 * 60 + 30)

    val hours = state.elapsedTimeSeconds / 3600
    val minutes = (state.elapsedTimeSeconds % 3600) / 60
    val seconds = state.elapsedTimeSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "CockpitBeacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CockpitPulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CockpitPulseAlpha"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = AppBackground,
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
            // 1. Flighty Telemetry Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = if (state.isPaused) AmberWarning else EmeraldGreen
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(18.dp)) {
                        if (!state.isPaused) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                        alpha = pulseAlpha * 0.45f
                                    }
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (state.isPaused) "TẠM DỪNG GHI HÀNH TRÌNH" else "BUỒNG LÁI ĐÈO LIVE TELEMETRY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp
                            ),
                            color = if (state.isPaused) AmberWarningText else EmeraldGreenText
                        )
                        Text(
                            text = "GPS Offline • Đang theo dõi tốc độ & cao độ",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SurfaceWhite)
                        .springClickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Thu nhỏ", tint = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Dual Radial HUD Gauges: Vận tốc & Cao độ (AnimatedArcGauge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedArcGauge(
                    label = "VẬN TỐC",
                    value = state.currentSpeedKmh,
                    maxValue = 80f,
                    unit = "km/h",
                    accentColor = ElectricOrange,
                    softColor = ElectricOrangeSoft,
                    modifier = Modifier.weight(1f)
                )

                AnimatedArcGauge(
                    label = "CAO ĐỘ",
                    value = state.currentAltitude.toFloat(),
                    maxValue = 1800f,
                    unit = "mét / biển",
                    accentColor = EmeraldGreenText,
                    softColor = EmeraldGreenSoft,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Bento Stats Strip: Quãng đường • Thời gian • Tốc độ Max
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = BorderSubtle
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("QUÃNG ĐƯỜNG", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(3.dp))
                        RollingNumberText(
                            targetValue = state.totalDistanceKm.toFloat(),
                            decimals = 1,
                            suffix = " km",
                            style = TelemetryTextStyle.copy(fontSize = 16.sp),
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(BorderSubtle)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("THỜI GIAN CHẠY", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = timeFormatted,
                            style = TelemetryTextStyle.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
                            color = TextPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(BorderSubtle)
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TỐC ĐỘ MAX", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(3.dp))
                        RollingNumberText(
                            targetValue = state.maxSpeedKmh,
                            suffix = " km/h",
                            style = TelemetryTextStyle.copy(fontSize = 16.sp),
                            color = ElectricOrangeHover
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3b. Thanh Cảnh báo Tầm Xăng & Nút Khẩn cấp SOS trên Buồng lái
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isLowFuel = fuelAdvisory?.isCriticalLow == true
                ShadcnCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = if (isLowFuel) AmberWarningSoft else SurfaceWhite,
                    borderColor = if (isLowFuel) AmberWarningBorder else BorderSubtle,
                    onClick = onRefuelFull
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isLowFuel) "⚠ SẮP HẾT TẦM XĂNG" else "⛽ TẦM XĂNG CÒN LẠI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isLowFuel) AmberWarningText else TextMuted
                            )
                            Text(
                                text = "~${fuelState.remainingKm.toInt()} / ${fuelState.maxRangeKm} km (Chạm khi đổ đầy)",
                                style = TelemetryTextStyle.copy(fontSize = 12.5.sp),
                                color = TextPrimary
                            )
                        }
                        Icon(Icons.Default.LocalGasStation, contentDescription = null, tint = AmberWarningText, modifier = Modifier.size(18.dp))
                    }
                }

                ShadcnButton(
                    onClick = onOpenSos,
                    variant = ShadcnButtonVariant.Destructive,
                    modifier = Modifier.height(52.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Default.Sos, contentDescription = "SOS", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SOS", fontWeight = FontWeight.Black)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Sunset Countdown Banner
            ShadcnAlert(
                title = if (minutesUntilSunset > 0) "Hoàng hôn đèo (17:15): Còn ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p"
                else "ĐÃ TỐI TRÊN ĐÈO! NÊN VỀ ĐIỂM NGHỈ",
                description = "Đèo Hà Giang không có đèn chiếu sáng, dễ sương mù đặc và khúc cua tay áo khuất tầm nhìn.",
                variant = if (isNearDark) ShadcnBadgeVariant.Rose else ShadcnBadgeVariant.Amber,
                icon = if (isNearDark) Icons.Default.Warning else Icons.Default.WbTwilight
            )

            // 5. Auto Check-in Badge
            if (!state.lastVisitedPlaceName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    backgroundColor = EmeraldGreenSoft,
                    borderColor = EmeraldGreenBorder
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vừa tự động check-in: ${state.lastVisitedPlaceName}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldGreenText
                        )
                    }
                }
            }

            // 6. Next Milestone Radar Card
            if (state.nextMilestonePlace != null) {
                val nextPlace = state.nextMilestonePlace
                Spacer(modifier = Modifier.height(12.dp))
                ShadcnCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = ElectricOrangeBorder
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(ElectricOrangeSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.NearMe, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(14.dp))
                            }
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "MỐC KẾ TIẾP PHÍA TRƯỚC",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricOrangeHover
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = nextPlace.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${nextPlace.category.label} • Cách ~${String.format(Locale.US, "%.1f", state.distanceToNextKm)} km • ${nextPlace.altitudeMeters}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            ShadcnButton(
                                onClick = {
                                    LocationMath.openNavigationIntent(context, nextPlace.lat, nextPlace.lng, nextPlace.name)
                                },
                                variant = ShadcnButtonVariant.Primary,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Dẫn đường", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (nextPlace.practicalTip.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceMuted)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "💡 ${nextPlace.practicalTip}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 7. Tactile Ride Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.isPaused) {
                    ShadcnButton(
                        onClick = onResume,
                        variant = ShadcnButtonVariant.Primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tiếp tục chặng", fontWeight = FontWeight.Bold)
                    }
                } else {
                    ShadcnButton(
                        onClick = onPause,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, tint = AmberWarningText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tạm dừng", fontWeight = FontWeight.Bold, color = AmberWarningText)
                    }
                }

                ShadcnButton(
                    onClick = onStop,
                    variant = ShadcnButtonVariant.Destructive,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Kết thúc chặng", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
