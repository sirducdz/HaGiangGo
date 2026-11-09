package com.reed.hagianggo.model

enum class PlaceCategory(val code: String, val title: String) {
    EAT("EAT", "Quán ăn"),
    CAFE_REST("CAFE_REST", "Cà phê & Nghỉ"),
    CHECKIN_VIEW("CHECKIN_VIEW", "Ngắm cảnh & Check-in"),
    FUEL("FUEL", "Cây xăng"),
    MECHANIC_SOS("MECHANIC_SOS", "Sửa xe & Cứu hộ"),
    HOMESTAY("HOMESTAY", "Nghỉ đêm / Homestay");

    companion object {
        fun fromCode(code: String): PlaceCategory {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: CHECKIN_VIEW
        }
    }
}

enum class WeatherFit(val code: String, val title: String) {
    SUNNY_ONLY("SUNNY_ONLY", "Chỉ nên đi trời nắng"),
    RAIN_OK("RAIN_OK", "Trời mưa vẫn ổn"),
    ANY("ANY", "Thời tiết nào cũng được");

    companion object {
        fun fromCode(code: String): WeatherFit {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ANY
        }
    }
}

enum class TimeSlot(val code: String, val title: String, val timeRangeHint: String) {
    MORNING("MORNING", "Buổi sáng", "06:00 - 11:00"),
    NOON("NOON", "Buổi trưa", "11:00 - 13:30"),
    AFTERNOON("AFTERNOON", "Buổi chiều", "13:30 - 17:30"),
    EVENING("EVENING", "Buổi tối", "17:30 - 22:00");

    companion object {
        fun fromCode(code: String): TimeSlot {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MORNING
        }
    }
}

enum class VehicleType(val code: String, val title: String) {
    MOTORBIKE("MOTORBIKE", "Xe máy"),
    CAR("CAR", "Ô tô");

    companion object {
        fun fromCode(code: String): VehicleType {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MOTORBIKE
        }
    }
}

enum class TripDuration(val code: String, val title: String, val totalDays: Int) {
    DAYS_2_NIGHTS_1("2N1D", "2 Ngày 1 Đêm", 2),
    DAYS_3_NIGHTS_2("3N2D", "3 Ngày 2 Đêm", 3),
    DAYS_4_NIGHTS_3("4N3D", "4 Ngày 3 Đêm", 4);

    companion object {
        fun fromCode(code: String): TripDuration {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: DAYS_3_NIGHTS_2
        }
    }
}

enum class ExpenseCategory(val code: String, val title: String) {
    FUEL("FUEL", "Xăng cộ"),
    FOOD("FOOD", "Ăn uống"),
    STAY("STAY", "Chỗ ngủ / Homestay"),
    TICKET("TICKET", "Vé & Thuyền"),
    OTHER("OTHER", "Khoản khác");

    companion object {
        fun fromCode(code: String): ExpenseCategory {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: OTHER
        }
    }
}

data class Zone(
    val id: String,
    val name: String,
    val seq: Int,
    val centerLat: Double,
    val centerLng: Double,
    val description: String
)

data class Place(
    val id: String,
    val name: String,
    val zoneId: String,
    val category: PlaceCategory,
    val lat: Double,
    val lng: Double,
    val phone: String = "",
    val addressHint: String = "",
    val timeSlots: List<TimeSlot> = listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON),
    val weatherFit: WeatherFit = WeatherFit.ANY,
    val durationMin: Int = 30,
    val carFriendly: Boolean = true,
    val priceEst: String = "",
    val practicalTip: String = "",
    val isBookmarked: Boolean = false,
    val isVisited: Boolean = false,
    val userNote: String = ""
)

data class TripConfig(
    val id: Int = 1,
    val duration: TripDuration = TripDuration.DAYS_3_NIGHTS_2,
    val vehicle: VehicleType = VehicleType.MOTORBIKE,
    val currentDay: Int = 1,
    val totalBudget: Long = 3000000L,
    val sunsetCutoffHour: Int = 17,
    val sunsetCutoffMinute: Int = 15,
    val preferences: Set<String> = setOf("view", "food", "cafe")
)

data class ItinerarySlot(
    val id: Long = 0,
    val planCode: String,
    val dayNumber: Int,
    val slot: TimeSlot,
    val title: String,
    val suggestedPlaceIds: List<String>
)

data class Expense(
    val id: Long = 0,
    val amount: Long,
    val category: ExpenseCategory,
    val title: String,
    val dayNumber: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val paidByMemberId: String = "me",
    val splitAmongIds: List<String> = emptyList()
)

data class TripMember(
    val id: String,
    val name: String,
    val isMe: Boolean = false
)

data class MemberBalance(
    val member: TripMember,
    val totalPaid: Long,
    val totalOwed: Long,
    val netBalance: Long
)

