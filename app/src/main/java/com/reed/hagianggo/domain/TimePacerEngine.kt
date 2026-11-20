package com.reed.hagianggo.domain

import com.reed.hagianggo.model.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Động cơ Điều độ Thời gian & Chống Lố Giờ Đèo Hà Giang (100% Offline).
 *
 * Tính toán dây chuyền:
 * - Cự ly đường đèo thực tế & thời gian chạy xe giữa từng điểm dừng liên tiếp.
 * - Giờ đến dự kiến (ETA), Thời lượng dừng chơi hợp lý, và Giờ chốt phải rời đi (Must-Leave Time).
 * - Giờ cán đích thị trấn nghỉ đêm (Finish Pass Time) so với mốc an toàn sương mù (17:15).
 * - Tự động gợi ý co giãn thời gian dừng tại các điểm dọc đèo để kịp về tới thị trấn trước 17:15.
 */
object TimePacerEngine {

    private const val MOUNTAIN_ROAD_FACTOR = 1.45

    data class DayStopInput(
        val place: Place,
        val customNote: String,
        val slot: TimeSlot
    )

    data class AutoTrimPlan(
        val updatedDurations: Map<String, Int>,
        val suggestedSkipIds: Set<String>,
        val totalSavedMinutes: Int,
        val summaryHint: String
    )

