package com.reed.hagianggo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.altitudeMeters
import com.reed.hagianggo.ui.theme.*
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// 1. CRITICALLY-DAMPED SPRING PRESS MODIFIER (Airbnb / iOS Tactile Feel)
// ============================================================================
fun Modifier.springClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    enableHaptic: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 650f
        ),
        label = "SpringPressScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = {
                if (enableHaptic) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}

// ============================================================================
// 2. ROLLING ODOMETER NUMBER (Flighty Telemetry Style)
// ============================================================================
@Composable
fun RollingNumberText(
    targetValue: Float,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String = "",
    prefix: String = "",
    useThousandSeparator: Boolean = false,
    style: TextStyle = TelemetryTextStyle.copy(fontSize = 20.sp),
    color: Color = TextPrimary
) {
    val animatedValue by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 300f
        ),
        label = "RollingOdometer"
    )

    val vnFormatter = remember { NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")) }
    val formatted = remember(animatedValue, decimals, useThousandSeparator) {
        if (useThousandSeparator) {
            vnFormatter.format(animatedValue.toLong())
        } else if (decimals <= 0) {
            animatedValue.roundToInt().toString()
        } else {
            String.format(Locale.US, "%.${decimals}f", animatedValue)
        }
    }

    Text(
        text = "$prefix$formatted$suffix",
        modifier = modifier,
        style = style.copy(fontFeatureSettings = "tnum"),
        color = color,
        maxLines = 1
    )
}

// ============================================================================
// 3. FLOATING PILL NAVIGATION BAR (AllTrails × Airbnb × Flighty)
// ============================================================================
data class NavPillItem(
    val index: Int,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

@Composable
fun FloatingPillNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val items = remember {
        listOf(
            NavPillItem(0, "Chuyến đi", Icons.Filled.Map, Icons.Outlined.Map),
            NavPillItem(1, "Hôm nay", Icons.Filled.Explore, Icons.Outlined.Explore),
            NavPillItem(2, "Gần đây", Icons.Filled.NearMe, Icons.Outlined.NearMe),
            NavPillItem(3, "Sổ tay", Icons.Filled.AutoStories, Icons.Outlined.AutoStories)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = SoftShadowColor,
                    spotColor = SoftShadowColor
                ),
            shape = CircleShape,
            color = SurfaceGlass,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                val tabWidth = maxWidth / items.size
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * selectedTab,
                    animationSpec = spring(
                        dampingRatio = 0.80f,
                        stiffness = 440f
                    ),
                    label = "NavPillSlide"
                )

                // Viên nang trượt vật lý lò xo phía dưới tab đang chọn
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    ElectricOrangeSoft,
                                    Color(0xFFFFEDD5)
                                )
                            )
                        )
                        .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEach { item ->
                        val isSelected = selectedTab == item.index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.12f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
                            label = "NavIconScale"
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) ElectricOrangeHover else TextMuted,
                            animationSpec = tween(180),
                            label = "NavContentColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (selectedTab != item.index) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onTabSelected(item.index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.activeIcon else item.inactiveIcon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. FLIGHTY-STYLE FLOATING HEADER & LIVE STATUS PILL
