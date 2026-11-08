package com.reed.hagianggo.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MountainSosSheet(
    currentLat: Double?,
    currentLng: Double?,
    isGpsLive: Boolean,
    currentAltitudeM: Int,
    activeZone: Zone?,
    places: List<Place>,
    emergencyPhone: String,
    onSaveEmergencyPhone: (String) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var phoneInput by remember(emergencyPhone) { mutableStateOf(emergencyPhone) }

    val refLat = currentLat ?: activeZone?.centerLat ?: 22.827330
    val refLng = currentLng ?: activeZone?.centerLng ?: 104.984169
    val latStr = String.format(Locale.US, "%.6f", refLat)
    val lngStr = String.format(Locale.US, "%.6f", refLng)
    val zoneTitle = activeZone?.name ?: "Hà Giang Loop"

    val nearestMechanics = remember(places, refLat, refLng) {
        places.filter { it.category == PlaceCategory.MECHANIC_SOS }
            .map { p -> p to (LocationMath.calculateDistanceKm(refLat, refLng, p.lat, p.lng) * 1.45) }
            .sortedBy { it.second }
            .take(3)
    }

    val nearestFuel = remember(places, refLat, refLng) {
        places.filter { it.category == PlaceCategory.FUEL }
            .map { p -> p to (LocationMath.calculateDistanceKm(refLat, refLng, p.lat, p.lng) * 1.45) }
            .minByOrNull { it.second }
    }

    val smsMessage = remember(latStr, lngStr, zoneTitle, currentAltitudeM, isGpsLive) {
        val gpsTag = if (isGpsLive) "Toa do GPS chinh xac" else "Vi tri uoc tinh gan"
        "SOS KHAN CAP HA GIANG! Toi can ho tro tai $zoneTitle (~${currentAltitudeM}m). $gpsTag: $latStr, $lngStr . Xem ban do: https://maps.google.com/?q=$latStr,$lngStr"
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Khẩn cấp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(RoseDestructiveSoft)
                            .border(BorderStroke(1.dp, RoseDestructiveBorder), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Sos,
                            contentDescription = null,
                            tint = RoseDestructiveText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CỨU HỘ ĐÈO NÚI KHẨN CẤP",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = RoseDestructiveText
                        )
                        Text(
                            text = "Hoạt động Offline 100% • Tín hiệu SMS sóng 2G",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted)
                }
            }

            // 1. Thẻ Tọa độ Vệ tinh trực tiếp
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = if (isGpsLive) EmeraldGreenBorder else AmberWarningBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShadcnBadge(
                            text = if (isGpsLive) "● TỌA ĐỘ VỆ TINH TRỰC TIẾP" else "⚠ TỌA ĐỘ TÂM CHẶNG (BẬT GPS ĐỂ CHÍNH XÁC)",
                            variant = if (isGpsLive) ShadcnBadgeVariant.Emerald else ShadcnBadgeVariant.Amber
                        )
                        Text(
                            text = "▲ ${currentAltitudeM}m",
                            style = TelemetryTextStyle.copy(fontSize = 13.sp),
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$latStr, $lngStr",
                        style = TelemetryTextStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                    Text(
                        text = "Khu vực: $zoneTitle",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShadcnButton(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SOS_GPS", "$latStr, $lngStr"))
                                onNotify("Đã sao chép tọa độ cứu hộ: $latStr, $lngStr")
                            },
                            variant = ShadcnButtonVariant.Secondary,
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Sao chép tọa độ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        ShadcnButton(
                            onClick = {
                                LocationMath.openNavigationIntent(context, refLat, refLng, "Vị trí của tôi")
                            },
                            variant = ShadcnButtonVariant.Secondary,
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, tint = SkyTelemetry, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Mở Google Maps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // 2. Phát tín hiệu SMS Cứu hộ 2G (Không cần mạng 4G)
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = RoseDestructiveSoft.copy(alpha = 0.55f),
                borderColor = RoseDestructiveBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PHÁT TÍN HIỆU SMS CỨU HỘ QUA SÓNG 2G",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = RoseDestructiveText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Khi rớt 4G giữa đèo, tin nhắn văn bản SMS thường vẫn gửi đi được chỉ với 1 vạch sóng 2G yếu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it.filter { ch -> ch.isDigit() || ch == '+' }
                            onSaveEmergencyPhone(phoneInput)
                        },
                        label = { Text("SĐT người thân / trưởng đoàn nhận SOS", color = TextSecondary, fontSize = 12.sp) },
                        placeholder = { Text("VD: 0912345678 (Bỏ trống để chọn khi gửi)", color = TextSubtle, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = RoseDestructive,
                            unfocusedBorderColor = BorderMedium
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ShadcnButton(
                        onClick = {
                            onSaveEmergencyPhone(phoneInput)
                            LocationMath.openSmsBeaconIntent(context, phoneInput, smsMessage)
                        },
                        variant = ShadcnButtonVariant.Destructive,
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gửi SMS Tọa độ Khẩn cấp Ngay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // 3. Tiệm sửa xe & Cứu hộ gần nhất
            Text(
                text = "SỬA XE & CỨU HỘ GẦN BẠN NHẤT",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted
            )

            nearestMechanics.forEach { (place, roadKm) ->
                SosRescueRowCard(
                    place = place,
                    roadDistanceKm = roadKm,
                    badgeLabel = "CỨU HỘ XE",
                    badgeVariant = ShadcnBadgeVariant.Rose,
                    onCall = { LocationMath.openDialerIntent(context, place.phone) },
                    onNavigate = { LocationMath.openNavigationIntent(context, place.lat, place.lng, place.name) }
                )
            }

            nearestFuel?.let { (fuelPlace, roadKm) ->
                SosRescueRowCard(
                    place = fuelPlace,
                    roadDistanceKm = roadKm,
                    badgeLabel = "CÂY XĂNG GẦN NHẤT",
                    badgeVariant = ShadcnBadgeVariant.Amber,
                    onCall = { LocationMath.openDialerIntent(context, fuelPlace.phone) },
                    onNavigate = { LocationMath.openNavigationIntent(context, fuelPlace.lat, fuelPlace.lng, fuelPlace.name) }
                )
            }

            // 4. Đường dây nóng khẩn cấp quốc gia
            Text(
                text = "ĐƯỜNG DÂY KHẨN CẤP QUỐC GIA",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = TextMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "113" to "Công an",
                    "114" to "Cứu nạn",
                    "115" to "Cấp cứu"
                ).forEach { (num, label) ->
                    ShadcnCard(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = SurfaceWhite,
                        borderColor = BorderSubtle,
                        onClick = { LocationMath.openDialerIntent(context, num) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "📞 $num",
                                style = TelemetryTextStyle.copy(fontSize = 15.sp),
                                color = RoseDestructiveText
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SosRescueRowCard(
    place: Place,
    roadDistanceKm: Double,
    badgeLabel: String,
    badgeVariant: ShadcnBadgeVariant,
    onCall: () -> Unit,
    onNavigate: () -> Unit
) {
    ShadcnCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        backgroundColor = SurfaceWhite,
        borderColor = BorderSubtle
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShadcnBadge(text = badgeLabel, variant = badgeVariant)
                Text(
                    text = "Cách ~${String.format(Locale.US, "%.1f", roadDistanceKm)} km đường đèo",
                    style = TelemetryTextStyle.copy(fontSize = 11.5.sp),
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = place.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            if (place.addressHint.isNotBlank()) {
                Text(
                    text = "📍 ${place.addressHint}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (place.phone.isNotBlank()) {
                    ShadcnButton(
                        onClick = onCall,
                        variant = ShadcnButtonVariant.Primary,
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Gọi ${place.phone}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
                ShadcnButton(
                    onClick = onNavigate,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(1f).height(36.dp)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Dẫn đường", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
