package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ItinerarySlot
import com.reed.hagianggo.model.NightStayVariant
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.TimeSlot
import com.reed.hagianggo.model.TripDuration
import com.reed.hagianggo.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ItineraryEditorLogicTest {

    private fun seedSlots(): List<ItinerarySlot> = listOf(
        ItinerarySlot(
            id = 1,
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            slot = TimeSlot.MORNING,
            title = "Sáng N1",
            suggestedPlaceIds = listOf("pl_a", "pl_b")
        ),
        ItinerarySlot(
            id = 2,
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            slot = TimeSlot.NOON,
            title = "Trưa N1",
            suggestedPlaceIds = listOf("pl_c", "pl_d")
        ),
        ItinerarySlot(
            id = 3,
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 2,
            slot = TimeSlot.MORNING,
            title = "Sáng N2",
            suggestedPlaceIds = listOf("pl_x")
        )
    )

    @Test
    fun addPlace_appendsToSlotAndDeduplicatesSameDay() {
        val updated = ItineraryEditorLogic.addPlace(
            slots = seedSlots(),
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            targetSlot = TimeSlot.NOON,
            placeId = "pl_a"
        )
        val morning = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.MORNING }
        val noon = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.NOON }
        assertFalse("pl_a" in morning.suggestedPlaceIds)
        assertEquals(listOf("pl_c", "pl_d", "pl_a"), noon.suggestedPlaceIds)
    }

    @Test
    fun removePlace_removesFromDay() {
        val updated = ItineraryEditorLogic.removePlace(seedSlots(), dayNumber = 1, placeId = "pl_c")
        val noon = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.NOON }
        assertEquals(listOf("pl_d"), noon.suggestedPlaceIds)
    }

    @Test
    fun movePlaceWithinSlot_swapsOrder() {
        val updated = ItineraryEditorLogic.movePlaceWithinDay(
            slots = seedSlots(),
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            placeId = "pl_b",
            direction = -1
        )
        val morning = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.MORNING }
        assertEquals(listOf("pl_b", "pl_a"), morning.suggestedPlaceIds)
    }

    @Test
    fun movePlaceUpAtTopOfNoon_crossesIntoMorningEnd() {
        val updated = ItineraryEditorLogic.movePlaceWithinDay(
            slots = seedSlots(),
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            placeId = "pl_c",
            direction = -1
        )
        val morning = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.MORNING }
        val noon = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.NOON }
        assertEquals(listOf("pl_a", "pl_b", "pl_c"), morning.suggestedPlaceIds)
        assertEquals(listOf("pl_d"), noon.suggestedPlaceIds)
    }

    @Test
    fun movePlaceDownAtBottomOfMorning_crossesIntoNoonStart() {
        val updated = ItineraryEditorLogic.movePlaceWithinDay(
            slots = seedSlots(),
            planCode = "3N2D_MOTORBIKE",
            dayNumber = 1,
            placeId = "pl_b",
            direction = 1
        )
        val morning = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.MORNING }
        val noon = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.NOON }
        assertEquals(listOf("pl_a"), morning.suggestedPlaceIds)
        assertEquals(listOf("pl_b", "pl_c", "pl_d"), noon.suggestedPlaceIds)
    }

    @Test
    fun movePlaceToAnotherDayAndSlot_removesFromSourceAndAddsToDest() {
        val updated = ItineraryEditorLogic.movePlaceToDaySlot(
            slots = seedSlots(),
            planCode = "3N2D_MOTORBIKE",
            fromDay = 1,
            toDay = 2,
            toSlot = TimeSlot.AFTERNOON,
            placeId = "pl_d"
        )
        val day1Noon = updated.first { it.dayNumber == 1 && it.slot == TimeSlot.NOON }
        val day2Afternoon = updated.first { it.dayNumber == 2 && it.slot == TimeSlot.AFTERNOON }
        assertFalse("pl_d" in day1Noon.suggestedPlaceIds)
        assertTrue("pl_d" in day2Afternoon.suggestedPlaceIds)
    }

    @Test
    fun resolvePlanCode_andFallback_handleDurationAndNightStayVariants() {
        assertEquals(
            "3N2D_MOTORBIKE",
            ItineraryEditorLogic.resolvePlanCode(TripDuration.DAYS_3_NIGHTS_2, VehicleType.MOTORBIKE, NightStayVariant.DONG_VAN)
        )
        assertEquals(
            "3N2D_MOTORBIKE_LLC",
            ItineraryEditorLogic.resolvePlanCode(TripDuration.DAYS_3_NIGHTS_2, VehicleType.MOTORBIKE, NightStayVariant.LO_LO_CHAI)
        )
        assertEquals(
            "4N3D_CAR_LLC",
            ItineraryEditorLogic.resolvePlanCode(TripDuration.DAYS_4_NIGHTS_3, VehicleType.CAR, NightStayVariant.LO_LO_CHAI)
        )
        assertEquals(
            "3N2D_MOTORBIKE_LLC",
            ItineraryEditorLogic.fallbackPlanCode(TripDuration.DAYS_2_NIGHTS_1, NightStayVariant.LO_LO_CHAI)
        )
        assertEquals(
            "4N3D_MOTORBIKE",
            ItineraryEditorLogic.fallbackPlanCode(TripDuration.DAYS_4_NIGHTS_3, NightStayVariant.DONG_VAN)
        )
    }

    @Test
    fun buildDynamicDayRouteSubtitle_handlesEmptyAndSummarizesLongDay() {
        assertEquals(
            "Chưa có điểm dừng cho ngày này • Bấm + Thêm điểm",
            ItineraryEditorLogic.buildDynamicDayRouteSubtitle(emptyList(), 0.0)
        )

        fun dummyPlace(id: String, name: String, category: PlaceCategory) = Place(
            id = id,
            name = name,
            zoneId = "tp_ha_giang",
            category = category,
            lat = 22.8,
            lng = 105.0
        )

        val shortDay = listOf(
            dummyPlace("p1", "Cột mốc Km0 Hà Giang", PlaceCategory.CHECKIN_VIEW),
            dummyPlace("p2", "Phở Chua (Nguyễn Trãi)", PlaceCategory.EAT)
        )
        assertEquals(
            "Cột mốc Km0 Hà Giang ➔ Phở Chua (~25km)",
            ItineraryEditorLogic.buildDynamicDayRouteSubtitle(shortDay, 24.6)
        )

        val longDay = listOf(
            dummyPlace("1", "Cột mốc Km0 Hà Giang", PlaceCategory.CHECKIN_VIEW),
            dummyPlace("2", "Cây xăng Petrolimex", PlaceCategory.FUEL),
            dummyPlace("3", "Cổng Trời Quản Bạ", PlaceCategory.CHECKIN_VIEW),
            dummyPlace("4", "Rừng Thông Yên Minh", PlaceCategory.CHECKIN_VIEW),
            dummyPlace("5", "Cây xăng Phố Cáo", PlaceCategory.FUEL),
            dummyPlace("6", "Dinh Thự Họ Vương (Dinh Vua Mèo)", PlaceCategory.CHECKIN_VIEW),
            dummyPlace("7", "Bản Văn Hóa Lô Lô Chải", PlaceCategory.HOMESTAY)
        )
        val subtitle = ItineraryEditorLogic.buildDynamicDayRouteSubtitle(longDay, 168.4)
        assertTrue(subtitle.startsWith("Cột mốc Km0 Hà Giang ➔ "))
        assertTrue(subtitle.endsWith("➔ Bản Văn Hóa Lô Lô Chải (~168km)"))
        assertFalse(subtitle.contains("Cây xăng"))
    }

    @Test
    fun seedJson_containsValidLoLoChaiVariantsAndExistingPlaceIds() {
        val seedFile = listOf(
            File("src/main/assets/hagiang_seed.json"),
            File("app/src/main/assets/hagiang_seed.json")
        ).firstOrNull { it.exists() }
        assertNotNull("hagiang_seed.json must be accessible from test runner", seedFile)
        val raw = seedFile!!.readText(Charsets.UTF_8)

        val placesStart = raw.indexOf("\"places\"")
        val itinerariesStart = raw.indexOf("\"default_itineraries\"")
        val checklistStart = raw.indexOf("\"checklist_seed\"")
        assertTrue(placesStart in 1 until itinerariesStart)
        assertTrue(itinerariesStart in 1 until checklistStart)

        val placesSection = raw.substring(placesStart, itinerariesStart)
        val itinerariesSection = raw.substring(itinerariesStart, checklistStart)

        val validPlaceIds = Regex(""""id"\s*:\s*"([^"]+)"""")
            .findAll(placesSection)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(validPlaceIds.size >= 40)

        val slotRegex = Regex(
            """\{\s*"planCode"\s*:\s*"([^"]+)"\s*,\s*"dayNumber"\s*:\s*(\d+)\s*,\s*"slot"\s*:\s*"([^"]+)"[\s\S]*?"suggestedPlaceIds"\s*:\s*\[([^\]]*)\]"""
        )
        val slotsByPlan = mutableMapOf<String, MutableList<Triple<Int, String, List<String>>>>()
        val idRegex = Regex(""""([^"]+)"""")

        for (match in slotRegex.findAll(itinerariesSection)) {
            val planCode = match.groupValues[1]
            val dayNum = match.groupValues[2].toInt()
            val slotCode = match.groupValues[3]
            val ids = idRegex.findAll(match.groupValues[4]).map { it.groupValues[1] }.toList()
            assertEquals("Duplicate placeId in $planCode D$dayNum $slotCode", ids.distinct().size, ids.size)
            for (pid in ids) {
                assertTrue("Unknown placeId $pid in $planCode", pid in validPlaceIds)
            }
            slotsByPlan.getOrPut(planCode) { mutableListOf() }.add(Triple(dayNum, slotCode, ids))
        }

        assertEquals(12, slotsByPlan["3N2D_MOTORBIKE"]?.size)
        assertEquals(12, slotsByPlan["3N2D_MOTORBIKE_LLC"]?.size)
        assertEquals(16, slotsByPlan["4N3D_MOTORBIKE"]?.size)
        assertEquals(16, slotsByPlan["4N3D_MOTORBIKE_LLC"]?.size)

        val llc3Day1After = slotsByPlan["3N2D_MOTORBIKE_LLC"]!!
            .first { it.first == 1 && it.second == "AFTERNOON" }.third
        assertTrue("pl_fuel_pho_cao" in llc3Day1After)
        assertEquals("pl_lo_lo_chai", llc3Day1After.last())

        val llc3Day2Morning = slotsByPlan["3N2D_MOTORBIKE_LLC"]!!
            .first { it.first == 2 && it.second == "MORNING" }.third
        assertEquals("pl_cot_co_lung_cu", llc3Day2Morning.first())
    }
}
