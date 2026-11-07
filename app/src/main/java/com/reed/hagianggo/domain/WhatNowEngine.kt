package com.reed.hagianggo.domain

import com.reed.hagianggo.model.*
import java.util.Calendar

object WhatNowEngine {

    fun generateSuggestions(
        query: WhatNowQuery,
        currentLat: Double?,
        currentLng: Double?,
        allPlaces: List<Place>,
        allZones: List<Zone>,
        tripConfig: TripConfig,
        currentTimeMillis: Long = System.currentTimeMillis(),
        shuffleOffset: Int = 0
    ): List<SuggestionResult> {
        val calendar = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute
        val sunsetTotalMinutes = tripConfig.sunsetCutoffHour * 60 + tripConfig.sunsetCutoffMinute

        val minutesToSunset = sunsetTotalMinutes - currentTotalMinutes
        val isSunsetWarning = currentTotalMinutes in 960..1110 // Từ 16:00 đến 18:30

        val sunsetWarningMsg = when {
            minutesToSunset in 1..75 -> "Chỉ còn khoảng $minutesToSunset phút nữa trời sẽ tối sập đèo (${tripConfig.sunsetCutoffHour}:${tripConfig.sunsetCutoffMinute.toString().padStart(2, '0')}) — Khuyến nghị ưu tiên di chuyển về chỗ nghỉ hoặc đổ xăng!"
            minutesToSunset <= 0 && currentTotalMinutes < 1200 -> "Trời đã sập tối trên núi đá — Khuyến nghị dừng xe nghỉ đêm tại thị trấn gần nhất, không nên tiếp tục đổ đèo mù!"
            else -> ""
        }

        val currentTimeSlot = when (currentHour) {
            in 5..10 -> TimeSlot.MORNING
            in 11..13 -> TimeSlot.NOON
            in 14..17 -> TimeSlot.AFTERNOON
            else -> TimeSlot.EVENING
        }

        // Xác định vị trí tham chiếu (từ GPS hoặc tâm của Zone được chọn)
        val targetZone = if (!query.zoneId.isNullOrBlank()) {
            allZones.firstOrNull { it.id == query.zoneId }
        } else if (currentLat != null && currentLng != null) {
            LocationMath.findClosestZone(currentLat, currentLng, allZones)
        } else {
            allZones.firstOrNull()
        }

        val refLat = currentLat ?: targetZone?.centerLat ?: 22.827330
        val refLng = currentLng ?: targetZone?.centerLng ?: 104.984169

        // 1. Hard Filter
        val candidates = allPlaces.filter { place ->
            // Thời tiết
            if (query.weather == CurrentWeather.RAIN_FOG && place.weatherFit == WeatherFit.SUNNY_ONLY) {
                return@filter false
            }

            // Xe ô tô
            if (tripConfig.vehicle == VehicleType.CAR && !place.carFriendly) {
                return@filter false
            }

            // Nhu cầu
            when (query.need) {
                CurrentNeed.ANY -> true
                CurrentNeed.EAT -> place.category == PlaceCategory.EAT
                CurrentNeed.CAFE_REST -> place.category == PlaceCategory.CAFE_REST
                CurrentNeed.VIEW -> place.category == PlaceCategory.CHECKIN_VIEW
                CurrentNeed.RAIN_SHELTER -> {
                    (place.category == PlaceCategory.CAFE_REST ||
                            place.category == PlaceCategory.EAT ||
                            place.category == PlaceCategory.HOMESTAY) &&
                            place.weatherFit != WeatherFit.SUNNY_ONLY
                }
                CurrentNeed.FUEL_SOS -> {
                    place.category == PlaceCategory.FUEL || place.category == PlaceCategory.MECHANIC_SOS
                }
            }
        }.filter { place ->
            // Quỹ thời gian
            when (query.availableDuration) {
                AvailableDuration.MIN_30 -> place.durationMin <= 45
                AvailableDuration.HOURS_1_2 -> place.durationMin <= 120
                AvailableDuration.HALF_DAY -> true
            }
        }

        // 2. Chấm điểm & Xếp hạng
        val scoredList = candidates.map { place ->
            var score = 50
            val reasons = mutableListOf<String>()

            val distanceKm = LocationMath.calculateDistanceKm(refLat, refLng, place.lat, place.lng)
            val travelTimeMin = LocationMath.estimateMountainTravelTimeMin(distanceKm, tripConfig.vehicle)

            // Cùng cụm chặng
            if (targetZone != null && place.zoneId == targetZone.id) {
                score += 45
                reasons.add("Nằm ngay cụm ${targetZone.name}")
            } else {
                val distancePenalty = (distanceKm * 2.5).toInt()
                score -= distancePenalty.coerceAtMost(40)
            }

            // Đúng khung giờ sinh hoạt
            if (place.timeSlots.contains(currentTimeSlot)) {
                score += 20
                reasons.add("Hợp khung giờ ${currentTimeSlot.title.lowercase()}")
            }

            // Đã lưu / Đánh dấu
            if (place.isBookmarked) {
                score += 15
                reasons.add("Bạn đã lưu trước chuyến đi")
            }

            // Chưa ghé thăm
            if (!place.isVisited) {
                score += 10
            } else {
                score -= 20
            }

            // Phù hợp cảnh báo tối đèo
            if (isSunsetWarning && (place.category == PlaceCategory.HOMESTAY || place.category == PlaceCategory.FUEL || place.category == PlaceCategory.EAT)) {
                score += 30
                reasons.add("Phù hợp chuẩn bị dừng đèo chiều tối")
            }

            // Mẹo sống còn hữu ích
            if (place.practicalTip.isNotBlank()) {
                reasons.add(place.practicalTip)
            }

            SuggestionResult(
                place = place,
                distanceKm = (distanceKm * 10).toInt() / 10.0,
                travelTimeMin = travelTimeMin,
                matchScore = score,
                reasons = reasons,
                isSunsetWarning = isSunsetWarning,
                sunsetWarningMessage = sunsetWarningMsg
            )
        }.sortedByDescending { it.matchScore }

        if (scoredList.isEmpty()) return emptyList()

        // Hỗ trợ nút "Đổi gợi ý khác" (xoay vòng)
        val rotated = if (shuffleOffset > 0 && scoredList.size > 3) {
            val offset = shuffleOffset % scoredList.size
            scoredList.drop(offset) + scoredList.take(offset)
        } else {
            scoredList
        }

        return rotated.take(5)
    }
}
