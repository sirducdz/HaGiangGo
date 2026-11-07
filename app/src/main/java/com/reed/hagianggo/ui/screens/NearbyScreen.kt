package com.reed.hagianggo.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.*
import com.reed.hagianggo.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val places by viewModel.places.collectAsState()
    val activeZone by viewModel.activeZone.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()
    val tripConfig by viewModel.tripConfig.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<PlaceCategory?>(null) }
    var filterRainShelterOnly by remember { mutableStateOf(false) }
    var filterBookmarkedOnly by remember { mutableStateOf(false) }

    // Tọa độ tham chiếu để tính cự ly: ưu tiên GPS thực -> tâm Zone -> Cột mốc Km0 TP. Hà Giang
    val refLat = currentLocation?.first ?: activeZone?.centerLat ?: 22.827330
    val refLng = currentLocation?.second ?: activeZone?.centerLng ?: 104.984169

    val placesWithDistance = remember(
        places,
        refLat,
        refLng,
        tripConfig.vehicle,
        searchQuery,
        selectedCategoryFilter,
        filterRainShelterOnly,
        filterBookmarkedOnly
    ) {
        places
            .asSequence()
            .filter { place ->
                if (filterBookmarkedOnly && !place.isBookmarked) return@filter false
                if (filterRainShelterOnly && !place.isShelterInRain) return@filter false
                if (selectedCategoryFilter != null && place.category != selectedCategoryFilter) return@filter false
                if (searchQuery.isNotBlank()) {
                    val q = searchQuery.trim().lowercase()
                    val matchName = place.name.lowercase().contains(q)
                    val matchHint = place.addressHint.lowercase().contains(q)
                    val matchTip = place.practicalTip.lowercase().contains(q)
                    val matchCat = place.category.label.lowercase().contains(q)
                    if (!matchName && !matchHint && !matchTip && !matchCat) return@filter false
                }
                true
            }
            .map { place ->
                val distKm = LocationMath.calculateDistanceKm(refLat, refLng, place.latitude, place.longitude)
                val travelMin = LocationMath.estimateMountainTravelTimeMin(distKm, tripConfig.vehicle)
                place to (distKm to travelMin)
            }
            .sortedBy { it.second.first }
            .toList()
    }

    val topClearance = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp
    val bottomClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topClearance)
        ) {
            // 1. Airbnb-style Soft-Depth Search Pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(18.dp),
                        ambientColor = SoftShadowColor,
                        spotColor = SoftShadowColor
                    ),
                shape = RoundedCornerShape(18.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Tìm quán ăn, cây xăng, cứu hộ, homestay...",
                            color = TextSubtle,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = ElectricOrange,
                            modifier = Modifier.size(19.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceMuted)
                                    .springClickable { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Xóa",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceWhite,
                        unfocusedContainerColor = SurfaceWhite,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricOrange,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
            }

            // 2. Tactile Filter Pills Row (AllTrails style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAllSelected = selectedCategoryFilter == null && !filterRainShelterOnly && !filterBookmarkedOnly
                NearbyFilterChip(
                    label = "Tất cả (${places.size})",
                    icon = Icons.Default.Explore,
                    isSelected = isAllSelected,
                    activeBg = ElectricOrangeSoft,
                    activeBorder = ElectricOrangeBorder,
                    activeContent = ElectricOrangeHover,
                    onClick = {
                        selectedCategoryFilter = null
                        filterRainShelterOnly = false
                        filterBookmarkedOnly = false
                    }
                )

                NearbyFilterChip(
                    label = "Trú mưa",
                    icon = Icons.Default.Umbrella,
                    isSelected = filterRainShelterOnly,
                    activeBg = SkyTelemetrySoft,
                    activeBorder = SkyTelemetryBorder,
                    activeContent = SkyTelemetry,
                    onClick = {
                        filterRainShelterOnly = !filterRainShelterOnly
                        if (filterRainShelterOnly) selectedCategoryFilter = null
                    }
                )

                NearbyFilterChip(
                    label = "Đã lưu",
                    icon = Icons.Filled.Bookmark,
                    isSelected = filterBookmarkedOnly,
                    activeBg = AmberWarningSoft,
                    activeBorder = AmberWarningBorder,
                    activeContent = AmberWarningText,
                    onClick = {
                        filterBookmarkedOnly = !filterBookmarkedOnly
                    }
                )

                PlaceCategory.entries.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat && !filterRainShelterOnly
                    val catIcon = when (cat) {
                        PlaceCategory.FUEL -> Icons.Default.LocalGasStation
                        PlaceCategory.MECHANIC_SOS -> Icons.Default.Build
                        PlaceCategory.CHECKIN_VIEW -> Icons.Default.Landscape
                        PlaceCategory.EAT -> Icons.Default.Restaurant
                        PlaceCategory.CAFE_REST -> Icons.Default.LocalCafe
                        PlaceCategory.HOMESTAY -> Icons.Default.Hotel
                    }
                    NearbyFilterChip(
                        label = cat.label,
                        icon = catIcon,
                        isSelected = isSelected,
                        activeBg = ElectricOrangeSoft,
                        activeBorder = ElectricOrangeBorder,
                        activeContent = ElectricOrangeHover,
                        onClick = {
                            selectedCategoryFilter = if (isSelected) null else cat
                            filterRainShelterOnly = false
                        }
                    )
                }
            }

            // 3. Live Reference Telemetry Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(EmeraldGreenSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (currentLocation != null) Icons.Default.MyLocation else Icons.Default.PinDrop,
                        contentDescription = null,
                        tint = EmeraldGreenText,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = if (currentLocation != null) "Cự ly tính từ GPS thực tế của bạn"
                    else "Cự ly tính từ cụm: ${activeZone?.name ?: "Hà Giang"}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ShadcnBadge(
                    text = "${placesWithDistance.size} ĐIỂM",
                    variant = ShadcnBadgeVariant.Emerald
                )
            }

            // 4. Animated Places List
            if (placesWithDistance.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SurfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Không tìm thấy địa điểm phù hợp",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hãy thử đổi từ khóa hoặc chạm vào bộ lọc 'Tất cả' ở trên nhé!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 6.dp,
                        end = 16.dp,
                        bottom = bottomClearance + 64.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(placesWithDistance, key = { it.first.id }) { (place, distanceInfo) ->
                        val (distKm, travelMin) = distanceInfo
                        NearbyPlaceCard(
                            place = place,
                            distanceKm = distKm,
                            travelMin = travelMin,
                            vehicle = tripConfig.vehicle,
                            onNavigate = {
                                LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                            },
                            onCall = {
                                LocationMath.openDialerIntent(context, place.phoneNumber)
                            },
                            onToggleBookmark = { viewModel.toggleBookmark(place.id) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(220),
                                fadeOutSpec = tween(160),
                                placementSpec = spring(
                                    dampingRatio = 0.82f,
                                    stiffness = 380f
                                )
                            )
                        )
                    }
                }
            }
        }

        // 5. Floating Pill Action Button "+ Thêm điểm" (sits cleanly above FloatingPillNavBar)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomClearance - 4.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = SoftShadowColor,
                    spotColor = SoftShadowColor
                )
                .springClickable { viewModel.openAddPlaceDialog(activeZone?.id) },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AddLocationAlt,
                    contentDescription = "Thêm địa điểm",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thêm điểm",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun NearbyFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    activeBg: Color,
    activeBorder: Color,
    activeContent: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeBg else SurfaceWhite,
        animationSpec = tween(180),
        label = "FilterChipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) activeBorder else BorderSubtle,
        animationSpec = tween(180),
        label = "FilterChipBorder"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) activeContent else TextSecondary,
        animationSpec = tween(180),
        label = "FilterChipContent"
    )

    Surface(
        modifier = Modifier
            .shadow(
                elevation = if (isSelected) 3.dp else 1.dp,
                shape = CircleShape,
                ambientColor = SoftShadowColor,
                spotColor = SoftShadowColor
            )
            .springClickable(onClick = onClick),
        shape = CircleShape,
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = contentColor,
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun NearbyPlaceCard(
    place: Place,
    distanceKm: Double,
    travelMin: Int,
    vehicle: VehicleType,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconTint, iconBg) = when (place.category) {
        PlaceCategory.FUEL -> Triple(Icons.Default.LocalGasStation, AmberWarningText, AmberWarningSoft)
        PlaceCategory.MECHANIC_SOS -> Triple(Icons.Default.Build, RoseDestructiveText, RoseDestructiveSoft)
        PlaceCategory.CHECKIN_VIEW -> Triple(Icons.Default.Landscape, EmeraldGreenText, EmeraldGreenSoft)
        PlaceCategory.EAT -> Triple(Icons.Default.Restaurant, ElectricOrangeHover, ElectricOrangeSoft)
        PlaceCategory.CAFE_REST -> Triple(Icons.Default.LocalCafe, AmberWarningText, AmberWarningSoft)
        PlaceCategory.HOMESTAY -> Triple(Icons.Default.Hotel, SkyTelemetry, SkyTelemetrySoft)
    }

    val bookmarkScale by animateFloatAsState(
        targetValue = if (place.isBookmarked) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f),
        label = "NearbyBookmarkBounce"
    )

    ShadcnCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        backgroundColor = SurfaceWhite,
        borderColor = when (place.category) {
            PlaceCategory.MECHANIC_SOS -> RoseDestructiveBorder
            PlaceCategory.FUEL -> AmberWarningBorder
            else -> BorderSubtle
        }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Soft-tinted Category Tile
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (place.isShelterInRain) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Umbrella,
                                contentDescription = "Trú mưa tốt",
                                tint = SkyTelemetry,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShadcnBadge(
                            text = "${String.format(Locale.US, "%.1f", distanceKm)} km",
                            variant = ShadcnBadgeVariant.Emerald
                        )
                        Text(
                            text = "~${travelMin}p (${if (vehicle == VehicleType.MOTORBIKE) "xe máy" else "ô tô"})",
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

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (place.isBookmarked) ElectricOrangeSoft else SurfaceMuted)
                        .springClickable(onClick = onToggleBookmark),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) ElectricOrange else TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer {
                                scaleX = bookmarkScale
                                scaleY = bookmarkScale
                            }
                    )
                }
            }

            if (place.addressHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📍 ${place.addressHint}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = TextPrimary
                )
            }

            if (place.practicalTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Dẫn đường", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }

                if (place.phoneNumber.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = EmeraldGreenText
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Gọi điện", fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}
