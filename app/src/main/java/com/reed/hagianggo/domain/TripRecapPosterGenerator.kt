package com.reed.hagianggo.domain

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.reed.hagianggo.model.*
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bộ dựng Poster Tổng kết Hành trình Hà Giang Loop (1080 x 1620 px) phong cách Strava × Polarsteps.
 * Hoạt động 100% Offline bằng Android Canvas 2D sắc nét, có tùy chọn ẩn chi phí cá nhân.
 */
object TripRecapPosterGenerator {

    private const val WIDTH = 1080
    private const val HEIGHT = 1620

    private val LOOP_TRACE = listOf(
        22.827330 to 104.984169,
        22.988169 to 104.935900,
        23.049316 to 104.993021,
        23.064631 to 105.004454,
        23.121011 to 105.109948,
        23.118139 to 105.145654,
        23.168925 to 105.194145,
        23.227137 to 105.202360,
        23.256248 to 105.262089,
        23.363461 to 105.316334,
        23.279671 to 105.360784,
        23.240621 to 105.412083,
        23.164376 to 105.408771,
        23.104663 to 105.260078,
        23.065592 to 105.237738,
        22.933029 to 105.226014,
        22.979511 to 105.185435,
        23.049316 to 104.993021,
        22.827330 to 104.984169
    )

    fun renderPoster(
        tripConfig: TripConfig,
        zones: List<Zone>,
        places: List<Place>,
        expenses: List<Expense>,
        journalCount: Int,
        rideStats: RideSummaryStats,
        hideExpenses: Boolean
    ): Bitmap {
        val bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Nền sáng cao cấp (Warm Alabaster -> Crisp Mist Gradient)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(
                    Color.parseColor("#FFFBF7"),
                    Color.parseColor("#F6F8FA"),
                    Color.parseColor("#EFF6FF")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), bgPaint)

