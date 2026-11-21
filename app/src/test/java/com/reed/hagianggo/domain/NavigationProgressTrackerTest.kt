package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ManeuverType
import com.reed.hagianggo.model.NavigationRoute
import com.reed.hagianggo.model.NavigationStep
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.RouteSource
import com.reed.hagianggo.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationProgressTrackerTest {

    private fun sampleRoute(): NavigationRoute {
        val dest = Place(
            id = "pl_cong_troi_quan_ba",
            name = "Cổng Trời Quản Bạ",
            zoneId = "quan_ba",
            category = PlaceCategory.CHECKIN_VIEW,
            lat = 23.0200,
            lng = 105.0100
        )
        // ~2.2km North then ~1.0km East
        val poly = listOf(
            23.0000 to 105.0000, // 0
            23.0100 to 105.0000, // 1 (~1.11km N)
            23.0200 to 105.0000, // 2 (~2.22km N) -> turn right here
            23.0200 to 105.0050, // 3 (~510m E)
            23.0200 to 105.0100  // 4 (~1.02km E)
        )
        val steps = listOf(
            NavigationStep(0, "Khởi hành theo QL4C", "QL4C", ManeuverType.DEPART, 2220.0, 280, 23.0000, 105.0000, 0),
            NavigationStep(1, "Rẽ phải vào Đỉnh Cổng Trời", "Đỉnh Cổng Trời", ManeuverType.RIGHT, 1020.0, 130, 23.0200, 105.0000, 2),
            NavigationStep(2, "Đã đến Cổng Trời Quản Bạ", "Cổng Trời Quản Bạ", ManeuverType.ARRIVE, 0.0, 0, 23.0200, 105.0100, 4)
        )
        return NavigationRoute(
            originLat = 23.0000,
            originLng = 105.0000,
            destination = dest,
            polyline = poly,
            steps = steps,
            totalDistanceMeters = 3240.0,
            totalDurationSeconds = 410,
            source = RouteSource.OSRM_ONLINE,
            originLabel = "Vị trí test"
        )
    }

    @Test
    fun `progress advances steps and reduces remaining distance along route`() {
        val route = sampleRoute()

        val startProg = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0050,
            lng = 105.0001, // ~10m off road halfway up first leg
            currentAltitudeM = 1200,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 8 * 60,
            previousMatchedIndex = 0,
            previousStepIndex = 0
        )

        assertFalse(startProg.isOffRoute)
        assertFalse(startProg.hasArrived)
        assertEquals(1, startProg.currentStepIndex) // Next upcoming turn is step 1 (RIGHT)
        assertTrue("Distance to turn ~1.67km", startProg.distanceToNextManeuverMeters in 1500.0..1850.0)
        assertEquals(1500 - 1200, startProg.elevationDeltaMeters) // pl_cong_troi altitude fallback

        val afterTurnProg = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0200,
            lng = 105.0060,
            currentAltitudeM = 1480,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 8 * 60 + 5,
            previousMatchedIndex = 2,
            previousStepIndex = 1
        )

        assertFalse(afterTurnProg.isOffRoute)
        assertFalse(afterTurnProg.hasArrived)
        assertEquals(2, afterTurnProg.currentStepIndex) // Past turn 1 -> targeting ARRIVE (step 2)
        assertTrue("Remaining ~400m", afterTurnProg.remainingDistanceMeters in 320.0..500.0)
    }

    @Test
    fun `detects off route when deviation exceeds 80 meters and stays quiet inside corridor`() {
        val route = sampleRoute()

        val insideCorridor = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0050,
            lng = 105.0003, // ~31m east
            currentAltitudeM = 500,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60
        )
        assertFalse(insideCorridor.isOffRoute)
        assertTrue(insideCorridor.offRouteDistanceMeters < 50.0)

        val offRoute = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0050,
            lng = 105.0015, // ~153m east
            currentAltitudeM = 500,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60
        )
        assertTrue(offRoute.isOffRoute)
        assertTrue(offRoute.offRouteDistanceMeters > 100.0)
    }

    @Test
    fun `detects arrival within 80 meters of destination`() {
        val route = sampleRoute()
        val arrived = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0200,
            lng = 105.0095, // ~51m west of destination
            currentAltitudeM = 1500,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 10 * 60,
            previousMatchedIndex = 3,
            previousStepIndex = 2
        )
        assertTrue(arrived.hasArrived)
        assertFalse(arrived.isOffRoute)
        assertEquals(0, arrived.remainingDurationMin)
    }

    @Test
    fun `bearing smoothing wraps cleanly across 355 to 5 degree boundary`() {
        val smoothed = LocationMath.smoothBearingDeg(currentDeg = 355f, targetDeg = 5f, alpha = 0.5f)
        // Halfway across 355 -> 5 clockwise (+10 deg) is 0f
        assertTrue("Expected near 0f or 360f, got $smoothed", smoothed <= 1f || smoothed >= 359f)

        val smoothedBack = LocationMath.smoothBearingDeg(currentDeg = 5f, targetDeg = 355f, alpha = 0.5f)
        assertTrue("Expected near 0f or 360f, got $smoothedBack", smoothedBack <= 1f || smoothedBack >= 359f)
    }

    @Test
    fun `distance formatter outputs clean compact units`() {
        assertEquals("75 m", LocationMath.formatNavigationDistance(74.0))
        assertEquals("450 m", LocationMath.formatNavigationDistance(452.0))
        assertEquals("1.4 km", LocationMath.formatNavigationDistance(1420.0))
        assertEquals("18 km", LocationMath.formatNavigationDistance(18300.0))
    }

    @Test
    fun `detects wrong way quickly when heading opposite and backtracking at least 60 meters`() {
        val route = sampleRoute()
        // Đã chạy tiến lên gần đầu khúc rẽ (lat 23.0150, còn ~1.58km)
        val forward = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0150,
            lng = 105.0000,
            currentAltitudeM = 800,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60,
            previousMatchedIndex = 1,
            previousStepIndex = 1,
            riderBearingDeg = 0f,
            hasRiderBearing = true,
            riderSpeedKmh = 30f
        )
        assertFalse(forward.isGoingWrongWay)

        // Quay ngược lại Nam (180 độ) lùi xuống ~110m (lat 23.0140)
        val wrongWay = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0140,
            lng = 105.0000,
            currentAltitudeM = 790,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60 + 1,
            previousMatchedIndex = forward.matchedSegmentIndex,
            previousStepIndex = forward.currentStepIndex,
            riderBearingDeg = 180f,
            hasRiderBearing = true,
            riderSpeedKmh = 25f,
            bestMinRemainingMeters = forward.bestMinRemainingMeters,
            previousRemainingMeters = forward.remainingDistanceMeters,
            wasGoingWrongWay = false
        )
        assertFalse(wrongWay.isOffRoute)
        assertTrue(wrongWay.isGoingWrongWay)
        assertTrue(wrongWay.wrongWayBacktrackMeters >= 60.0)
    }

    @Test
    fun `detects wrong way by pure backtracking over 130 meters even without GPS bearing`() {
        val route = sampleRoute()
        val forward = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0150,
            lng = 105.0000,
            currentAltitudeM = 800,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60
        )

        // Lùi ~165m (lat 23.0135) không có bearing từ phần cứng GPS
        val wrongWayNoBearing = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0135,
            lng = 105.0000,
            currentAltitudeM = 780,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60 + 1,
            previousMatchedIndex = forward.matchedSegmentIndex,
            previousStepIndex = forward.currentStepIndex,
            hasRiderBearing = false,
            riderSpeedKmh = 0f,
            bestMinRemainingMeters = forward.bestMinRemainingMeters,
            previousRemainingMeters = forward.remainingDistanceMeters,
            wasGoingWrongWay = false
        )
        assertTrue(wrongWayNoBearing.isGoingWrongWay)
        assertTrue(wrongWayNoBearing.wrongWayBacktrackMeters >= 130.0)
    }

    @Test
    fun `does not false alarm wrong way during normal forward progress around a turn`() {
        val route = sampleRoute()
        val p1 = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0180,
            lng = 105.0000,
            currentAltitudeM = 1200,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60,
            riderBearingDeg = 0f,
            hasRiderBearing = true,
            riderSpeedKmh = 28f
        )
        assertFalse(p1.isGoingWrongWay)

        // Vào cua 90 độ rẽ phải sang Đông
        val p2 = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0200,
            lng = 105.0020,
            currentAltitudeM = 1300,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60 + 1,
            previousMatchedIndex = p1.matchedSegmentIndex,
            previousStepIndex = p1.currentStepIndex,
            riderBearingDeg = 90f,
            hasRiderBearing = true,
            riderSpeedKmh = 25f,
            bestMinRemainingMeters = p1.bestMinRemainingMeters,
            previousRemainingMeters = p1.remainingDistanceMeters,
            wasGoingWrongWay = p1.isGoingWrongWay
        )
        assertFalse(p2.isGoingWrongWay)
    }

    @Test
    fun `clears wrong way alert once rider turns forward and remaining distance drops`() {
        val route = sampleRoute()
        val forward = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0150,
            lng = 105.0000,
            currentAltitudeM = 800,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60
        )

        val reversed = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0138,
            lng = 105.0000,
            currentAltitudeM = 790,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60 + 1,
            previousMatchedIndex = forward.matchedSegmentIndex,
            previousStepIndex = forward.currentStepIndex,
            riderBearingDeg = 180f,
            hasRiderBearing = true,
            riderSpeedKmh = 22f,
            bestMinRemainingMeters = forward.bestMinRemainingMeters,
            previousRemainingMeters = forward.remainingDistanceMeters,
            wasGoingWrongWay = false
        )
        assertTrue(reversed.isGoingWrongWay)

        // Quay đầu lại hướng Bắc (0 độ) và chạy tiến thêm ~30m
        val recovered = NavigationProgressTracker.evaluateProgress(
            route = route,
            lat = 23.0141,
            lng = 105.0000,
            currentAltitudeM = 795,
            vehicle = VehicleType.MOTORBIKE,
            riderPaceFactor = 1.0f,
            currentClockMinutesOfDay = 9 * 60 + 2,
            previousMatchedIndex = reversed.matchedSegmentIndex,
            previousStepIndex = reversed.currentStepIndex,
            riderBearingDeg = 0f,
            hasRiderBearing = true,
            riderSpeedKmh = 24f,
            bestMinRemainingMeters = reversed.bestMinRemainingMeters,
            previousRemainingMeters = reversed.remainingDistanceMeters,
            wasGoingWrongWay = reversed.isGoingWrongWay
        )
        assertFalse(recovered.isGoingWrongWay)
    }
}
