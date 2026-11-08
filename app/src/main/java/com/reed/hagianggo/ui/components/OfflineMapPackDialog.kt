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
    onPreviewOfflineLayer: (() -> Unit)? = null,
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
                    text = "Dựng & lưu vĩnh viễn gói Bản đồ Địa hình Offline Hà Giang (Zoom 8–15, gồm 5.350 điểm uốn đèo QL4C/ĐT176/QL34, sông Nho Quế/Miện/Lô & đường đồng mức) trực tiếp vào filesDir. Không phụ thuộc máy chủ ngoài, không bao giờ bị chặn 403, đồng thời giữ lại vĩnh viễn mọi vùng bản đồ chuẩn bạn đã xem.",
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
                                else if (status.isDownloading) "ĐANG DỰNG VÀO MÁY..."
                                else "CHƯA LƯU TRỌN GÓI",
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
                        title = "Lưu ý bộ nhớ",
                        description = status.errorMessage,
                        variant = ShadcnBadgeVariant.Amber,
                        icon = Icons.Default.WifiOff
                    )
                }

                if (status.isReadyOffline && onPreviewOfflineLayer != null) {
                    ShadcnButton(
                        onClick = onPreviewOfflineLayer,
                        variant = ShadcnButtonVariant.Secondary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Xem ngay Lớp Bản đồ Địa hình Offline", color = EmeraldGreenText, fontWeight = FontWeight.Bold)
                    }
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
                        text = if (status.isReadyOffline) "Kiểm tra mảnh thiếu" else "Lưu trọn bộ Hà Giang",
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
