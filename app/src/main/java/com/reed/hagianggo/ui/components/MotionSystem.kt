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
import androidx.compose.ui.draw.drawWithCache
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
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = SoftAmbientShadow,
                    spotColor = SoftAmbientShadow
                ),
            shape = CircleShape,
            color = SurfaceGlass,
            border = BorderStroke(0.8.dp, BorderHairline)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
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

                // Viên nang trượt nhẹ tinh tế chuẩn iOS 18 Floating Tab Bar
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(ElectricOrangeSoft)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    items.forEach { item ->
                        val isSelected = selectedTab == item.index
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1.0f,
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
                                        .size(19.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                                Spacer(modifier = Modifier.height(1.5.dp))
                                Text(
                                    text = item.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
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
        // Viên nang Trái: Dynamic Island Light gọn 1 dòng
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(38.dp)
                .shadow(6.dp, CircleShape, ambientColor = SoftAmbientShadow, spotColor = SoftAmbientShadow)
                .springClickable(onClick = onStatusClick),
            shape = CircleShape,
            color = SurfaceGlass,
            border = BorderStroke(0.8.dp, BorderHairline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(14.dp)) {
                    val dotColor = when {
                        isTrackingRide -> EmeraldGreen
                        isOutsideHaGiang -> AmberWarning
                        isGpsLive -> EmeraldGreen
                        else -> TextSubtle
                    }
                    if (isTrackingRide || isGpsLive) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = pulseAlpha * 0.4f
                                }
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(7.5.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = activeZoneName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isTrackingRide) {
                        " • ${activeAltitudeMeters}m • ${currentSpeedKmh.roundToInt()} km/h"
                    } else {
                        " • ${activeAltitudeMeters}m"
                    },
                    style = TelemetryTextStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = if (isTrackingRide) EmeraldGreenText else TextSecondary,
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Viên nang Phải: AI Gợi ý "Làm gì?" cùng chiều cao 38dp cân đối
        Surface(
            modifier = Modifier
                .height(38.dp)
                .shadow(6.dp, CircleShape, ambientColor = SoftAmbientShadow, spotColor = SoftAmbientShadow)
                .springClickable(onClick = onWhatNowClick),
            shape = CircleShape,
            color = ElectricOrange
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Làm gì bây giờ?",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Làm gì?",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
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
    modifier: Modifier = Modifier,
    embedded: Boolean = false
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

    // Dùng 1 Animatable duy nhất nội suy ở pha draw (tránh tạo 25 coroutine animateFloatAsState khi mở/đóng panel)
    var prevAltitudes by remember { mutableStateOf(targetAltitudes) }
    val transitionProgress = remember { Animatable(1f) }
    var hasInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(targetAltitudes) {
        if (!hasInitialized) {
            hasInitialized = true
            prevAltitudes = targetAltitudes
            transitionProgress.snapTo(1f)
        } else {
            transitionProgress.snapTo(0f)
            transitionProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            prevAltitudes = targetAltitudes
        }
    }

    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }

    val maxAlt = remember(stops, targetAltitudes) {
        (stops.maxOfOrNull { it.altitudeMeters } ?: targetAltitudes.maxOrNull()?.toInt() ?: 1500)
    }
    val minAlt = remember(stops, targetAltitudes) {
        (stops.minOfOrNull { it.altitudeMeters } ?: targetAltitudes.minOrNull()?.toInt() ?: 120)
    }
    val selectedStop = stops.getOrNull(selectedIdx)

    val chartContent: @Composable () -> Unit = {
        Column(modifier = if (embedded) Modifier else Modifier.padding(16.dp)) {
            if (!embedded) {
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
                                text = "Mặt cắt độ cao đèo • Ngày $dayNumber",
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
                            .border(BorderStroke(0.8.dp, EmeraldGreenBorder), RoundedCornerShape(12.dp))
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (embedded) 96.dp else 112.dp)
            ) {
                Spacer(
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
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val topPad = 14.dp.toPx()
                            val bottomPad = 10.dp.toPx()
                            val chartH = (h - topPad - bottomPad).coerceAtLeast(10f)
                            val ceilAlt = 1700f
                            val progress = transitionProgress.value
                            val currentSamples = if (progress >= 0.999f || prevAltitudes.size != targetAltitudes.size) {
                                targetAltitudes
                            } else {
                                List(targetAltitudes.size) { i ->
                                    prevAltitudes[i] + (targetAltitudes[i] - prevAltitudes[i]) * progress
                                }
                            }

                            val gridYs = floatArrayOf(
                                topPad + chartH * (1f - (500f / ceilAlt)),
                                topPad + chartH * (1f - (1000f / ceilAlt)),
                                topPad + chartH * (1f - (1500f / ceilAlt))
                            )

                            val pts = currentSamples.mapIndexed { i, alt ->
                                val x = if (currentSamples.size > 1) w * i / (currentSamples.size - 1) else w / 2f
                                val norm = (alt / ceilAlt).coerceIn(0.06f, 0.96f)
                                val y = topPad + chartH * (1f - norm)
                                Offset(x, y)
                            }

                            val linePath = if (pts.size >= 2) {
                                Path().apply {
                                    moveTo(pts.first().x, pts.first().y)
                                    for (i in 0 until pts.lastIndex) {
                                        val p0 = pts[i]
                                        val p1 = pts[i + 1]
                                        val cx = (p0.x + p1.x) / 2f
                                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                                    }
                                }
                            } else null

                            val fillPath = if (linePath != null) {
                                Path().apply {
                                    addPath(linePath)
                                    lineTo(pts.last().x, h - bottomPad)
                                    lineTo(pts.first().x, h - bottomPad)
                                    close()
                                }
                            } else null

                            val fillBrush = Brush.verticalGradient(
                                colors = listOf(
                                    EmeraldGreen.copy(alpha = 0.26f),
                                    ElectricOrange.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = h
                            )
                            val lineBrush = Brush.horizontalGradient(
                                listOf(EmeraldGreen, ElectricOrange)
                            )
                            val strokeStyle = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                            val gridStrokePx = 1.dp.toPx()
                            val selLineStrokePx = 1.5.dp.toPx()
                            val selGlowRadius = 10.dp.toPx()
                            val selOuterRadius = 5.5.dp.toPx()
                            val normalOuterRadius = 3.8.dp.toPx()
                            val selInnerRadius = 4.dp.toPx()
                            val normalInnerRadius = 2.4.dp.toPx()

                            val stopCoords = if (stops.isNotEmpty()) {
                                List(stops.size) { idx ->
                                    val ratio = if (stops.size > 1) idx.toFloat() / (stops.size - 1) else 0.5f
                                    val x = w * ratio
                                    val samplePos = ratio * (currentSamples.size - 1)
                                    val sLow = samplePos.toInt().coerceIn(0, currentSamples.lastIndex)
                                    val sHigh = (sLow + 1).coerceIn(0, currentSamples.lastIndex)
                                    val sFrac = samplePos - sLow
                                    val interpAlt = currentSamples[sLow] + (currentSamples[sHigh] - currentSamples[sLow]) * sFrac
                                    val norm = (interpAlt / ceilAlt).coerceIn(0.06f, 0.96f)
                                    val y = topPad + chartH * (1f - norm)
                                    Offset(x, y)
                                }
                            } else emptyList()

                            onDrawBehind {
                                for (y in gridYs) {
                                    drawLine(
                                        color = BorderSubtle,
                                        start = Offset(0f, y),
                                        end = Offset(w, y),
                                        strokeWidth = gridStrokePx,
                                        pathEffect = dashEffect
                                    )
                                }

                                if (fillPath != null && linePath != null) {
                                    drawPath(path = fillPath, brush = fillBrush)
                                    drawPath(path = linePath, brush = lineBrush, style = strokeStyle)
                                }

                                val curSelected = selectedIdx
                                for (idx in stopCoords.indices) {
                                    val pt = stopCoords[idx]
                                    val isSel = idx == curSelected
                                    val isEndpointOrPeak = idx == 0 || idx == stopCoords.lastIndex || isSel || (stopCoords.size <= 8) || (idx % 2 == 0)

                                    if (isSel) {
                                        drawLine(
                                            color = ElectricOrange.copy(alpha = 0.45f),
                                            start = pt,
                                            end = Offset(pt.x, h - bottomPad),
                                            strokeWidth = selLineStrokePx,
                                            pathEffect = dashEffect
                                        )
                                        drawCircle(
                                            color = ElectricOrange.copy(alpha = 0.22f),
                                            radius = selGlowRadius,
                                            center = pt
                                        )
                                    }
                                    if (isEndpointOrPeak) {
                                        drawCircle(
                                            color = Color.White,
                                            radius = if (isSel) selOuterRadius else normalOuterRadius,
                                            center = pt
                                        )
                                        drawCircle(
                                            color = if (isSel) ElectricOrange else EmeraldGreen,
                                            radius = if (isSel) selInnerRadius else normalInnerRadius,
                                            center = pt
                                        )
                                    }
                                }
                            }
                        }
                )
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
                        text = "${selectedStop.altitudeMeters}m • Chạm biểu đồ",
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

    if (embedded) {
        Box(modifier = modifier.fillMaxWidth()) {
            chartContent()
        }
    } else {
        ShadcnCard(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            backgroundColor = SurfaceWhite,
            borderColor = BorderHairline
        ) {
            chartContent()
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
