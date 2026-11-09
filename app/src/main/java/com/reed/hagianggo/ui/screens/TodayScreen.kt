package com.reed.hagianggo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.TimePacerEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

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
    val currentLocation by viewModel.currentLocation.collectAsState()
    val isOutsideHaGiang by viewModel.isOutsideHaGiang.collectAsState()
    val distanceToHaGiangKm by viewModel.distanceToHaGiangKm.collectAsState()
    val liveRideState by viewModel.liveRideState.collectAsState()
    val fuelState by viewModel.fuelState.collectAsState()
    val fuelAdvisory by viewModel.fuelAdvisory.collectAsState()
    val weatherForecasts by viewModel.weatherForecasts.collectAsState()
    val dayStartMinutes by viewModel.dayStartMinutes.collectAsState()
    val useCurrentClockForPacer by viewModel.useCurrentClockForPacer.collectAsState()
    val skippedTodayPlaceIds by viewModel.skippedTodayPlaceIds.collectAsState()
    val activeStopTimer by viewModel.activeStopTimer.collectAsState()

    var selectedDay by remember { mutableIntStateOf(1) }
    var highlightedPlaceId by remember { mutableStateOf<String?>(null) }

    val now = remember { Calendar.getInstance() }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute
    val sunsetMinutes = config.sunsetCutoffHour * 60 + config.sunsetCutoffMinute
    val minutesUntilSunset = sunsetMinutes - currentTotalMinutes
    val isNearDark = currentTotalMinutes >= (16 * 60 + 30)
    val isNight = currentTotalMinutes >= sunsetMinutes

    val placesById = remember(places) { places.associateBy { it.id } }

    val daySlots = remember(itinerarySlots, selectedDay) {
        itinerarySlots.filter { it.dayNumber == selectedDay }
    }
    val orderedDayStops = remember(daySlots, placesById) {
        val seenIds = HashSet<String>()
        val result = ArrayList<TimePacerEngine.DayStopInput>()
        for (slot in listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING)) {
            for (item in daySlots.filter { it.slot == slot }) {
                for (pid in item.suggestedPlaceIds) {
                    val p = placesById[pid]
                    if (p != null && seenIds.add(p.id)) {
                        result.add(TimePacerEngine.DayStopInput(place = p, customNote = item.title, slot = slot))
                    }
                }
            }
        }
        result
    }
    val orderedDayPlaces = remember(orderedDayStops) {
        orderedDayStops.map { it.place }
    }

    val dayPacingSummary = remember(
        orderedDayStops,
        config.vehicle,
        dayStartMinutes,
        useCurrentClockForPacer,
        skippedTodayPlaceIds,
        sunsetMinutes
    ) {
        TimePacerEngine.computeDaySchedule(
            dayStops = orderedDayStops,
            vehicle = config.vehicle,
            startMinutesOfDay = dayStartMinutes,
            isLiveNowMode = useCurrentClockForPacer,
            skippedPlaceIds = skippedTodayPlaceIds,
            sunsetCutoffMinutes = sunsetMinutes
        )
    }
    val scheduledNodeById = remember(dayPacingSummary) {
        dayPacingSummary.nodes.associateBy { it.place.id }
    }

    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
        contentPadding = PaddingValues(
            top = topClearance,
            bottom = bottomClearance,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // =====================================================================
        // 1. HERO EDITORIAL CARD: "LÀM GÌ BÂY GIỜ?" KÈM ĐƯỜNG ĐỒNG MỨC TOPO
        // =====================================================================
        item(key = "hero_what_now") {
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                backgroundColor = SurfaceWhite,
                borderColor = ElectricOrangeBorder,
                onClick = { viewModel.openWhatNow() }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFFFFFBF7),
                                    ElectricOrangeSoft
                                )
                            )
                        )
                ) {
                    // Họa tiết đường đồng mức địa hình (Topographic Contours) vẽ bằng Vector Canvas
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                    ) {
                        val w = size.width
                        val h = size.height
                        listOf(0.55f, 0.72f, 0.88f).forEachIndexed { idx, rx ->
                            val path = Path().apply {
                                moveTo(w * rx, 0f)
                                cubicTo(
                                    w * (rx - 0.12f), h * 0.35f,
                                    w * (rx + 0.08f), h * 0.70f,
                                    w * (rx - 0.05f), h
                                )
                            }
                            drawPath(
                                path = path,
                                color = ElectricOrange.copy(alpha = 0.08f + idx * 0.03f),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(ElectricOrange, ElectricOrangeHover)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LÀM GÌ BÂY GIỜ?",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ShadcnBadge(text = "AI ĐÈO NÚI", variant = ShadcnBadgeVariant.Orange)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Gợi ý điểm ăn, trú mưa, xăng & ngắm cảnh tối ưu ngay tại ${activeZone?.name ?: "Hà Giang"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SurfaceWhite)
                                .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = ElectricOrangeHover,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 2. BENTO COCKPIT ROW: LIVE RIDE TELEMETRY × ĐẾM NGƯỢC HOÀNG HÔN ĐÈO
        // =====================================================================
        item(key = "bento_cockpit_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bento Trái: Live Ride Mode
                ShadcnCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = if (liveRideState.isTracking) EmeraldGreenBorder else BorderSubtle,
                    onClick = {
                        if (!liveRideState.isTracking) viewModel.startRide()
                        viewModel.openCockpit()
                    }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(EmeraldGreenSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.TwoWheeler,
                                    contentDescription = null,
                                    tint = EmeraldGreenText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            ShadcnBadge(
                                text = if (liveRideState.isTracking) "LIVE" else "HUD",
                                variant = ShadcnBadgeVariant.Emerald
                            )
                        }

                        if (liveRideState.isTracking) {
                            RollingNumberText(
                                targetValue = liveRideState.currentSpeedKmh,
                                suffix = " km/h",
                                style = TelemetryTextStyle.copy(fontSize = 20.sp),
                                color = EmeraldGreenText
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", liveRideState.totalDistanceKm)} km • ${liveRideState.currentAltitude.toInt()}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        } else {
                            Text(
                                text = "Chế độ Phượt",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Đo tốc độ, cao độ & tự check-in",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bento Phải: Đồng hồ an toàn Hoàng hôn đèo (Flighty Status Widget)
                val (sunBg, sunBorder, sunTint) = when {
                    isNight -> Triple(RoseDestructiveSoft, RoseDestructiveBorder, RoseDestructiveText)
                    isNearDark -> Triple(AmberWarningSoft, AmberWarningBorder, AmberWarningText)
                    else -> Triple(SkyTelemetrySoft, SkyTelemetryBorder, SkyTelemetry)
                }

                ShadcnCard(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = sunBorder
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(sunBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isNight) Icons.Default.NightsStay else Icons.Default.WbTwilight,
                                    contentDescription = null,
                                    tint = sunTint,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            ShadcnBadge(
                                text = "17:15 TỐI",
                                variant = if (isNight) ShadcnBadgeVariant.Rose else if (isNearDark) ShadcnBadgeVariant.Amber else ShadcnBadgeVariant.Sky
                            )
                        }

                        Text(
                            text = when {
                                isNight -> "Đèo đã tối!"
                                minutesUntilSunset > 0 -> "Còn ${minutesUntilSunset / 60}h ${minutesUntilSunset % 60}p"
                                else -> "Sắp tối đèo"
                            },
                            style = TelemetryTextStyle.copy(fontSize = 18.sp),
                            color = if (isNight) RoseDestructiveText else TextPrimary
                        )
                        Text(
                            text = when {
                                isNight -> "Nghỉ đêm, tránh đổ đèo mù"
                                isNearDark -> "Chốt homestay & đổ xăng"
                                isOutsideHaGiang -> "Cách Hà Giang ~${distanceToHaGiangKm?.toInt() ?: 0}km"
                                else -> "Khung giờ sáng an toàn"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2B. TRẠM AN TOÀN ĐÈO NÚI: THỜI TIẾT SĂN MÂY × CỨU HỘ SOS × TẦM XĂNG
        // =====================================================================
        item(key = "weather_and_sos_row") {
            val activeWeather = activeZone?.id?.let { weatherForecasts[it] } ?: weatherForecasts.values.firstOrNull()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Bento Thời tiết Đèo & Săn mây
                ShadcnCard(
                    modifier = Modifier.weight(1.15f),
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = if (activeWeather?.isFogHazard == true) RoseDestructiveBorder else SkyTelemetryBorder,
                    onClick = { viewModel.openWeatherSheet() }
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SkyTelemetrySoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = SkyTelemetry,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            ShadcnBadge(
                                text = "☁️ MÂY ${activeWeather?.cloudHuntScore ?: 68}%",
                                variant = if ((activeWeather?.cloudHuntScore ?: 68) >= 70) ShadcnBadgeVariant.Emerald else ShadcnBadgeVariant.Sky
                            )
                        }

                        Text(
                            text = "${activeWeather?.currentTempC ?: 18}°C • ${activeZone?.name?.substringBefore("(")?.trim() ?: "Hà Giang"}",
                            style = TelemetryTextStyle.copy(fontSize = 15.5.sp),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeWeather?.summaryLabel ?: "Xem dự báo 8 cụm đèo & săn mây",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeWeather?.isFogHazard == true) RoseDestructiveText else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bento SOS Cứu hộ 2G & Poster Hành trình
                Column(
                    modifier = Modifier.weight(0.85f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        backgroundColor = RoseDestructiveSoft,
                        borderColor = RoseDestructiveBorder,
                        onClick = { viewModel.openSosSheet() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "🆘 SOS ĐÈO NÚI",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = RoseDestructiveText
                                )
                                Text(
                                    text = "SMS 2G & Sửa xe",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RoseDestructiveText, modifier = Modifier.size(18.dp))
                        }
                    }

                    ShadcnCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = ElectricOrangeBorder,
                        onClick = { viewModel.openRecapSheet() }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "🖼️ POSTER RECAP",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = ElectricOrangeHover
                                )
                                Text(
                                    text = "Tổng kết hành trình",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            Icon(Icons.Default.AutoAwesomeMotion, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // Thẻ Cảnh báo Tầm hoạt động Xăng & Điểm mù Cây xăng trên đèo
        item(key = "fuel_range_guard_card") {
            val animFuelPct by animateFloatAsState(
                targetValue = fuelAdvisory.fuelPercent,
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 320f),
                label = "FuelBarAnim"
            )
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = if (fuelAdvisory.isCriticalLow) AmberWarningBorder else BorderSubtle
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AmberWarningSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.LocalGasStation,
                                    contentDescription = null,
                                    tint = AmberWarningText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Column {
                                Text(
                                    text = "TẦM XĂNG & CÂY XĂNG ĐÈO",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (fuelAdvisory.isCriticalLow) AmberWarningText else TextMuted
                                )
                                Text(
                                    text = "Còn đi được ~${fuelState.remainingKm.toInt()} / ${fuelState.maxRangeKm} km",
                                    style = TelemetryTextStyle.copy(fontSize = 14.sp),
                                    color = TextPrimary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EmeraldGreenSoft)
                                .border(BorderStroke(1.dp, EmeraldGreenBorder), CircleShape)
                                .springClickable { viewModel.markTankRefueled() }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⛽ Vừa đổ đầy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldGreenText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Thanh mức xăng lò xo
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(SurfaceMuted)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animFuelPct.coerceIn(0.04f, 1f))
                                .clip(CircleShape)
                                .background(
                                    when {
                                        animFuelPct <= 0.28f -> RoseDestructive
                                        animFuelPct <= 0.50f -> AmberWarning
                                        else -> EmeraldGreen
                                    }
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hàng chọn nhanh vạch xăng trên đồng hồ xe + chỉ đường cây xăng gần nhất
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Vạch xe:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            listOf(0.25f to "1/4", 0.5f to "1/2", 0.75f to "3/4").forEach { (frac, label) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(SurfaceMuted)
                                        .springClickable { viewModel.setFuelFraction(frac) }
                                        .padding(horizontal = 7.dp, vertical = 3.5.dp)
                                ) {
                                    Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                }
                            }
                        }

                        fuelAdvisory.nearestStation?.let { station ->
                            Text(
                                text = "📍 Gần nhất ~${String.format(Locale.US, "%.1f", fuelAdvisory.distanceToNearestKm)} km",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = ElectricOrangeHover,
                                modifier = Modifier.clickable {
                                    LocationMath.openNavigationIntent(context, station.lat, station.lng, station.name)
                                }
                            )
                        }
                    }

                    if (fuelAdvisory.gapWarningTitle != null && (fuelAdvisory.isCriticalLow || fuelAdvisory.nextGapAheadKm >= 40.0)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (fuelAdvisory.isCriticalLow) RoseDestructiveSoft else AmberWarningSoft)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "⚠ ${fuelAdvisory.gapWarningTitle}: ${fuelAdvisory.gapWarningDetail}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (fuelAdvisory.isCriticalLow) RoseDestructiveText else AmberWarningText
                            )
                        }
                    }
                }
            }
        }

        // =====================================================================
        // 3. KHAY CHỌN NGÀY TRƯỢT LÒ XO (SLIDING PILL DAY TABS)
        // =====================================================================
        item(key = "day_selector") {
            val daysList = (1..config.durationDays).toList()
            ShadcnSegmentedTabs(
                items = daysList,
                selectedItem = selectedDay.coerceIn(1, config.durationDays),
                onItemSelected = {
                    selectedDay = it
                    highlightedPlaceId = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { day, isSelected ->
                Text(
                    text = "Ngày $day",
                    color = if (isSelected) TextPrimary else TextMuted,
                    fontSize = 12.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // =====================================================================
        // 4. BIỂU ĐỒ MẶT CẮT ĐỘ CAO ĐÈO ĐỘNG (ALLTRAILS ELEVATION PROFILE)
        // =====================================================================
        item(key = "elevation_profile_card") {
            val dayRouteHint = when (selectedDay) {
                1 -> "TP. Hà Giang (Km0) ➔ Bắc Sum ➔ Cổng Trời ➔ Yên Minh ➔ Đồng Văn (~145km)"
                2 -> "Đồng Văn ➔ Cột Cờ Lũng Cú ➔ Lô Lô Chải ➔ Mã Pì Lèng ➔ Sông Nho Quế ➔ Pả Vi (~80km)"
                3 -> "Mèo Vạc ➔ Cua Chữ M ➔ Mậu Duệ ➔ Thác Du Già ➔ Đường Thượng (~150km)"
                else -> "Du Già ➔ Đường Thượng ➔ Thái An ➔ Về lại Cột mốc Km0 TP. Hà Giang"
            }

            ElevationProfileCard(
                dayNumber = selectedDay,
                dayPlaces = orderedDayPlaces,
                routeSubtitle = dayRouteHint,
                onPlaceSelected = { place ->
                    highlightedPlaceId = place.id
                }
            )
        }

        // =====================================================================
        // 4B. BỘ ĐIỀU ĐỘ THỜI GIAN & CHỐNG LỐ GIỜ ĐÈO (SMART TIME PACER)
        // =====================================================================
        item(key = "day_time_pacer_card") {
            DayTimePacerCard(
                dayNumber = selectedDay,
                summary = dayPacingSummary,
                onSelectStartMinutes = { min -> viewModel.setDayStartTime(min) },
                onEnableLiveNow = {
                    val cal = Calendar.getInstance()
                    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    viewModel.enableLiveNowPacer(nowMin)
                },
                onAutoTrim = {
                    viewModel.applySmartAutoTrim(
                        dayStops = orderedDayStops,
                        startMinutes = dayStartMinutes,
                        isLiveNowMode = useCurrentClockForPacer
                    )
                }
            )
        }

        // Thanh đồng hồ đếm ngược khi đang dừng chân tại điểm (Pit-Stop Timer)
        activeStopTimer?.let { timer ->
            item(key = "active_pit_stop_banner") {
                ActivePitStopBanner(
                    timer = timer,
                    onAdd10Min = { viewModel.addPitStopMinutes(10) },
                    onComplete = { viewModel.completePitStop(timer.placeId) },
                    onCancel = { viewModel.cancelPitStopTimer() }
                )
            }
        }

        // =====================================================================
        // 5. DÒNG THỜI GIAN HÀNH TRÌNH LIỀN MẠCH (POLARSTEPS TIMELINE + PACER)
        // =====================================================================
        listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING).forEach { slot ->
            val slotsInThisTime = daySlots.filter { it.slot == slot }
            val placesForSlot = slotsInThisTime.flatMap { item ->
                item.suggestedPlaceIds.mapNotNull { pid -> placesById[pid]?.let { p -> item to p } }
            }

            item(key = "header_${selectedDay}_${slot.name}") {
                TimeSlotHeader(slot = slot, count = placesForSlot.size)
            }

            if (placesForSlot.isEmpty()) {
                item(key = "empty_${selectedDay}_${slot.name}") {
                    Text(
                        text = "Linh hoạt nghỉ ngơi trong ${slot.label.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 28.dp, bottom = 4.dp)
                    )
                }
            } else {
                items(
                    items = placesForSlot,
                    key = { (slotItem, place) -> "day${selectedDay}_${slotItem.id}_${place.id}" }
                ) { (slotItem, place) ->
                    val scheduledNode = scheduledNodeById[place.id]
                    val isTimerRunningHere = activeStopTimer?.placeId == place.id
                    ItineraryPlaceCard(
                        place = place,
                        customNote = slotItem.title,
                        scheduledNode = scheduledNode,
                        vehicle = config.vehicle,
                        isTimerRunningHere = isTimerRunningHere,
                        isHighlighted = highlightedPlaceId == place.id,
                        onNavigate = {
                            LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                        },
                        onToggleVisited = { viewModel.toggleVisited(place.id) },
                        onAdjustStayMin = { delta -> viewModel.adjustPlaceStayDuration(place.id, delta) },
                        onStartPitStopTimer = { viewModel.startPitStopTimer(place) },
                        onToggleSkipToday = { viewModel.toggleSkipPlaceToday(place.id) },
                        onCall = {
                            if (place.phoneNumber.isNotBlank()) {
                                LocationMath.openDialerIntent(context, place.phoneNumber)
                            }
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun DayTimePacerCard(
    dayNumber: Int,
    summary: DayPacingSummary,
    onSelectStartMinutes: (Int) -> Unit,
    onEnableLiveNow: () -> Unit,
    onAutoTrim: () -> Unit
) {
    val isOvertime = summary.overtimeMinutes > 0
    val finishStr = TimePacerEngine.formatMinutesOfDay(summary.finishMinutesOfDay)
    val cutoffStr = TimePacerEngine.formatMinutesOfDay(summary.sunsetCutoffMinutes)
    val bufferMin = (summary.sunsetCutoffMinutes - summary.finishMinutesOfDay).coerceAtLeast(0)

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = if (isOvertime) RoseDestructiveBorder else EmeraldGreenBorder
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Tiêu đề + Trạng thái tổng quát
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isOvertime) RoseDestructiveSoft else EmeraldGreenSoft),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isOvertime) Icons.Default.WarningAmber else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isOvertime) RoseDestructiveText else EmeraldGreenText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(9.dp))
                    Column {
                        Text(
                            text = "ĐIỀU ĐỘ THỜI GIAN NGÀY $dayNumber",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = if (isOvertime) RoseDestructiveText else EmeraldGreenText
                        )
                        Text(
                            text = "Tính giờ chạy đèo & giờ dừng từng điểm",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                ShadcnBadge(
                    text = if (isOvertime) "LỐ +${TimePacerEngine.formatDurationHoursMin(summary.overtimeMinutes)}"
                    else "AN TOÀN ✓",
                    variant = if (isOvertime) ShadcnBadgeVariant.Rose else ShadcnBadgeVariant.Emerald
                )
            }

            // Hàng chọn Giờ Xuất Phát hoặc Tính từ bây giờ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Xuất phát:",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextMuted
                )
                listOf(
                    420 to "07:00",
                    450 to "07:30",
                    480 to "08:00",
                    510 to "08:30",
                    540 to "09:00"
                ).forEach { (minVal, label) ->
                    val isSelected = !summary.isLiveNowMode && summary.startMinutesOfDay == minVal
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) ElectricOrange else SurfaceMuted)
                            .border(
                                BorderStroke(1.dp, if (isSelected) ElectricOrangeHover else BorderSubtle),
                                RoundedCornerShape(8.dp)
                            )
                            .springClickable { onSelectStartMinutes(minVal) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = label,
                            style = TelemetryTextStyle.copy(fontSize = 11.sp),
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }

                // Nút Tính từ đồng hồ hiện tại
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (summary.isLiveNowMode) SkyTelemetry else SkyTelemetrySoft)
                        .border(BorderStroke(1.dp, SkyTelemetryBorder), RoundedCornerShape(8.dp))
                        .springClickable(onClick = onEnableLiveNow)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (summary.isLiveNowMode) {
                            "⚡ Đang tính từ ${TimePacerEngine.formatMinutesOfDay(summary.startMinutesOfDay)}"
                        } else {
                            "⚡ Tính từ bây giờ"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                        color = if (summary.isLiveNowMode) Color.White else SkyTelemetry
                    )
                }
            }

            // 3 Ô Bento Thông số Thời gian Tổng hợp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceMuted)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("TỔNG CHẠY XE", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextMuted)
                        Text(
                            text = TimePacerEngine.formatDurationHoursMin(summary.totalRideMinutes),
                            style = TelemetryTextStyle.copy(fontSize = 14.sp),
                            color = TextPrimary
                        )
                        Text(
                            text = "~${String.format(Locale.US, "%.0f", summary.totalRoadKm)} km đèo",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceMuted)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text("TỔNG DỪNG CHƠI", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = TextMuted)
                        Text(
                            text = TimePacerEngine.formatDurationHoursMin(summary.totalStayMinutes),
                            style = TelemetryTextStyle.copy(fontSize = 14.sp),
                            color = TextPrimary
                        )
                        val activeCount = summary.nodes.count { it.status != StopPacingStatus.SKIPPED && it.status != StopPacingStatus.COMPLETED }
                        Text(
                            text = "$activeCount điểm dừng",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isOvertime) RoseDestructiveSoft else EmeraldGreenSoft)
                        .border(
                            BorderStroke(1.dp, if (isOvertime) RoseDestructiveBorder else EmeraldGreenBorder),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "VỀ TỚI NGHỈ ĐÊM",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            color = if (isOvertime) RoseDestructiveText else EmeraldGreenText
                        )
                        Text(
                            text = finishStr,
                            style = TelemetryTextStyle.copy(fontSize = 15.sp),
                            color = if (isOvertime) RoseDestructiveText else EmeraldGreenText
                        )
                        Text(
                            text = "Mốc tối: $cutoffStr",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (isOvertime) RoseDestructiveText else EmeraldGreenText
                        )
                    }
                }
            }

            // Hộp Cảnh báo Lố giờ hoặc Xác nhận An toàn + Nút Tự động Rút ngắn
            if (isOvertime) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoseDestructiveSoft.copy(alpha = 0.7f))
                        .border(BorderStroke(1.dp, RoseDestructiveBorder), RoundedCornerShape(12.dp))
                        .padding(11.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            text = "⚠️ Lịch trình đang bị lố ${TimePacerEngine.formatDurationHoursMin(summary.overtimeMinutes)} sau mốc sương mù đèo ($cutoffStr)!",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = RoseDestructiveText
                        )
                        if (!summary.autoTrimHint.isNullOrBlank()) {
                            Text(
                                text = "💡 Gợi ý: ${summary.autoTrimHint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        if (summary.canAutoTrim) {
                            ShadcnButton(
                                onClick = onAutoTrim,
                                variant = ShadcnButtonVariant.Primary,
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Tự động co giờ dừng để kịp về trước $cutoffStr",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(11.dp))
                        .background(EmeraldGreenSoft.copy(alpha = 0.65f))
                        .padding(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "✓ Tiến độ rất đẹp! Bạn sẽ hoàn thành điểm cuối lúc $finishStr (dư $bufferMin phút trước khi sương mù xuống).",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = EmeraldGreenText
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivePitStopBanner(
    timer: ActiveStopTimer,
    onAdd10Min: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timer.startedAtMillis, timer.targetDurationMin) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val elapsedSec = ((nowMillis - timer.startedAtMillis) / 1000L).coerceAtLeast(0L)
    val targetSec = timer.targetDurationMin * 60L
    val remainingSec = targetSec - elapsedSec
    val isOver = remainingSec < 0
    val absSec = kotlin.math.abs(remainingSec)
    val mm = absSec / 60L
    val ss = absSec % 60L
    val countdownText = String.format(Locale.US, "%s%02d:%02d", if (isOver) "+" else "", mm, ss)

    val leaveCalendar = remember(timer.startedAtMillis, timer.targetDurationMin) {
        Calendar.getInstance().apply {
            timeInMillis = timer.startedAtMillis + timer.targetDurationMin * 60_000L
        }
    }
    val leaveTimeStr = remember(leaveCalendar) {
        String.format(
            Locale.US,
            "%02d:%02d",
            leaveCalendar.get(Calendar.HOUR_OF_DAY),
            leaveCalendar.get(Calendar.MINUTE)
        )
    }

    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = if (isOver) RoseDestructiveSoft else ElectricOrangeSoft,
        borderColor = if (isOver) RoseDestructive else ElectricOrange
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isOver) "⚠️ ĐÃ QUÁ GIỜ DỪNG TẠI ĐIỂM!" else "⏱️ ĐANG DỪNG CHƠI / NGHỈ TẠI ĐIỂM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = if (isOver) RoseDestructiveText else ElectricOrangeHover
                    )
                    Text(
                        text = timer.placeName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Nên lên xe đi tiếp lúc: $leaveTimeStr (Mục tiêu ${timer.targetDurationMin}p)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceWhite)
                        .border(
                            BorderStroke(1.dp, if (isOver) RoseDestructiveBorder else ElectricOrangeBorder),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOver) "LỐ GIỜ" else "CÒN LẠI",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isOver) RoseDestructiveText else TextMuted
                        )
                        Text(
                            text = countdownText,
                            style = TelemetryTextStyle.copy(fontSize = 18.sp),
                            color = if (isOver) RoseDestructiveText else ElectricOrangeHover
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onAdd10Min,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(0.9f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("+10 phút ở thêm", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                ShadcnButton(
                    onClick = onComplete,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1.3f).height(34.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xong • Lên xe đi tiếp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Tắt", tint = TextMuted, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
fun TimeSlotHeader(slot: TimeSlot, count: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (icon, color, softBg) = when (slot) {
                TimeSlot.MORNING -> Triple(Icons.Default.WbTwilight, ElectricOrange, ElectricOrangeSoft)
                TimeSlot.NOON -> Triple(Icons.Default.WbSunny, AmberWarningText, AmberWarningSoft)
                TimeSlot.AFTERNOON -> Triple(Icons.Default.Terrain, SkyTelemetry, SkyTelemetrySoft)
                TimeSlot.EVENING -> Triple(Icons.Default.NightsStay, TextSecondary, SurfaceMuted)
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(softBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${slot.label.uppercase()} • ${slot.timeRange}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        }

        if (count > 0) {
            Text(
                text = "$count chặng dừng",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }
}

@Composable
fun ItineraryPlaceCard(
    place: Place,
    customNote: String,
    scheduledNode: ScheduledStopNode? = null,
    vehicle: VehicleType = VehicleType.MOTORBIKE,
    isTimerRunningHere: Boolean = false,
    isHighlighted: Boolean = false,
    onNavigate: () -> Unit,
    onToggleVisited: () -> Unit,
    onAdjustStayMin: ((Int) -> Unit)? = null,
    onStartPitStopTimer: (() -> Unit)? = null,
    onToggleSkipToday: (() -> Unit)? = null,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSkipped = scheduledNode?.status == StopPacingStatus.SKIPPED
    val isOvertime = scheduledNode?.status == StopPacingStatus.OVERTIME_LATE
    val isNearDusk = scheduledNode?.status == StopPacingStatus.NEAR_DUSK

    val checkScale by animateFloatAsState(
        targetValue = if (place.isVisited) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "TimelineCheckBounce"
    )
    val railColor by animateColorAsState(
        targetValue = when {
            place.isVisited -> EmeraldGreen
            isOvertime -> RoseDestructive
            else -> BorderMedium
        },
        animationSpec = tween(220),
        label = "TimelineRailColor"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Thanh nối thời gian chạy xe đèo từ điểm trước tới điểm này
        if (scheduledNode != null && scheduledNode.legTravelMin > 0 && !isSkipped) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.width(26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(26.dp)
                            .background(railColor.copy(alpha = 0.5f))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SkyTelemetrySoft)
                        .border(BorderStroke(1.dp, SkyTelemetryBorder), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.5.dp)
                ) {
                    val vehIcon = if (vehicle == VehicleType.MOTORBIKE) "🛵" else "🚗"
                    Text(
                        text = "$vehIcon Chạy đèo ~${String.format(Locale.US, "%.1f", scheduledNode.legRoadKm)} km • Mất ~${scheduledNode.legTravelMin} phút",
                        style = TelemetryTextStyle.copy(fontSize = 10.5.sp),
                        color = SkyTelemetry
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Cột nối Timeline kiểu Polarsteps bên trái
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(26.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(14.dp)
                        .background(railColor.copy(alpha = 0.5f))
                )
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                        .clip(CircleShape)
                        .background(
                            when {
                                place.isVisited -> EmeraldGreen
                                isSkipped -> SurfaceMuted
                                else -> SurfaceWhite
                            }
                        )
                        .border(
                            BorderStroke(
                                2.dp,
                                when {
                                    place.isVisited -> EmeraldGreen
                                    isSkipped -> BorderMedium
                                    isOvertime -> RoseDestructive
                                    else -> ElectricOrange
                                }
                            ),
                            CircleShape
                        )
                        .springClickable(onClick = onToggleVisited),
                    contentAlignment = Alignment.Center
                ) {
                    if (place.isVisited) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Đã ghé",
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(railColor.copy(alpha = 0.5f))
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Thẻ chi tiết địa điểm
            ShadcnCard(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = if (place.isVisited || isSkipped) SurfaceMuted.copy(alpha = 0.6f) else SurfaceWhite,
                borderColor = when {
                    isTimerRunningHere -> ElectricOrange
                    isHighlighted -> ElectricOrange
                    isOvertime -> RoseDestructiveBorder
                    place.isVisited -> EmeraldGreenBorder
                    else -> BorderSubtle
                },
                borderWidth = if (isHighlighted || isTimerRunningHere) 1.5.dp else 1.dp
            ) {
                Column(modifier = Modifier.padding(13.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = place.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    textDecoration = if (place.isVisited || isSkipped) {
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    } else null
                                ),
                                color = if (place.isVisited || isSkipped) TextMuted else TextPrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ShadcnBadge(
                                    text = place.category.label,
                                    variant = ShadcnBadgeVariant.Orange
                                )
                                Text(
                                    text = "▲ ${place.altitudeMeters}m",
                                    style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                    color = TextMuted
                                )
                            }
                        }

                        if (place.phoneNumber.isNotBlank()) {
                            IconButton(onClick = onCall, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Phone, contentDescription = "Gọi", tint = EmeraldGreenText, modifier = Modifier.size(17.dp))
                            }
                        }
                    }

                    // Dải Giờ Đến -> Thời lượng ở lại -> Giờ chốt phải rời đi
                    if (scheduledNode != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (isSkipped) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceWhite)
                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏭️ Đã tạm bỏ qua để kịp giờ về",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextMuted
                                )
                                if (onToggleSkipToday != null) {
                                    Text(
                                        text = "↩️ Đi lại điểm này",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElectricOrangeHover,
                                        modifier = Modifier.clickable(onClick = onToggleSkipToday)
                                    )
                                }
                            }
                        } else {
                            val arrivalStr = TimePacerEngine.formatMinutesOfDay(scheduledNode.arrivalMinutesOfDay)
                            val departStr = TimePacerEngine.formatMinutesOfDay(scheduledNode.departureMinutesOfDay)
                            val boxBg = when {
                                isOvertime -> RoseDestructiveSoft
                                isNearDusk -> AmberWarningSoft
                                else -> SurfaceMuted
                            }
                            val boxBorder = when {
                                isOvertime -> RoseDestructiveBorder
                                isNearDusk -> AmberWarningBorder
                                else -> BorderSubtle
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(boxBg)
                                    .border(BorderStroke(1.dp, boxBorder), RoundedCornerShape(11.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🕒 Đến $arrivalStr • Ở ${scheduledNode.stayDurationMin}p ➔ Đi lúc $departStr",
                                        style = TelemetryTextStyle.copy(fontSize = 11.5.sp),
                                        color = if (isOvertime) RoseDestructiveText else TextPrimary
                                    )
                                    if (isOvertime) {
                                        Text(
                                            text = "⚠ Sau 17:15",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                            color = RoseDestructiveText
                                        )
                                    }
                                }

                                // Cụm nút chỉnh nhanh thời gian dừng [-10p] [+10p] + Bấm giờ dừng + Bỏ qua
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (onAdjustStayMin != null) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SurfaceWhite)
                                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                                                    .springClickable { onAdjustStayMin(-10) }
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Text("-10p", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(SurfaceWhite)
                                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(6.dp))
                                                    .springClickable { onAdjustStayMin(10) }
                                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                                            ) {
                                                Text("+10p", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                                            }
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (onStartPitStopTimer != null && !place.isVisited) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(if (isTimerRunningHere) ElectricOrange else ElectricOrangeSoft)
                                                    .border(BorderStroke(1.dp, ElectricOrangeBorder), RoundedCornerShape(7.dp))
                                                    .springClickable(onClick = onStartPitStopTimer)
                                                    .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                            ) {
                                                Text(
                                                    text = if (isTimerRunningHere) "⏱️ Đang đếm..." else "⏱️ Bấm giờ dừng",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isTimerRunningHere) Color.White else ElectricOrangeHover
                                                )
                                            }
                                        }

                                        if (onToggleSkipToday != null && !place.isVisited) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(SurfaceWhite)
                                                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(7.dp))
                                                    .springClickable(onClick = onToggleSkipToday)
                                                    .padding(horizontal = 7.dp, vertical = 3.5.dp)
                                            ) {
                                                Text(
                                                    text = "Bỏ qua",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
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

                    if (customNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🧭 $customNote",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricOrangeHover
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    ShadcnButton(
                        onClick = onNavigate,
                        variant = ShadcnButtonVariant.Primary,
                        modifier = Modifier.fillMaxWidth().height(37.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Dẫn đường Google Maps", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
