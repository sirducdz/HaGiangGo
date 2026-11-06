package com.reed.hagianggo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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

    // Tọa độ tham chiếu để tính cự ly: ưu tiên GPS thực, fallback về tâm Zone hiện tại, fallback về TP. Hà Giang
    val refLat = currentLocation?.first ?: activeZone?.centerLat ?: 22.8233
    val refLng = currentLocation?.second ?: activeZone?.centerLng ?: 104.9839

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
                    val matchTip = place.practicalTip.lowercase().contains(q)
                    val matchCat = place.category.label.lowercase().contains(q)
                    if (!matchName && !matchTip && !matchCat) return@filter false
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddPlaceDialog(activeZone?.id) },
                containerColor = SunsetOrange,
                contentColor = Color.White,
                shape = CircleShape
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
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Tìm quán ăn, cây xăng, cứu hộ xe, homestay...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Xóa")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SunsetOrange,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            // Category Filter Chips (Horizontal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null && !filterRainShelterOnly,
                    onClick = {
                        selectedCategoryFilter = null
                        filterRainShelterOnly = false
                    },
                    label = { Text("Tất cả (${places.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SunsetOrange,
                        selectedLabelColor = Color.White
                    )
                )

                // Trú mưa
                FilterChip(
                    selected = filterRainShelterOnly,
                    onClick = {
                        filterRainShelterOnly = !filterRainShelterOnly
                        if (filterRainShelterOnly) selectedCategoryFilter = null
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Umbrella, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("Trú mưa") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SlateBlue,
                        selectedLabelColor = Color.White
                    )
                )

                PlaceCategory.entries.forEach { cat ->
                    val isSelected = selectedCategoryFilter == cat && !filterRainShelterOnly
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedCategoryFilter = if (isSelected) null else cat
                            filterRainShelterOnly = false
                        },
                        label = { Text(cat.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (cat) {
                                PlaceCategory.FUEL -> FuelGold
                                PlaceCategory.MECHANIC_SOS -> AlertRed
                                PlaceCategory.EAT -> SunsetOrange
                                PlaceCategory.CAFE_REST -> AmberGlow
                                PlaceCategory.HOMESTAY -> SlateBlue
                                PlaceCategory.CHECKIN_VIEW -> HighlandGreen
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Reference Info Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (currentLocation != null) Icons.Default.MyLocation else Icons.Default.PinDrop,
                    contentDescription = null,
                    tint = HighlandGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (currentLocation != null) "Khoảng cách tính theo GPS hiện tại"
                    else "Khoảng cách tính từ tâm cụm: ${activeZone?.name ?: "Hà Giang"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${placesWithDistance.size} địa điểm",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SunsetOrange
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
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (place.category == PlaceCategory.MECHANIC_SOS) AlertRed.copy(alpha = 0.08f)
            else if (place.category == PlaceCategory.FUEL) FuelGold.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (place.category) {
                                PlaceCategory.FUEL -> FuelGold.copy(alpha = 0.2f)
                                PlaceCategory.MECHANIC_SOS -> AlertRed.copy(alpha = 0.2f)
                                PlaceCategory.CHECKIN_VIEW -> HighlandGreen.copy(alpha = 0.2f)
                                PlaceCategory.EAT -> SunsetOrange.copy(alpha = 0.2f)
                                PlaceCategory.CAFE_REST -> AmberGlow.copy(alpha = 0.2f)
                                PlaceCategory.HOMESTAY -> SlateBlue.copy(alpha = 0.2f)
                            }
                        ),
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
                        PlaceCategory.FUEL -> FuelGold
                        PlaceCategory.MECHANIC_SOS -> AlertRed
                        PlaceCategory.CHECKIN_VIEW -> HighlandGreen
                        PlaceCategory.EAT -> SunsetOrange
                        PlaceCategory.CAFE_REST -> AmberGlow
                        PlaceCategory.HOMESTAY -> SlateBlue
                    }
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (place.isShelterInRain) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Umbrella,
                                contentDescription = "Trú mưa tốt",
                                tint = SlateBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Khoảng cách đường đèo & thời gian chạy ước tính
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Cách ~${String.format(java.util.Locale.US, "%.1f", distanceKm)} km",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = SunsetOrange
                        )
                        Text(
                            text = "• ~${travelMin} phút (${if (vehicle == VehicleType.MOTORBIKE) "xe máy đèo" else "ô tô"})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) SunsetOrange else MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (place.practicalTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "💡 ${place.practicalTip}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f).height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dẫn đường", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (place.phoneNumber.isNotBlank()) {
                    Button(
                        onClick = onCall,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HighlandGreen)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gọi ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
