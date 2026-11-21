package com.reed.hagianggo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.TimePacerEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import kotlin.math.abs

/**
 * Giao diện HUD Dẫn đường trực tiếp trong app (AllTrails × Apple Maps Clean Light Mode):
 * - Banner hướng rẽ phía trên + nút tắt/bật giọng nói + chip chuyển gốc Km0 khi ở ngoài Hà Giang
 * - Cụm nút điều khiển bản đồ bên phải (Xoay theo xe, Toàn tuyến, Lớp bản đồ, SOS 2G)
 * - Dock thông số phía dưới (Cự ly thực, Phút còn lại, ETA, Độ cao, Tốc độ, Bước rẽ, G.Maps, Thoát)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationOverlayHud(
    navState: ActiveNavigationState,
    currentSpeedKmh: Float,
    isOutsideHaGiang: Boolean,
    fuelAdvisory: FuelAdvisory,
    nextScheduledPlace: Place?,
    currentLayerStyle: MapLayerStyle,
    onToggleFollowMode: () -> Unit,
    onRecenter: () -> Unit,
    onOverviewRoute: () -> Unit,
    onToggleVoice: () -> Unit,
    onToggleOriginMode: () -> Unit,
    onToggleMapLayer: () -> Unit,
    onOpenSos: () -> Unit,
    onOpenGoogleMaps: () -> Unit,
    onForceReroute: () -> Unit = {},
    onNavigateNext: (Place) -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showStepsSheet by remember { mutableStateOf(false) }

    val route = navState.route
    val currentStep = route?.steps?.getOrNull(navState.currentStepIndex) ?: route?.steps?.firstOrNull()
    val nextAfterCurrent = if (route != null && navState.currentStepIndex + 1 < route.steps.size) {
        route.steps[navState.currentStepIndex + 1]
    } else null
    val isWrongWayAlert = navState.isGoingWrongWay && !navState.hasArrived

    Box(modifier = modifier.fillMaxSize()) {
        // =====================================================================
        // 1. TOP TURN-BY-TURN BANNER
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShadcnCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, RoundedCornerShape(20.dp), ambientColor = SoftShadowColor, spotColor = SoftShadowColor),
                shape = RoundedCornerShape(20.dp),
                backgroundColor = SurfaceWhite,
                borderColor = when {
                    navState.hasArrived -> EmeraldGreenBorder
                    isWrongWayAlert -> RoseDestructiveBorder
                    else -> BorderSubtle
                },
                borderWidth = if (isWrongWayAlert || navState.hasArrived) 1.5.dp else 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val maneuverIcon = when {
                        navState.hasArrived -> Icons.Default.Flag
                        isWrongWayAlert -> Icons.Default.UTurnLeft
                        currentStep != null -> maneuverToIcon(currentStep.maneuver)
                        else -> Icons.Default.Navigation
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                when {
                                    navState.hasArrived -> EmeraldGreenSoft
                                    isWrongWayAlert -> RoseDestructiveSoft
                                    else -> ElectricOrangeSoft
                                }
                            )
                            .border(
                                BorderStroke(
                                    1.dp,
                                    when {
                                        navState.hasArrived -> EmeraldGreenBorder
                                        isWrongWayAlert -> RoseDestructiveBorder
                                        else -> ElectricOrangeBorder
                                    }
                                ),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = maneuverIcon,
                            contentDescription = null,
                            tint = when {
                                navState.hasArrived -> EmeraldGreenText
                                isWrongWayAlert -> RoseDestructiveText
                                else -> ElectricOrangeHover
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        if (navState.isLoadingRoute && route == null) {
                            Text(
                                text = "Đang tính tuyến tới ${navState.destination.name}...",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape),
                                color = ElectricOrange,
                                trackColor = SurfaceMuted
                            )
                        } else if (navState.hasArrived) {
                            Text(
                                text = "ĐÃ TỚI ĐIỂM ĐẾN",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = EmeraldGreenText
                            )
                            Text(
                                text = navState.destination.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (isWrongWayAlert) {
                            Text(
                                text = "ĐI NGƯỢC CHIỀU • QUAY ĐẦU LẠI",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = RoseDestructiveText
                            )
                            Text(
                                text = "Đã đi ngược tuyến ~${navState.wrongWayBacktrackMeters.toInt()}m • Hãy quay đầu theo hướng mũi tên",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = LocationMath.formatNavigationDistance(navState.distanceToNextManeuverMeters),
                                    style = TelemetryTextStyle.copy(fontSize = 21.sp, fontWeight = FontWeight.ExtraBold),
                                    color = ElectricOrangeHover
                                )
                                if (currentStep?.roadName?.isNotBlank() == true) {
                                    Text(
                                        text = currentStep.roadName,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = currentStep?.instructionVi ?: "Di chuyển theo tuyến tới ${navState.destination.name}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (route != null) {
                            ShadcnBadge(
                                text = route.source.badgeLabel,
                                variant = if (route.source == RouteSource.OFFLINE_LOOP_GRAPH) {
                                    ShadcnBadgeVariant.Emerald
                                } else {
                                    ShadcnBadgeVariant.Sky
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (navState.isVoiceEnabled) ElectricOrangeSoft else SurfaceMuted)
                                .border(
                                    BorderStroke(1.dp, if (navState.isVoiceEnabled) ElectricOrangeBorder else BorderSubtle),
                                    CircleShape
                                )
                                .springClickable(onClick = onToggleVoice),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (navState.isVoiceEnabled) {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                },
                                contentDescription = if (navState.isVoiceEnabled) "Tắt giọng nói" else "Bật giọng nói",
                                tint = if (navState.isVoiceEnabled) ElectricOrangeHover else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Thanh phụ: Tính lại tuyến / Cảnh báo đi ngược chiều / Lệch tuyến / Ngoài Hà Giang / Bước kế tiếp
            if (navState.isRerouting) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberWarningSoft,
                    border = BorderStroke(1.dp, AmberWarningBorder),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = AmberWarningText
                        )
                        Text(
                            text = "Đang tự tính lại lộ trình mới từ vị trí hiện tại...",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberWarningText
                        )
                    }
                }
            } else if (isWrongWayAlert) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RoseDestructiveSoft,
                    border = BorderStroke(1.dp, RoseDestructiveBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "↩️ Quay đầu lại hoặc bấm để tìm đường mới",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = RoseDestructiveText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(RoseDestructive)
                                .springClickable(onClick = onForceReroute)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Tính lại",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (navState.isOffRoute) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AmberWarningSoft,
                    border = BorderStroke(1.dp, AmberWarningBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Lệch tuyến (~${navState.offRouteDistanceMeters.toInt()}m) • Đang theo dõi",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = AmberWarningText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AmberWarningText)
                                .springClickable(onClick = onForceReroute)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Tính lại",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            } else if (isOutsideHaGiang && route != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceGlass,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (navState.isUsingZonePreviewOrigin) {
                                "🧭 Xem trước tuyến từ ${route.originLabel}"
                            } else {
                                "📍 Đang dẫn từ GPS thật tới Hà Giang"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ElectricOrangeSoft)
                                .border(BorderStroke(1.dp, ElectricOrangeBorder), CircleShape)
                                .springClickable(onClick = onToggleOriginMode)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = ElectricOrangeHover, modifier = Modifier.size(13.dp))
                                Text(
                                    text = if (navState.forceFromHaGiangOrigin) "Từ GPS thật" else "Xem từ Km0",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricOrangeHover
                                )
                            }
                        }
                    }
                }
            } else if (nextAfterCurrent != null && !navState.hasArrived) {
                Surface(
                    shape = CircleShape,
                    color = SurfaceGlass,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = maneuverToIcon(nextAfterCurrent.maneuver),
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Sau đó: ${nextAfterCurrent.instructionVi}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. FLOATING RIGHT CONTROLS (Xoay theo xe / Toàn tuyến / Lớp bản đồ / SOS)
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val isHeadingUp = navState.followMode == NavCameraFollowMode.HEADING_UP
            NavFloatingCircleButton(
                icon = if (isHeadingUp) Icons.Default.Navigation else Icons.Default.Explore,
                contentDescription = if (isHeadingUp) "Đang xoay theo hướng xe" else "Bám hướng Bắc",
                tint = if (navState.followMode == NavCameraFollowMode.FREE_PAN) TextMuted else ElectricOrangeHover,
                backgroundColor = if (isHeadingUp) ElectricOrangeSoft else SurfaceGlass,
                borderColor = if (isHeadingUp) ElectricOrangeBorder else BorderSubtle,
                onClick = onToggleFollowMode
            )

            NavFloatingCircleButton(
                icon = Icons.AutoMirrored.Filled.AltRoute,
                contentDescription = "Xem toàn tuyến",
                tint = SkyTelemetry,
                backgroundColor = SurfaceGlass,
                borderColor = BorderSubtle,
                onClick = onOverviewRoute
            )

            NavFloatingCircleButton(
                icon = Icons.Default.Layers,
                contentDescription = "Đổi lớp bản đồ (${currentLayerStyle.label})",
                tint = when (currentLayerStyle) {
                    MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenText
                    MapLayerStyle.OPEN_TOPO -> SkyTelemetry
                    MapLayerStyle.STANDARD_OSM -> TextSecondary
                },
                backgroundColor = when (currentLayerStyle) {
                    MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenSoft
                    MapLayerStyle.OPEN_TOPO -> SkyTelemetrySoft
                    MapLayerStyle.STANDARD_OSM -> SurfaceGlass
                },
                borderColor = when (currentLayerStyle) {
                    MapLayerStyle.OFFLINE_TOPO -> EmeraldGreenBorder
                    MapLayerStyle.OPEN_TOPO -> SkyTelemetryBorder
                    MapLayerStyle.STANDARD_OSM -> BorderSubtle
                },
                onClick = onToggleMapLayer
            )

            NavFloatingCircleButton(
                icon = Icons.Default.Sos,
                contentDescription = "Cứu hộ SOS 2G",
                tint = RoseDestructiveText,
                backgroundColor = RoseDestructiveSoft,
                borderColor = RoseDestructiveBorder,
                onClick = onOpenSos
            )
        }

        // =====================================================================
        // 3. BOTTOM TELEMETRY DOCK + RECENTER PILL
        // =====================================================================
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = navState.followMode == NavCameraFollowMode.FREE_PAN,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Start)
            ) {
                Surface(
                    modifier = Modifier
                        .shadow(10.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                        .springClickable(onClick = onRecenter),
                    shape = CircleShape,
                    color = SurfaceWhite,
                    border = BorderStroke(1.5.dp, ElectricOrange)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = ElectricOrangeHover,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Bám lại xe",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricOrangeHover
                        )
                    }
                }
            }

            if (navState.hasArrived) {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = SoftShadowColor, spotColor = SoftShadowColor),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = EmeraldGreenBorder,
                    borderWidth = 1.5.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎉 Bạn đã đến ${navState.destination.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Độ cao ▲ ${navState.destination.altitudeMeters}m • Chúc bạn check-in thật đẹp!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (nextScheduledPlace != null) {
                                ShadcnButton(
                                    onClick = { onNavigateNext(nextScheduledPlace) },
                                    variant = ShadcnButtonVariant.Primary,
                                    modifier = Modifier.weight(1.4f).height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Đi tiếp: ${nextScheduledPlace.name}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            ShadcnButton(
                                onClick = onStopNavigation,
                                variant = if (nextScheduledPlace != null) ShadcnButtonVariant.Secondary else ShadcnButtonVariant.Primary,
                                modifier = Modifier.weight(0.8f).height(42.dp)
                            ) {
                                Text("Hoàn tất", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                ShadcnCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = SoftShadowColor, spotColor = SoftShadowColor),
                    shape = RoundedCornerShape(22.dp),
                    backgroundColor = SurfaceWhite,
                    borderColor = BorderSubtle
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = navState.destination.name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val elevStr = buildString {
                                    append("▲ ${navState.destination.altitudeMeters}m")
                                    if (navState.elevationDeltaMeters != 0) {
                                        val delta = navState.elevationDeltaMeters
                                        append(if (delta > 0) " (Leo +${delta}m)" else " (Đổ ${abs(delta)}m)")
                                    }
                                }
                                Text(
                                    text = elevStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (fuelAdvisory.isCriticalLow) {
                                    ShadcnBadge(
                                        text = "Xăng thấp",
                                        variant = ShadcnBadgeVariant.Rose,
                                        icon = Icons.Default.LocalGasStation
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = SkyTelemetrySoft,
                                    border = BorderStroke(1.dp, SkyTelemetryBorder)
                                ) {
                                    Text(
                                        text = "${currentSpeedKmh.toInt()} km/h",
                                        style = TelemetryTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        color = SkyTelemetry,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // 3 ô đo đạc Bento gọn gàng: Phút còn lại • Cự ly • ETA
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceMuted)
                                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NavMetricCell(
                                label = "THỜI GIAN",
                                value = if (navState.isLoadingRoute && route == null) "..." else "~${navState.remainingDurationMin} phút",
                                valueColor = EmeraldGreenText,
                                modifier = Modifier.weight(1f)
                            )
                            NavMetricCell(
                                label = "CỰ LY ĐƯỜNG",
                                value = if (navState.isLoadingRoute && route == null) "..." else LocationMath.formatNavigationDistance(navState.remainingDistanceMeters),
                                valueColor = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            NavMetricCell(
                                label = "DỰ KIẾN TỚI",
                                value = if (navState.isLoadingRoute && route == null) "--:--" else TimePacerEngine.formatMinutesOfDay(navState.etaMinutesOfDay),
                                valueColor = SkyTelemetry,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Hàng nút: Bước rẽ • G.Maps dự phòng • Thoát
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShadcnButton(
                                onClick = { showStepsSheet = true },
                                enabled = route != null && route.steps.isNotEmpty(),
                                variant = ShadcnButtonVariant.Secondary,
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Bước rẽ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            ShadcnButton(
                                onClick = onOpenGoogleMaps,
                                variant = ShadcnButtonVariant.Secondary,
                                modifier = Modifier.weight(0.95f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = SkyTelemetry, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("G.Maps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            ShadcnButton(
                                onClick = onStopNavigation,
                                variant = ShadcnButtonVariant.Destructive,
                                modifier = Modifier.weight(0.85f).height(40.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thoát", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStepsSheet && route != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showStepsSheet = false },
            sheetState = sheetState,
            containerColor = AppBackground,
            contentColor = TextPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lộ trình chi tiết (${route.steps.size} chặng)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${route.originLabel} ➔ ${navState.destination.name} • ${LocationMath.formatNavigationDistance(route.totalDistanceMeters)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    ShadcnBadge(
                        text = route.source.badgeLabel,
                        variant = if (route.source == RouteSource.OFFLINE_LOOP_GRAPH) ShadcnBadgeVariant.Emerald else ShadcnBadgeVariant.Sky
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(route.steps, key = { it.index }) { step ->
                        val isCurrent = step.index == navState.currentStepIndex
                        val isPassed = step.index < navState.currentStepIndex
                        ShadcnCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            backgroundColor = if (isCurrent) ElectricOrangeSoft else SurfaceWhite,
                            borderColor = if (isCurrent) ElectricOrangeBorder else BorderSubtle,
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) ElectricOrange else SurfaceMuted),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = maneuverToIcon(step.maneuver),
                                        contentDescription = null,
                                        tint = when {
                                            isCurrent -> Color.White
                                            isPassed -> TextSubtle
                                            else -> TextPrimary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = step.instructionVi,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isPassed) TextMuted else TextPrimary
                                    )
                                    if (step.roadName.isNotBlank() && step.maneuver != ManeuverType.ARRIVE) {
                                        Text(
                                            text = step.roadName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                }
                                if (step.distanceMeters > 0.0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = LocationMath.formatNavigationDistance(step.distanceMeters),
                                        style = TelemetryTextStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                        color = if (isCurrent) ElectricOrangeHover else TextSecondary
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

@Composable
private fun NavFloatingCircleButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    backgroundColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .shadow(8.dp, CircleShape, ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderColor), CircleShape)
            .springClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun NavMetricCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TelemetryTextStyle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold),
            color = valueColor,
            maxLines = 1
        )
    }
}

private fun maneuverToIcon(maneuver: ManeuverType): ImageVector = when (maneuver) {
    ManeuverType.DEPART -> Icons.Default.Navigation
    ManeuverType.STRAIGHT -> Icons.Default.Straight
    ManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    ManeuverType.LEFT -> Icons.Default.TurnLeft
    ManeuverType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    ManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    ManeuverType.RIGHT -> Icons.Default.TurnRight
    ManeuverType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    ManeuverType.UTURN -> Icons.Default.UTurnLeft
    ManeuverType.ROUNDABOUT -> Icons.Default.RoundaboutRight
    ManeuverType.MERGE -> Icons.Default.Merge
    ManeuverType.ARRIVE -> Icons.Default.Flag
}
