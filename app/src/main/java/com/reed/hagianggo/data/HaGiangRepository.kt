package com.reed.hagianggo.data

import android.content.Context
import com.reed.hagianggo.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class HaGiangRepository(private val context: Context) {

    private val db = HaGiangDatabase.getInstance(context)

    private val _zones = MutableStateFlow<List<Zone>>(emptyList())
    val zones: StateFlow<List<Zone>> = _zones.asStateFlow()

    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places.asStateFlow()

    private val _tripConfig = MutableStateFlow(TripConfig())
    val tripConfig: StateFlow<TripConfig> = _tripConfig.asStateFlow()

    private val _itinerarySlots = MutableStateFlow<List<ItinerarySlot>>(emptyList())
    val itinerarySlots: StateFlow<List<ItinerarySlot>> = _itinerarySlots.asStateFlow()

    private val _expenses = MutableStateFlow<List<Expense>>(emptyList())
    val expenses: StateFlow<List<Expense>> = _expenses.asStateFlow()

    private val _journal = MutableStateFlow<List<JournalEntry>>(emptyList())
    val journal: StateFlow<List<JournalEntry>> = _journal.asStateFlow()

    private val _checklist = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val checklist: StateFlow<List<ChecklistItem>> = _checklist.asStateFlow()

    private val _members = MutableStateFlow<List<TripMember>>(listOf(TripMember("me", "Tôi", isMe = true)))
    val members: StateFlow<List<TripMember>> = _members.asStateFlow()

    private val _fuelState = MutableStateFlow(FuelState())
    val fuelState: StateFlow<FuelState> = _fuelState.asStateFlow()

    private val _rideStats = MutableStateFlow(RideSummaryStats())
    val rideStats: StateFlow<RideSummaryStats> = _rideStats.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        db.seedIfEmpty()
        reloadAll()
    }

    suspend fun reloadAll() = withContext(Dispatchers.IO) {
        _zones.value = db.getAllZones()
        _places.value = db.getAllPlaces()
        val config = db.getTripConfig()
        _tripConfig.value = config
        _itinerarySlots.value = db.getItinerarySlots("${config.duration.code}_${config.vehicle.code}")
            .ifEmpty { db.getItinerarySlots("3N2D_MOTORBIKE") }
        _expenses.value = db.getAllExpenses()
        _journal.value = db.getAllJournalEntries()
        _checklist.value = db.getAllChecklistItems()
        _members.value = db.getAllMembers()
        _fuelState.value = db.getFuelState()
        _rideStats.value = db.getRideSummaryStats()
    }

    suspend fun updateTripConfig(config: TripConfig) = withContext(Dispatchers.IO) {
        db.updateTripConfig(config)
        _tripConfig.value = config
        _itinerarySlots.value = db.getItinerarySlots("${config.duration.code}_${config.vehicle.code}")
            .ifEmpty { db.getItinerarySlots("3N2D_MOTORBIKE") }
    }

    suspend fun savePlace(place: Place) = withContext(Dispatchers.IO) {
        db.insertOrUpdatePlace(place)
        _places.value = db.getAllPlaces()
    }

    suspend fun deletePlace(placeId: String) = withContext(Dispatchers.IO) {
        db.deletePlace(placeId)
        _places.value = db.getAllPlaces()
    }

    suspend fun toggleBookmark(placeId: String) = withContext(Dispatchers.IO) {
        val current = _places.value.firstOrNull { it.id == placeId } ?: return@withContext
        val newBookmarked = !current.isBookmarked
        db.togglePlaceBookmark(placeId, newBookmarked)
        _places.value = db.getAllPlaces()
    }

    suspend fun toggleVisited(placeId: String) = withContext(Dispatchers.IO) {
        val current = _places.value.firstOrNull { it.id == placeId } ?: return@withContext
        val newVisited = !current.isVisited
        db.togglePlaceVisited(placeId, newVisited)
        _places.value = db.getAllPlaces()
    }

    suspend fun addExpense(expense: Expense) = withContext(Dispatchers.IO) {
        db.insertExpense(expense)
        _expenses.value = db.getAllExpenses()
    }

    suspend fun deleteExpense(id: Long) = withContext(Dispatchers.IO) {
        db.deleteExpense(id)
        _expenses.value = db.getAllExpenses()
    }

    suspend fun addJournalEntry(entry: JournalEntry) = withContext(Dispatchers.IO) {
        db.insertJournalEntry(entry)
        _journal.value = db.getAllJournalEntries()
    }

    suspend fun deleteJournalEntry(id: Long) = withContext(Dispatchers.IO) {
        db.deleteJournalEntry(id)
        _journal.value = db.getAllJournalEntries()
    }

    suspend fun toggleChecklistItem(id: Long) = withContext(Dispatchers.IO) {
        val current = _checklist.value.firstOrNull { it.id == id } ?: return@withContext
        val newState = !current.isChecked
        db.toggleChecklistItem(id, newState)
        _checklist.value = db.getAllChecklistItems()
    }

    suspend fun addChecklistItem(group: String, title: String) = withContext(Dispatchers.IO) {
        db.insertChecklistItem(ChecklistItem(groupName = group, title = title, isChecked = false))
        _checklist.value = db.getAllChecklistItems()
    }

    suspend fun deleteChecklistItem(id: Long) = withContext(Dispatchers.IO) {
        db.deleteChecklistItem(id)
        _checklist.value = db.getAllChecklistItems()
    }

    // ====== Adventure Pack Repository Methods ======

    suspend fun addOrUpdateMember(member: TripMember) = withContext(Dispatchers.IO) {
        db.insertOrUpdateMember(member)
        _members.value = db.getAllMembers()
    }

    suspend fun deleteMember(memberId: String) = withContext(Dispatchers.IO) {
        db.deleteMember(memberId)
        _members.value = db.getAllMembers()
    }

    suspend fun updateFuelState(newState: FuelState) = withContext(Dispatchers.IO) {
        db.updateFuelState(newState)
        _fuelState.value = db.getFuelState()
    }

    suspend fun refreshFuelAndRideStats() = withContext(Dispatchers.IO) {
        _fuelState.value = db.getFuelState()
        _rideStats.value = db.getRideSummaryStats()
    }

    suspend fun getCachedWeatherRaw(): Map<String, Pair<String, Long>> = withContext(Dispatchers.IO) {
        db.getAllWeatherCache()
    }

    suspend fun saveCachedWeatherRaw(zoneId: String, json: String, fetchedAt: Long) = withContext(Dispatchers.IO) {
        db.saveWeatherCache(zoneId, json, fetchedAt)
    }

    suspend fun importBackup(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = JsonBackupManager.parseImportedJson(jsonString)
            for (m in payload.members) {
                db.insertOrUpdateMember(m)
            }
            for (p in payload.places) {
                db.insertOrUpdatePlace(p)
            }
            for (e in payload.expenses) {
                db.insertExpense(e)
            }
            for (c in payload.checklist) {
                db.insertChecklistItem(c)
            }
            reloadAll()
            true
        } catch (_: Exception) {
            false
        }
    }
}