        // Họa tiết đường đồng mức địa hình (Topographic Contours mờ tinh tế)
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(18, 15, 23, 42)
        }
        for (r in 1..8) {
            canvas.drawCircle(WIDTH * 0.88f, HEIGHT * 0.16f, r * 78f, contourPaint)
            canvas.drawCircle(WIDTH * 0.12f, HEIGHT * 0.55f, r * 92f, contourPaint)
        }

        // 2. Huy hiệu Header & Tiêu đề lớn
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFF7ED")
        }
        val pillBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.parseColor("#FED7AA")
        }
        val badgeRect = RectF(72f, 76f, 460f, 130f)
        canvas.drawRoundRect(badgeRect, 27f, 27f, pillPaint)
        canvas.drawRoundRect(badgeRect, 27f, 27f, pillBorder)

        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#EA580C")
            letterSpacing = 0.06f
        }
        canvas.drawText("HÀ GIANG LOOP PASSPORT", 98f, 111f, badgeTextPaint)

        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#64748B")
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(dateStr, WIDTH - 72f, 111f, datePaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText("Chinh Phục Cao Nguyên Đá", 72f, 214f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.parseColor("#475569")
        }
        val subtitle = "${tripConfig.duration.title}  •  Phương tiện: ${tripConfig.vehicle.title}  •  8 Chặng Đèo"
        canvas.drawText(subtitle, 72f, 262f, subPaint)

        // 3. Khung Bản đồ Hành trình (Vector Route Card)
        val mapCardRect = RectF(72f, 304f, WIDTH - 72f, 976f)
        drawElevatedCard(canvas, mapCardRect, 40f)

        // Nền nhẹ trong khung bản đồ
        val mapInnerRect = RectF(mapCardRect.left + 18f, mapCardRect.top + 18f, mapCardRect.right - 18f, mapCardRect.bottom - 18f)
        val mapBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F8FAFC")
        }
        canvas.drawRoundRect(mapInnerRect, 28f, 28f, mapBgPaint)

        // Ánh xạ tọa độ địa lý vào khung bản đồ
        val minLat = 22.78
        val maxLat = 23.40
        val minLng = 104.90
        val maxLng = 105.45
        val padX = 95f
        val padY = 74f
        val drawLeft = mapInnerRect.left + padX
        val drawRight = mapInnerRect.right - padX
        val drawTop = mapInnerRect.top + padY
        val drawBottom = mapInnerRect.bottom - padY

        fun project(lat: Double, lng: Double): PointF {
            val nx = ((lng - minLng) / (maxLng - minLng)).coerceIn(0.0, 1.0).toFloat()
            val ny = (1.0 - ((lat - minLat) / (maxLat - minLat)).coerceIn(0.0, 1.0)).toFloat()
            return PointF(
                drawLeft + nx * (drawRight - drawLeft),
                drawTop + ny * (drawBottom - drawTop)
            )
        }

        // Vẽ vòng cung Hà Giang Loop nền
        val loopPath = Path()
        LOOP_TRACE.forEachIndexed { idx, (lat, lng) ->
            val pt = project(lat, lng)
            if (idx == 0) loopPath.moveTo(pt.x, pt.y) else loopPath.lineTo(pt.x, pt.y)
        }
        val baseRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#CBD5E1")
            pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
        }
        canvas.drawPath(loopPath, baseRoutePaint)

        // Vẽ vệt GPS thực tế trong vùng Hà Giang (nếu có) chồng lên cung đường Loop cam tươi
        val loopHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 8.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#F97316")
            alpha = 195
        }
        canvas.drawPath(loopPath, loopHighlightPaint)

        val inLoopPoints = rideStats.allTrackPoints.filter { (lat, lng) ->
            lat in 22.72..23.45 && lng in 104.80..105.55
        }
        if (inLoopPoints.size >= 2) {
            val gpsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 10f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = Color.parseColor("#10B981")
            }
            val gpsPath = Path()
            inLoopPoints.forEachIndexed { idx, (lat, lng) ->
                val pt = project(lat, lng)
                if (idx == 0) gpsPath.moveTo(pt.x, pt.y) else gpsPath.lineTo(pt.x, pt.y)
            }
            canvas.drawPath(gpsPath, gpsPaint)
        }

        // Vẽ 8 mốc Zone & các điểm đã Check-in
        val visitedPlaces = places.filter { it.isVisited }
        val zoneDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EA580C") }
        val visitedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(65, 16, 185, 129) }
        val visitedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10B981") }
        val whiteStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.WHITE
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#334155")
        }

        for (z in zones) {
            val pt = project(z.centerLat, z.centerLng)
            canvas.drawCircle(pt.x, pt.y, 7.5f, zoneDotPaint)
            canvas.drawCircle(pt.x, pt.y, 7.5f, whiteStroke)
            val shortName = z.name.substringBefore("(").substringBefore("-").trim()
            val labelOffsetY = when (z.id) {
                "tp_ha_giang", "mau_due_du_gia", "meo_vac" -> 28f
                "ma_pi_leng_nho_que" -> 25f
                else -> -15f
            }
            val labelOffsetX = when (z.id) {
                "dong_van_lung_cu" -> -18f
                "ma_pi_leng_nho_que" -> 16f
                else -> 0f
            }
            labelPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(shortName, pt.x + labelOffsetX, pt.y + labelOffsetY, labelPaint)
        }

        for (vp in visitedPlaces) {
            val pt = project(vp.lat, vp.lng)
            canvas.drawCircle(pt.x, pt.y, 17f, visitedGlowPaint)
            canvas.drawCircle(pt.x, pt.y, 9f, visitedDotPaint)
            canvas.drawCircle(pt.x, pt.y, 9f, whiteStroke)
        }

        // Chú thích góc dưới bản đồ
        val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#64748B")
        }
        canvas.drawText(
            "● Cam: Cung đường Hà Giang Loop   ● Xanh ngọc: Điểm đã Check-in (${visitedPlaces.size}/${places.size})",
            mapInnerRect.left + 26f,
            mapInnerRect.bottom - 22f,
            legendPaint
        )

        // 4. Lưới 4 Thẻ Chỉ số Hành trình (2x2 Telemetry Bento)
        val visitedMaxAlt = visitedPlaces.maxOfOrNull { it.altitudeMeters } ?: 0
        val peakAltitude = maxOf(rideStats.maxRecordedAltitudeM, visitedMaxAlt, 1500)
        val estimatedLoopKm = when (tripConfig.duration) {
            TripDuration.DAYS_2_NIGHTS_1 -> 280.0
            TripDuration.DAYS_3_NIGHTS_2 -> 350.0
            TripDuration.DAYS_4_NIGHTS_3 -> 410.0
        }
        val displayKm = if (rideStats.totalRecordedKm >= 1.0) {
            String.format(Locale.US, "%.1f km", rideStats.totalRecordedKm)
        } else {
            "~${estimatedLoopKm.toInt()} km"
        }
        val kmCaption = if (rideStats.totalRecordedKm >= 1.0) "QUÃNG ĐƯỜNG GPS ĐÃ GHI" else "CUNG ĐƯỜNG HÀ GIANG LOOP"

        val fmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
        val fourthTitle: String
        val fourthValue: String
        val fourthAccent: Int
        if (hideExpenses) {
            fourthTitle = "NHẬT KÝ & DẤU ẤN"
            fourthValue = "$journalCount bài • ${places.count { it.isBookmarked }} ghim"
            fourthAccent = Color.parseColor("#0284C7")
        } else {
            val totalSpent = expenses.sumOf { it.amount }
            fourthTitle = "TỔNG CHI PHÍ CHUYẾN ĐI"
            fourthValue = "${fmt.format(totalSpent)} đ"
            fourthAccent = Color.parseColor("#F59E0B")
        }

        val gap = 24f
        val cardW = (WIDTH - 72f * 2 - gap) / 2f
        val cardH = 210f
        val row1Y = 1006f
        val row2Y = row1Y + cardH + gap

        drawStatBento(
            canvas = canvas,
            rect = RectF(72f, row1Y, 72f + cardW, row1Y + cardH),
            label = kmCaption,
            value = displayKm,
            sub = if (rideStats.maxRecordedSpeedKmh > 5f) "Tốc độ đỉnh: ${rideStats.maxRecordedSpeedKmh.toInt()} km/h" else "QL4C • Đồng Văn • Mã Pì Lèng",
            accentColor = Color.parseColor("#F97316")
        )
        drawStatBento(
            canvas = canvas,
            rect = RectF(72f + cardW + gap, row1Y, WIDTH - 72f, row1Y + cardH),
            label = "ĐỈNH CAO CHINH PHỤC",
            value = "${peakAltitude}m",
            sub = when {
                peakAltitude >= 1600 -> "Vách Đá Trắng • Mã Pì Lèng"
                peakAltitude >= 1470 -> "Đèo Mã Pì Lèng & Cột Cờ Lũng Cú"
                else -> "Cổng Trời Quản Bạ & Cao Nguyên Đá"
            },
            accentColor = Color.parseColor("#10B981")
        )
        drawStatBento(
            canvas = canvas,
            rect = RectF(72f, row2Y, 72f + cardW, row2Y + cardH),
            label = "ĐỊA ĐIỂM ĐÃ CHECK-IN",
            value = "${visitedPlaces.size} cột mốc",
            sub = if (visitedPlaces.isNotEmpty()) {
                visitedPlaces.take(2).joinToString(" • ") { it.name }
            } else {
                "Sẵn sàng lăn bánh từ Km0"
            },
            accentColor = Color.parseColor("#0D9488")
        )
        drawStatBento(
            canvas = canvas,
            rect = RectF(72f + cardW + gap, row2Y, WIDTH - 72f, row2Y + cardH),
            label = fourthTitle,
            value = fourthValue,
            sub = if (hideExpenses) "Chế độ riêng tư (Đã ẩn chi tiêu)" else "${expenses.size} khoản ghi chép thực tế",
            accentColor = fourthAccent
        )

        // 5. Chân trang thương hiệu
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 23f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#64748B")
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Hà Giang Go  •  Trợ lý Phượt Đèo Offline & An Toàn Vùng Cao",
            WIDTH / 2f,
            HEIGHT - 58f,
            footerPaint
        )

        return bmp
    }

    private fun drawElevatedCard(canvas: Canvas, rect: RectF, radius: Float) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(18, 15, 23, 42)
        }
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + 8f, rect.right, rect.bottom + 10f),
            radius,
            radius,
            shadowPaint
        )
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#E2E8F0")
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun drawStatBento(
        canvas: Canvas,
        rect: RectF,
        label: String,
        value: String,
        sub: String,
        accentColor: Int
    ) {
        drawElevatedCard(canvas, rect, 32f)

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
        canvas.drawRoundRect(
            RectF(rect.left + 28f, rect.top + 28f, rect.left + 84f, rect.top + 36f),
            4f,
            4f,
            barPaint
        )

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#64748B")
            letterSpacing = 0.04f
        }
        canvas.drawText(label, rect.left + 28f, rect.top + 72f, labelPaint)

        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (value.length > 13) 40f else 48f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            color = Color.parseColor("#0F172A")
        }
        canvas.drawText(value, rect.left + 28f, rect.top + 134f, valPaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            color = Color.parseColor("#475569")
        }
        val clippedSub = if (sub.length > 32) sub.take(30) + "…" else sub
        canvas.drawText(clippedSub, rect.left + 28f, rect.top + 176f, subPaint)
    }

    fun savePosterToGallery(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val filename = "HaGiangGo_Recap_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/HaGiangGo")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
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

    fun sharePoster(context: Context, bitmap: Bitmap) {
        try {
            val posterDir = File(context.filesDir, "posters").apply { mkdirs() }
            val file = File(posterDir, "hagiang_recap_share.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Hành trình chinh phục Hà Giang Loop cùng Hà Giang Go 🏔️🛵")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ Poster Hành trình").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            // Ignore
        }
    }
}
