package com.reed.hagianggo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.altitudeMeters
import com.reed.hagianggo.ui.theme.*

/**
 * Hộp thoại cho phép người dùng chọn nhanh dẫn đường bằng Bản đồ trong app (Hà Giang Go)
 * hay mở ứng dụng Google Maps bên ngoài, kèm tùy chọn ghi nhớ mặc định.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationChoiceSheet(
    place: Place,
    onChooseInApp: (rememberAsDefault: Boolean) -> Unit,
    onChooseGoogleMaps: (rememberAsDefault: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rememberDefault by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Chọn bản đồ dẫn đường",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = TextPrimary
                )
                Text(
                    text = "Đích đến: ${place.name} (▲ ${place.altitudeMeters}m)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Lựa chọn 1: Dẫn đường trực tiếp trong Hà Giang Go
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = ElectricOrangeBorder,
                borderWidth = 1.5.dp,
                onClick = { onChooseInApp(rememberDefault) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ElectricOrangeSoft)
                            .border(BorderStroke(1.dp, ElectricOrangeBorder), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Navigation,
                            contentDescription = null,
                            tint = ElectricOrangeHover,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Dẫn đường trong Hà Giang Go",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            ShadcnBadge(text = "OFFLINE ĐÈO", variant = ShadcnBadgeVariant.Emerald)
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Tuyến trực tiếp trên app (OSRM + Offline 100%), bám xe xoay hướng, giữ ngữ cảnh xăng & check-in.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Lựa chọn 2: Mở Google Maps như trước
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = SkyTelemetryBorder,
                borderWidth = 1.dp,
                onClick = { onChooseGoogleMaps(rememberDefault) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(SkyTelemetrySoft)
                            .border(BorderStroke(1.dp, SkyTelemetryBorder), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Map,
                            contentDescription = null,
                            tint = SkyTelemetry,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mở trên Google Maps",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Chuyển sang ứng dụng Google Maps như trước (tiện tra hẻm ngõ nội thị hoặc quen giao diện G.Maps).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        Icons.Default.Directions,
                        contentDescription = null,
                        tint = SkyTelemetry,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Tùy chọn ghi nhớ làm mặc định
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceWhite)
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .clickable { rememberDefault = !rememberDefault }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberDefault,
                    onCheckedChange = { rememberDefault = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ElectricOrange,
                        uncheckedColor = TextMuted
                    )
                )
                Column {
                    Text(
                        text = "Ghi nhớ lựa chọn này làm mặc định",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Có thể đổi lại bất cứ lúc nào ở tab Chuyến đi",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