    fun formatMinutesOfDay(minutesOfDay: Int): String {
        val normalized = ((minutesOfDay % 1440) + 1440) % 1440
        val h = normalized / 60
        val m = normalized % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatDurationHoursMin(totalMinutes: Int): String {
        if (totalMinutes <= 0) return "0p"
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}p"
            h > 0 -> "${h}h"
            else -> "${m}p"
        }
    }

    fun computeDaySchedule(
        dayStops: List<DayStopInput>,
        vehicle: VehicleType,
        startMinutesOfDay: Int,
        isLiveNowMode: Boolean,
        skippedPlaceIds: Set<String>,
        sunsetCutoffMinutes: Int = 17 * 60 + 15,
        currentClockMinutes: Int = startMinutesOfDay,
        liveGpsLat: Double? = null,
        liveGpsLng: Double? = null,
        isOutsideHaGiang: Boolean = true,
        activeStopTimer: ActiveStopTimer? = null,
        riderPaceFactor: Float = 1.0f
    ): DayPacingSummary {
        val safePaceFactor = riderPaceFactor.coerceIn(0.75f, 1.45f)
        // Nếu người dùng bật Auto Live vào ban đêm (sau 17:30 tối hoặc trước 05:30 sáng để chuẩn bị lịch hôm sau),
        // tự động lấy mốc sáng sớm (07:30) làm gốc; còn nếu đang trong khung giờ phượt ban ngày (05:30..17:30) thì bám sát từng phút đồng hồ thật!
        val effectiveStartMin = if (isLiveNowMode) {
            if (currentClockMinutes in (5 * 60 + 30)..(17 * 60 + 30)) {
                currentClockMinutes
            } else {
                startMinutesOfDay.coerceIn(5 * 60 + 30, 15 * 60)
            }
        } else {
            startMinutesOfDay
        }
        var cursorMinutes = effectiveStartMin
        var prevPlace: Place? = null
        var totalRoadKm = 0.0
        var totalRideMinutes = 0
        var totalStayMinutes = 0
        var hasAnchoredLiveFirstUnvisited = false
        var liveGpsAnchorPlaceId: String? = null

        // Tìm điểm kết thúc chặng đèo ban ngày (điểm cuối cùng của buổi Sáng/Trưa/Chiều trước khi sang buổi Tối)
        val lastDaytimeIndex = dayStops.indexOfLast {
            it.slot != TimeSlot.EVENING && it.place.id !in skippedPlaceIds
        }

        var passFinishMinutes = effectiveStartMin
        val nodes = ArrayList<ScheduledStopNode>(dayStops.size)

        for ((index, item) in dayStops.withIndex()) {
            val place = item.place
            val note = item.customNote
            val isEveningSlot = item.slot == TimeSlot.EVENING

            if (place.id in skippedPlaceIds) {
                nodes.add(
                    ScheduledStopNode(
                        place = place,
                        customNote = note,
                        legRoadKm = 0.0,
                        legTravelMin = 0,
                        arrivalMinutesOfDay = cursorMinutes,
                        stayDurationMin = place.durationMin,
                        departureMinutesOfDay = cursorMinutes,
                        status = StopPacingStatus.SKIPPED
                    )
                )
                continue
            }

            if (isLiveNowMode && place.isVisited && activeStopTimer?.placeId != place.id) {
                nodes.add(
                    ScheduledStopNode(
                        place = place,
                        customNote = note,
                        legRoadKm = 0.0,
                        legTravelMin = 0,
                        arrivalMinutesOfDay = cursorMinutes,
                        stayDurationMin = place.durationMin,
                        departureMinutesOfDay = cursorMinutes,
                        status = StopPacingStatus.COMPLETED
                    )
                )
                prevPlace = place
                if (index == lastDaytimeIndex) {
                    passFinishMinutes = cursorMinutes
                }
                continue
            }

            val isStoppedHere = activeStopTimer != null && activeStopTimer.placeId == place.id
            var isFromLiveGps = false

            val (legRoadKm, legTravelMin) = when {
                isStoppedHere -> {
                    0.0 to 0
                }
                isLiveNowMode && !hasAnchoredLiveFirstUnvisited && !isOutsideHaGiang && liveGpsLat != null && liveGpsLng != null -> {
                    val straightKm = LocationMath.calculateDistanceKm(liveGpsLat, liveGpsLng, place.lat, place.lng)
                    if (straightKm <= 65.0) {
                        isFromLiveGps = true
                        liveGpsAnchorPlaceId = place.id
                        val roadKm = straightKm * MOUNTAIN_ROAD_FACTOR
                        val baseMin = if (straightKm < 0.2) 1 else LocationMath.estimateMountainTravelTimeMin(straightKm, vehicle)
                        val scaledMin = max(1, kotlin.math.round(baseMin * safePaceFactor).toInt())
                        roadKm to scaledMin
                    } else if (prevPlace == null) {
                        0.0 to 0
                    } else {
                        val sKm = LocationMath.calculateDistanceKm(prevPlace.lat, prevPlace.lng, place.lat, place.lng)
                        val rKm = sKm * MOUNTAIN_ROAD_FACTOR
                        val bMin = if (sKm < 0.15) 2 else LocationMath.estimateMountainTravelTimeMin(sKm, vehicle)
                        val scaledMin = max(2, kotlin.math.round(bMin * safePaceFactor).toInt())
                        rKm to scaledMin
                    }
                }
                prevPlace == null -> {
                    0.0 to 0
                }
                else -> {
                    val straightKm = LocationMath.calculateDistanceKm(
                        prevPlace.lat,
                        prevPlace.lng,
                        place.lat,
                        place.lng
                    )
                    val roadKm = straightKm * MOUNTAIN_ROAD_FACTOR
                    val baseMin = if (straightKm < 0.15) {
                        2
                    } else {
                        LocationMath.estimateMountainTravelTimeMin(straightKm, vehicle)
                    }
                    val scaledMin = max(2, kotlin.math.round(baseMin * safePaceFactor).toInt())
                    roadKm to scaledMin
                }
            }

            if (!hasAnchoredLiveFirstUnvisited && !place.isVisited) {
                hasAnchoredLiveFirstUnvisited = true
                if (isLiveNowMode) {
                    cursorMinutes = max(cursorMinutes, effectiveStartMin)
                }
            }

            val arrivalMin: Int
            val stayMin: Int
            val departMin: Int

            if (isStoppedHere && activeStopTimer != null) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = activeStopTimer.startedAtMillis
                }
                val startedMinOfDay = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
                val elapsedMin = ((System.currentTimeMillis() - activeStopTimer.startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
                val targetMin = activeStopTimer.targetDurationMin.coerceIn(10, 180)
                val actualOrRemainingStay = max(targetMin, elapsedMin)
                arrivalMin = startedMinOfDay
                stayMin = actualOrRemainingStay
                departMin = max(startedMinOfDay + actualOrRemainingStay, currentClockMinutes)
            } else {
                arrivalMin = if (isEveningSlot && cursorMinutes < 18 * 60) {
                    max(cursorMinutes + legTravelMin, 18 * 60 + 15)
                } else {
                    cursorMinutes + legTravelMin
                }
                stayMin = place.durationMin.coerceIn(10, 180)
                departMin = arrivalMin + stayMin
            }

            // Điểm cuối cùng của buổi Chiều chính là đích đến thị trấn nghỉ đêm (hạ sơn an toàn)
            val isFinalTownArrival = index == lastDaytimeIndex
            if (isFinalTownArrival) {
                passFinishMinutes = arrivalMin
            }

            val status = when {
                place.isVisited && !isStoppedHere -> StopPacingStatus.COMPLETED
                isEveningSlot -> StopPacingStatus.ON_TIME
                arrivalMin > sunsetCutoffMinutes -> StopPacingStatus.OVERTIME_LATE
                !isFinalTownArrival && departMin > sunsetCutoffMinutes + 10 -> StopPacingStatus.OVERTIME_LATE
                arrivalMin >= sunsetCutoffMinutes - 30 -> StopPacingStatus.NEAR_DUSK
                else -> StopPacingStatus.ON_TIME
            }

            nodes.add(
                ScheduledStopNode(
                    place = place,
                    customNote = note,
                    legRoadKm = legRoadKm,
                    legTravelMin = legTravelMin,
                    arrivalMinutesOfDay = arrivalMin,
                    stayDurationMin = stayMin,
                    departureMinutesOfDay = departMin,
                    status = status,
                    isFromLiveGps = isFromLiveGps,
                    isCurrentlyStoppedHere = isStoppedHere
                )
            )

            cursorMinutes = departMin
            prevPlace = place
            if (!isEveningSlot) {
                totalRoadKm += legRoadKm
                totalRideMinutes += legTravelMin
                if (!isFinalTownArrival) {
                    totalStayMinutes += stayMin
                }
            }
        }

        if (lastDaytimeIndex < 0) {
            passFinishMinutes = cursorMinutes
        }

        val overtime = max(0, passFinishMinutes - sunsetCutoffMinutes)

        // Chỉ co giãn các điểm dọc đường đèo TRƯỚC điểm đích nghỉ đêm để kéo giờ về đích sớm lại
        val daytimeNodesBeforeDestination = if (lastDaytimeIndex > 0) {
            nodes.take(lastDaytimeIndex)
        } else {
            nodes
        }

        val trimPlan = if (overtime > 0) {
            buildAutoTrimPlan(
                enRouteNodes = daytimeNodesBeforeDestination,
                overtimeMinutes = overtime
            )
        } else null

        return DayPacingSummary(
            startMinutesOfDay = effectiveStartMin,
            isLiveNowMode = isLiveNowMode,
            riderPaceFactor = safePaceFactor,
            liveGpsAnchorPlaceId = liveGpsAnchorPlaceId,
            totalRoadKm = totalRoadKm,
            totalRideMinutes = totalRideMinutes,
            totalStayMinutes = totalStayMinutes,
            finishMinutesOfDay = passFinishMinutes,
            sunsetCutoffMinutes = sunsetCutoffMinutes,
            overtimeMinutes = overtime,
            nodes = nodes,
            autoTrimHint = trimPlan?.summaryHint,
            canAutoTrim = trimPlan != null && trimPlan.totalSavedMinutes > 0
        )
    }

    fun buildAutoTrimPlan(
        enRouteNodes: List<ScheduledStopNode>,
        overtimeMinutes: Int
    ): AutoTrimPlan? {
        if (overtimeMinutes <= 0) return null

        val activeNodes = enRouteNodes.filter {
            it.status != StopPacingStatus.COMPLETED &&
                it.status != StopPacingStatus.SKIPPED &&
                it.place.category != PlaceCategory.FUEL
        }
        if (activeNodes.isEmpty()) return null

        var neededToSave = overtimeMinutes
        val updatedDurations = LinkedHashMap<String, Int>()
        val suggestedSkips = LinkedHashSet<String>()
        var savedSoFar = 0

        // Bước 1: Rút ngắn thời gian dừng tại các điểm Cà phê / Check-in / Ăn uống dọc đèo
        val candidatesForTrim = activeNodes.sortedByDescending { it.stayDurationMin }
        for (node in candidatesForTrim) {
            if (neededToSave <= 0) break
            val minAllowed = when (node.place.category) {
                PlaceCategory.CAFE_REST -> 15
                PlaceCategory.CHECKIN_VIEW -> 15
                PlaceCategory.EAT -> 25
                else -> 15
            }
            val currentStay = node.stayDurationMin
            if (currentStay > minAllowed) {
                val maxReducible = currentStay - minAllowed
                val reduceBy = min(maxReducible, ((neededToSave + 4) / 5) * 5)
                if (reduceBy >= 5) {
                    val newDuration = (currentStay - reduceBy).coerceAtLeast(minAllowed)
                    val actualSaved = currentStay - newDuration
                    updatedDurations[node.place.id] = newDuration
                    savedSoFar += actualSaved
                    neededToSave -= actualSaved
                }
            }
        }

        // Bước 2: Nếu vẫn còn lố > 10 phút, gợi ý tạm bỏ qua 1-2 điểm phụ dọc đường
        val skippedNames = ArrayList<String>()
        if (neededToSave > 10 && activeNodes.size >= 2) {
            val middleCandidates = activeNodes
                .drop(1)
                .filter { it.place.category == PlaceCategory.CAFE_REST || it.place.category == PlaceCategory.CHECKIN_VIEW }
                .sortedBy { it.stayDurationMin }

            for (node in middleCandidates) {
                if (neededToSave <= 5) break
                val alreadyReduced = updatedDurations.remove(node.place.id)
                if (alreadyReduced != null) {
                    val prevSaved = node.stayDurationMin - alreadyReduced
                    savedSoFar -= prevSaved
                    neededToSave += prevSaved
                }
                suggestedSkips.add(node.place.id)
                val fullNodeSave = node.stayDurationMin
                savedSoFar += fullNodeSave
                neededToSave -= fullNodeSave
                skippedNames.add(node.place.name)
            }
        }

        if (savedSoFar <= 0) return null

        val summary = buildString {
            if (updatedDurations.isNotEmpty()) {
                append("Rút ngắn thời gian dừng tại ${updatedDurations.size} điểm dọc đèo")
            }
            if (skippedNames.isNotEmpty()) {
                if (isNotEmpty()) append(" & ")
                append("tạm bỏ qua ${skippedNames.joinToString(", ")}")
            }
            append(" (tiết kiệm ~$savedSoFar phút) để về tới thị trấn trước 17:15.")
        }

        return AutoTrimPlan(
            updatedDurations = updatedDurations,
            suggestedSkipIds = suggestedSkips,
            totalSavedMinutes = savedSoFar,
            summaryHint = summary
        )
    }
}
