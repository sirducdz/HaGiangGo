package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ActiveStopTimer
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.TimeSlot
import com.reed.hagianggo.model.VehicleType
import com.reed.hagianggo.model.WeatherFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCheckInLogicTest {

    private fun samplePlace(
        id: String = "pl_bac_sum",
        name: String = "Dốc Bắc Sum",
        lat: Double = 22.9465,
        lng: Double = 104.9912,
        durationMin: Int = 20,
        isVisited: Boolean = false
    ) = Place(
        id = id,
        name = name,
        zoneId = "quan_ba",
        category = PlaceCategory.CHECKIN_VIEW,
        lat = lat,
        lng = lng,
        phone = "",
        addressHint = "",
        timeSlots = listOf(TimeSlot.MORNING),
        weatherFit = WeatherFit.SUNNY_ONLY,
        durationMin = durationMin,
        carFriendly = true,
        priceEst = "",
        practicalTip = "",
        isVisited = isVisited
    )

    @Test
    fun arriveSlowWithin250m_triggersArriveAndStartTimer() {
        val target = samplePlace()
        // ~111m phía bắc của target (~0.001 độ vĩ)
        val action = AutoCheckInLogic.evaluateLocationTransition(
            lat = target.lat + 0.001,
            lng = target.lng,
            speedKmh = 5f,
            nowMillis = 1_000_000L,
            activeTimer = null,
            allPlaces = listOf(target),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = emptySet()
        )
        assertTrue(action is AutoCheckInLogic.TransitionAction.ArriveAndStartTimer)
        assertEquals(target.id, (action as AutoCheckInLogic.TransitionAction.ArriveAndStartTimer).place.id)
    }

    @Test
    fun passThroughFastWithin250m_triggersPassThroughCheckIn() {
        val target = samplePlace()
        val action = AutoCheckInLogic.evaluateLocationTransition(
            lat = target.lat + 0.001,
            lng = target.lng,
            speedKmh = 32f,
            nowMillis = 1_000_000L,
            activeTimer = null,
            allPlaces = listOf(target),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = emptySet()
        )
        assertTrue(action is AutoCheckInLogic.TransitionAction.PassThroughCheckIn)
        assertEquals(target.id, (action as AutoCheckInLogic.TransitionAction.PassThroughCheckIn).place.id)
    }

    @Test
    fun alreadyVisitedOrNotified_returnsNoneNoSpam() {
        val visitedPlace = samplePlace(id = "pl_1", isVisited = true)
        val actionVisited = AutoCheckInLogic.evaluateLocationTransition(
            lat = visitedPlace.lat,
            lng = visitedPlace.lng,
            speedKmh = 4f,
            nowMillis = 1_000_000L,
            activeTimer = null,
            allPlaces = listOf(visitedPlace),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = emptySet()
        )
        assertEquals(AutoCheckInLogic.TransitionAction.None, actionVisited)

        val notifiedPlace = samplePlace(id = "pl_2", isVisited = false)
        val actionNotified = AutoCheckInLogic.evaluateLocationTransition(
            lat = notifiedPlace.lat,
            lng = notifiedPlace.lng,
            speedKmh = 4f,
            nowMillis = 1_000_000L,
            activeTimer = null,
            allPlaces = listOf(notifiedPlace),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = setOf("pl_2")
        )
        assertEquals(AutoCheckInLogic.TransitionAction.None, actionNotified)
    }

    @Test
    fun departBeyond350mAfter60s_triggersDepartPitStop() {
        val target = samplePlace()
        val startMs = 1_000_000L
        val timer = ActiveStopTimer(
            placeId = target.id,
            placeName = target.name,
            startedAtMillis = startMs,
            targetDurationMin = 20,
            isAutoGeofence = true
        )
        // ~555m phía bắc (~0.005 độ vĩ) sau 65 giây dù tốc độ thấp
        val action = AutoCheckInLogic.evaluateLocationTransition(
            lat = target.lat + 0.005,
            lng = target.lng,
            speedKmh = 8f,
            nowMillis = startMs + 65_000L,
            activeTimer = timer,
            allPlaces = listOf(target),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = setOf(target.id)
        )
        assertTrue(action is AutoCheckInLogic.TransitionAction.DepartPitStop)
        assertEquals(target.id, (action as AutoCheckInLogic.TransitionAction.DepartPitStop).place.id)
    }

    @Test
    fun departBeyond350mFastSpeed_triggersDepartPitStop() {
        val target = samplePlace()
        val startMs = 1_000_000L
        val timer = ActiveStopTimer(
            placeId = target.id,
            placeName = target.name,
            startedAtMillis = startMs,
            targetDurationMin = 20,
            isAutoGeofence = true
        )
        // Mới dừng 25 giây nhưng đã phóng xe ra > 350m ở tốc độ 22 km/h -> chốt rời trạm ngay
        val action = AutoCheckInLogic.evaluateLocationTransition(
            lat = target.lat + 0.005,
            lng = target.lng,
            speedKmh = 22f,
            nowMillis = startMs + 25_000L,
            activeTimer = timer,
            allPlaces = listOf(target),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = setOf(target.id)
        )
        assertTrue(action is AutoCheckInLogic.TransitionAction.DepartPitStop)
    }

    @Test
    fun stayWithin350m_doesNotDepart() {
        val target = samplePlace()
        val startMs = 1_000_000L
        val timer = ActiveStopTimer(
            placeId = target.id,
            placeName = target.name,
            startedAtMillis = startMs,
            targetDurationMin = 20,
            isAutoGeofence = true
        )
        // Di chuyển loanh quanh 220m (< 350m) trong 5 phút vẫn đang ở điểm dừng
        val action = AutoCheckInLogic.evaluateLocationTransition(
            lat = target.lat + 0.002,
            lng = target.lng,
            speedKmh = 6f,
            nowMillis = startMs + 300_000L,
            activeTimer = timer,
            allPlaces = listOf(target),
            skippedPlaceIds = emptySet(),
            alreadyNotifiedIds = setOf(target.id)
        )
        assertEquals(AutoCheckInLogic.TransitionAction.None, action)
    }

    @Test
    fun overstayTriggersOnceAndResetsWhenFlagCleared() {
        val startMs = 10_000_000L
        val timer = ActiveStopTimer(
            placeId = "pl_1",
            placeName = "Cổng Trời Quản Bạ",
            startedAtMillis = startMs,
            targetDurationMin = 20,
            isAutoGeofence = true,
            hasNotifiedOverstay = false
        )
        assertFalse(AutoCheckInLogic.shouldTriggerOverstay(timer, startMs + 19 * 60_000L))
        assertTrue(AutoCheckInLogic.shouldTriggerOverstay(timer, startMs + 20 * 60_000L))

        val alreadyNotified = timer.copy(hasNotifiedOverstay = true)
        assertFalse(AutoCheckInLogic.shouldTriggerOverstay(alreadyNotified, startMs + 25 * 60_000L))

        // Sau khi người dùng bấm "+10 phút" -> target = 30p và hasNotifiedOverstay = false
        val extended = alreadyNotified.copy(targetDurationMin = 30, hasNotifiedOverstay = false)
        assertFalse(AutoCheckInLogic.shouldTriggerOverstay(extended, startMs + 25 * 60_000L))
        assertTrue(AutoCheckInLogic.shouldTriggerOverstay(extended, startMs + 31 * 60_000L))
    }

    @Test
    fun paceFactorLearnsWithEmaAndClampsBounds() {
        val from = samplePlace(id = "pl_from", lat = 22.90, lng = 104.99)
        val to = samplePlace(id = "pl_to", lat = 23.02, lng = 105.00) // ~13.3km chim bay -> ~19.3km đèo -> ~41p xe máy
        val departedMs = 1_000_000L
        val expectedMin = LocationMath.estimateMountainTravelTimeMin(
            LocationMath.calculateDistanceKm(from.lat, from.lng, to.lat, to.lng),
            VehicleType.MOTORBIKE
        )
        // Chạy rất thong thả (gấp 2 lần lý thuyết -> observedRatio bị kẹp ở 1.40f)
        val slowArriveMs = departedMs + (expectedMin * 2L * 60_000L)
        val learnedSlow = AutoCheckInLogic.computeUpdatedPaceFactor(
            currentFactor = 1.0f,
            prevPlace = from,
            arrivedPlace = to,
            prevDepartedAtMillis = departedMs,
            nowMillis = slowArriveMs,
            vehicle = VehicleType.MOTORBIKE
        )
        // 1.0 * 0.65 + 1.40 * 0.35 = 1.14f
        assertEquals(1.14f, learnedSlow, 0.01f)

        // Chạy nhanh (bằng nửa lý thuyết -> observedRatio bị kẹp ở 0.75f)
        val fastArriveMs = departedMs + (expectedMin / 2L * 60_000L).coerceAtLeast(3L * 60_000L)
        val learnedFast = AutoCheckInLogic.computeUpdatedPaceFactor(
            currentFactor = 1.0f,
            prevPlace = from,
            arrivedPlace = to,
            prevDepartedAtMillis = departedMs,
            nowMillis = fastArriveMs,
            vehicle = VehicleType.MOTORBIKE
        )
        // 1.0 * 0.65 + 0.75 * 0.35 = 0.9125f
        assertEquals(0.9125f, learnedFast, 0.01f)
    }
}
