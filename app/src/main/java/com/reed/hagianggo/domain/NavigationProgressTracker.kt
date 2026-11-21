package com.reed.hagianggo.domain

import com.reed.hagianggo.model.NavigationRoute
import com.reed.hagianggo.model.VehicleType
import com.reed.hagianggo.model.altitudeMeters
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Thuật toán thuần Kotlin theo dõi vị trí dọc tuyến đường dẫn:
 * - Khớp đoạn đường (snap-to-route) ưu tiên cửa sổ phía trước & sau để bắt kịp cả khi quay xe.
 * - Tính cự ly đường thực còn lại, thời gian còn lại, giờ đến (ETA) và cự ly tới khúc rẽ kế tiếp.
 * - Phát hiện lệch tuyến (> 80m), đi ngược chiều trên chính tuyến đang đi (quay đầu) và tới đích (<= 80m).
 */
object NavigationProgressTracker {

    const val OFF_ROUTE_THRESHOLD_METERS = 80.0
    const val ARRIVAL_THRESHOLD_METERS = 80.0
    const val WRONG_WAY_HEADING_DIFF_DEG = 135f
    const val WRONG_WAY_FAST_BACKTRACK_METERS = 60.0
    const val WRONG_WAY_PURE_BACKTRACK_METERS = 130.0
    const val WRONG_WAY_MIN_SPEED_KMH = 8f
    const val WRONG_WAY_CLEAR_HEADING_DEG = 65f
    const val WRONG_WAY_CLEAR_MARGIN_METERS = 25.0

    data class ProgressUpdate(
        val matchedSegmentIndex: Int,
        val remainingDistanceMeters: Double,
        val remainingDurationMin: Int,
        val etaMinutesOfDay: Int,
        val currentStepIndex: Int,
        val distanceToNextManeuverMeters: Double,
        val offRouteDistanceMeters: Double,
        val isOffRoute: Boolean,
        val hasArrived: Boolean,
        val elevationDeltaMeters: Int,
        val suggestedRouteBearingDeg: Float,
        val isGoingWrongWay: Boolean = false,
        val wrongWayBacktrackMeters: Double = 0.0,
        val bestMinRemainingMeters: Double = Double.POSITIVE_INFINITY
    )

