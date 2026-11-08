package com.reed.hagianggo.domain

import com.reed.hagianggo.model.*

/**
 * Cảnh báo Tầm hoạt động Xăng & Điểm mù Cây xăng trên Hà Giang Loop (Fuel Range Guard).
 * Đặc biệt cảnh báo trước các cung đèo dài > 40km vắng bóng cây xăng Petrolimex chính quy.
 */
object FuelRangeGuard {

    private data class MountainFuelGap(
        val zoneIds: Set<String>,
        val gapKm: Double,
        val title: String,
        val detail: String
    )

    // Các chặng đèo khét tiếng ít cây xăng lớn trên vòng lặp Hà Giang
    private val KNOWN_FUEL_GAPS = listOf(
        MountainFuelGap(
            zoneIds = setOf("meo_vac", "ma_pi_leng_nho_que"),
            gapKm = 73.0,
            title = "Chặng Mèo Vạc ➔ Mậu Duệ ➔ Du Già (~73 km)",
            detail = "Đường đèo dốc gắt & cua chữ M liên tục, cực ít cây xăng lớn. Nên đổ đầy bình tại TT. Mèo Vạc trước khi xuất phát!"
        ),
        MountainFuelGap(
            zoneIds = setOf("mau_due_du_gia"),
            gapKm = 72.0,
            title = "Chặng Du Già ➔ Đường Thượng ➔ TP. Hà Giang (~72 km)",
            detail = "Cung Đường Thượng – Thái An gần như không có trạm xăng chính quy trên đèo. Kiểm tra kỹ bình xăng tại Du Già."
        ),
        MountainFuelGap(
            zoneIds = setOf("yen_minh", "sung_la_pho_bang"),
            gapKm = 44.0,
            title = "Đoạn leo dốc Thẩm Mã ➔ Phố Cáo ➔ Lũng Cú (~44 km)",
            detail = "Leo dốc gắt hao xăng gấp ~1.3 lần đường bằng. Ưu tiên đổ đầy tại TT. Yên Minh hoặc Phố Cáo."
        )
    )

    fun defaultMaxRangeForVehicle(vehicle: VehicleType): Int = when (vehicle) {
        VehicleType.MOTORBIKE -> 135 // Bình ~3.7-4L đi đèo dốc thực tế an toàn ~135km
        VehicleType.CAR -> 450
    }

    fun evaluate(
        fuelState: FuelState,
        vehicle: VehicleType,
        currentLat: Double?,
        currentLng: Double?,
        activeZone: Zone?,
        places: List<Place>
    ): FuelAdvisory {
        val zoneLat = activeZone?.centerLat ?: 22.827330
        val zoneLng = activeZone?.centerLng ?: 104.984169

        // Nếu người dùng đang ở ngoài Hà Giang (> 80km, ví dụ ở nhà chuẩn bị lịch trình),
        // tính cây xăng gần nhất theo tâm Cụm chặng đang chọn trên Hà Giang thay vì tính từ Hà Nội/Hải Dương
        val isNearLoop = if (currentLat != null && currentLng != null) {
            LocationMath.calculateDistanceKm(currentLat, currentLng, 23.0950, 105.1900) <= 85.0
        } else false

        val refLat = if (isNearLoop && currentLat != null) currentLat else zoneLat
        val refLng = if (isNearLoop && currentLng != null) currentLng else zoneLng

        val fuelStations = places.filter { it.category == PlaceCategory.FUEL }
        val nearestPair = fuelStations.map { station ->
            val roadKm = LocationMath.calculateDistanceKm(refLat, refLng, station.lat, station.lng) * 1.45
            station to roadKm
        }.minByOrNull { it.second }

        val maxRange = fuelState.maxRangeKm.coerceAtLeast(50)
        val remaining = fuelState.remainingKm.coerceIn(0.0, maxRange.toDouble())
        val percent = (remaining / maxRange.toDouble()).toFloat().coerceIn(0f, 1f)

        val currentZoneId = activeZone?.id ?: "tp_ha_giang"
        val matchingGap = KNOWN_FUEL_GAPS.firstOrNull { currentZoneId in it.zoneIds }
        val nextGapKm = matchingGap?.gapKm ?: 32.0

        val isCritical = remaining <= 35.0 ||
            (matchingGap != null && remaining < matchingGap.gapKm * 1.15) ||
            (nearestPair != null && remaining <= nearestPair.second + 12.0)

        return FuelAdvisory(
            nearestStation = nearestPair?.first,
            distanceToNearestKm = nearestPair?.second ?: 0.0,
            nextGapAheadKm = nextGapKm,
            gapWarningTitle = matchingGap?.title,
            gapWarningDetail = matchingGap?.detail,
            isCriticalLow = isCritical,
            fuelPercent = percent
        )
    }
}
