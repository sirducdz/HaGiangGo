package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ManeuverType
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.RouteSource
import com.reed.hagianggo.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class OfflineRoadGraphRouterTest {

    companion object {
        private lateinit var realGraph: OfflineRoadGraphRouter.RoadGraph

        @BeforeClass
        @JvmStatic
        fun setupGraph() {
            val candidates = listOf(
                File("src/main/assets/hagiang_loop_roads.json"),
                File("app/src/main/assets/hagiang_loop_roads.json")
            )
            val assetFile = candidates.firstOrNull { it.exists() }
                ?: error("Không tìm thấy hagiang_loop_roads.json trong ${File(".").absolutePath}")
            val jsonStr = assetFile.readText(Charsets.UTF_8)
            realGraph = OfflineRoadGraphRouter.parseGraphFromJson(jsonStr)
        }
    }

    private fun testPlace(id: String, name: String, zoneId: String, lat: Double, lng: Double) = Place(
        id = id,
        name = name,
        zoneId = zoneId,
        category = PlaceCategory.CHECKIN_VIEW,
        lat = lat,
        lng = lng
    )

    @Test
    fun `graph loads main loop and all branches as a connected network`() {
        assertTrue("Graph should have > 6000 nodes", realGraph.nodeCount > 6000)
        assertTrue(realGraph.roadNames.any { it.contains("ĐT176") })
        assertTrue(realGraph.roadNames.any { it.contains("Lô Lô Chải") })
        assertTrue(realGraph.roadNames.any { it.contains("Nho Quế") })
    }

    @Test
    fun `route from Km0 Ha Giang to Cong Troi Quan Ba follows main loop accurately`() {
        val dest = testPlace("pl_cong_troi_quan_ba", "Cổng Trời Quản Bạ", "quan_ba", 23.049316, 104.993021)
        val route = OfflineRoadGraphRouter.computeRouteOnGraph(
            graph = realGraph,
            originLat = 22.827330,
            originLng = 104.984169,
            destination = dest,
            vehicle = VehicleType.MOTORBIKE,
            originLabel = "TP. Hà Giang"
        )

        val distKm = route.totalDistanceMeters / 1000.0
        assertEquals(RouteSource.OFFLINE_LOOP_GRAPH, route.source)
        assertTrue("Expected ~40-58 km via Doc Bac Sum, got $distKm km", distKm in 38.0..60.0)
        assertTrue("Route polyline should follow actual road curve", route.polyline.size > 150)
        assertEquals(ManeuverType.DEPART, route.steps.first().maneuver)
        assertEquals(ManeuverType.ARRIVE, route.steps.last().maneuver)
    }

    @Test
    fun `route from Dong Van to Lo Lo Chai enters extreme north branch`() {
        val dest = testPlace("pl_lo_lo_chai", "Lô Lô Chải", "dong_van_lung_cu", 23.36954, 105.32192)
        val route = OfflineRoadGraphRouter.computeRouteOnGraph(
            graph = realGraph,
            originLat = 23.279671,
            originLng = 105.360784,
            destination = dest,
            vehicle = VehicleType.MOTORBIKE,
            originLabel = "Phố Cổ Đồng Văn"
        )

        val distKm = route.totalDistanceMeters / 1000.0
        assertTrue("Dong Van -> Lo Lo Chai should be ~20-35 km, got $distKm", distKm in 18.0..38.0)
        assertTrue(
            "Steps should mention Lô Lô Chải branch junction",
            route.steps.any { it.instructionVi.contains("Lô Lô Chải") || it.roadName.contains("Lô Lô Chải") }
        )
    }

    @Test
    fun `route from Ma Pi Leng to Ben thuyen Ta Lang Nho Que enters Nho Que descent branch`() {
        val dest = testPlace("pl_ben_thuyen_ta_lang", "Bến thuyền Nho Quế", "ma_pi_leng_nho_que", 23.22823, 105.42979)
        val route = OfflineRoadGraphRouter.computeRouteOnGraph(
            graph = realGraph,
            originLat = 23.240621,
            originLng = 105.412083,
            destination = dest,
            vehicle = VehicleType.MOTORBIKE,
            originLabel = "Đèo Mã Pì Lèng"
        )

        val distKm = route.totalDistanceMeters / 1000.0
        assertTrue("Ma Pi Leng -> Ta Lang boat dock should be ~6-18 km, got $distKm", distKm in 5.0..20.0)
        assertTrue(
            "Steps should include descent branch to Nho Quế",
            route.steps.any { it.instructionVi.contains("Nho Quế") || it.roadName.contains("Nho Quế") }
        )
    }

    @Test
    fun `route from Yen Minh to Mau Due prefers DT176 shortcut over long loop`() {
        val dest = testPlace("pl_nga_ba_mau_due", "Ngã Ba Mậu Duệ", "mau_due_du_gia", 23.065592, 105.237738)
        val route = OfflineRoadGraphRouter.computeRouteOnGraph(
            graph = realGraph,
            originLat = 23.118139,
            originLng = 105.145654,
            destination = dest,
            vehicle = VehicleType.CAR,
            originLabel = "TT. Yên Minh"
        )

        val distKm = route.totalDistanceMeters / 1000.0
        assertTrue("Yen Minh -> Mau Due shortcut via DT176 must be < 30 km, got $distKm", distKm in 10.0..30.0)
        assertTrue(
            "Steps should mention DT176 shortcut",
            route.steps.any { it.roadName.contains("ĐT176") || it.instructionVi.contains("ĐT176") }
        )
    }
}
