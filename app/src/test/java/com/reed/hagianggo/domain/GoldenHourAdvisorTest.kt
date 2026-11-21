package com.reed.hagianggo.domain

import com.reed.hagianggo.model.GoldenHourMatch
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GoldenHourAdvisorTest {

    private fun place(id: String, category: PlaceCategory) = Place(
        id = id,
        name = id,
        zoneId = "quan_ba",
        category = category,
        lat = 23.0,
        lng = 105.0
    )

    @Test
    fun nhoQueWithinGoldenWindow_returnsOptimal() {
        val res = GoldenHourAdvisor.evaluate(
            place = place("pl_ben_thuyen_ta_lang", PlaceCategory.CHECKIN_VIEW),
            arrivalMin = 11 * 60 + 30,
            departMin = 13 * 60
        )
        assertNotNull(res)
        assertEquals(GoldenHourMatch.OPTIMAL, res!!.match)
    }

    @Test
    fun nhoQueLateAfternoon_returnsSuboptimalLate() {
        val res = GoldenHourAdvisor.evaluate(
            place = place("pl_ben_thuyen_ta_lang", PlaceCategory.CHECKIN_VIEW),
            arrivalMin = 15 * 60 + 30,
            departMin = 16 * 60 + 45
        )
        assertNotNull(res)
        assertEquals(GoldenHourMatch.SUBOPTIMAL_LATE, res!!.match)
    }

    @Test
    fun congTroiEarlyMorning_returnsOptimal() {
        val res = GoldenHourAdvisor.evaluate(
            place = place("pl_cong_troi_quan_ba", PlaceCategory.CHECKIN_VIEW),
            arrivalMin = 8 * 60,
            departMin = 8 * 60 + 30
        )
        assertNotNull(res)
        assertEquals(GoldenHourMatch.OPTIMAL, res!!.match)
    }

    @Test
    fun fuelAndMechanicPlaces_returnNullNoClutter() {
        assertNull(
            GoldenHourAdvisor.evaluate(
                place("pl_fuel_tphg", PlaceCategory.FUEL),
                8 * 60,
                8 * 60 + 10
            )
        )
        assertNull(
            GoldenHourAdvisor.evaluate(
                place("pl_sos_quan_ba", PlaceCategory.MECHANIC_SOS),
                9 * 60,
                9 * 60 + 20
            )
        )
    }

    @Test
    fun customViewpointPlace_returnsFallbackProfile() {
        val res = GoldenHourAdvisor.evaluate(
            place = place("custom_view_1", PlaceCategory.CHECKIN_VIEW),
            arrivalMin = 14 * 60,
            departMin = 14 * 60 + 30
        )
        assertNotNull(res)
        assertEquals(GoldenHourMatch.SUBOPTIMAL_LATE, res!!.match)
    }
}
