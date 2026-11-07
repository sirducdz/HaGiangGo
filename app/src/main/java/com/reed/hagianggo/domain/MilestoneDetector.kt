package com.reed.hagianggo.domain

import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.VehicleType

object MilestoneDetector {

    /**
     * Tìm địa điểm quan trọng kế tiếp (ưu tiên cây xăng, trạm nghỉ, điểm ngắm cảnh)
     * đang nằm gần nhất trên cung đường phía trước.
     */
    fun findNextMilestone(
        currentLat: Double,
        currentLng: Double,
        allPlaces: List<Place>,
        vehicle: VehicleType
    ): Pair<Place, Double>? {
        if (allPlaces.isEmpty()) return null

        // Lọc các điểm chưa ghé, sắp xếp theo khoảng cách đèo thực tế
        return allPlaces
            .asSequence()
            .filter { !it.isVisited }
            .filter { if (vehicle == VehicleType.CAR) it.carFriendly else true }
            .map { place ->
                val straightKm = LocationMath.calculateDistanceKm(currentLat, currentLng, place.lat, place.lng)
                // Khoảng cách thực tế tính cả đường cua uốn lượn
                val mountainKm = straightKm * 1.45
                place to mountainKm
            }
            .filter { it.second in 0.1..25.0 } // Trong phạm vi 100m đến 25km phía trước
            .minByOrNull { it.second }
    }

    /**
     * Kiểm tra xem người dùng có đang ở gần (< 250m) một địa điểm nào chưa ghé không
     * để kích hoạt Auto Check-in tự động đánh dấu "Đã ghé".
     */
    fun detectApproachingPlace(
        currentLat: Double,
        currentLng: Double,
        allPlaces: List<Place>,
        thresholdMeters: Double = 250.0
    ): Place? {
        val thresholdKm = thresholdMeters / 1000.0
        return allPlaces.firstOrNull { place ->
            !place.isVisited && LocationMath.calculateDistanceKm(currentLat, currentLng, place.lat, place.lng) <= thresholdKm
        }
    }
}
