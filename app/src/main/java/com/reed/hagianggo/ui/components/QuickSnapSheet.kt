package com.reed.hagianggo.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.reed.hagianggo.domain.PhotoWatermarkEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSnapSheet(
    request: QuickSnapRequest,
    zones: List<Zone>,
    currentLat: Double?,
    currentLng: Double?,
    liveAltitudeM: Int,
    onSaveToJournal: (placeId: String?, dayNumber: Int, zoneId: String, content: String, photoPath: String) -> Unit,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val zonesById = remember(zones) { zones.associateBy { it.id } }
    val resolvedZone = request.place?.zoneId?.let { zonesById[it] } ?: zonesById[request.zoneId] ?: zones.firstOrNull()
    val resolvedPlaceName = request.place?.name ?: resolvedZone?.name ?: "Hà Giang Loop"
    val resolvedAlt = when {
        liveAltitudeM > 30 -> liveAltitudeM
        request.place != null -> request.place.altitudeMeters
        resolvedZone != null -> resolvedZone.elevationMeters
        else -> 120
    }
    val resolvedLat = request.place?.lat ?: currentLat ?: resolvedZone?.centerLat ?: 22.82733
    val resolvedLng = request.place?.lng ?: currentLng ?: resolvedZone?.centerLng ?: 104.98417

    var rawPhotoPath by remember { mutableStateOf<String?>(null) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf(request.defaultTitle) }
    var debouncedCaption by remember { mutableStateOf(request.defaultTitle) }
    var watermarkStyle by remember { mutableStateOf(WatermarkStyle.FULL_TELEMETRY) }
    var saveCopyToGallery by remember { mutableStateOf(true) }
    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDecodingInitial by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null && File(path).length() > 0L) {
            rawPhotoPath = path
        } else {
            onNotify("Chưa chụp được ảnh mới.")
        }
    }

    fun launchCameraNow() {
        try {
            val tempFile = PhotoWatermarkEngine.createTempRawPhotoFile(context)
            pendingCameraPath = tempFile.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            onNotify("Thiết bị không mở được Camera.")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraNow()
        } else {
            onNotify("Cần cấp quyền Camera để chụp ảnh trực tiếp!")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val copied = PhotoWatermarkEngine.copyUriToTempFile(context, uri)
                withContext(Dispatchers.Main) {
                    if (copied != null) {
                        rawPhotoPath = copied.absolutePath
                    } else {
                        onNotify("Không đọc được ảnh từ thư viện.")
                    }
                }
            }
        }
    }

    val snapTimestampMillis = remember(rawPhotoPath) { System.currentTimeMillis() }

    LaunchedEffect(caption) {
        if (debouncedCaption != caption) {
            delay(250L)
            debouncedCaption = caption
        }
    }

    val previewMeta = remember(
        resolvedPlaceName,
        resolvedZone,
        request.dayNumber,
        resolvedAlt,
        resolvedLat,
        resolvedLng,
        debouncedCaption,
        snapTimestampMillis,
        watermarkStyle
    ) {
        PhotoWatermarkEngine.WatermarkMetadata(
            placeName = resolvedPlaceName,
            zoneName = resolvedZone?.name ?: "Hà Giang",
            dayNumber = request.dayNumber,
            altitudeMeters = resolvedAlt,
            lat = resolvedLat,
            lng = resolvedLng,
            caption = debouncedCaption,
            timestampMillis = snapTimestampMillis,
            style = watermarkStyle
        )
    }

    LaunchedEffect(rawPhotoPath) {
        val src = rawPhotoPath
        if (src == null) {
            baseBitmap = null
            previewBitmap = null
            isDecodingInitial = false
            return@LaunchedEffect
        }
        isDecodingInitial = true
        previewBitmap = null
        baseBitmap = null
        val (decoded, initialPreview) = withContext(Dispatchers.IO) {
            val base = PhotoWatermarkEngine.decodeBaseBitmap(src)
            val first = base?.let { PhotoWatermarkEngine.renderWatermarkOnBitmap(it, previewMeta) }
            base to first
        }
        baseBitmap = decoded
        previewBitmap = initialPreview
        isDecodingInitial = false
    }

    LaunchedEffect(baseBitmap, previewMeta) {
        val base = baseBitmap ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            PhotoWatermarkEngine.renderWatermarkOnBitmap(base, previewMeta)
        }
        if (baseBitmap === base) {
            previewBitmap = rendered
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = AppBackground,
        contentColor = TextPrimary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = BorderMedium) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElectricOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CHỤP NHANH GẮN WATERMARK",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = ElectricOrangeHover
                        )
                        Text(
                            text = resolvedPlaceName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                ShadcnBadge(text = "▲ ${resolvedAlt}m", variant = ShadcnBadgeVariant.Emerald)
            }

            // Thông số gắn tem tự động
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceWhite)
                    .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ngày ${request.dayNumber} • ${resolvedZone?.name ?: "Hà Giang"}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary
                )
                Text(
                    text = String.format(Locale.US, "%.4f°N, %.4f°E", resolvedLat, resolvedLng),
                    style = TelemetryTextStyle.copy(fontSize = 11.sp),
                    color = SkyTelemetry
                )
            }

            // Nút chụp ảnh mới / chọn từ máy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = {
                        val hasCamera = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCamera) {
                            launchCameraNow()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chụp ảnh mới", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }

                ShadcnButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chọn ảnh có sẵn", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Khung xem trước Watermark
            ShadcnCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                backgroundColor = SurfaceWhite,
                borderColor = BorderSubtle
            ) {
                val bmp = previewBitmap
                val previewBoxModifier = if (bmp != null || isDecodingInitial) {
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 170.dp, max = 240.dp)
                }
                Box(
                    modifier = previewBoxModifier,
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDecodingInitial && bmp == null -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ElectricOrange, modifier = Modifier.size(30.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Đang chuẩn bị ảnh & thông số đèo...", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        bmp != null -> {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Ảnh đã gắn watermark",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                        else -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Landscape, contentDescription = null, tint = TextSubtle, modifier = Modifier.size(42.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Chụp ngay hoặc chọn một tấm ảnh để tự động đóng dấu Tên điểm • Độ cao ▲${resolvedAlt}m • Tọa độ GPS.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Chọn kiểu Watermark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Kiểu dấu:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                WatermarkStyle.entries.forEach { st ->
                    val isSel = watermarkStyle == st
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSel) SkyTelemetry else SurfaceWhite)
                            .border(
                                BorderStroke(1.dp, if (isSel) SkyTelemetry else BorderSubtle),
                                RoundedCornerShape(9.dp)
                            )
                            .springClickable { watermarkStyle = st }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = st.title,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else TextSecondary
                        )
                    }
                }
            }

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it },
                label = { Text("Dòng ghi chú in lên ảnh & nhật ký (tùy chọn)", color = TextSecondary, fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceWhite,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = ElectricOrange,
                    unfocusedBorderColor = BorderSubtle
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { saveCopyToGallery = !saveCopyToGallery },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = saveCopyToGallery,
                    onCheckedChange = { saveCopyToGallery = it },
                    colors = CheckboxDefaults.colors(checkedColor = ElectricOrange)
                )
                Text(
                    text = "Lưu thêm bản ảnh vào Thư viện máy (Pictures/HaGiangGo)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }

            fun exportWatermarkedFile(alsoSaveToGallery: Boolean, onDone: (File?) -> Unit) {
                val currentPreview = previewBitmap ?: return
                if (isSaving) return
                isSaving = true
                val base = baseBitmap
                val exactCaption = caption
                val finalMeta = previewMeta.copy(caption = exactCaption)
                scope.launch(Dispatchers.IO) {
                    val savedFile = try {
                        val finalBmp = if (base != null && exactCaption != debouncedCaption) {
                            withContext(Dispatchers.Default) {
                                PhotoWatermarkEngine.renderWatermarkOnBitmap(base, finalMeta)
                            }
                        } else {
                            currentPreview
                        }
                        val appFile = PhotoWatermarkEngine.saveWatermarkedToAppStorage(context, finalBmp)
                        if (alsoSaveToGallery) {
                            PhotoWatermarkEngine.savePhotoToGallery(context, finalBmp, resolvedPlaceName)
                        }
                        appFile
                    } finally {
                        withContext(Dispatchers.Main) {
                            isSaving = false
                        }
                    }
                    withContext(Dispatchers.Main) {
                        onDone(savedFile)
                    }
                }
            }

            // Nút Lưu vào Nhật ký & Chia sẻ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShadcnButton(
                    onClick = {
                        exportWatermarkedFile(alsoSaveToGallery = saveCopyToGallery) { savedFile ->
                            if (savedFile != null) {
                                val noteText = caption.trim().ifBlank {
                                    "Check-in tại $resolvedPlaceName (▲ ${resolvedAlt}m)"
                                }
                                onSaveToJournal(
                                    request.place?.id,
                                    request.dayNumber,
                                    resolvedZone?.id ?: request.zoneId,
                                    noteText,
                                    savedFile.absolutePath
                                )
                            } else {
                                onNotify("Lỗi khi lưu file ảnh.")
                            }
                        }
                    },
                    enabled = previewBitmap != null && !isDecodingInitial && !isSaving,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1.3f).height(46.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu vào Nhật ký", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                ShadcnButton(
                    onClick = {
                        exportWatermarkedFile(alsoSaveToGallery = false) { file ->
                            if (file != null) {
                                PhotoWatermarkEngine.sharePhotoFile(context, file, resolvedPlaceName)
                            } else {
                                onNotify("Không thể chia sẻ ảnh lúc này.")
                            }
                        }
                    },
                    enabled = previewBitmap != null && !isDecodingInitial && !isSaving,
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(0.7f).height(46.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chia sẻ", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
    }
}

@Composable
fun JournalPhotoThumbnail(
    photoPath: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bmp by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoPath) {
        bmp = withContext(Dispatchers.IO) {
            if (File(photoPath).exists()) {
                PhotoWatermarkEngine.decodeOrientedSampledBitmap(photoPath, maxEdgePx = 900)
            } else {
                null
            }
        }
    }

    val loaded = bmp
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = "Ảnh nhật ký",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
        )
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceMuted)
                .border(BorderStroke(1.dp, BorderSubtle), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Photo, contentDescription = null, tint = ElectricOrange, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Ảnh đính kèm: ${File(photoPath).name}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun FullscreenPhotoDialog(
    photoPath: String,
    title: String,
    onNotify: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fullBmp by remember(photoPath) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoPath) {
        fullBmp = withContext(Dispatchers.IO) {
            PhotoWatermarkEngine.decodeOrientedSampledBitmap(photoPath, maxEdgePx = 1920)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF20F172A))
                .padding(16.dp)
        ) {
            val loaded = fullBmp
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                )
            } else {
                CircularProgressIndicator(
                    color = ElectricOrange,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = Color.White)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ShadcnButton(
                    onClick = {
                        val b = fullBmp ?: return@ShadcnButton
                        scope.launch(Dispatchers.IO) {
                            val uri = PhotoWatermarkEngine.savePhotoToGallery(context, b, title)
                            withContext(Dispatchers.Main) {
                                onNotify(if (uri != null) "Đã lưu ảnh vào Thư viện (Pictures/HaGiangGo)!" else "Không thể lưu ảnh.")
                            }
                        }
                    },
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu về Thư viện", fontWeight = FontWeight.Bold)
                }

                ShadcnButton(
                    onClick = {
                        PhotoWatermarkEngine.sharePhotoFile(context, File(photoPath), title)
                    },
                    variant = ShadcnButtonVariant.Secondary,
                    modifier = Modifier.weight(0.8f).height(46.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chia sẻ", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
