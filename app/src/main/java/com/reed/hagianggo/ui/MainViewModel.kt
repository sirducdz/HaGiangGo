package com.reed.hagianggo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reed.hagianggo.data.HaGiangRepository
import com.reed.hagianggo.data.JsonBackupManager
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.OfflineLocationTracker
import com.reed.hagianggo.domain.WhatNowEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.service.RideTrackerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = HaGiangRepository(application)
    val locationTracker = OfflineLocationTracker(application)

    val zones = repository.zones
    val places = repository.places
    val tripConfig = repository.tripConfig
    val itinerarySlots = repository.itinerarySlots
    val expenses = repository.expenses
    val journal = repository.journal
    val checklist = repository.checklist

    val currentLocation = locationTracker.currentLocation
    val isGpsEnabled = locationTracker.isGpsEnabled

    private val _selectedTab = MutableStateFlow(1) // Mặc định mở tab "Hôm nay" (1)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isWhatNowOpen = MutableStateFlow(false)
    val isWhatNowOpen: StateFlow<Boolean> = _isWhatNowOpen.asStateFlow()

    private val _whatNowQuery = MutableStateFlow(WhatNowQuery())
    val whatNowQuery: StateFlow<WhatNowQuery> = _whatNowQuery.asStateFlow()

    private val _shuffleOffset = MutableStateFlow(0)

    private val _editingPlace = MutableStateFlow<Place?>(null)
    val editingPlace: StateFlow<Place?> = _editingPlace.asStateFlow()

    private val _isPlaceFormOpen = MutableStateFlow(false)
    val isPlaceFormOpen: StateFlow<Boolean> = _isPlaceFormOpen.asStateFlow()

    private val _notificationMessage = MutableStateFlow<String?>(null)
    val notificationMessage: StateFlow<String?> = _notificationMessage.asStateFlow()

    // Cụm chặng hiện tại (tính từ override tay hoặc chip GPS)
    val activeZone: StateFlow<Zone?> = combine(
        zones,
        locationTracker.manualOverrideZoneId,
        currentLocation
    ) { allZones, manualId, loc ->
        if (!manualId.isNullOrBlank()) {
            allZones.firstOrNull { it.id == manualId }
        } else if (loc != null) {
            LocationMath.findClosestZone(loc.first, loc.second, allZones)
        } else {
            allZones.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isOutsideHaGiang: StateFlow<Boolean> = combine(
        zones,
        currentLocation,
        locationTracker.manualOverrideZoneId
    ) { allZones, loc, manualId ->
        if (!manualId.isNullOrBlank()) false
        else if (loc != null) LocationMath.isOutsideHaGiang(loc.first, loc.second, allZones)
        else false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val distanceToHaGiangKm: StateFlow<Double?> = combine(
        zones,
        currentLocation
    ) { allZones, loc ->
        if (loc != null) {
            val tpZone = allZones.firstOrNull { it.id == "tp_ha_giang" }
            if (tpZone != null) {
                LocationMath.calculateDistanceKm(loc.first, loc.second, tpZone.centerLat, tpZone.centerLng)
            } else null
        } else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Gợi ý "Làm gì bây giờ?"
    @Suppress("UNCHECKED_CAST")
    val whatNowSuggestions: StateFlow<List<SuggestionResult>> = combine(
        whatNowQuery,
        currentLocation,
        places,
        zones,
        tripConfig,
        _shuffleOffset
    ) { args: Array<Any?> ->
        val query = args[0] as WhatNowQuery
        val loc = args[1] as? Pair<Double, Double>
        val allPlaces = args[2] as List<Place>
        val allZones = args[3] as List<Zone>
        val config = args[4] as TripConfig
        val offset = args[5] as Int

        WhatNowEngine.generateSuggestions(
            query = query,
            currentLat = loc?.first,
            currentLng = loc?.second,
            allPlaces = allPlaces,
            allZones = allZones,
            tripConfig = config,
            shuffleOffset = offset
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initialize()
            locationTracker.startListening()
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopListening()
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun openWhatNow() {
        _shuffleOffset.value = 0
        _isWhatNowOpen.value = true
    }

    fun closeWhatNow() {
        _isWhatNowOpen.value = false
    }

    fun updateWhatNowQuery(query: WhatNowQuery) {
        _whatNowQuery.value = query
        _shuffleOffset.value = 0
    }

    fun refreshWhatNowAlternative() {
        _shuffleOffset.value += 1
    }

    fun setManualZoneOverride(zoneId: String?) {
        locationTracker.setManualZoneOverride(zoneId)
        _whatNowQuery.value = _whatNowQuery.value.copy(zoneId = zoneId)
        _shuffleOffset.value = 0
    }

    fun openAddPlaceDialog(preselectedZoneId: String? = null) {
        _editingPlace.value = null
        _isPlaceFormOpen.value = true
    }

    fun openEditPlaceDialog(place: Place) {
        _editingPlace.value = place
        _isPlaceFormOpen.value = true
    }

    fun closePlaceFormDialog() {
        _isPlaceFormOpen.value = false
        _editingPlace.value = null
    }

    fun savePlace(place: Place) {
        viewModelScope.launch {
            repository.savePlace(place)
            closePlaceFormDialog()
            showNotification("Đã lưu địa điểm: ${place.name}")
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            repository.deletePlace(placeId)
            closePlaceFormDialog()
            showNotification("Đã xóa địa điểm.")
        }
    }

    fun toggleBookmark(placeId: String) {
        viewModelScope.launch {
            repository.toggleBookmark(placeId)
        }
    }

    fun toggleVisited(placeId: String) {
        viewModelScope.launch {
            repository.toggleVisited(placeId)
        }
    }

    fun updateTripConfig(config: TripConfig) {
        viewModelScope.launch {
            repository.updateTripConfig(config)
            showNotification("Đã cập nhật cấu hình chuyến đi.")
        }
    }

    fun addExpense(amount: Long, category: ExpenseCategory, title: String, dayNumber: Int) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    category = category,
                    title = title,
                    dayNumber = dayNumber
                )
            )
            showNotification("Đã thêm chi tiêu: +${amount}đ")
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    fun addJournalEntry(placeId: String?, dayNumber: Int, zoneId: String, content: String, photoPath: String?) {
        viewModelScope.launch {
            repository.addJournalEntry(
                JournalEntry(
                    placeId = placeId,
                    dayNumber = dayNumber,
                    zoneId = zoneId,
                    content = content,
                    photoPath = photoPath
                )
            )
            showNotification("Đã lưu ghi chú nhật ký.")
        }
    }

    fun deleteJournalEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteJournalEntry(id)
        }
    }

    fun toggleChecklistItem(id: Long) {
        viewModelScope.launch {
            repository.toggleChecklistItem(id)
        }
    }

    fun addChecklistItem(group: String, title: String) {
        viewModelScope.launch {
            repository.addChecklistItem(group, title)
        }
    }

    fun deleteChecklistItem(id: Long) {
        viewModelScope.launch {
            repository.deleteChecklistItem(id)
        }
    }

    fun exportBackupJson(): String {
        return JsonBackupManager.exportToJson(
            tripConfig = tripConfig.value,
            places = places.value,
            expenses = expenses.value,
            checklist = checklist.value,
            journal = journal.value
        )
    }

    fun importBackupJson(jsonString: String) {
        viewModelScope.launch {
            val success = repository.importBackup(jsonString)
            if (success) {
                showNotification("Nhập dữ liệu thành công!")
            } else {
                showNotification("Lỗi định dạng file JSON!")
            }
        }
    }

    fun clearNotification() {
        _notificationMessage.value = null
    }

    // ====== Live Ride Tracking Controls ======

    val liveRideState: StateFlow<LiveRideState> = RideTrackerService.liveRideState

    private val _isCockpitOpen = MutableStateFlow(false)
    val isCockpitOpen: StateFlow<Boolean> = _isCockpitOpen.asStateFlow()

    fun openCockpit() {
        _isCockpitOpen.value = true
    }

    fun closeCockpit() {
        _isCockpitOpen.value = false
    }

    fun startRide() {
        RideTrackerService.startRide(getApplication())
        _isCockpitOpen.value = true
        showNotification("Bắt đầu theo dõi hành trình phượt!")
    }

    fun pauseRide() {
        RideTrackerService.pauseRide(getApplication())
        showNotification("Đã tạm dừng chặng.")
    }

    fun resumeRide() {
        RideTrackerService.resumeRide(getApplication())
        showNotification("Tiếp tục hành trình.")
    }

    fun stopRide() {
        RideTrackerService.stopRide(getApplication())
        _isCockpitOpen.value = false
        showNotification("Đã kết thúc và lưu chặng xe.")
    }

    private fun showNotification(msg: String) {
        _notificationMessage.value = msg
    }
}