    fun evaluateProgress(
        route: NavigationRoute,
        lat: Double,
        lng: Double,
        currentAltitudeM: Int,
        vehicle: VehicleType,
        riderPaceFactor: Float,
        currentClockMinutesOfDay: Int,
        previousMatchedIndex: Int = 0,
        previousStepIndex: Int = 0,
        riderBearingDeg: Float = 0f,
        hasRiderBearing: Boolean = false,
        riderSpeedKmh: Float = 0f,
        bestMinRemainingMeters: Double = Double.POSITIVE_INFINITY,
        previousRemainingMeters: Double = Double.POSITIVE_INFINITY,
        wasGoingWrongWay: Boolean = false
    ): ProgressUpdate {
        val poly = route.polyline
        if (poly.size < 2) {
            val directM = LocationMath.calculateDistanceKm(
                lat, lng, route.destination.lat, route.destination.lng
            ) * 1000.0
            val arrived = directM <= ARRIVAL_THRESHOLD_METERS
            val minLeft = if (arrived) 0 else LocationMath.estimateMountainTravelTimeMin(directM / 1000.0, vehicle)
            val remM = if (arrived) 0.0 else directM
            return ProgressUpdate(
                matchedSegmentIndex = 0,
                remainingDistanceMeters = remM,
                remainingDurationMin = minLeft,
                etaMinutesOfDay = (currentClockMinutesOfDay + minLeft) % 1440,
                currentStepIndex = 0,
                distanceToNextManeuverMeters = remM,
                offRouteDistanceMeters = 0.0,
                isOffRoute = false,
                hasArrived = arrived,
                elevationDeltaMeters = if (currentAltitudeM > 10) route.destination.altitudeMeters - currentAltitudeM else 0,
                suggestedRouteBearingDeg = LocationMath.calculateBearingDeg(lat, lng, route.destination.lat, route.destination.lng),
                isGoingWrongWay = false,
                wrongWayBacktrackMeters = 0.0,
                bestMinRemainingMeters = remM
            )
        }

        // Bảng cộng dồn khoảng cách từ đỉnh 0..lastIndex
        val cumMeters = DoubleArray(poly.size)
        for (i in 1 until poly.size) {
            val a = poly[i - 1]
            val b = poly[i]
            cumMeters[i] = cumMeters[i - 1] +
                LocationMath.calculateDistanceKm(a.first, a.second, b.first, b.second) * 1000.0
        }

        val lastSeg = poly.size - 2
        val safePrev = previousMatchedIndex.coerceIn(0, lastSeg)
        val winStart = (safePrev - 25).coerceAtLeast(0)
        val winEnd = (safePrev + 55).coerceAtMost(lastSeg)

        var bestSeg = winStart
        var bestDistM = Double.MAX_VALUE
        for (i in winStart..winEnd) {
            val d = LocationMath.pointToSegmentDistanceMeters(
                lat, lng,
                poly[i].first, poly[i].second,
                poly[i + 1].first, poly[i + 1].second
            )
            if (d < bestDistM) {
                bestDistM = d
                bestSeg = i
            }
        }

        // Nếu ngoài cửa sổ cục bộ (> 120m), quét toàn bộ tuyến để khớp lại chính xác
        if (bestDistM > 120.0) {
            var globalBestSeg = bestSeg
            var globalBestDistM = bestDistM
            for (i in 0..lastSeg) {
                val d = LocationMath.pointToSegmentDistanceMeters(
                    lat, lng,
                    poly[i].first, poly[i].second,
                    poly[i + 1].first, poly[i + 1].second
                )
                if (d < globalBestDistM) {
                    globalBestDistM = d
                    globalBestSeg = i
                }
            }
            bestSeg = globalBestSeg
            bestDistM = globalBestDistM
        }

        // Ước lượng vị trí chiếu dọc theo đoạn [bestSeg, bestSeg+1]
        val segStartM = cumMeters[bestSeg]
        val segEndM = cumMeters[bestSeg + 1]
        val segLenM = (segEndM - segStartM).coerceAtLeast(0.0)
        val dStart = LocationMath.calculateDistanceKm(
            lat, lng, poly[bestSeg].first, poly[bestSeg].second
        ) * 1000.0
        val projOffsetAlongSegM = if (segLenM > 1.0 && dStart > bestDistM) {
            sqrt((dStart * dStart - bestDistM * bestDistM).coerceAtLeast(0.0)).coerceIn(0.0, segLenM)
        } else {
            0.0
        }
        val matchedCumMeters = (segStartM + projOffsetAlongSegM).coerceIn(0.0, cumMeters.last())

        val directDestM = LocationMath.calculateDistanceKm(
            lat, lng, route.destination.lat, route.destination.lng
        ) * 1000.0
        val polyRemainM = (cumMeters.last() - matchedCumMeters).coerceAtLeast(0.0)
        val remainingMeters = if (bestDistM > OFF_ROUTE_THRESHOLD_METERS) {
            polyRemainM + bestDistM
        } else {
            polyRemainM
        }

        val hasArrived = directDestM <= ARRIVAL_THRESHOLD_METERS ||
            (polyRemainM <= 70.0 && directDestM <= 120.0)
        val isOffRoute = !hasArrived && bestDistM > OFF_ROUTE_THRESHOLD_METERS

        // Phát hiện đi ngược chiều trên chính tuyến đường hiện tại (tránh báo giả ở cua tay áo)
        val baselineMin = if (bestMinRemainingMeters.isFinite()) bestMinRemainingMeters else remainingMeters
        val currentBestMin = if (!isOffRoute) minOf(baselineMin, remainingMeters) else baselineMin
        val backtrackedMeters = if (!isOffRoute) (remainingMeters - currentBestMin).coerceAtLeast(0.0) else 0.0
        val segForwardBearing = LocationMath.calculateBearingDeg(
            poly[bestSeg].first, poly[bestSeg].second,
            poly[bestSeg + 1].first, poly[bestSeg + 1].second
        )
        val headingDiffDeg = abs(LocationMath.normalizeAngleDeltaDeg(riderBearingDeg, segForwardBearing))

        val triggerFast = hasRiderBearing &&
            riderSpeedKmh >= WRONG_WAY_MIN_SPEED_KMH &&
            headingDiffDeg >= WRONG_WAY_HEADING_DIFF_DEG &&
            backtrackedMeters >= WRONG_WAY_FAST_BACKTRACK_METERS
        val triggerPureBacktrack = backtrackedMeters >= WRONG_WAY_PURE_BACKTRACK_METERS
        val recoveredForward = hasRiderBearing &&
            headingDiffDeg <= WRONG_WAY_CLEAR_HEADING_DEG &&
            previousRemainingMeters.isFinite() &&
            remainingMeters < previousRemainingMeters - 8.0

        val isGoingWrongWay = when {
            hasArrived || isOffRoute -> false
            wasGoingWrongWay -> !(recoveredForward || backtrackedMeters <= WRONG_WAY_CLEAR_MARGIN_METERS)
            else -> triggerFast || triggerPureBacktrack
        }
        val finalBestMin = if (wasGoingWrongWay && !isGoingWrongWay && recoveredForward) {
            remainingMeters
        } else {
            currentBestMin
        }

        // Xác định bước rẽ kế tiếp mà xe chưa đi qua (tự cập nhật lùi nếu đi ngược tuyến)
        val steps = route.steps
        val targetStepIndex = if (steps.size <= 1) {
            0
        } else if (hasArrived) {
            steps.lastIndex
        } else {
            val minStepFloor = if (isGoingWrongWay || bestSeg < safePrev - 3) 1 else previousStepIndex
            val found = steps.indexOfFirst { step ->
                step.index >= 1 &&
                    step.index >= minStepFloor &&
                    step.routePointIndex > bestSeg
            }
            if (found >= 0) found else steps.lastIndex
        }

        val maneuverCumMeters = if (steps.isNotEmpty()) {
            val ptIdx = steps[targetStepIndex].routePointIndex.coerceIn(0, cumMeters.lastIndex)
            cumMeters[ptIdx]
        } else {
            cumMeters.last()
        }
        val distToNextManeuverM = if (hasArrived) {
            0.0
        } else {
            (maneuverCumMeters - matchedCumMeters).coerceAtLeast(0.0)
        }

        val baseSpeedKmh = if (vehicle == VehicleType.MOTORBIKE) 28.0 else 32.0
        val pace = riderPaceFactor.coerceIn(0.75f, 1.45f)
        val remainMin = if (hasArrived) {
            0
        } else if (route.totalDistanceMeters > 100.0 && route.totalDurationSeconds > 0) {
            val ratio = (remainingMeters / route.totalDistanceMeters).coerceIn(0.0, 2.0)
            ((route.totalDurationSeconds / 60.0) * ratio * pace).roundToInt().coerceAtLeast(1)
        } else {
            ((remainingMeters / 1000.0) / baseSpeedKmh * 60.0 * pace).roundToInt().coerceAtLeast(1)
        }

        val etaMin = ((currentClockMinutesOfDay + remainMin) % 1440 + 1440) % 1440
        val elevDelta = if (currentAltitudeM > 10) route.destination.altitudeMeters - currentAltitudeM else 0

        val lookAheadIdx = (bestSeg + 3).coerceAtMost(poly.lastIndex)
        val suggestedBearing = if (lookAheadIdx > bestSeg) {
            LocationMath.calculateBearingDeg(
                poly[bestSeg].first, poly[bestSeg].second,
                poly[lookAheadIdx].first, poly[lookAheadIdx].second
            )
        } else {
            LocationMath.calculateBearingDeg(lat, lng, route.destination.lat, route.destination.lng)
        }

        return ProgressUpdate(
            matchedSegmentIndex = bestSeg,
            remainingDistanceMeters = if (hasArrived) 0.0 else remainingMeters,
            remainingDurationMin = remainMin,
            etaMinutesOfDay = etaMin,
            currentStepIndex = targetStepIndex,
            distanceToNextManeuverMeters = distToNextManeuverM,
            offRouteDistanceMeters = bestDistM,
            isOffRoute = isOffRoute,
            hasArrived = hasArrived,
            elevationDeltaMeters = elevDelta,
            suggestedRouteBearingDeg = suggestedBearing,
            isGoingWrongWay = isGoingWrongWay,
            wrongWayBacktrackMeters = if (isGoingWrongWay) backtrackedMeters else 0.0,
            bestMinRemainingMeters = finalBestMin
        )
    }
}
