package com.reed.hagianggo.domain

import com.reed.hagianggo.model.ItinerarySlot
import com.reed.hagianggo.model.NightStayVariant
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.PlaceCategory
import com.reed.hagianggo.model.TimeSlot
import com.reed.hagianggo.model.TripDuration
import com.reed.hagianggo.model.VehicleType
import kotlin.math.roundToInt

/**
 * Logic thuần Kotlin cho Trình Chỉnh Sửa Lịch Trình Cá Nhân (Custom Itinerary Editor).
 * Đảm bảo không trùng lặp địa điểm trong cùng một ngày (tránh đụng LazyColumn key),
 * tự động tạo buổi trống khi cần và hỗ trợ đẩy lên/xuống vượt ranh giới Sáng/Trưa/Chiều/Tối.
 */
object ItineraryEditorLogic {

    fun resolvePlanCode(
        duration: TripDuration,
        vehicle: VehicleType,
        variant: NightStayVariant
    ): String {
        val suffix = if (variant == NightStayVariant.LO_LO_CHAI) "_LLC" else ""
        return "${duration.code}_${vehicle.code}$suffix"
    }

    fun fallbackPlanCode(
        duration: TripDuration,
        variant: NightStayVariant
    ): String {
        val base = if (duration == TripDuration.DAYS_4_NIGHTS_3) "4N3D" else "3N2D"
        val suffix = if (variant == NightStayVariant.LO_LO_CHAI) "_LLC" else ""
        return "${base}_MOTORBIKE$suffix"
    }

    fun buildDynamicDayRouteSubtitle(places: List<Place>, totalRoadKm: Double): String {
        if (places.isEmpty()) {
            return "Chưa có điểm dừng cho ngày này • Bấm + Thêm điểm"
        }

        val picked: List<Place> = if (places.size <= 5) {
            places
        } else {
            val middle = places.subList(1, places.lastIndex)
            val scenic = middle.filter {
                it.category == PlaceCategory.CHECKIN_VIEW || it.category == PlaceCategory.HOMESTAY
            }
            val pool = if (scenic.size >= 2) scenic else middle
            val sampledMiddle = when {
                pool.size <= 3 -> pool
                else -> listOf(
                    pool.first(),
                    pool[pool.size / 2],
                    pool.last()
                )
            }
            listOf(places.first()) + sampledMiddle + listOf(places.last())
        }

        val dedupedLabels = mutableListOf<String>()
        for (p in picked) {
            val label = shortPlaceLabel(p.name)
            if (label.isNotEmpty() && dedupedLabels.lastOrNull() != label) {
                dedupedLabels.add(label)
            }
        }
        val routeText = dedupedLabels.joinToString(" ➔ ")
        return if (totalRoadKm >= 1.0) {
            "$routeText (~${totalRoadKm.roundToInt()}km)"
        } else {
            routeText
        }
    }

    private fun shortPlaceLabel(rawName: String): String {
        val trimmed = rawName.trim()
        val parenIdx = trimmed.indexOf(" (")
        return if (parenIdx >= 4) {
            trimmed.substring(0, parenIdx).trim()
        } else {
            trimmed
        }
    }

    private val SLOT_ORDER = listOf(
        TimeSlot.MORNING,
        TimeSlot.NOON,
        TimeSlot.AFTERNOON,
        TimeSlot.EVENING
    )

    fun defaultSlotTitle(dayNumber: Int, slot: TimeSlot): String {
        return "Lịch trình ${slot.title.lowercase()} Ngày $dayNumber"
    }

    fun addPlace(
        slots: List<ItinerarySlot>,
        planCode: String,
        dayNumber: Int,
        targetSlot: TimeSlot,
        placeId: String
    ): List<ItinerarySlot> {
        val cleaned = removePlace(slots, dayNumber, placeId).toMutableList()
        val existingIdx = cleaned.indexOfFirst { it.dayNumber == dayNumber && it.slot == targetSlot }
        if (existingIdx >= 0) {
            val cur = cleaned[existingIdx]
            cleaned[existingIdx] = cur.copy(
                suggestedPlaceIds = cur.suggestedPlaceIds + placeId
            )
        } else {
            cleaned.add(
                ItinerarySlot(
                    id = 0L,
                    planCode = planCode,
                    dayNumber = dayNumber,
                    slot = targetSlot,
                    title = defaultSlotTitle(dayNumber, targetSlot),
                    suggestedPlaceIds = listOf(placeId)
                )
            )
        }
        return canonicalSort(cleaned)
    }

    fun removePlace(
        slots: List<ItinerarySlot>,
        dayNumber: Int,
        placeId: String
    ): List<ItinerarySlot> {
        return slots.map { item ->
            if (item.dayNumber == dayNumber && placeId in item.suggestedPlaceIds) {
                item.copy(suggestedPlaceIds = item.suggestedPlaceIds.filter { it != placeId })
            } else {
                item
            }
        }
    }

