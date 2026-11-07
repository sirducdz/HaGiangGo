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
    var name by remember { mutableStateOf(initialPlace?.name ?: "") }
    var zoneId by remember { mutableStateOf(initialPlace?.zoneId ?: defaultZoneId) }
    var category by remember { mutableStateOf(initialPlace?.category ?: PlaceCategory.CHECKIN_VIEW) }
    var latText by remember { mutableStateOf(initialPlace?.lat?.toString() ?: "23.2795") }
    var lngText by remember { mutableStateOf(initialPlace?.lng?.toString() ?: "105.3629") }
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
                        onValueChange = { lngText = it },
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
                    val latVal = latText.toDoubleOrNull() ?: 23.2795
                    val lngVal = lngText.toDoubleOrNull() ?: 105.3629
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
