package com.reed.hagianggo.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.reed.hagianggo.model.*
import org.json.JSONObject

class HaGiangDatabase(private val context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "hagiang_go.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var INSTANCE: HaGiangDatabase? = null

        fun getInstance(context: Context): HaGiangDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HaGiangDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE zones (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                seq INTEGER NOT NULL,
                center_lat REAL NOT NULL,
                center_lng REAL NOT NULL,
                description TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE places (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                zone_id TEXT NOT NULL,
                category TEXT NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                phone TEXT,
                address_hint TEXT,
                time_slots TEXT,
                weather_fit TEXT,
                duration_min INTEGER,
                car_friendly INTEGER,
                price_est TEXT,
                practical_tip TEXT,
                is_bookmarked INTEGER DEFAULT 0,
                is_visited INTEGER DEFAULT 0,
                user_note TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE trip_config (
                id INTEGER PRIMARY KEY,
                duration TEXT NOT NULL,
                vehicle TEXT NOT NULL,
                current_day INTEGER NOT NULL,
                total_budget INTEGER NOT NULL,
                sunset_hour INTEGER NOT NULL,
                sunset_minute INTEGER NOT NULL,
                preferences TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE itinerary_slots (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                plan_code TEXT NOT NULL,
                day_number INTEGER NOT NULL,
                slot TEXT NOT NULL,
                title TEXT NOT NULL,
                suggested_place_ids TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount INTEGER NOT NULL,
                category TEXT NOT NULL,
                title TEXT NOT NULL,
                day_number INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                paid_by_id TEXT DEFAULT 'me',
                split_ids TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE journal_entries (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                place_id TEXT,
                day_number INTEGER NOT NULL,
                zone_id TEXT NOT NULL,
                content TEXT NOT NULL,
                photo_path TEXT,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE checklist_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_name TEXT NOT NULL,
                title TEXT NOT NULL,
                is_checked INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ride_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER DEFAULT 0,
                total_distance_km REAL DEFAULT 0,
                max_speed_kmh REAL DEFAULT 0,
                avg_speed_kmh REAL DEFAULT 0,
                max_altitude REAL DEFAULT 0,
                min_altitude REAL DEFAULT 0,
                status TEXT DEFAULT 'RECORDING'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ride_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                lat REAL NOT NULL,
                lng REAL NOT NULL,
                altitude REAL NOT NULL,
                speed REAL NOT NULL,
                timestamp INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Seed default config
        val configValues = ContentValues().apply {
            put("id", 1)
            put("duration", TripDuration.DAYS_3_NIGHTS_2.code)
            put("vehicle", VehicleType.MOTORBIKE.code)
            put("current_day", 1)
            put("total_budget", 3000000L)
            put("sunset_hour", 17)
            put("sunset_minute", 15)
            put("preferences", "view,food,cafe")
        }
        db.insert("trip_config", null, configValues)

        ensureAdventurePackTables(db)
    }

    private fun ensureAdventurePackTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS trip_members (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                is_me INTEGER DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS weather_cache (
                zone_id TEXT PRIMARY KEY,
                forecast_json TEXT NOT NULL,
                fetched_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS rider_safety_state (
                id INTEGER PRIMARY KEY,
                max_range_km INTEGER NOT NULL,
                remaining_km REAL NOT NULL,
                last_refuel_at INTEGER NOT NULL,
                emergency_phone TEXT DEFAULT ''
            )
            """.trimIndent()
        )

        val defaultMember = ContentValues().apply {
            put("id", "me")
            put("name", "Tôi")
            put("is_me", 1)
        }
        db.insertWithOnConflict("trip_members", null, defaultMember, SQLiteDatabase.CONFLICT_IGNORE)

        val defaultFuel = ContentValues().apply {
            put("id", 1)
            put("max_range_km", 135)
            put("remaining_km", 135.0)
            put("last_refuel_at", 0L)
            put("emergency_phone", "")
        }
        db.insertWithOnConflict("rider_safety_state", null, defaultFuel, SQLiteDatabase.CONFLICT_IGNORE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ride_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER DEFAULT 0,
                    total_distance_km REAL DEFAULT 0,
                    max_speed_kmh REAL DEFAULT 0,
                    avg_speed_kmh REAL DEFAULT 0,
                    max_altitude REAL DEFAULT 0,
                    min_altitude REAL DEFAULT 0,
                    status TEXT DEFAULT 'RECORDING'
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ride_points (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    lat REAL NOT NULL,
                    lng REAL NOT NULL,
                    altitude REAL NOT NULL,
                    speed REAL NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            ensureAdventurePackTables(db)
            try {
                db.execSQL("ALTER TABLE expenses ADD COLUMN paid_by_id TEXT DEFAULT 'me'")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE expenses ADD COLUMN split_ids TEXT DEFAULT ''")
            } catch (_: Exception) {}
        }
    }

    fun seedIfEmpty() {
        val db = writableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM zones", null)
        val existingZoneCount = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        val isFirstSeed = existingZoneCount == 0

        try {
            val jsonString = context.assets.open("hagiang_seed.json").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            db.beginTransaction()
            try {
                // 1. Luôn đồng bộ tọa độ chuẩn xác của 8 Zones
                val zonesArray = root.optJSONArray("zones")
                if (zonesArray != null) {
                    for (i in 0 until zonesArray.length()) {
                        val obj = zonesArray.getJSONObject(i)
                        val cv = ContentValues().apply {
                            put("id", obj.getString("id"))
                            put("name", obj.getString("name"))
                            put("seq", obj.getInt("seq"))
                            put("center_lat", obj.getDouble("centerLat"))
                            put("center_lng", obj.getDouble("centerLng"))
                            put("description", obj.optString("description", ""))
                        }
                        db.insertWithOnConflict("zones", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }

                // 2. Đồng bộ tọa độ GPS chính xác cho tất cả Places gốc (bảo toàn bookmark, đã ghé, ghi chú của người dùng)
                val placesArray = root.optJSONArray("places")
                if (placesArray != null) {
                    for (i in 0 until placesArray.length()) {
                        val obj = placesArray.getJSONObject(i)
                        val placeId = obj.getString("id")
                        val slotsArray = obj.optJSONArray("timeSlots")
                        val slots = mutableListOf<String>()
                        if (slotsArray != null) {
                            for (s in 0 until slotsArray.length()) {
                                slots.add(slotsArray.getString(s))
                            }
                        }
                        val coreValues = ContentValues().apply {
                            put("name", obj.getString("name"))
                            put("zone_id", obj.getString("zoneId"))
                            put("category", obj.getString("category"))
                            put("lat", obj.getDouble("lat"))
                            put("lng", obj.getDouble("lng"))
                            put("phone", obj.optString("phone", ""))
                            put("address_hint", obj.optString("addressHint", ""))
                            put("time_slots", slots.joinToString(","))
                            put("weather_fit", obj.optString("weatherFit", "ANY"))
                            put("car_friendly", if (obj.optBoolean("carFriendly", true)) 1 else 0)
                            put("price_est", obj.optString("priceEst", ""))
                            put("practical_tip", obj.optString("practicalTip", ""))
                        }

                        val updatedRows = db.update("places", coreValues, "id = ?", arrayOf(placeId))
                        if (updatedRows == 0) {
                            coreValues.apply {
                                put("id", placeId)
                                put("duration_min", obj.optInt("durationMin", 30))
                                put("is_bookmarked", 0)
                                put("is_visited", 0)
                                put("user_note", "")
                            }
                            db.insertWithOnConflict("places", null, coreValues, SQLiteDatabase.CONFLICT_REPLACE)
                        }
                    }
                }

                // 3. Đồng bộ lịch trình chuẩn (Itineraries) để bao gồm các mốc mới chính xác
                val itinerariesArray = root.optJSONArray("default_itineraries")
                if (itinerariesArray != null) {
                    db.delete("itinerary_slots", null, null)
                    for (i in 0 until itinerariesArray.length()) {
                        val obj = itinerariesArray.getJSONObject(i)
                        val placeIdsArray = obj.optJSONArray("suggestedPlaceIds")
                        val placeIds = mutableListOf<String>()
                        if (placeIdsArray != null) {
                            for (p in 0 until placeIdsArray.length()) {
                                placeIds.add(placeIdsArray.getString(p))
                            }
                        }
                        val cv = ContentValues().apply {
                            put("plan_code", obj.getString("planCode"))
                            put("day_number", obj.getInt("dayNumber"))
                            put("slot", obj.getString("slot"))
                            put("title", obj.getString("title"))
                            put("suggested_place_ids", placeIds.joinToString(","))
                        }
                        db.insert("itinerary_slots", null, cv)
                    }
                }

                // 4. Checklist chỉ tạo lần đầu để giữ trạng thái tick đồ của người dùng
                if (isFirstSeed) {
                    val checklistArray = root.optJSONArray("checklist_seed")
                    if (checklistArray != null) {
                        for (i in 0 until checklistArray.length()) {
                            val obj = checklistArray.getJSONObject(i)
                            val cv = ContentValues().apply {
                                put("group_name", obj.getString("group"))
                                put("title", obj.getString("title"))
                                put("is_checked", 0)
                            }
                            db.insert("checklist_items", null, cv)
                        }
                    }
                }

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
            // Log fallback
        }
    }

    // ====== CRUD Operations ======

    fun getAllZones(): List<Zone> {
        val list = mutableListOf<Zone>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT id, name, seq, center_lat, center_lng, description FROM zones ORDER BY seq ASC", null)
        while (cursor.moveToNext()) {
            list.add(
                Zone(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    seq = cursor.getInt(2),
                    centerLat = cursor.getDouble(3),
                    centerLng = cursor.getDouble(4),
                    description = cursor.getString(5) ?: ""
                )
            )
        }
        cursor.close()
        return list
    }

    fun getAllPlaces(): List<Place> {
        val list = mutableListOf<Place>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT id, name, zone_id, category, lat, lng, phone, address_hint,
                   time_slots, weather_fit, duration_min, car_friendly, price_est,
                   practical_tip, is_bookmarked, is_visited, user_note
            FROM places
            ORDER BY name ASC
            """.trimIndent(),
            null
        )
        while (cursor.moveToNext()) {
            val slotsStr = cursor.getString(8) ?: ""
            val slots = if (slotsStr.isNotBlank()) {
                slotsStr.split(",").map { TimeSlot.fromCode(it) }
            } else {
                listOf(TimeSlot.MORNING, TimeSlot.AFTERNOON)
            }
            list.add(
                Place(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    zoneId = cursor.getString(2),
                    category = PlaceCategory.fromCode(cursor.getString(3)),
                    lat = cursor.getDouble(4),
                    lng = cursor.getDouble(5),
                    phone = cursor.getString(6) ?: "",
                    addressHint = cursor.getString(7) ?: "",
                    timeSlots = slots,
                    weatherFit = WeatherFit.fromCode(cursor.getString(9) ?: "ANY"),
                    durationMin = cursor.getInt(10),
                    carFriendly = cursor.getInt(11) == 1,
                    priceEst = cursor.getString(12) ?: "",
                    practicalTip = cursor.getString(13) ?: "",
                    isBookmarked = cursor.getInt(14) == 1,
                    isVisited = cursor.getInt(15) == 1,
                    userNote = cursor.getString(16) ?: ""
                )
            )
        }
        cursor.close()
        return list
    }

    fun insertOrUpdatePlace(place: Place) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("id", place.id)
            put("name", place.name)
            put("zone_id", place.zoneId)
            put("category", place.category.code)
            put("lat", place.lat)
            put("lng", place.lng)
            put("phone", place.phone)
            put("address_hint", place.addressHint)
            put("time_slots", place.timeSlots.joinToString(",") { it.code })
            put("weather_fit", place.weatherFit.code)
            put("duration_min", place.durationMin)
            put("car_friendly", if (place.carFriendly) 1 else 0)
            put("price_est", place.priceEst)
            put("practical_tip", place.practicalTip)
            put("is_bookmarked", if (place.isBookmarked) 1 else 0)
            put("is_visited", if (place.isVisited) 1 else 0)
            put("user_note", place.userNote)
        }
        db.insertWithOnConflict("places", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePlace(placeId: String) {
        writableDatabase.delete("places", "id = ?", arrayOf(placeId))
    }

    fun togglePlaceBookmark(placeId: String, bookmarked: Boolean) {
        val cv = ContentValues().apply { put("is_bookmarked", if (bookmarked) 1 else 0) }
        writableDatabase.update("places", cv, "id = ?", arrayOf(placeId))
    }

    fun togglePlaceVisited(placeId: String, visited: Boolean) {
        val cv = ContentValues().apply { put("is_visited", if (visited) 1 else 0) }
        writableDatabase.update("places", cv, "id = ?", arrayOf(placeId))
    }

    fun getTripConfig(): TripConfig {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, duration, vehicle, current_day, total_budget, sunset_hour, sunset_minute, preferences FROM trip_config WHERE id = 1",
            null
        )
        val config = if (cursor.moveToFirst()) {
            val prefStr = cursor.getString(7) ?: "view,food,cafe"
            val prefSet = prefStr.split(",").filter { it.isNotBlank() }.toSet()
            TripConfig(
                id = cursor.getInt(0),
                duration = TripDuration.fromCode(cursor.getString(1)),
                vehicle = VehicleType.fromCode(cursor.getString(2)),
                currentDay = cursor.getInt(3),
                totalBudget = cursor.getLong(4),
                sunsetCutoffHour = cursor.getInt(5),
                sunsetCutoffMinute = cursor.getInt(6),
                preferences = prefSet
            )
        } else {
            TripConfig()
        }
        cursor.close()
        return config
    }

    fun updateTripConfig(config: TripConfig) {
        val cv = ContentValues().apply {
            put("duration", config.duration.code)
            put("vehicle", config.vehicle.code)
            put("current_day", config.currentDay)
            put("total_budget", config.totalBudget)
            put("sunset_hour", config.sunsetCutoffHour)
            put("sunset_minute", config.sunsetCutoffMinute)
            put("preferences", config.preferences.joinToString(","))
        }
        writableDatabase.update("trip_config", cv, "id = 1", null)
    }

    fun getItinerarySlots(planCode: String): List<ItinerarySlot> {
        val list = mutableListOf<ItinerarySlot>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, plan_code, day_number, slot, title, suggested_place_ids FROM itinerary_slots WHERE plan_code = ? ORDER BY day_number ASC, id ASC",
            arrayOf(planCode)
        )
        while (cursor.moveToNext()) {
            val idsStr = cursor.getString(5) ?: ""
            val ids = if (idsStr.isNotBlank()) idsStr.split(",") else emptyList()
            list.add(
                ItinerarySlot(
                    id = cursor.getLong(0),
                    planCode = cursor.getString(1),
                    dayNumber = cursor.getInt(2),
                    slot = TimeSlot.fromCode(cursor.getString(3)),
                    title = cursor.getString(4),
                    suggestedPlaceIds = ids
                )
            )
        }
        cursor.close()
        return list
    }

    // ====== Expenses ======

    fun getAllExpenses(): List<Expense> {
        val list = mutableListOf<Expense>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, amount, category, title, day_number, created_at, paid_by_id, split_ids FROM expenses ORDER BY created_at DESC",
            null
        )
        while (cursor.moveToNext()) {
            val paidBy = cursor.getString(6)?.takeIf { it.isNotBlank() } ?: "me"
            val splitRaw = cursor.getString(7) ?: ""
            val splitList = if (splitRaw.isNotBlank()) {
                splitRaw.split(",").filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            list.add(
                Expense(
                    id = cursor.getLong(0),
                    amount = cursor.getLong(1),
                    category = ExpenseCategory.fromCode(cursor.getString(2)),
                    title = cursor.getString(3),
                    dayNumber = cursor.getInt(4),
                    createdAt = cursor.getLong(5),
                    paidByMemberId = paidBy,
                    splitAmongIds = splitList
                )
            )
        }
        cursor.close()
        return list
    }

    fun insertExpense(expense: Expense): Long {
        val cv = ContentValues().apply {
            put("amount", expense.amount)
            put("category", expense.category.code)
            put("title", expense.title)
            put("day_number", expense.dayNumber)
            put("created_at", expense.createdAt)
            put("paid_by_id", expense.paidByMemberId.ifBlank { "me" })
            put("split_ids", expense.splitAmongIds.joinToString(","))
        }
        return writableDatabase.insert("expenses", null, cv)
    }

    fun deleteExpense(id: Long) {
        writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    // ====== Journal ======

    fun getAllJournalEntries(): List<JournalEntry> {
        val list = mutableListOf<JournalEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, place_id, day_number, zone_id, content, photo_path, created_at FROM journal_entries ORDER BY created_at DESC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                JournalEntry(
                    id = cursor.getLong(0),
                    placeId = cursor.getString(1),
                    dayNumber = cursor.getInt(2),
                    zoneId = cursor.getString(3),
                    content = cursor.getString(4),
                    photoPath = cursor.getString(5),
                    createdAt = cursor.getLong(6)
                )
            )
        }
        cursor.close()
        return list
    }

    fun insertJournalEntry(entry: JournalEntry): Long {
        val cv = ContentValues().apply {
            put("place_id", entry.placeId)
            put("day_number", entry.dayNumber)
            put("zone_id", entry.zoneId)
            put("content", entry.content)
            put("photo_path", entry.photoPath)
            put("created_at", entry.createdAt)
        }
        return writableDatabase.insert("journal_entries", null, cv)
    }

    fun deleteJournalEntry(id: Long) {
        writableDatabase.delete("journal_entries", "id = ?", arrayOf(id.toString()))
    }

    // ====== Checklist ======

    fun getAllChecklistItems(): List<ChecklistItem> {
        val list = mutableListOf<ChecklistItem>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, group_name, title, is_checked FROM checklist_items ORDER BY group_name ASC, id ASC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                ChecklistItem(
                    id = cursor.getLong(0),
                    groupName = cursor.getString(1),
                    title = cursor.getString(2),
                    isChecked = cursor.getInt(3) == 1
                )
            )
        }
        cursor.close()
        return list
    }

    fun insertChecklistItem(item: ChecklistItem): Long {
        val cv = ContentValues().apply {
            put("group_name", item.groupName)
            put("title", item.title)
            put("is_checked", if (item.isChecked) 1 else 0)
        }
        return writableDatabase.insert("checklist_items", null, cv)
    }

    fun toggleChecklistItem(id: Long, isChecked: Boolean) {
        val cv = ContentValues().apply { put("is_checked", if (isChecked) 1 else 0) }
        writableDatabase.update("checklist_items", cv, "id = ?", arrayOf(id.toString()))
    }

    fun deleteChecklistItem(id: Long) {
        writableDatabase.delete("checklist_items", "id = ?", arrayOf(id.toString()))
    }

    // ====== Live Ride Sessions & Tracking Points ======

    fun createRideSession(): Long {
        val cv = ContentValues().apply {
            put("start_time", System.currentTimeMillis())
            put("status", "RECORDING")
        }
        return writableDatabase.insert("ride_sessions", null, cv)
    }

    fun finishRideSession(
        sessionId: Long,
        totalDistKm: Double,
        maxSpeedKmh: Float,
        avgSpeedKmh: Float,
        maxAlt: Double,
        minAlt: Double
    ) {
        val cv = ContentValues().apply {
            put("end_time", System.currentTimeMillis())
            put("total_distance_km", totalDistKm)
            put("max_speed_kmh", maxSpeedKmh)
            put("avg_speed_kmh", avgSpeedKmh)
            put("max_altitude", maxAlt)
            put("min_altitude", minAlt)
            put("status", "FINISHED")
        }
        writableDatabase.update("ride_sessions", cv, "id = ?", arrayOf(sessionId.toString()))
    }

    fun insertRidePoint(point: RidePoint): Long {
        val cv = ContentValues().apply {
            put("session_id", point.sessionId)
            put("lat", point.lat)
            put("lng", point.lng)
            put("altitude", point.altitude)
            put("speed", point.speedKmh)
            put("timestamp", point.timestamp)
        }
        return writableDatabase.insert("ride_points", null, cv)
    }

    fun getRecentRidePoints(sessionId: Long, limit: Int = 120): List<Pair<Double, Double>> {
        val list = mutableListOf<Pair<Double, Double>>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT lat, lng FROM ride_points WHERE session_id = ? ORDER BY id ASC LIMIT ?",
            arrayOf(sessionId.toString(), limit.toString())
        )
        while (cursor.moveToNext()) {
            list.add(Pair(cursor.getDouble(0), cursor.getDouble(1)))
        }
        cursor.close()
        return list
    }

    // ====== Adventure Pack: Trip Members (Splitbill) ======

    fun getAllMembers(): List<TripMember> {
        val list = mutableListOf<TripMember>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT id, name, is_me FROM trip_members ORDER BY is_me DESC, name ASC",
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                TripMember(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    isMe = cursor.getInt(2) == 1
                )
            )
        }
        cursor.close()
        if (list.none { it.id == "me" }) {
            val me = TripMember(id = "me", name = "Tôi", isMe = true)
            insertOrUpdateMember(me)
            list.add(0, me)
        }
        return list
    }

    fun insertOrUpdateMember(member: TripMember) {
        val cv = ContentValues().apply {
            put("id", member.id)
            put("name", member.name.trim().ifBlank { "Bạn đồng hành" })
            put("is_me", if (member.isMe) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("trip_members", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteMember(memberId: String) {
        if (memberId == "me") return
        writableDatabase.delete("trip_members", "id = ?", arrayOf(memberId))
    }

    // ====== Adventure Pack: Fuel Guard & Emergency Contact ======

    fun getFuelState(): FuelState {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT max_range_km, remaining_km, last_refuel_at, emergency_phone FROM rider_safety_state WHERE id = 1",
            null
        )
        val state = if (cursor.moveToFirst()) {
            FuelState(
                maxRangeKm = cursor.getInt(0).coerceAtLeast(50),
                remainingKm = cursor.getDouble(1).coerceAtLeast(0.0),
                lastRefuelAt = cursor.getLong(2),
                emergencyContactPhone = cursor.getString(3) ?: ""
            )
        } else {
            FuelState()
        }
        cursor.close()
        return state
    }

    fun updateFuelState(state: FuelState) {
        val cv = ContentValues().apply {
            put("id", 1)
            put("max_range_km", state.maxRangeKm.coerceAtLeast(50))
            put("remaining_km", state.remainingKm.coerceIn(0.0, state.maxRangeKm.toDouble()))
            put("last_refuel_at", state.lastRefuelAt)
            put("emergency_phone", state.emergencyContactPhone.trim())
        }
        writableDatabase.insertWithOnConflict("rider_safety_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deductFuelKm(deltaKm: Double) {
        if (deltaKm <= 0.0) return
        val current = getFuelState()
        val nextRemaining = (current.remainingKm - deltaKm).coerceAtLeast(0.0)
        updateFuelState(current.copy(remainingKm = nextRemaining))
    }

    // ====== Adventure Pack: Offline 8-Zone Weather Cache ======

    fun getAllWeatherCache(): Map<String, Pair<String, Long>> {
        val map = mutableMapOf<String, Pair<String, Long>>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT zone_id, forecast_json, fetched_at FROM weather_cache", null)
        while (cursor.moveToNext()) {
            map[cursor.getString(0)] = Pair(cursor.getString(1), cursor.getLong(2))
        }
        cursor.close()
        return map
    }

    fun saveWeatherCache(zoneId: String, forecastJson: String, fetchedAt: Long) {
        val cv = ContentValues().apply {
            put("zone_id", zoneId)
            put("forecast_json", forecastJson)
            put("fetched_at", fetchedAt)
        }
        writableDatabase.insertWithOnConflict("weather_cache", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    // ====== Adventure Pack: Ride Summary Stats for Recap Poster ======

    fun getRideSummaryStats(): RideSummaryStats {
        val db = readableDatabase
        var totalKm = 0.0
        var maxSpeed = 0f
        var maxAlt = 0
        var count = 0

        val sessionCursor = db.rawQuery(
            "SELECT COALESCE(SUM(total_distance_km), 0), COALESCE(MAX(max_speed_kmh), 0), COALESCE(MAX(max_altitude), 0), COUNT(*) FROM ride_sessions",
            null
        )
        if (sessionCursor.moveToFirst()) {
            totalKm = sessionCursor.getDouble(0)
            maxSpeed = sessionCursor.getFloat(1)
            maxAlt = sessionCursor.getDouble(2).toInt()
            count = sessionCursor.getInt(3)
        }
        sessionCursor.close()

        val rawPoints = mutableListOf<Pair<Double, Double>>()
        val ptCursor = db.rawQuery(
            "SELECT lat, lng, altitude, speed FROM ride_points ORDER BY id ASC",
            null
        )
        var prevPt: Pair<Double, Double>? = null
        var pointSumKm = 0.0
        while (ptCursor.moveToNext()) {
            val lat = ptCursor.getDouble(0)
            val lng = ptCursor.getDouble(1)
            val alt = ptCursor.getDouble(2).toInt()
            val spd = ptCursor.getFloat(3)
            if (alt > maxAlt) maxAlt = alt
            if (spd > maxSpeed) maxSpeed = spd
            val prev = prevPt
            if (prev != null) {
                val d = com.reed.hagianggo.domain.LocationMath.calculateDistanceKm(prev.first, prev.second, lat, lng)
                if (d in 0.005..5.0) pointSumKm += d
            }
            prevPt = Pair(lat, lng)
            rawPoints.add(Pair(lat, lng))
        }
        ptCursor.close()

        val sampled = if (rawPoints.size <= 350) {
            rawPoints
        } else {
            val step = (rawPoints.size / 350).coerceAtLeast(1)
            rawPoints.filterIndexed { index, _ -> index % step == 0 || index == rawPoints.lastIndex }
        }

        return RideSummaryStats(
            totalRecordedKm = maxOf(totalKm, pointSumKm),
            maxRecordedSpeedKmh = maxSpeed,
            maxRecordedAltitudeM = maxAlt,
            sessionCount = count,
            allTrackPoints = sampled
        )
    }
}
