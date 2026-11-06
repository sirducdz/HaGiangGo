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
    val createdAt: Long = System.currentTimeMillis()
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
val Place.altitudeMeters: Int get() = when (zoneId) {
    "quan_ba" -> 1000
    "dong_van" -> 1100
    "lung_cu" -> 1470
    "meo_vac" -> 1150
    "du_gia" -> 450
    "bac_me" -> 250
    "yen_minh" -> 800
    else -> 300
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
    "quan_ba" -> 1000
    "dong_van" -> 1100
    "lung_cu" -> 1470
    "meo_vac" -> 1150
    "du_gia" -> 450
    "bac_me" -> 250
    "yen_minh" -> 800
    else -> 300
}

