package com.reed.hagianggo.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.reed.hagianggo.model.VehicleType
import com.reed.hagianggo.model.Zone
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

    fun findClosestZone(lat: Double, lng: Double, zones: List<Zone>): Zone? {
        if (zones.isEmpty()) return null
        return zones.minByOrNull { calculateDistanceKm(lat, lng, it.centerLat, it.centerLng) }
    }

    fun openNavigationIntent(context: Context, lat: Double, lng: Double, label: String = "Điểm đến") {
        try {
            val encodedLabel = Uri.encode(label)
            val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                val browserIntent = Intent(Intent.ACTION_VIEW, browserUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            } catch (_: Exception) {
                // Ignore if no browser
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
}
