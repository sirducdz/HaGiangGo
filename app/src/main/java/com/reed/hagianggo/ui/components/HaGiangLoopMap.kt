package com.reed.hagianggo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.Zone
import com.reed.hagianggo.ui.theme.HighlandGreen
import com.reed.hagianggo.ui.theme.SunsetOrange

@Composable
fun HaGiangLoopMap(
    zones: List<Zone>,
    places: List<Place>,
    activeZoneId: String?,
    onZoneSelected: (Zone) -> Unit,
    currentLat: Double? = null,
    currentLng: Double? = null,
    trackPoints: List<Pair<Double, Double>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val sortedZones = zones.sortedBy { it.seq }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(sortedZones) {
                        detectTapGestures { tapOffset ->
                            // Tìm zone gần nhất với điểm tap
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            val coords = getNormalizedZoneCoords(width, height)

                            coords.entries.minByOrNull { (_, pt) ->
                                (pt.x - tapOffset.x) * (pt.x - tapOffset.x) + (pt.y - tapOffset.y) * (pt.y - tapOffset.y)
                            }?.let { (seq, _) ->
                                sortedZones.firstOrNull { it.seq == seq }?.let(onZoneSelected)
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val coords = getNormalizedZoneCoords(width, height)

                // 1. Vẽ đường đèo nối các chặng (Loop cung tròn)
                val path = Path()
                val seqList = (1..8).toList()
                coords[1]?.let { path.moveTo(it.x, it.y) }
                for (seq in 2..8) {
                    coords[seq]?.let { path.lineTo(it.x, it.y) }
                }
                coords[1]?.let { path.lineTo(it.x, it.y) } // Khép vòng về TP Hà Giang

                drawPath(
                    path = path,
                    color = Color.Gray.copy(alpha = 0.45f),
                    style = Stroke(width = 6.dp.toPx(), miter = 4f)
                )

                // 2. Vẽ vệt hành trình GPS thực tế (nếu có)
                if (trackPoints.size >= 2) {
                    val trackPath = Path()
                    val p0 = projectGpsToCanvas(trackPoints[0].first, trackPoints[0].second, width, height)
                    trackPath.moveTo(p0.x, p0.y)
                    for (i in 1 until trackPoints.size) {
                        val pt = projectGpsToCanvas(trackPoints[i].first, trackPoints[i].second, width, height)
                        trackPath.lineTo(pt.x, pt.y)
                    }
                    drawPath(
                        path = trackPath,
                        color = SunsetOrange,
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                // 3. Vẽ vị trí Live GPS hiện tại
                if (currentLat != null && currentLng != null) {
                    val livePt = projectGpsToCanvas(currentLat, currentLng, width, height)
                    // Vòng ngoài nhấp nháy
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = 0.35f),
                        radius = 14.dp.toPx(),
                        center = livePt
                    )
                    drawCircle(
                        color = Color(0xFF0284C7),
                        radius = 6.dp.toPx(),
                        center = livePt
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = livePt
                    )
                }

                // 4. Vẽ các trạm chặng
                for (zone in sortedZones) {
                    val pt = coords[zone.seq] ?: continue
                    val isActive = zone.id == activeZoneId
                    val visitedCount = places.count { it.zoneId == zone.id && it.isVisited }
                    val totalPlaces = places.count { it.zoneId == zone.id }

                    // Vòng ngoài
                    val circleColor = when {
                        isActive -> SunsetOrange
                        visitedCount > 0 -> HighlandGreen
                        else -> Color(0xFF64748B)
                    }

                    drawCircle(
                        color = circleColor.copy(alpha = 0.25f),
                        radius = if (isActive) 18.dp.toPx() else 12.dp.toPx(),
                        center = pt
                    )

                    drawCircle(
                        color = circleColor,
                        radius = if (isActive) 10.dp.toPx() else 7.dp.toPx(),
                        center = pt
                    )

                    // Text tên trạm
                    val textLayout = textMeasurer.measure(
                        text = "${zone.seq}. ${zone.name.split("-")[0].trim()}",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) SunsetOrange else Color.DarkGray
                        )
                    )

                    val textX = (pt.x - textLayout.size.width / 2).coerceIn(0f, width - textLayout.size.width)
                    val textY = if (pt.y > height * 0.65f) pt.y - 20.dp.toPx() else pt.y + 12.dp.toPx()
                    drawText(textLayout, topLeft = Offset(textX, textY))
                }
            }

            // Legend / Chú thích nhỏ góc trên
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = SunsetOrange,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Vòng lặp 8 chặng (Chạm để xem)",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

private fun getNormalizedZoneCoords(width: Float, height: Float): Map<Int, Offset> {
    // Tọa độ hình học mô phỏng bản đồ vòng cung Hà Giang
    return mapOf(
        1 to Offset(width * 0.16f, height * 0.82f), // 1. TP Hà Giang (Dưới cùng bên trái)
        2 to Offset(width * 0.18f, height * 0.50f), // 2. Quản Bạ (Giữa bên trái)
        3 to Offset(width * 0.38f, height * 0.35f), // 3. Yên Minh (Lưng đèo giữa)
        4 to Offset(width * 0.58f, height * 0.22f), // 4. Sủng Là / Thẩm Mã (Cận bắc)
        5 to Offset(width * 0.82f, height * 0.18f), // 5. Đồng Văn / Lũng Cú (Đỉnh đông bắc)
        6 to Offset(width * 0.90f, height * 0.45f), // 6. Mã Pì Lèng (Vách đông)
        7 to Offset(width * 0.80f, height * 0.70f), // 7. Mèo Vạc (Góc đông nam)
        8 to Offset(width * 0.48f, height * 0.80f)  // 8. Mậu Duệ / Du Già (Về lại tây nam)
    )
}

private fun projectGpsToCanvas(lat: Double, lng: Double, width: Float, height: Float): Offset {
    val minLat = 22.75
    val maxLat = 23.40
    val minLng = 104.85
    val maxLng = 105.50
    val xNorm = ((lng - minLng) / (maxLng - minLng)).coerceIn(0.05, 0.95).toFloat()
    val yNorm = (1.0 - ((lat - minLat) / (maxLat - minLat))).coerceIn(0.05, 0.95).toFloat()
    return Offset(width * (0.08f + xNorm * 0.84f), height * (0.08f + yNorm * 0.84f))
}
