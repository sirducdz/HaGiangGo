package com.reed.hagianggo.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.ui.theme.*

// =========================================================================
// 1. ShadcnCard: Thẻ trắng sang trọng kiểu Airbnb / AllTrails với chiều sâu nhẹ & lò xo
// =========================================================================
@Composable
fun ShadcnCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = SurfaceWhite,
    borderColor: Color = BorderSubtle,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.springClickable(onClick = onClick)
    } else {
        modifier
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = if (backgroundColor == SurfaceWhite) shadowElevation else 0.dp
    ) {
        Column(content = content)
    }
}

// =========================================================================
// 2. ShadcnBadge: Huy hiệu/tag nhỏ gọn với tông màu pastel tươi tắn
// =========================================================================
enum class ShadcnBadgeVariant {
    Default,
    Secondary,
    Outline,
    Orange,
    Emerald,
    Amber,
    Rose,
    Sky
}

@Composable
fun ShadcnBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: ShadcnBadgeVariant = ShadcnBadgeVariant.Default,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val (bgColor, borderColor, textColor) = when (variant) {
        ShadcnBadgeVariant.Default -> Triple(SurfaceMuted, BorderSubtle, TextSecondary)
        ShadcnBadgeVariant.Secondary -> Triple(SurfaceWhite, BorderMedium, TextPrimary)
        ShadcnBadgeVariant.Outline -> Triple(Color.Transparent, BorderMedium, TextSecondary)
        ShadcnBadgeVariant.Orange -> Triple(ElectricOrangeSoft, ElectricOrangeBorder, ElectricOrangeHover)
        ShadcnBadgeVariant.Emerald -> Triple(EmeraldGreenSoft, EmeraldGreenBorder, EmeraldGreenText)
        ShadcnBadgeVariant.Amber -> Triple(AmberWarningSoft, AmberWarningBorder, AmberWarningText)
        ShadcnBadgeVariant.Rose -> Triple(RoseDestructiveSoft, RoseDestructiveBorder, RoseDestructiveText)
        ShadcnBadgeVariant.Sky -> Triple(SkyTelemetrySoft, SkyTelemetryBorder, SkyTelemetry)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier
                        .size(11.dp)
                        .padding(end = 3.dp)
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                maxLines = 1
            )
        }
    }
}

// =========================================================================
// 3. ShadcnButton: Nút bấm bo tròn đàn hồi lò xo kèm phản hồi xúc giác Haptic
// =========================================================================
enum class ShadcnButtonVariant {
    Primary,
    Secondary,
    Outline,
    Ghost,
    Destructive
}

@Composable
fun ShadcnButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ShadcnButtonVariant = ShadcnButtonVariant.Primary,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 680f
        ),
        label = "ShadcnBtnScale"
    )

    val (containerColor, contentColor, border) = when (variant) {
        ShadcnButtonVariant.Primary -> Triple(ElectricOrange, Color.White, null)
        ShadcnButtonVariant.Secondary -> Triple(SurfaceWhite, TextPrimary, BorderStroke(1.dp, BorderSubtle))
        ShadcnButtonVariant.Outline -> Triple(Color.Transparent, TextPrimary, BorderStroke(1.dp, BorderMedium))
        ShadcnButtonVariant.Ghost -> Triple(Color.Transparent, TextSecondary, null)
        ShadcnButtonVariant.Destructive -> Triple(RoseDestructive, Color.White, null)
    }

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = SurfaceMuted,
            disabledContentColor = TextSubtle
        ),
        border = border,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (variant == ShadcnButtonVariant.Primary) 2.dp else 0.dp,
            pressedElevation = 0.dp
        ),
        contentPadding = contentPadding,
        content = content
    )
}

// =========================================================================
// 4. ShadcnSegmentedTabs: Khay trượt có viên nang trắng lướt vật lý lò xo thực thụ
// =========================================================================
@Composable
fun <T> ShadcnSegmentedTabs(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: @Composable (T, Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceMuted)
            .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
            .padding(3.dp)
    ) {
        val tabCount = items.size.coerceAtLeast(1)
        val tabWidth = maxWidth / tabCount
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 460f
            ),
            label = "SegmentedSlideOffset"
        )

        // Viên nang trắng có bóng đổ nhẹ trượt mượt giữa các tab
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(9.dp), ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                .clip(RoundedCornerShape(9.dp))
                .background(SurfaceWhite)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(9.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onItemSelected(item)
                                }
                            }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    itemLabel(item, isSelected)
                }
            }
        }
    }
}

// =========================================================================
// 5. ShadcnAlert: Khối thông báo pastel viền mảnh tinh gọn
// =========================================================================
@Composable
fun ShadcnAlert(
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    variant: ShadcnBadgeVariant = ShadcnBadgeVariant.Amber,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null
) {
    val (accentColor, bgColor) = when (variant) {
        ShadcnBadgeVariant.Rose -> RoseDestructive to RoseDestructiveSoft
        ShadcnBadgeVariant.Emerald -> EmeraldGreen to EmeraldGreenSoft
        ShadcnBadgeVariant.Orange -> ElectricOrange to ElectricOrangeSoft
        ShadcnBadgeVariant.Sky -> SkyTelemetry to SkyTelemetrySoft
        else -> AmberWarning to AmberWarningSoft
    }

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = bgColor,
        borderColor = accentColor.copy(alpha = 0.30f),
        borderWidth = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(13.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 1.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (action != null) {
                Spacer(modifier = Modifier.width(8.dp))
                action()
            }
        }
    }
}

// =========================================================================
// 6. ShadcnMetricCard: Khối hiển thị chỉ số đo đạc lớn
// =========================================================================
@Composable
fun ShadcnMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = TextPrimary,
    icon: ImageVector? = null
) {
    ShadcnCard(
        modifier = modifier,
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle,
        borderWidth = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label.uppercase(),
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextSubtle,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = valueColor,
                    style = TelemetryTextStyle.copy(fontSize = 20.sp)
                )
                if (unit != null) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}
