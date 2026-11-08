package com.reed.hagianggo.data

import com.reed.hagianggo.model.*
import org.json.JSONArray
import org.json.JSONObject

object JsonBackupManager {

    fun exportToJson(
        tripConfig: TripConfig,
        places: List<Place>,
        expenses: List<Expense>,
        checklist: List<ChecklistItem>,
        journal: List<JournalEntry>,
        members: List<TripMember> = emptyList()
    ): String {
        val root = JSONObject()

        // 1. Config
        val configObj = JSONObject().apply {
            put("duration", tripConfig.duration.code)
            put("vehicle", tripConfig.vehicle.code)
            put("currentDay", tripConfig.currentDay)
            put("totalBudget", tripConfig.totalBudget)
            put("sunsetCutoffHour", tripConfig.sunsetCutoffHour)
            put("sunsetCutoffMinute", tripConfig.sunsetCutoffMinute)
            put("preferences", JSONArray(tripConfig.preferences))
        }
        root.put("trip_config", configObj)

        // 2. Places
        val placesArray = JSONArray()
        for (p in places) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("zoneId", p.zoneId)
                put("category", p.category.code)
                put("lat", p.lat)
                put("lng", p.lng)
                put("phone", p.phone)
                put("addressHint", p.addressHint)
                put("timeSlots", JSONArray(p.timeSlots.map { it.code }))
                put("weatherFit", p.weatherFit.code)
                put("durationMin", p.durationMin)
                put("carFriendly", p.carFriendly)
                put("priceEst", p.priceEst)
                put("practicalTip", p.practicalTip)
                put("isBookmarked", p.isBookmarked)
                put("isVisited", p.isVisited)
                put("userNote", p.userNote)
            }
            placesArray.put(pObj)
        }
        root.put("places", placesArray)

        // 3. Expenses
        val expensesArray = JSONArray()
        for (e in expenses) {
            val eObj = JSONObject().apply {
                put("amount", e.amount)
                put("category", e.category.code)
                put("title", e.title)
                put("dayNumber", e.dayNumber)
                put("createdAt", e.createdAt)
                put("paidByMemberId", e.paidByMemberId)
                put("splitAmongIds", JSONArray(e.splitAmongIds))
            }
            expensesArray.put(eObj)
        }
        root.put("expenses", expensesArray)

        // 3b. Trip Members
        if (members.isNotEmpty()) {
            val membersArray = JSONArray()
            for (m in members) {
                val mObj = JSONObject().apply {
                    put("id", m.id)
                    put("name", m.name)
                    put("isMe", m.isMe)
                }
                membersArray.put(mObj)
            }
            root.put("members", membersArray)
        }

        // 4. Checklist
        val checklistArray = JSONArray()
        for (c in checklist) {
            val cObj = JSONObject().apply {
                put("groupName", c.groupName)
                put("title", c.title)
                put("isChecked", c.isChecked)
            }
            checklistArray.put(cObj)
        }
        root.put("checklist", checklistArray)

        // 5. Journal
        val journalArray = JSONArray()
        for (j in journal) {
            val jObj = JSONObject().apply {
                put("placeId", j.placeId ?: "")
                put("dayNumber", j.dayNumber)
                put("zoneId", j.zoneId)
                put("content", j.content)
                put("photoPath", j.photoPath ?: "")
                put("createdAt", j.createdAt)
            }
            journalArray.put(jObj)
        }
        root.put("journal", journalArray)

        return root.toString(2)
    }

    fun parseImportedJson(jsonString: String): ImportPayload {
        val root = JSONObject(jsonString)

        val places = mutableListOf<Place>()
        val placesArray = root.optJSONArray("places")
        if (placesArray != null) {
            for (i in 0 until placesArray.length()) {
                val obj = placesArray.getJSONObject(i)
                val slotsArray = obj.optJSONArray("timeSlots")
                val slots = mutableListOf<TimeSlot>()
                if (slotsArray != null) {
                    for (s in 0 until slotsArray.length()) {
                        slots.add(TimeSlot.fromCode(slotsArray.getString(s)))
                    }
                }
                places.add(
                    Place(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        zoneId = obj.getString("zoneId"),
                        category = PlaceCategory.fromCode(obj.getString("category")),
                        lat = obj.getDouble("lat"),
                        lng = obj.getDouble("lng"),
                        phone = obj.optString("phone", ""),
                        addressHint = obj.optString("addressHint", ""),
                        timeSlots = if (slots.isNotEmpty()) slots else listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON),
                        weatherFit = WeatherFit.fromCode(obj.optString("weatherFit", "ANY")),
                        durationMin = obj.optInt("durationMin", 30),
                        carFriendly = obj.optBoolean("carFriendly", true),
                        priceEst = obj.optString("priceEst", ""),
                        practicalTip = obj.optString("practicalTip", ""),
                        isBookmarked = obj.optBoolean("isBookmarked", false),
                        isVisited = obj.optBoolean("isVisited", false),
                        userNote = obj.optString("userNote", "")
                    )
                )
            }
        }

        val expenses = mutableListOf<Expense>()
        val expensesArray = root.optJSONArray("expenses")
        if (expensesArray != null) {
            for (i in 0 until expensesArray.length()) {
                val obj = expensesArray.getJSONObject(i)
                val splitArr = obj.optJSONArray("splitAmongIds")
                val splitIds = mutableListOf<String>()
                if (splitArr != null) {
                    for (s in 0 until splitArr.length()) {
                        splitIds.add(splitArr.getString(s))
                    }
                }
                expenses.add(
                    Expense(
                        amount = obj.getLong("amount"),
                        category = ExpenseCategory.fromCode(obj.getString("category")),
                        title = obj.getString("title"),
                        dayNumber = obj.getInt("dayNumber"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        paidByMemberId = obj.optString("paidByMemberId", "me").ifBlank { "me" },
                        splitAmongIds = splitIds
                    )
                )
            }
        }

        val members = mutableListOf<TripMember>()
        val membersArray = root.optJSONArray("members")
        if (membersArray != null) {
            for (i in 0 until membersArray.length()) {
                val obj = membersArray.getJSONObject(i)
                members.add(
                    TripMember(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        isMe = obj.optBoolean("isMe", false)
                    )
                )
            }
        }

        val checklist = mutableListOf<ChecklistItem>()
        val checklistArray = root.optJSONArray("checklist")
        if (checklistArray != null) {
            for (i in 0 until checklistArray.length()) {
                val obj = checklistArray.getJSONObject(i)
                checklist.add(
                    ChecklistItem(
                        groupName = obj.getString("groupName"),
                        title = obj.getString("title"),
                        isChecked = obj.optBoolean("isChecked", false)
                    )
                )
            }
        }

        return ImportPayload(places, expenses, checklist, members)
    }

    data class ImportPayload(
        val places: List<Place>,
        val expenses: List<Expense>,
        val checklist: List<ChecklistItem>,
        val members: List<TripMember> = emptyList()
    )
}
