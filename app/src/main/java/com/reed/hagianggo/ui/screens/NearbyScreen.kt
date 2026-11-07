package com.reed.hagianggo.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
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

    // Tọa độ tham chiếu để tính cự ly: ưu tiên GPS thực, fallback về tâm Zone hiện tại, fallback về Cột mốc Km0 TP. Hà Giang
    val refLat = currentLocation?.first ?: activeZone?.centerLat ?: 22.827330
    val refLng = currentLocation?.second ?: activeZone?.centerLng ?: 104.984169

    // Danh sách kèm cự ly tính toán
    val placesWithDistance = remember(places, refLat, refLng, tripConfig.vehicle, searchQuery, selectedCategoryFilter, filterRainShelterOnly) {
        places
            .asSequence()
            .filter { place ->
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
            .sortedBy { it.second.first } // Gần nhất xếp trước
            .toList()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddPlaceDialog(activeZone?.id) },
                containerColor = ElectricOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm địa điểm")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Input phong cách shadcn light (nền trắng, viền mảnh 1px)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Tìm quán ăn, cây xăng, cứu hộ xe, homestay...", color = TextSubtle, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = ElectricOrange,
                    unfocusedBorderColor = BorderSubtle
                )
            )

            // Category Filter Chips (Horizontal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isAllSelected = selectedCategoryFilter == null && !filterRainShelterOnly
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAllSelected) ElectricOrangeSoft else SurfaceWhite)
                        .border(BorderStroke(1.dp, if (isAllSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                        .clickable {
                            selectedCategoryFilter = null
                            filterRainShelterOnly = false
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Tất cả (${places.size})",
                        color = if (isAllSelected) ElectricOrangeHover else TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Trú mưa chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (filterRainShelterOnly) SkyTelemetrySoft else SurfaceWhite)
                        .border(BorderStroke(1.dp, if (filterRainShelterOnly) SkyTelemetryBorder else BorderSubtle), RoundedCornerShape(6.dp))
                        .clickable {
                            filterRainShelterOnly = !filterRainShelterOnly
                            if (filterRainShelterOnly) selectedCategoryFilter = null
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Umbrella,
                            contentDescription = null,
                            tint = if (filterRainShelterOnly) SkyTelemetry else TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Trú mưa",
                            color = if (filterRainShelterOnly) SkyTelemetry else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                PlaceCategory.entries.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat && !filterRainShelterOnly
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) ElectricOrangeSoft else SurfaceWhite)
                            .border(BorderStroke(1.dp, if (isSelected) ElectricOrangeBorder else BorderSubtle), RoundedCornerShape(6.dp))
                            .clickable {
                                selectedCategoryFilter = if (isSelected) null else cat
                                filterRainShelterOnly = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = cat.label,
                            color = if (isSelected) ElectricOrangeHover else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Reference Info Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (currentLocation != null) Icons.Default.MyLocation else Icons.Default.PinDrop,
                    contentDescription = null,
                    tint = EmeraldGreenText,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (currentLocation != null) "Khoảng cách theo GPS thực tế"
                    else "Tính từ cụm: ${activeZone?.name ?: "Hà Giang"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.weight(1f))
                ShadcnBadge(
                    text = "${placesWithDistance.size} ĐIỂM",
                    variant = ShadcnBadgeVariant.Secondary
                )
            }

            // Places List
            if (placesWithDistance.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Không tìm thấy địa điểm phù hợp.\nHãy thử đổi từ khóa hoặc bộ lọc!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            onToggleBookmark = { viewModel.toggleBookmark(place.id) }
                        )
                    }
                }
            }
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
    onToggleBookmark: () -> Unit
) {
    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        backgroundColor = SurfaceWhite,
        borderColor = if (place.category == PlaceCategory.MECHANIC_SOS) RoseDestructiveBorder
        else if (place.category == PlaceCategory.FUEL) AmberWarningBorder
        else BorderSubtle
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Badge in 1px border box
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceMuted)
                        .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (place.category) {
                        PlaceCategory.FUEL -> Icons.Default.LocalGasStation
                        PlaceCategory.MECHANIC_SOS -> Icons.Default.Build
                        PlaceCategory.CHECKIN_VIEW -> Icons.Default.Landscape
                        PlaceCategory.EAT -> Icons.Default.Restaurant
                        PlaceCategory.CAFE_REST -> Icons.Default.LocalCafe
                        PlaceCategory.HOMESTAY -> Icons.Default.Hotel
                    }
                    val iconTint = when (place.category) {
                        PlaceCategory.FUEL -> AmberWarningText
                        PlaceCategory.MECHANIC_SOS -> RoseDestructiveText
                        PlaceCategory.CHECKIN_VIEW -> EmeraldGreenText
                        PlaceCategory.EAT -> ElectricOrangeHover
                        PlaceCategory.CAFE_REST -> AmberWarningText
                        PlaceCategory.HOMESTAY -> SkyTelemetry
                    }
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (place.isShelterInRain) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Umbrella,
                                contentDescription = "Trú mưa tốt",
                                tint = SkyTelemetry,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Distance badge pill (shadcn pastel style)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShadcnBadge(
                            text = "${String.format(java.util.Locale.US, "%.1f", distanceKm)} km",
                            variant = ShadcnBadgeVariant.Emerald
                        )
                        Text(
                            text = "~${travelMin} phút (${if (vehicle == VehicleType.MOTORBIKE) "xe máy" else "ô tô"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) ElectricOrange else TextSubtle,
                        modifier = Modifier.size(18.dp)
                    )
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

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dẫn đường", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }

                if (place.phoneNumber.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.weight(1f).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp), tint = EmeraldGreenText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gọi điện", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