// ============================================================================
@Composable
fun FlightyFloatingHeader(
    activeZoneName: String,
    activeAltitudeMeters: Int,
    isGpsLive: Boolean,
    isOutsideHaGiang: Boolean,
    isTrackingRide: Boolean,
    currentSpeedKmh: Float,
    onStatusClick: () -> Unit,
    onWhatNowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LivePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Viên nang Trái: Thương hiệu + Live GPS / Chặng hiện tại
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .shadow(10.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                .springClickable(onClick = onStatusClick),
            shape = CircleShape,
            color = SurfaceGlass,
            border = BorderStroke(1.dp, BorderSubtle)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Đèn hiệu GPS / Live Ride
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(16.dp)) {
                    val dotColor = when {
                        isTrackingRide -> EmeraldGreen
                        isOutsideHaGiang -> AmberWarning
                        isGpsLive -> EmeraldGreen
                        else -> TextSubtle
                    }
                    if (isTrackingRide || isGpsLive) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
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
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "HÀ GIANG GO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                fontSize = 9.5.sp
                            ),
                            color = TextMuted
                        )
                        if (isTrackingRide) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${currentSpeedKmh.roundToInt()} km/h",
                                style = TelemetryTextStyle.copy(fontSize = 10.sp),
                                color = EmeraldGreenText
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activeZoneName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = " • ${activeAltitudeMeters}m",
                            style = TelemetryTextStyle.copy(fontSize = 11.sp),
                            color = ElectricOrangeHover
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Viên nang Phải: AI Gợi ý "Làm gì?" nổi bật kiểu Airbnb/Flighty Action Pill
        Surface(
            modifier = Modifier
                .shadow(10.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                .springClickable(onClick = onWhatNowClick),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            listOf(ElectricOrange, ElectricOrangeHover)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Làm gì bây giờ?",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Làm gì?",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

// ============================================================================
// 5. ALLTRAILS-STYLE ANIMATED MOUNTAIN ELEVATION PROFILE
// ============================================================================
@Composable
fun ElevationProfileCard(
    dayNumber: Int,
    dayPlaces: List<Place>,
    routeSubtitle: String,
    onPlaceSelected: ((Place) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val stops = remember(dayPlaces) {
        dayPlaces.distinctBy { it.id }.ifEmpty { emptyList() }
    }
    var selectedIdx by remember(dayNumber, stops.size) {
        mutableIntStateOf(
            stops.indexOfFirst { it.altitudeMeters >= (stops.maxOfOrNull { p -> p.altitudeMeters } ?: 0) }
                .coerceAtLeast(0)
        )
    }

    val sampleCount = 25
    val targetAltitudes = remember(stops, dayNumber) {
        if (stops.size >= 2) {
            List(sampleCount) { i ->
                val pos = i.toFloat() / (sampleCount - 1) * (stops.size - 1)
                val low = pos.toInt().coerceIn(0, stops.lastIndex)
                val high = (low + 1).coerceIn(0, stops.lastIndex)
                val frac = pos - low
                val a1 = stops[low].altitudeMeters.toFloat()
                val a2 = stops[high].altitudeMeters.toFloat()
                a1 + (a2 - a1) * frac
            }
        } else {
            val base = when (dayNumber) {
                1 -> listOf(120f, 950f, 1500f, 650f, 1150f, 1320f, 1050f)
                2 -> listOf(1050f, 1470f, 1050f, 1500f, 380f, 980f, 1000f)
                else -> listOf(1000f, 1120f, 750f, 620f, 1080f, 450f, 120f)
            }
            List(sampleCount) { i ->
                val pos = i.toFloat() / (sampleCount - 1) * (base.size - 1)
                val low = pos.toInt().coerceIn(0, base.lastIndex)
                val high = (low + 1).coerceIn(0, base.lastIndex)
                val frac = pos - low
                base[low] + (base[high] - base[low]) * frac
            }
        }
    }

    // Animate từng điểm cao độ bằng lò xo khi chuyển Ngày 1 <-> Ngày 2 <-> Ngày 3
    val animatedSamples = targetAltitudes.mapIndexed { idx, target ->
        animateFloatAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
            label = "ElevSample_$idx"
        ).value
    }

    val maxAlt = remember(stops, targetAltitudes) {
        (stops.maxOfOrNull { it.altitudeMeters } ?: targetAltitudes.maxOrNull()?.toInt() ?: 1500)
    }
    val minAlt = remember(stops, targetAltitudes) {
        (stops.minOfOrNull { it.altitudeMeters } ?: targetAltitudes.minOrNull()?.toInt() ?: 120)
    }
    val selectedStop = stops.getOrNull(selectedIdx)

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terrain,
                            contentDescription = null,
                            tint = EmeraldGreenText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "MẶT CẮT ĐỘ CAO ĐÈO • NGÀY $dayNumber",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldGreenText
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = routeSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(EmeraldGreenSoft)
                        .border(BorderStroke(1.dp, EmeraldGreenBorder), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "ĐỈNH ĐÈO",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = EmeraldGreenText
                    )
                    RollingNumberText(
                        targetValue = maxAlt.toFloat(),
                        suffix = "m",
                        style = TelemetryTextStyle.copy(fontSize = 16.sp),
                        color = EmeraldGreenText
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(stops) {
                            if (stops.isNotEmpty()) {
                                detectTapGestures { tap ->
                                    val ratio = (tap.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                                    val nearest = (ratio * (stops.size - 1)).roundToInt().coerceIn(0, stops.lastIndex)
                                    selectedIdx = nearest
                                    onPlaceSelected?.invoke(stops[nearest])
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val topPad = 14.dp.toPx()
                    val bottomPad = 10.dp.toPx()
                    val chartH = (h - topPad - bottomPad).coerceAtLeast(10f)
                    val ceilAlt = 1700f

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(500f, 1000f, 1500f).forEach { level ->
                        val y = topPad + chartH * (1f - (level / ceilAlt))
                        drawLine(
                            color = BorderSubtle,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    val pts = animatedSamples.mapIndexed { i, alt ->
                        val x = if (animatedSamples.size > 1) w * i / (animatedSamples.size - 1) else w / 2f
                        val norm = (alt / ceilAlt).coerceIn(0.06f, 0.96f)
                        val y = topPad + chartH * (1f - norm)
                        Offset(x, y)
                    }

                    if (pts.size >= 2) {
                        val linePath = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 0 until pts.lastIndex) {
                                val p0 = pts[i]
                                val p1 = pts[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(pts.last().x, h - bottomPad)
                            lineTo(pts.first().x, h - bottomPad)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    EmeraldGreen.copy(alpha = 0.26f),
                                    ElectricOrange.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = h
                            )
                        )

                        drawPath(
                            path = linePath,
                            brush = Brush.horizontalGradient(
                                listOf(EmeraldGreen, ElectricOrange)
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (stops.isNotEmpty()) {
                        stops.forEachIndexed { idx, _ ->
                            val ratio = if (stops.size > 1) idx.toFloat() / (stops.size - 1) else 0.5f
                            val x = w * ratio
                            val samplePos = ratio * (animatedSamples.size - 1)
                            val sLow = samplePos.toInt().coerceIn(0, animatedSamples.lastIndex)
                            val sHigh = (sLow + 1).coerceIn(0, animatedSamples.lastIndex)
                            val sFrac = samplePos - sLow
                            val interpAlt = animatedSamples[sLow] + (animatedSamples[sHigh] - animatedSamples[sLow]) * sFrac
                            val norm = (interpAlt / ceilAlt).coerceIn(0.06f, 0.96f)
                            val y = topPad + chartH * (1f - norm)
                            val isSel = idx == selectedIdx
                            val isEndpointOrPeak = idx == 0 || idx == stops.lastIndex || isSel || (stops.size <= 8) || (idx % 2 == 0)

                            if (isSel) {
                                drawLine(
                                    color = ElectricOrange.copy(alpha = 0.45f),
                                    start = Offset(x, y),
                                    end = Offset(x, h - bottomPad),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = dashEffect
                                )
                                drawCircle(
                                    color = ElectricOrange.copy(alpha = 0.22f),
                                    radius = 10.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                            if (isEndpointOrPeak) {
                                drawCircle(
                                    color = Color.White,
                                    radius = if (isSel) 5.5.dp.toPx() else 3.8.dp.toPx(),
                                    center = Offset(x, y)
                                )
                                drawCircle(
                                    color = if (isSel) ElectricOrange else EmeraldGreen,
                                    radius = if (isSel) 4.dp.toPx() else 2.4.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceMuted)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedStop != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Place,
                            contentDescription = null,
                            tint = ElectricOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = selectedStop.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${selectedStop.altitudeMeters}m • Chạm đỉnh đèo",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                } else {
                    Text(
                        text = "Biên độ cao độ: ${minAlt}m ➔ ${maxAlt}m so với mực nước biển",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

// ============================================================================
// 6. ANIMATED RADIAL ARC GAUGE (HUD Cockpit Speedometer & Altimeter)
// ============================================================================
@Composable
fun AnimatedArcGauge(
    label: String,
    value: Float,
    maxValue: Float,
    unit: String,
    accentColor: Color,
    softColor: Color,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (value / maxValue.coerceAtLeast(1f)).coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 240f),
        label = "ArcGaugeProgress"
    )

    ShadcnCard(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(128.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2f + 5.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)

                drawArc(
                    color = softColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                if (progress > 0.01f) {
                    drawArc(
                        color = accentColor.copy(alpha = 0.18f),
                        startAngle = 150f,
                        sweepAngle = 240f * progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke * 1.65f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accentColor,
                        startAngle = 150f,
                        sweepAngle = 240f * progress,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextMuted
                )
                RollingNumberText(
                    targetValue = value,
                    style = TelemetryTextStyle.copy(fontSize = 34.sp),
                    color = accentColor
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}
