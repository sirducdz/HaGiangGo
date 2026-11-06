package com.reed.hagianggo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.MainViewModel
import com.reed.hagianggo.ui.components.HaGiangLoopMap
import com.reed.hagianggo.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zones by viewModel.zones.collectAsState()
    val places by viewModel.places.collectAsState()
    val config by viewModel.tripConfig.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val activeZone by viewModel.activeZone.collectAsState()

    var selectedZoneId by remember { mutableStateOf<String?>(null) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    val totalSpent = remember(expenses) { expenses.sumOf { it.amount } }
    val currencyFormatter = remember { NumberFormat.getNumberInstance(Locale("vi", "VN")) }

    // Lọc places theo zone đang chọn (hoặc tất cả nếu null)
    val displayedPlaces = remember(places, selectedZoneId) {
        if (selectedZoneId == null) places
        else places.filter { it.zoneId == selectedZoneId }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddPlaceDialog(selectedZoneId) },
                containerColor = SunsetOrange,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Thêm địa điểm")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // 1. Interactive Loop Map
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BẢN ĐỒ VÒNG LẶP HÀ GIANG",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                color = SunsetOrange
                            )
                            if (selectedZoneId != null) {
                                TextButton(
                                    onClick = { selectedZoneId = null },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Xem tất cả", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HaGiangLoopMap(
                            zones = zones,
                            places = places,
                            activeZoneId = selectedZoneId,
                            onZoneSelected = { zone ->
                                selectedZoneId = if (selectedZoneId == zone.id) null else zone.id
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 2. Trip Config Overview
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "KẾ HOẠCH & PHƯƠNG TIỆN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Số ngày đi
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(3 to "3N2Đ", 4 to "4N3Đ").forEach { (days, label) ->
                                val isSelected = config.durationDays == days
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateTripConfig(config.withDurationDays(days)) },
                                    label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SunsetOrange,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Phương tiện
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(4.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.updateTripConfig(config.copy(vehicle = VehicleType.MOTORBIKE)) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (config.vehicle == VehicleType.MOTORBIKE) SunsetOrange else Color.Transparent)
                                ) {
                                    Icon(
                                        Icons.Default.TwoWheeler,
                                        contentDescription = "Xe máy",
                                        tint = if (config.vehicle == VehicleType.MOTORBIKE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.updateTripConfig(config.copy(vehicle = VehicleType.CAR)) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (config.vehicle == VehicleType.CAR) SunsetOrange else Color.Transparent)
                                ) {
                                    Icon(
                                        Icons.Default.DirectionsCar,
                                        contentDescription = "Ô tô",
                                        tint = if (config.vehicle == VehicleType.CAR) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Ngân sách dự kiến vs thực tế
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBudgetDialog = true },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Đã chi tiêu: ${currencyFormatter.format(totalSpent)} đ",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (totalSpent > config.totalBudget && config.totalBudget > 0) AlertRed else HighlandGreen
                                )
                                Text(
                                    text = "Ngân sách: ${currencyFormatter.format(config.totalBudget)} đ (Chạm để sửa)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            val progress = if (config.totalBudget > 0) (totalSpent.toFloat() / config.totalBudget).coerceIn(0f, 1f) else 0f
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(36.dp),
                                color = if (progress >= 1f) AlertRed else SunsetOrange,
                                strokeWidth = 4.dp
                            )
                        }
                    }
                }
            }

            // 3. Zone list chips
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedZoneId == null) "TẤT CẢ ĐỊA ĐIỂM (${displayedPlaces.size})"
                        else "ĐỊA ĐIỂM TẠI ${zones.firstOrNull { it.id == selectedZoneId }?.name?.uppercase()} (${displayedPlaces.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // 4. Place items
            if (displayedPlaces.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có địa điểm nào trong cụm này.\nBấm + để thêm địa điểm mới!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                items(displayedPlaces, key = { it.id }) { place ->
                    PlaceRowCard(
                        place = place,
                        onNavigate = {
                            LocationMath.openNavigationIntent(context, place.latitude, place.longitude, place.name)
                        },
                        onCall = {
                            LocationMath.openDialerIntent(context, place.phoneNumber)
                        },
                        onToggleBookmark = { viewModel.toggleBookmark(place.id) },
                        onToggleVisited = { viewModel.toggleVisited(place.id) },
                        onEdit = { viewModel.openEditPlaceDialog(place) },
                        onDelete = { viewModel.deletePlace(place.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showBudgetDialog) {
        var budgetInput by remember { mutableStateOf(config.totalBudget.toString()) }
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Cài đặt ngân sách chuyến đi") },
            text = {
                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { budgetInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Tổng ngân sách (VNĐ)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBudget = budgetInput.toLongOrNull() ?: config.totalBudget
                        viewModel.updateTripConfig(config.copy(totalBudget = newBudget))
                        showBudgetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange)
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun PlaceRowCard(
    place: Place,
    onNavigate: () -> Unit,
    onCall: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleVisited: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (place.isVisited) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
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
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (place.isVisited) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            ),
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = place.category.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        if (place.altitudeMeters > 0) {
                            Text(
                                text = "• ${place.altitudeMeters}m",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // Bookmark toggle
                IconButton(onClick = onToggleBookmark, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (place.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lưu",
                        tint = if (place.isBookmarked) SunsetOrange else MaterialTheme.colorScheme.outline
                    )
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { expandedMenu = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn")
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (place.isVisited) "Đánh dấu chưa đi" else "Đánh dấu đã đi") },
                            leadingIcon = {
                                Icon(
                                    if (place.isVisited) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                onToggleVisited()
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Chỉnh sửa") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                onEdit()
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa địa điểm", color = AlertRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed) },
                            onClick = {
                                onDelete()
                                expandedMenu = false
                            }
                        )
                    }
                }
            }

            if (place.practicalTip.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
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
                    OutlinedButton(
                        onClick = onCall,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp), tint = HighlandGreen)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gọi điện", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
