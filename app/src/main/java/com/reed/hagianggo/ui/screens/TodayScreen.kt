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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
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

    var selectedDay by remember { mutableIntStateOf(1) }
    var highlightedPlaceId by remember { mutableStateOf<String?>(null) }

    val now = remember { Calendar.getInstance() }
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(Calendar.MINUTE)
    val currentTotalMinutes = currentHour * 60 + currentMinute
    val sunsetMinutes = 17 * 60 + 15
    val minutesUntilSunset = sunsetMinutes - currentTotalMinutes
    val isNearDark = currentTotalMinutes >= (16 * 60 + 30)
    val isNight = currentTotalMinutes >= sunsetMinutes

    val placesById = remember(places) { places.associateBy { it.id } }

    val daySlots = remember(itinerarySlots, selectedDay) {
        itinerarySlots.filter { it.dayNumber == selectedDay }
    }
    val orderedDayPlaces = remember(daySlots, placesById) {
        listOf(TimeSlot.MORNING, TimeSlot.NOON, TimeSlot.AFTERNOON, TimeSlot.EVENING)
            .flatMap { slot ->
                daySlots.filter { it.slot == slot }
                    .flatMap { item -> item.suggestedPlaceIds.mapNotNull { placesById[it] } }
            }
            .distinctBy { it.id }
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
        // 5. DÒNG THỜI GIAN HÀNH TRÌNH LIỀN MẠCH (POLARSTEPS TIMELINE)
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
                    ItineraryPlaceCard(
                        place = place,
                        customNote = slotItem.title,
                        isHighlighted = highlightedPlaceId == place.id,
                        onNavigate = {
                            LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                        },
                        onToggleVisited = { viewModel.toggleVisited(place.id) },
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
    isHighlighted: Boolean = false,
    onNavigate: () -> Unit,
    onToggleVisited: () -> Unit,
    onCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val checkScale by animateFloatAsState(
        targetValue = if (place.isVisited) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "TimelineCheckBounce"
    )
    val railColor by animateColorAsState(
        targetValue = if (place.isVisited) EmeraldGreen else BorderMedium,
        animationSpec = tween(220),
        label = "TimelineRailColor"
    )

    Row(
        modifier = modifier
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
                    .background(if (place.isVisited) EmeraldGreen else SurfaceWhite)
                    .border(
                        BorderStroke(2.dp, if (place.isVisited) EmeraldGreen else ElectricOrange),
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
            backgroundColor = if (place.isVisited) SurfaceMuted.copy(alpha = 0.6f) else SurfaceWhite,
            borderColor = if (isHighlighted) ElectricOrange else if (place.isVisited) EmeraldGreenBorder else BorderSubtle,
            borderWidth = if (isHighlighted) 1.5.dp else 1.dp
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
                                textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            ),
                            color = if (place.isVisited) TextMuted else TextPrimary
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
                                text = "▲ ${place.altitudeMeters}m • ~${place.suggestedDurationMin}p",
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