data class SettlementTransfer(
    val fromMemberName: String,
    val toMemberName: String,
    val amount: Long
)

data class FuelState(
    val maxRangeKm: Int = 135,
    val remainingKm: Double = 135.0,
    val lastRefuelAt: Long = 0L,
    val emergencyContactPhone: String = ""
)

data class FuelAdvisory(
    val nearestStation: Place?,
    val distanceToNearestKm: Double,
    val nextGapAheadKm: Double,
    val gapWarningTitle: String?,
    val gapWarningDetail: String?,
    val isCriticalLow: Boolean,
    val fuelPercent: Float
)

data class DailyZoneTemp(
    val dateLabel: String,
    val minTempC: Int,
    val maxTempC: Int,
    val rainProbPercent: Int
)

data class ZoneWeatherForecast(
    val zoneId: String,
    val currentTempC: Int,
    val minTempC: Int,
    val maxTempC: Int,
    val rainProbPercent: Int,
    val cloudHuntScore: Int,
    val isFogHazard: Boolean,
    val summaryLabel: String,
    val dailyForecasts: List<DailyZoneTemp> = emptyList(),
    val isFromLiveSync: Boolean = false,
    val fetchedAt: Long = 0L
)

data class OfflineMapStatus(
    val isDownloading: Boolean = false,
    val downloadedTiles: Int = 0,
    val totalTiles: Int = 0,
    val storageSizeMb: Double = 0.0,
    val isReadyOffline: Boolean = false,
    val statusMessage: String = "Chưa tải gói bản đồ Offline",
    val errorMessage: String? = null
)

data class RideSummaryStats(
    val totalRecordedKm: Double = 0.0,
    val maxRecordedSpeedKmh: Float = 0f,
    val maxRecordedAltitudeM: Int = 0,
    val sessionCount: Int = 0,
    val allTrackPoints: List<Pair<Double, Double>> = emptyList()
)

