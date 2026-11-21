package com.reed.hagianggo.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.reed.hagianggo.model.VehicleType
import com.reed.hagianggo.model.Zone
import java.util.Locale
import kotlin.math.*

object LocationMath {

    private const val EARTH_RADIUS_KM = 6371.0
    private const val MOUNTAIN_ROAD_FACTOR = 1.45 // Hệ số uốn lượn đèo vùng cao

    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun estimateMountainTravelTimeMin(straightDistanceKm: Double, vehicle: VehicleType): Int {
        val actualRoadKm = straightDistanceKm * MOUNTAIN_ROAD_FACTOR
        val avgSpeedKmh = when (vehicle) {
            VehicleType.MOTORBIKE -> 28.0 // Tốc độ an toàn leo đèo xe máy
            VehicleType.CAR -> 32.0       // Xe ô tô đường đèo
        }
        val hours = actualRoadKm / avgSpeedKmh
        return max(3, (hours * 60).roundToInt())
    }

    fun findClosestZone(lat: Double, lng: Double, zones: List<Zone>, maxRadiusKm: Double = 45.0): Zone? {
        if (zones.isEmpty()) return null
        val closest = zones.minByOrNull { calculateDistanceKm(lat, lng, it.centerLat, it.centerLng) } ?: return null
        val dist = calculateDistanceKm(lat, lng, closest.centerLat, closest.centerLng)
        // Nếu ở xa ngoài cung đường Hà Giang (> 45km, ví dụ ở Hải Dương / Hà Nội),
        // mặc định chọn TP. Hà Giang (Điểm xuất phát Km0) thay vì tính góc lệch địa lý
        return if (dist <= maxRadiusKm) {
            closest
        } else {
            zones.firstOrNull { it.id == "tp_ha_giang" } ?: closest
        }
    }

    fun isOutsideHaGiang(lat: Double, lng: Double, zones: List<Zone>, maxRadiusKm: Double = 45.0): Boolean {
        if (zones.isEmpty()) return false
        val minDist = zones.minOfOrNull { calculateDistanceKm(lat, lng, it.centerLat, it.centerLng) } ?: return false
        return minDist > maxRadiusKm
    }

    fun openNavigationIntent(context: Context, lat: Double, lng: Double, label: String = "Điểm đến") {
        val latStr = String.format(Locale.US, "%.6f", lat)
        val lngStr = String.format(Locale.US, "%.6f", lng)
        val encodedLabel = Uri.encode(label)
        val geoUri = Uri.parse("geo:$latStr,$lngStr?q=$latStr,$lngStr($encodedLabel)")

        try {
            // Ưu tiên mở trực tiếp Google Maps tới đúng tọa độ chuẩn 6 chữ số thập phân
            val gmapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                setPackage("com.google.android.apps.maps")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(gmapsIntent)
        } catch (_: Exception) {
            try {
                val genericIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(genericIntent)
            } catch (_: Exception) {
                try {
                    val browserUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr")
                    val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                } catch (_: Exception) {
                    // Ignore if no browser
                }
            }
        }
    }

    fun openDialerIntent(context: Context, phoneNumber: String) {
        if (phoneNumber.isBlank()) return
        try {
            val uri = Uri.parse("tel:${phoneNumber.trim()}")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun openSmsBeaconIntent(context: Context, phoneNumber: String, message: String) {
        val cleanPhone = phoneNumber.trim()
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$cleanPhone")).apply {
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$cleanPhone")).apply {
                    putExtra("sms_body", message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Tính góc phương vị [0, 360) độ theo chiều kim đồng hồ từ hướng Bắc giữa 2 tọa độ.
     */
    fun calculateBearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        if (x == 0.0 && y == 0.0) return 0f
        val deg = Math.toDegrees(atan2(y, x))
        return (((deg % 360.0) + 360.0) % 360.0).toFloat()
    }

    /**
     * Hiệu góc ngắn nhất nằm trong (-180, 180] độ giữa 2 góc phương vị.
     */
    fun normalizeAngleDeltaDeg(fromDeg: Float, toDeg: Float): Float {
        var delta = ((toDeg - fromDeg) % 360f + 540f) % 360f - 180f
        if (delta <= -180f) delta += 360f
        return delta
    }

    /**
     * Làm mượt góc xoay qua ranh giới 355° <-> 5° để la bàn bản đồ không bị quay ngược vòng.
     */
    fun smoothBearingDeg(currentDeg: Float, targetDeg: Float, alpha: Float = 0.35f): Float {
        val clampedAlpha = alpha.coerceIn(0.05f, 1f)
        val delta = normalizeAngleDeltaDeg(currentDeg, targetDeg)
        val next = currentDeg + delta * clampedAlpha
        return ((next % 360f) + 360f) % 360f
    }

    /**
     * Khoảng cách vuông góc (tính bằng mét) từ điểm P tới đoạn thẳng [A, B] trên mặt cầu xấp xỉ phẳng.
     */
    fun pointToSegmentDistanceMeters(
        pLat: Double,
        pLng: Double,
        aLat: Double,
        aLng: Double,
        bLat: Double,
        bLng: Double
    ): Double {
        val midLatRad = Math.toRadians((aLat + bLat) * 0.5)
        val metersPerDegLat = 111_132.92
        val metersPerDegLng = 111_412.84 * cos(midLatRad).coerceAtLeast(0.01)

        val ax = (aLng - pLng) * metersPerDegLng
        val ay = (aLat - pLat) * metersPerDegLat
        val bx = (bLng - pLng) * metersPerDegLng
        val by = (bLat - pLat) * metersPerDegLat

        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6) {
            return sqrt(ax * ax + ay * ay)
        }
        val t = (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return sqrt(projX * projX + projY * projY)
    }

    /**
     * Định dạng cự ly dẫn đường ngắn gọn dễ liếc khi đang lái xe.
     */
    fun formatNavigationDistance(meters: Double): String {
        val safe = meters.coerceAtLeast(0.0)
        return when {
            safe < 100.0 -> "${((safe / 5.0).roundToInt() * 5).coerceAtLeast(0)} m"
            safe < 1000.0 -> {
                val rounded = ((safe / 10.0).roundToInt() * 10)
                if (rounded >= 1000) "1.0 km" else "$rounded m"
            }
            safe < 10_000.0 -> String.format(Locale.US, "%.1f km", safe / 1000.0)
            else -> "${(safe / 1000.0).roundToInt()} km"
        }
    }
}
