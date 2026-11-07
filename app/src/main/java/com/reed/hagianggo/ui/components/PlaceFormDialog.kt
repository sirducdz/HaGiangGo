package com.reed.hagianggo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceFormDialog(
    initialPlace: Place?,
    zones: List<Zone>,
    defaultZoneId: String,
    onSave: (Place) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val defaultZone = zones.firstOrNull { it.id == (initialPlace?.zoneId ?: defaultZoneId) } ?: zones.firstOrNull()
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var zoneId by remember { mutableStateOf(initialPlace?.zoneId ?: defaultZone?.id ?: defaultZoneId) }
    var category by remember { mutableStateOf(initialPlace?.category ?: PlaceCategory.CHECKIN_VIEW) }
    var latText by remember {
        mutableStateOf(
            initialPlace?.let { String.format(Locale.US, "%.6f", it.lat) }
                ?: String.format(Locale.US, "%.6f", defaultZone?.centerLat ?: 22.827330)
        )
    }
    var lngText by remember {
        mutableStateOf(
            initialPlace?.let { String.format(Locale.US, "%.6f", it.lng) }
                ?: String.format(Locale.US, "%.6f", defaultZone?.centerLng ?: 104.984169)
        )
    }
    var hasCustomCoords by remember { mutableStateOf(initialPlace != null) }
    var phone by remember { mutableStateOf(initialPlace?.phone ?: "") }
    var addressHint by remember { mutableStateOf(initialPlace?.addressHint ?: "") }
    var practicalTip by remember { mutableStateOf(initialPlace?.practicalTip ?: "") }
    var carFriendly by remember { mutableStateOf(initialPlace?.carFriendly ?: true) }

    var zoneExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = SurfaceWhite,
        unfocusedContainerColor = SurfaceWhite,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        focusedBorderColor = ElectricOrange,
        unfocusedBorderColor = BorderMedium,
        focusedLabelColor = ElectricOrange,
        unfocusedLabelColor = TextSecondary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = {
            Text(
                text = if (initialPlace == null) "Thêm địa điểm mới" else "Chỉnh sửa địa điểm",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tên địa điểm
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên địa điểm (*)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Cụm chặng
                ExposedDropdownMenuBox(
                    expanded = zoneExpanded,
                    onExpandedChange = { zoneExpanded = !zoneExpanded }
                ) {
                    val currentZoneName = zones.firstOrNull { it.id == zoneId }?.name ?: "Chọn chặng"
                    OutlinedTextField(
                        value = currentZoneName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Thuộc chặng Hà Giang") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = zoneExpanded) },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = zoneExpanded,
                        onDismissRequest = { zoneExpanded = false }
                    ) {
                        zones.sortedBy { it.seq }.forEach { z ->
                            DropdownMenuItem(
                                text = { Text("${z.seq}. ${z.name}") },
                                onClick = {
                                    zoneId = z.id
                                    if (!hasCustomCoords) {
                                        latText = String.format(Locale.US, "%.6f", z.centerLat)
                                        lngText = String.format(Locale.US, "%.6f", z.centerLng)
                                    }
                                    zoneExpanded = false
                                }
                            )
                        }
                    }
                }

                // Danh mục
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category.title,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Phân loại") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = textFieldColors,
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        PlaceCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.title) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                // Tọa độ Lat - Lng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = latText,
                        onValueChange = { input ->
                            hasCustomCoords = true
                            if (input.contains(",")) {
                                val parts = input.split(",")
                                latText = parts.getOrNull(0)?.trim() ?: ""
                                lngText = parts.getOrNull(1)?.trim() ?: ""
                            } else {
                                latText = input
                            }
                        },
                        label = { Text("Vĩ độ (Lat)") },
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lngText,
                        onValueChange = {
                            hasCustomCoords = true
                            lngText = it
                        },
                        label = { Text("Kinh độ (Lng)") },
                        colors = textFieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Số điện thoại & Địa chỉ gợi ý
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Số điện thoại liên hệ (nếu có)") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = addressHint,
                    onValueChange = { addressHint = it },
                    label = { Text("Ghi chú vị trí / mốc km") },
                    singleLine = true,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Mẹo sống còn 1 dòng
                OutlinedTextField(
                    value = practicalTip,
                    onValueChange = { practicalTip = it },
                    label = { Text("Mẹo sống còn 1 dòng (Tip)") },
                    minLines = 2,
                    maxLines = 3,
                    colors = textFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                // Checkbox ô tô đi được
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = carFriendly,
                        onCheckedChange = { carFriendly = it },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricOrange, uncheckedColor = BorderMedium)
                    )
                    Text(text = "Đường đi ô tô vào được", fontSize = 13.sp, color = TextPrimary)
                }
            }
        },
        confirmButton = {
            ShadcnButton(
                onClick = {
                    val selectedZone = zones.firstOrNull { it.id == zoneId }
                    val latVal = latText.toDoubleOrNull() ?: selectedZone?.centerLat ?: 22.827330
                    val lngVal = lngText.toDoubleOrNull() ?: selectedZone?.centerLng ?: 104.984169
                    val placeId = initialPlace?.id ?: "custom_${UUID.randomUUID().toString().take(8)}"
                    onSave(
                        Place(
                            id = placeId,
                            name = name.ifBlank { "Địa điểm mới" },
                            zoneId = zoneId,
                            category = category,
                            lat = latVal,
                            lng = lngVal,
                            phone = phone.trim(),
                            addressHint = addressHint.trim(),
                            practicalTip = practicalTip.trim(),
                            carFriendly = carFriendly,
                            isBookmarked = initialPlace?.isBookmarked ?: true,
                            isVisited = initialPlace?.isVisited ?: false
                        )
                    )
                },
                variant = ShadcnButtonVariant.Primary
            ) {
                Text("LƯU ĐỊA ĐIỂM")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (initialPlace != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initialPlace.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = RoseDestructive)
                    }
                }
                ShadcnButton(onClick = onDismiss, variant = ShadcnButtonVariant.Ghost) {
                    Text("HỦY", color = TextSecondary)
                }
            }
        }
    )
}