    fun movePlaceWithinDay(
        slots: List<ItinerarySlot>,
        planCode: String,
        dayNumber: Int,
        placeId: String,
        direction: Int
    ): List<ItinerarySlot> {
        if (direction == 0) return slots
        val normalized = ensureAllSlotsForDay(slots, planCode, dayNumber)
        val dayMap = linkedMapOf<TimeSlot, MutableList<String>>()
        for (s in SLOT_ORDER) {
            val items = normalized
                .filter { it.dayNumber == dayNumber && it.slot == s }
                .flatMap { it.suggestedPlaceIds }
                .toMutableList()
            dayMap[s] = items
        }

        // Tìm vị trí hiện tại của placeId
        var currentSlotIdx = -1
        var currentItemIdx = -1
        for ((sIdx, s) in SLOT_ORDER.withIndex()) {
            val list = dayMap[s] ?: continue
            val idx = list.indexOf(placeId)
            if (idx >= 0) {
                currentSlotIdx = sIdx
                currentItemIdx = idx
                break
            }
        }
        if (currentSlotIdx < 0 || currentItemIdx < 0) return slots

        val currentSlot = SLOT_ORDER[currentSlotIdx]
        val currentList = dayMap[currentSlot]!!

        if (direction < 0) {
            if (currentItemIdx > 0) {
                // Đổi chỗ ngay bên trên trong cùng buổi
                val prevId = currentList[currentItemIdx - 1]
                currentList[currentItemIdx - 1] = placeId
                currentList[currentItemIdx] = prevId
            } else if (currentSlotIdx > 0) {
                // Đang ở đầu buổi -> chuyển sang cuối buổi trước
                currentList.removeAt(currentItemIdx)
                val prevSlot = SLOT_ORDER[currentSlotIdx - 1]
                dayMap[prevSlot]!!.add(placeId)
            } else {
                return slots
            }
        } else {
            if (currentItemIdx < currentList.lastIndex) {
                // Đổi chỗ ngay bên dưới trong cùng buổi
                val nextId = currentList[currentItemIdx + 1]
                currentList[currentItemIdx + 1] = placeId
                currentList[currentItemIdx] = nextId
            } else if (currentSlotIdx < SLOT_ORDER.lastIndex) {
                // Đang ở cuối buổi -> chuyển lên đầu buổi tiếp theo
                currentList.removeAt(currentItemIdx)
                val nextSlot = SLOT_ORDER[currentSlotIdx + 1]
                dayMap[nextSlot]!!.add(0, placeId)
            } else {
                return slots
            }
        }

        val updated = normalized.map { item ->
            if (item.dayNumber != dayNumber) {
                item
            } else {
                item.copy(suggestedPlaceIds = dayMap[item.slot]?.toList() ?: item.suggestedPlaceIds)
            }
        }
        return canonicalSort(updated)
    }

    fun movePlaceToDaySlot(
        slots: List<ItinerarySlot>,
        planCode: String,
        fromDay: Int,
        toDay: Int,
        toSlot: TimeSlot,
        placeId: String
    ): List<ItinerarySlot> {
        val afterRemove = removePlace(slots, fromDay, placeId)
        return addPlace(afterRemove, planCode, toDay, toSlot, placeId)
    }

    private fun ensureAllSlotsForDay(
        slots: List<ItinerarySlot>,
        planCode: String,
        dayNumber: Int
    ): List<ItinerarySlot> {
        val result = ArrayList<ItinerarySlot>(slots.size + 4)
        // Gộp nếu có lỡ dư nhiều row cho cùng (dayNumber, slot)
        val mergedDaySlots = mutableMapOf<TimeSlot, ItinerarySlot>()
        for (item in slots) {
            if (item.dayNumber != dayNumber) {
                result.add(item)
            } else {
                val existing = mergedDaySlots[item.slot]
                if (existing == null) {
                    mergedDaySlots[item.slot] = item
                } else {
                    mergedDaySlots[item.slot] = existing.copy(
                        suggestedPlaceIds = existing.suggestedPlaceIds + item.suggestedPlaceIds
                    )
                }
            }
        }
        for (s in SLOT_ORDER) {
            result.add(
                mergedDaySlots[s] ?: ItinerarySlot(
                    id = 0L,
                    planCode = planCode,
                    dayNumber = dayNumber,
                    slot = s,
                    title = defaultSlotTitle(dayNumber, s),
                    suggestedPlaceIds = emptyList()
                )
            )
        }
        return result
    }

    private fun canonicalSort(slots: List<ItinerarySlot>): List<ItinerarySlot> {
        return slots.sortedWith(
            compareBy<ItinerarySlot> { it.dayNumber }
                .thenBy { SLOT_ORDER.indexOf(it.slot).let { idx -> if (idx >= 0) idx else 99 } }
        )
    }
}
