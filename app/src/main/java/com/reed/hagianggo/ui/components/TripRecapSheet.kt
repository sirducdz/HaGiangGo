package com.reed.hagianggo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.TripRecapPosterGenerator
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripRecapSheet(
    tripConfig: TripConfig,
    zones: List<Zone>,
    places: List<Place>,
    expenses: List<Expense>,
    journalCount: Int,
    rideStats: RideSummaryStats,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var hideExpenses by remember { mutableStateOf(false) }
    var posterBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(tripConfig, zones, places, expenses, journalCount, rideStats, hideExpenses) {
        val rendered = withContext(Dispatchers.Default) {
            TripRecapPosterGenerator.renderPoster(
                tripConfig = tripConfig,
                zones = zones,
                places = places,
                expenses = expenses,
                journalCount = journalCount,
                rideStats = rideStats,
                hideExpenses = hideExpenses
            )
        }
        posterBitmap = rendered
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = AppBackground,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "POSTER TỔNG KẾT HÀNH TRÌNH",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                    Text(
                        text = "Ảnh 1080×1620 sắc nét • Khoe Story Facebook, TikTok, Zalo",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted)
                }
            }

            // Khung xem trước Poster
            val bmp = posterBitmap
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1080f / 1620f)
                    .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = SoftShadowColor, spotColor = SoftShadowColor)
                    .clip(RoundedCornerShape(22.dp))
                    .background(SurfaceWhite)
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Poster Hành trình Hà Giang",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = ElectricOrange)
                }
            }

            // Nút gạt Quyền riêng tư: Ẩn số tiền chi tiêu
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                backgroundColor = SurfaceWhite,
                borderColor = BorderSubtle,
                onClick = { hideExpenses = !hideExpenses }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            if (hideExpenses) Icons.Default.VisibilityOff else Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (hideExpenses) SkyTelemetry else ElectricOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Ẩn chi phí trên ảnh khi đăng mạng xã hội",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = if (hideExpenses) "Đang thay bằng thống kê Nhật ký & Ghim" else "Đang hiển thị Tổng chi phí chuyến đi",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = hideExpenses,
                        onCheckedChange = { hideExpenses = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceWhite,
                            checkedTrackColor = ElectricOrange
                        )
                    )
                }
            }

            // Hai nút Lưu & Chia sẻ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShadcnButton(
                    onClick = {
                        val b = posterBitmap ?: return@ShadcnButton
                        val savedUri = TripRecapPosterGenerator.savePosterToGallery(context, b)
                        if (savedUri != null) {
                            onNotify("Đã lưu Poster vào Thư viện Ảnh (Pictures/HaGiangGo)!")
                        } else {
                            onNotify("Không thể lưu ảnh vào bộ nhớ máy.")
                        }
                    },
                    enabled = posterBitmap != null,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = EmeraldGreenText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu Thư viện", fontWeight = FontWeight.Bold, color = EmeraldGreenText)
                }

                ShadcnButton(
                    onClick = {
                        val b = posterBitmap ?: return@ShadcnButton
                        TripRecapPosterGenerator.sharePoster(context, b)
                    },
                    enabled = posterBitmap != null,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chia sẻ ngay", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
