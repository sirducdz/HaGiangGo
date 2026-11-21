package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ItinerarySlot
import com.reed.hagianggo.model.TimeSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