data class JournalEntry(
    val id: Long = 0,
    val placeId: String? = null,
    val dayNumber: Int,
    val zoneId: String,
    val content: String,
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ChecklistItem(
    val id: Long = 0,
    val groupName: String,
    val title: String,
    val isChecked: Boolean = false
)

enum class CurrentNeed(val code: String, val title: String) {
    ANY("ANY", "Linh hoạt / Tự do"),
    EAT("EAT", "Ăn ấm bụng"),
    CAFE_REST("CAFE_REST", "Cà phê & Nghỉ"),
    VIEW("VIEW", "Ngắm cảnh & Chụp ảnh"),
    RAIN_SHELTER("RAIN_SHELTER", "Tránh mưa & Sương mù"),
    FUEL_SOS("FUEL_SOS", "Đổ xăng & Sửa xe")
}

enum class CurrentWeather(val code: String, val title: String) {
    SUNNY("SUNNY", "Nắng ráo"),
    RAIN_FOG("RAIN_FOG", "Mưa / Sương mù dày")
}

enum class AvailableDuration(val minutes: Int, val title: String) {
    MIN_30(30, "30 phút"),
    HOURS_1_2(90, "1 - 2 tiếng"),
    HALF_DAY(240, "Cả buổi")
}

data class WhatNowQuery(
    val zoneId: String? = null,
    val availableDuration: AvailableDuration = AvailableDuration.HOURS_1_2,
    val weather: CurrentWeather = CurrentWeather.SUNNY,
    val need: CurrentNeed = CurrentNeed.ANY
)

data class SuggestionResult(
    val place: Place,
    val distanceKm: Double,
    val travelTimeMin: Int,
    val matchScore: Int,
    val reasons: List<String>,
    val isSunsetWarning: Boolean = false,
    val sunsetWarningMessage: String = ""
)

// Extension properties for concise UI code
val Place.latitude: Double get() = lat
val Place.longitude: Double get() = lng
val Place.phoneNumber: String get() = phone
val Place.isShelterInRain: Boolean get() = weatherFit == WeatherFit.RAIN_OK || category == PlaceCategory.CAFE_REST || category == PlaceCategory.EAT
val Place.suggestedDurationMin: Int get() = durationMin
val Place.altitudeMeters: Int get() = when (id) {
    "pl_km0", "pl_fuel_tphg", "pl_eat_pho_tphg", "pl_sos_tphg" -> 120
    "pl_cafe_nui_cam" -> 280
    "pl_doc_bac_sum" -> 1100
    "pl_cong_troi_quan_ba", "pl_cafe_cong_troi" -> 1500
    "pl_nui_doi_quan_ba" -> 1120
    "pl_fuel_tam_son" -> 1050
    "pl_eat_trang_kim" -> 580
    "pl_sos_quan_ba" -> 480
    "pl_cay_nghien_co_don" -> 860
    "pl_rung_thong_yen_minh" -> 1080
    "pl_doc_tham_ma" -> 1320
    "pl_fuel_pho_cao" -> 1410
    "pl_nha_cua_pao", "pl_cafe_sung_la" -> 1450
    "pl_pho_bang_co_tran" -> 1380
    "pl_dinh_vua_meo", "pl_eat_sa_phin" -> 1420
    "pl_cot_co_lung_cu" -> 1470
    "pl_lo_lo_chai", "pl_cafe_cuc_bac" -> 1410
    "pl_deo_ma_pi_leng" -> 1500
    "pl_vach_da_trang" -> 1620
    "pl_cafe_panorama" -> 1280
    "pl_ben_thuyen_ta_lang" -> 380
    "pl_lang_pa_vi" -> 980
    "pl_cua_chu_m" -> 1120
    "pl_nga_ba_mau_due" -> 760
    "pl_thac_du_gia" -> 620
    "pl_sos_du_gia" -> 950
    else -> when (zoneId) {
        "tp_ha_giang" -> 120
        "quan_ba" -> 1050
        "yen_minh" -> 650
        "sung_la_pho_bang" -> 1420
        "dong_van_lung_cu" -> 1050
        "ma_pi_leng_nho_que" -> 1450
        "meo_vac" -> 1000
        "mau_due_du_gia" -> 580
        else -> 500
    }
}

val PlaceCategory.label: String get() = title
val TimeSlot.label: String get() = title
val TimeSlot.timeRange: String get() = timeRangeHint
val ExpenseCategory.label: String get() = title

val TripConfig.durationDays: Int get() = duration.totalDays

fun TripConfig.withDurationDays(days: Int): TripConfig {
    val d = when (days) {
        2 -> TripDuration.DAYS_2_NIGHTS_1
        4 -> TripDuration.DAYS_4_NIGHTS_3
        else -> TripDuration.DAYS_3_NIGHTS_2
    }
    return this.copy(duration = d)
}

val Zone.elevationMeters: Int get() = when (id) {
    "tp_ha_giang" -> 120
    "quan_ba" -> 1050
    "yen_minh" -> 650
    "sung_la_pho_bang" -> 1420
    "dong_van_lung_cu" -> 1050
    "ma_pi_leng_nho_que" -> 1500
    "meo_vac" -> 1000
    "mau_due_du_gia" -> 580
    else -> 500
}

data class RidePoint(
    val id: Long = 0,
    val sessionId: Long,
    val lat: Double,
    val lng: Double,
    val altitude: Double,
    val speedKmh: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class RideSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long = 0L,
    val totalDistanceKm: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val maxAltitude: Double = 0.0,
    val minAltitude: Double = 0.0,
    val status: String = "RECORDING" // RECORDING, PAUSED, FINISHED
)

data class LiveRideState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val sessionId: Long = 0L,
    val currentSpeedKmh: Float = 0f,
    val currentAltitude: Double = 0.0,
    val totalDistanceKm: Double = 0.0,
    val maxSpeedKmh: Float = 0f,
    val maxAltitude: Double = 0.0,
    val elapsedTimeSeconds: Long = 0L,
    val nextMilestonePlace: Place? = null,
    val distanceToNextKm: Double = 0.0,
    val recentPoints: List<Pair<Double, Double>> = emptyList(),
    val lastVisitedPlaceName: String? = null
)

enum class StopPacingStatus {
    COMPLETED,      // Đã đi qua
    SKIPPED,        // Tạm bỏ qua hôm nay để kịp giờ
    ON_TIME,        // Đúng tiến độ an toàn (trước 16:45)
    NEAR_DUSK,      // Sát giờ chiều muộn (16:45 - 17:15)
    OVERTIME_LATE   // Bị lố qua 17:15 (nguy cơ chạy đèo tối / sương mù)
}

data class ActiveStopTimer(
    val placeId: String,
    val placeName: String,
    val startedAtMillis: Long,
    val targetDurationMin: Int
)

data class ScheduledStopNode(
    val place: Place,
    val customNote: String,
    val legRoadKm: Double,
    val legTravelMin: Int,
    val arrivalMinutesOfDay: Int,
    val stayDurationMin: Int,
    val departureMinutesOfDay: Int,
    val status: StopPacingStatus
)

data class DayPacingSummary(
    val startMinutesOfDay: Int,
    val isLiveNowMode: Boolean,
    val totalRoadKm: Double,
    val totalRideMinutes: Int,
    val totalStayMinutes: Int,
    val finishMinutesOfDay: Int,
    val sunsetCutoffMinutes: Int,
    val overtimeMinutes: Int,
    val nodes: List<ScheduledStopNode>,
    val autoTrimHint: String? = null,
    val canAutoTrim: Boolean = false
)



