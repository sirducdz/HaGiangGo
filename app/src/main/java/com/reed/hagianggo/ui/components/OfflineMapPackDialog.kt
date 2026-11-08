package com.reed.hagianggo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.OfflineMapStatus
import com.reed.hagianggo.ui.theme.*
import java.util.Locale

@Composable
fun OfflineMapPackDialog(
    status: OfflineMapStatus,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val progress = if (status.totalTiles > 0) {
        (status.downloadedTiles.toFloat() / status.totalTiles.toFloat()).coerceIn(0f, 1f)
    } else 0f

    AlertDialog(
        onDismissRequest = {
            if (!status.isDownloading) onDismiss()
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = SurfaceWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (status.isReadyOffline) EmeraldGreenSoft else ElectricOrangeSoft)
                        .border(
                            BorderStroke(1.dp, if (status.isReadyOffline) EmeraldGreenBorder else ElectricOrangeBorder),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (status.isReadyOffline) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (status.isReadyOffline) EmeraldGreenText else ElectricOrangeHover,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Bản Đồ Offline Vĩnh Viễn",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "Lưu trong filesDir • Không lo mất sóng đèo",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Tải trước toàn bộ cung đường Hà Giang Loop (TP. Hà Giang – Quản Bạ – Yên Minh – Đồng Văn – Lũng Cú – Mã Pì Lèng – Mèo Vạc – Du Già, mức phóng Zoom 9–15). Dữ liệu được giữ vĩnh viễn trong bộ nhớ riêng, không bị trình dọn rác xóa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                // Thẻ trạng thái dung lượng
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (status.isReadyOffline) EmeraldGreenSoft.copy(alpha = 0.6f) else SurfaceMuted)
                        .border(
                            BorderStroke(1.dp, if (status.isReadyOffline) EmeraldGreenBorder else BorderSubtle),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShadcnBadge(
                                text = if (status.isReadyOffline) "✓ ĐÃ SẴN SÀNG OFFLINE"
                                else if (status.isDownloading) "ĐANG TẢI VỀ MÁY..."
                                else "CHƯA TẢI TRỌN GÓI",
                                variant = if (status.isReadyOffline) ShadcnBadgeVariant.Emerald
                                else if (status.isDownloading) ShadcnBadgeVariant.Orange
                                else ShadcnBadgeVariant.Default
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f MB", status.storageSizeMb),
                                style = TelemetryTextStyle.copy(fontSize = 13.sp),
                                color = TextPrimary
                            )
                        }

                        Text(
                            text = status.statusMessage,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = TextPrimary
                        )

                        if (status.isDownloading) {
                            Spacer(modifier = Modifier.height(2.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = ElectricOrange,
                                trackColor = SurfaceWhite
                            )
                            Text(
                                text = "${(progress * 100).toInt()}% (${status.downloadedTiles}/${status.totalTiles} mảnh bản đồ)",
                                style = TelemetryTextStyle.copy(fontSize = 11.sp),
                                color = ElectricOrangeHover
                            )
                        }
                    }
                }

                if (!status.errorMessage.isNullOrBlank()) {
                    ShadcnAlert(
                        title = "Lưu ý kết nối",
                        description = status.errorMessage,
                        variant = ShadcnBadgeVariant.Amber,
                        icon = Icons.Default.WifiOff
                    )
                }
            }
        },
        confirmButton = {
            if (status.isDownloading) {
                ShadcnButton(
                    onClick = onCancelDownload,
                    variant = ShadcnButtonVariant.Destructive
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tạm dừng", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                ShadcnButton(
                    onClick = onStartDownload,
                    variant = ShadcnButtonVariant.Primary
                ) {
                    Icon(
                        if (status.isReadyOffline) Icons.Default.Refresh else Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (status.isReadyOffline) "Cập nhật mảnh thiếu" else "Tải gói Hà Giang (~24 MB)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            ShadcnButton(
                onClick = onDismiss,
                variant = ShadcnButtonVariant.Ghost
            ) {
                Text(if (status.isDownloading) "Ẩn xuống nền" else "Đóng", color = TextSecondary)
            }
        }
    )
}
