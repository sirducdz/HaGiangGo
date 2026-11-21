package com.reed.hagianggo.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.reed.hagianggo.model.WatermarkStyle
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Động cơ Xử lý Ảnh & Đóng Dấu Watermark Độ Cao / Tọa Độ 100% Offline bằng Canvas 2D.
 * An toàn bộ nhớ (downscale cạnh lớn nhất <= 1920px) & tự xoay đúng chiều EXIF từ Camera.
 */
object PhotoWatermarkEngine {

    data class WatermarkMetadata(
        val placeName: String,
        val zoneName: String,
        val dayNumber: Int,
        val altitudeMeters: Int,
        val lat: Double,
        val lng: Double,
        val caption: String = "",
        val timestampMillis: Long = System.currentTimeMillis(),
        val style: WatermarkStyle = WatermarkStyle.FULL_TELEMETRY
    )

    fun createTempRawPhotoFile(context: Context): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "raw_snap_${System.currentTimeMillis()}.jpg")
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        return try {
            val dest = createTempRawPhotoFile(context)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (dest.exists() && dest.length() > 0) dest else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeOrientedSampledBitmap(filePath: String, maxEdgePx: Int = 1920): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxEdgePx * 2 ||
                bounds.outHeight / sampleSize > maxEdgePx * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(filePath, decodeOpts) ?: return null

            val rotationDeg = try {
                val exif = ExifInterface(filePath)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (_: Exception) {
                0f
            }

            val maxCurrent = maxOf(decoded.width, decoded.height)
            val scaleDown = if (maxCurrent > maxEdgePx) maxEdgePx.toFloat() / maxCurrent.toFloat() else 1f
            if (rotationDeg == 0f && scaleDown >= 0.99f) {
                return decoded.copy(Bitmap.Config.ARGB_8888, true).also {
                    if (it != decoded) decoded.recycle()
                }
            }

            val matrix = Matrix().apply {
                if (rotationDeg != 0f) postRotate(rotationDeg)
                if (scaleDown < 0.99f) postScale(scaleDown, scaleDown)
            }
            val transformed = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (transformed != decoded) decoded.recycle()
            if (transformed.isMutable) transformed else {
                transformed.copy(Bitmap.Config.ARGB_8888, true).also { transformed.recycle() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Giải mã và xoay đúng hướng EXIF một lần duy nhất (tối đa 1920px cạnh dài)
     * để tái sử dụng làm ảnh nền khi người dùng chỉnh sửa ghi chú Watermark.
     */
    fun decodeBaseBitmap(sourcePath: String): Bitmap? =
        decodeOrientedSampledBitmap(sourcePath, maxEdgePx = 1920)

    fun renderWatermarkedBitmap(sourcePath: String, meta: WatermarkMetadata): Bitmap? {
        val base = decodeOrientedSampledBitmap(sourcePath, maxEdgePx = 1920) ?: return null
        return try {
            renderWatermarkOnBitmap(base, meta)
        } finally {
            base.recycle()
        }
    }

    /**
     * Vẽ lớp Watermark lên bản sao của [baseBitmap] trong bộ nhớ,
     * giữ nguyên [baseBitmap] gốc để cập nhật mượt mà không cần giải mã lại từ file.
     */
    fun renderWatermarkOnBitmap(baseBitmap: Bitmap, meta: WatermarkMetadata): Bitmap {
        val bmp = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            ?: Bitmap.createBitmap(baseBitmap.width, baseBitmap.height, Bitmap.Config.ARGB_8888).also { copy ->
                Canvas(copy).drawBitmap(baseBitmap, 0f, 0f, null)
            }
        val w = bmp.width.toFloat()
        val h = bmp.height.toFloat()
        val unit = minOf(w, h) / 1000f
        val canvas = Canvas(bmp)

        val dateStr = SimpleDateFormat("HH:mm • dd/MM/yyyy", Locale.getDefault())
            .format(Date(meta.timestampMillis))
        val cleanCaption = meta.caption.trim()

        if (meta.style == WatermarkStyle.MINIMAL) {
            val scrimHeight = (150f * unit).coerceAtLeast(h * 0.14f)
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, h - scrimHeight,
                    0f, h,
                    Color.TRANSPARENT,
                    Color.argb(190, 15, 23, 42),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, h - scrimHeight, w, h, scrimPaint)

            val padX = 34f * unit
            val padBottom = 34f * unit
            val minimalText = "HÀ GIANG GO  •  ${meta.placeName}  •  ▲ ${meta.altitudeMeters}m  •  $dateStr"
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (26f * unit).coerceIn(18f, 44f)
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                setShadowLayer(4f * unit, 0f, 1.5f * unit, Color.argb(160, 0, 0, 0))
            }
            // Nếu quá dài thì thu gọn nhẹ cỡ chữ để không tràn viền
            while (textPaint.measureText(minimalText) > (w - padX * 2f) && textPaint.textSize > 14f) {
                textPaint.textSize -= 1.5f
            }
            canvas.drawText(minimalText, padX, h - padBottom, textPaint)
            return bmp
        }

        // FULL_TELEMETRY Style
        val scrimHeight = (270f * unit).coerceAtLeast(h * 0.24f)
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, h - scrimHeight,
                0f, h,
                Color.TRANSPARENT,
                Color.argb(215, 15, 23, 42),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h - scrimHeight, w, h, scrimPaint)

        val marginX = 36f * unit
        val bottomPad = 34f * unit
        val barWidth = (8f * unit).coerceAtLeast(5f)

        val altPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(16, 185, 129) // EmeraldGreen sáng trên nền tối
            textSize = (42f * unit).coerceIn(26f, 64f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            setShadowLayer(4f * unit, 0f, 1.5f * unit, Color.argb(160, 0, 0, 0))
        }
        val coordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(235, 226, 232, 240)
            textSize = (22f * unit).coerceIn(15f, 34f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        val rightColX = w - marginX
        val coordsText = String.format(Locale.US, "%.4f°N, %.4f°E", meta.lat, meta.lng)

        val dateY = h - bottomPad
        val coordY = dateY - (30f * unit)
        val altY = coordY - (34f * unit)

        canvas.drawText("▲ ${meta.altitudeMeters}m", rightColX, altY, altPaint)
        canvas.drawText(coordsText, rightColX, coordY, coordPaint)
        canvas.drawText(dateStr, rightColX, dateY, coordPaint)

        // Góc trái: Thanh nhấn ElectricOrange + Badge + Tên địa điểm + Caption/Zone
        val rightReserved = maxOf(
            altPaint.measureText("▲ ${meta.altitudeMeters}m"),
            coordPaint.measureText(coordsText),
            coordPaint.measureText(dateStr)
        ) + 28f * unit
        val leftTextMaxWidth = (w - marginX * 2f - barWidth - 18f * unit - rightReserved).coerceAtLeast(w * 0.42f)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 241, 245, 249)
            textSize = (24f * unit).coerceIn(16f, 36f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (36f * unit).coerceIn(22f, 54f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f * unit, 0f, 1.5f * unit, Color.argb(160, 0, 0, 0))
        }
        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(251, 146, 60) // Cam sáng dễ đọc trên nền tối
            textSize = (21f * unit).coerceIn(14f, 32f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.06f
        }

        val leftX = marginX + barWidth + 16f * unit
        val rawTitle = meta.placeName.ifBlank { meta.zoneName }
        var drawnTitle = rawTitle
        while (titlePaint.measureText(drawnTitle) > leftTextMaxWidth && titlePaint.textSize > 18f) {
            titlePaint.textSize -= 2f
        }
        if (titlePaint.measureText(drawnTitle) > leftTextMaxWidth && drawnTitle.length > 12) {
            while (titlePaint.measureText("$drawnTitle…") > leftTextMaxWidth && drawnTitle.length > 8) {
                drawnTitle = drawnTitle.dropLast(1)
            }
            drawnTitle = "$drawnTitle…"
        }

        val subTextRaw = if (cleanCaption.isNotEmpty()) cleanCaption else meta.zoneName
        var subText = subTextRaw
        if (subPaint.measureText(subText) > leftTextMaxWidth && subText.length > 10) {
            while (subPaint.measureText("$subText…") > leftTextMaxWidth && subText.length > 6) {
                subText = subText.dropLast(1)
            }
            subText = "$subText…"
        }

        val subY = h - bottomPad
        val titleY = subY - (34f * unit)
        val tagY = titleY - (38f * unit)

        canvas.drawText("HÀ GIANG GO • NGÀY ${meta.dayNumber}", leftX, tagY, tagPaint)
        canvas.drawText(drawnTitle, leftX, titleY, titlePaint)
        canvas.drawText(subText, leftX, subY, subPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(249, 115, 22) // ElectricOrange #F97316
        }
        val barTop = tagY - (20f * unit)
        canvas.drawRoundRect(
            RectF(marginX, barTop, marginX + barWidth, h - bottomPad + 4f * unit),
            barWidth / 2f,
            barWidth / 2f,
            accentPaint
        )

        return bmp
    }

    fun saveWatermarkedToAppStorage(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = File(context.filesDir, "photos").apply { mkdirs() }
            val outFile = File(dir, "snap_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    fun savePhotoToGallery(context: Context, bitmap: Bitmap, label: String): Uri? {
        return try {
            val safeName = label.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(24).ifBlank { "Snap" }
            val filename = "HaGiangGo_${safeName}_${System.currentTimeMillis()}.jpg"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HaGiangGo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }
            uri
        } catch (_: Exception) {
            null
        }
    }

    fun sharePhotoFile(context: Context, photoFile: File, placeName: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Check-in tại $placeName — Hà Giang Go 🏔️🛵")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                Intent.createChooser(shareIntent, "Chia sẻ ảnh $placeName").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) {
            // Ignore
        }
    }
}
