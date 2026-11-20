package com.reed.hagianggo.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reed.hagianggo.MainActivity
import com.reed.hagianggo.data.HaGiangRepository
import com.reed.hagianggo.data.JsonBackupManager
import com.reed.hagianggo.domain.FuelRangeGuard
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.MountainWeatherManager
import com.reed.hagianggo.domain.OfflineLocationTracker
import com.reed.hagianggo.domain.OfflineMapManager
import com.reed.hagianggo.domain.SplitbillEngine
import com.reed.hagianggo.domain.TimePacerEngine
import com.reed.hagianggo.domain.WhatNowEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.service.RideTrackerService
import com.reed.hagianggo.ui.components.MapLayerStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = HaGiangRepository(application)
    val locationTracker = OfflineLocationTracker(application)
    val offlineMapManager = OfflineMapManager(application)
    val weatherManager = MountainWeatherManager(repository)

    val zones = repository.zones
    val places = repository.places
    val tripConfig = repository.tripConfig
    val itinerarySlots = repository.itinerarySlots
    val expenses = repository.expenses
    val journal = repository.journal
    val checklist = repository.checklist
    val members = repository.members
    val fuelState = repository.fuelState
    val rideStats = repository.rideStats

    val offlineMapStatus = offlineMapManager.status
    val weatherForecasts = weatherManager.forecasts
    val isWeatherSyncing = weatherManager.isSyncing

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

    private val _isSosSheetOpen = MutableStateFlow(false)
    val isSosSheetOpen: StateFlow<Boolean> = _isSosSheetOpen.asStateFlow()

    private val _isWeatherSheetOpen = MutableStateFlow(false)
    val isWeatherSheetOpen: StateFlow<Boolean> = _isWeatherSheetOpen.asStateFlow()

    private val _isRecapSheetOpen = MutableStateFlow(false)
    val isRecapSheetOpen: StateFlow<Boolean> = _isRecapSheetOpen.asStateFlow()

    private val _isOfflineMapDialogOpen = MutableStateFlow(false)
    val isOfflineMapDialogOpen: StateFlow<Boolean> = _isOfflineMapDialogOpen.asStateFlow()

    private val _preferredMapLayer = MutableStateFlow<MapLayerStyle?>(null)
    val preferredMapLayer: StateFlow<MapLayerStyle?> = _preferredMapLayer.asStateFlow()

    // ====== Smart Time Pacer & Zero-Touch Auto Live Pacing State ======
    private val _dayStartMinutes = MutableStateFlow(7 * 60 + 30) // Mặc định xuất phát 07:30 sáng
    val dayStartMinutes: StateFlow<Int> = _dayStartMinutes.asStateFlow()

    private val _useCurrentClockForPacer = MutableStateFlow(true)
    val useCurrentClockForPacer: StateFlow<Boolean> = _useCurrentClockForPacer.asStateFlow()

    private val _liveClockMinutes = MutableStateFlow(currentSystemMinutesOfDay())
    val liveClockMinutes: StateFlow<Int> = _liveClockMinutes.asStateFlow()

    private val _riderPaceFactor = MutableStateFlow(1.0f)
    val riderPaceFactor: StateFlow<Float> = _riderPaceFactor.asStateFlow()

    private val _isAutoGeofenceEnabled = MutableStateFlow(true)
    val isAutoGeofenceEnabled: StateFlow<Boolean> = _isAutoGeofenceEnabled.asStateFlow()

    private val _skippedTodayPlaceIds = MutableStateFlow<Set<String>>(emptySet())
    val skippedTodayPlaceIds: StateFlow<Set<String>> = _skippedTodayPlaceIds.asStateFlow()

    private val _activeStopTimer = MutableStateFlow<ActiveStopTimer?>(null)
    val activeStopTimer: StateFlow<ActiveStopTimer?> = _activeStopTimer.asStateFlow()

    // Lưu mốc rời điểm trước đó để tự động học tốc độ tay lái thực tế (Adaptive Rider Pace)
    private var lastDepartedPlace: Place? = null
    private var lastDepartedAtMillis: Long = 0L

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

    // Cảnh báo Tầm Xăng & Điểm mù Cây xăng trên đèo
    val fuelAdvisory: StateFlow<FuelAdvisory> = combine(
        fuelState,
        tripConfig,
        currentLocation,
        activeZone,
        places
    ) { fState, cfg, loc, z, allPlaces ->
        FuelRangeGuard.evaluate(
            fuelState = fState,
            vehicle = cfg.vehicle,
            currentLat = loc?.first,
            currentLng = loc?.second,
            activeZone = z,
            places = allPlaces
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        FuelRangeGuard.evaluate(FuelState(), VehicleType.MOTORBIKE, null, null, null, emptyList())
    )

    // Tính toán Chia tiền nhóm phượt (Splitbill)
    val memberBalances: StateFlow<List<MemberBalance>> = combine(
        members,
        expenses
    ) { mList, eList ->
        SplitbillEngine.calculateBalances(mList, eList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settlementTransfers: StateFlow<List<SettlementTransfer>> = memberBalances
        .map { SplitbillEngine.computeSettlements(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        createPacerAlertChannel()
        viewModelScope.launch {
            repository.initialize()
            locationTracker.startListening()
            val loadedZones = repository.zones.value
            weatherManager.loadCachedOrBaseline(loadedZones)
            if (OfflineMapManager.hasValidatedInternet(getApplication())) {
                weatherManager.syncLiveForecasts(loadedZones, force = false)
            }
        }
        viewModelScope.launch {
            RideTrackerService.liveRideState
                .map { (it.totalDistanceKm * 10).toInt() }
                .distinctUntilChanged()
                .collect {
                    repository.refreshFuelAndRideStats()
                }
        }
        // 1. Ticker cập nhật đồng hồ thực mỗi 15 giây & kiểm tra nhắc nhở quá giờ dừng (Overstay Alert)
        viewModelScope.launch {
            while (isActive) {
                val nowMin = currentSystemMinutesOfDay()
                _liveClockMinutes.value = nowMin
                if (_useCurrentClockForPacer.value && nowMin in (5 * 60 + 30)..(17 * 60 + 30)) {
                    _dayStartMinutes.value = nowMin
                }
                checkPitStopOverstayAndNotify()
                delay(15_000L)
            }
        }
        // 2. Tự động nhận diện Đến điểm (<250m) & Rời điểm (>350m) qua Geofence GPS
        viewModelScope.launch {
            combine(
                currentLocation,
                RideTrackerService.liveRideState,
                places,
                _isAutoGeofenceEnabled
            ) { loc, ride, allPlaces, autoEnabled ->
                Triple(loc, ride.currentSpeedKmh, if (autoEnabled) allPlaces else emptyList())
            }.collect { (loc, speedKmh, allPlaces) ->
                if (loc != null && allPlaces.isNotEmpty()) {
                    evaluateAutoGeofencePitStop(loc.first, loc.second, speedKmh, allPlaces)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stopListening()
    }

    fun onAppResumed() {
        locationTracker.refreshLocationState(requestImmediate = true)
    }

    fun requestImmediateMyLocation() {
        locationTracker.refreshLocationState(requestImmediate = true)
        if (!isGpsEnabled.value) {
            showNotification("Hãy bật Vị trí (GPS) để tự động định vị ngay!")
        } else if (currentLocation.value == null) {
            showNotification("Đang bắt tín hiệu vệ tinh GPS...")
        }
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
            val prevVehicle = tripConfig.value.vehicle
            repository.updateTripConfig(config)
            if (prevVehicle != config.vehicle) {
                val newMax = FuelRangeGuard.defaultMaxRangeForVehicle(config.vehicle)
                val curFuel = fuelState.value
                val ratio = (curFuel.remainingKm / curFuel.maxRangeKm.coerceAtLeast(1).toDouble()).coerceIn(0.1, 1.0)
                repository.updateFuelState(
                    curFuel.copy(
                        maxRangeKm = newMax,
                        remainingKm = newMax * ratio
                    )
                )
            }
            showNotification("Đã cập nhật cấu hình chuyến đi.")
        }
    }

    fun addExpense(
        amount: Long,
        category: ExpenseCategory,
        title: String,
        dayNumber: Int,
        paidByMemberId: String = "me",
        splitAmongIds: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            repository.addExpense(
                Expense(
                    amount = amount,
                    category = category,
                    title = title,
                    dayNumber = dayNumber,
                    paidByMemberId = paidByMemberId,
                    splitAmongIds = splitAmongIds
                )
            )
            // Nếu người dùng vừa ghi chi tiêu Đổ xăng thì tự động gợi ý / nạp đầy bình xăng!
            if (category == ExpenseCategory.FUEL) {
                val cur = fuelState.value
                repository.updateFuelState(
                    cur.copy(
                        remainingKm = cur.maxRangeKm.toDouble(),
                        lastRefuelAt = System.currentTimeMillis()
                    )
                )
                showNotification("Đã ghi tiền xăng +${amount}đ & tự cập nhật đầy bình!")
            } else {
                showNotification("Đã thêm chi tiêu: +${amount}đ")
            }
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
            journal = journal.value,
            members = members.value
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
        viewModelScope.launch {
            repository.refreshFuelAndRideStats()
        }
        showNotification("Đã kết thúc và lưu chặng xe.")
    }

    // ====== Adventure Pack Controls (Offline Map, SOS, Fuel, Weather, Splitbill, Poster) ======

    fun openSosSheet() {
        locationTracker.refreshLocationState(requestImmediate = true)
        _isSosSheetOpen.value = true
    }

    fun closeSosSheet() {
        _isSosSheetOpen.value = false
    }

    fun openWeatherSheet() {
        _isWeatherSheetOpen.value = true
        if (OfflineMapManager.hasValidatedInternet(getApplication())) {
            viewModelScope.launch {
                weatherManager.syncLiveForecasts(zones.value, force = false)
            }
        }
    }

    fun closeWeatherSheet() {
        _isWeatherSheetOpen.value = false
    }

    fun syncMountainWeatherNow() {
        viewModelScope.launch {
            if (!OfflineMapManager.hasValidatedInternet(getApplication())) {
                showNotification("Đang mất mạng • Hiển thị dự báo đã lưu Offline")
                return@launch
            }
            val ok = weatherManager.syncLiveForecasts(zones.value, force = true)
            if (ok) {
                showNotification("Đã đồng bộ thời tiết & săn mây 8 cụm đèo!")
            } else {
                showNotification("Không thể kết nối máy chủ thời tiết lúc này.")
            }
        }
    }

    fun applyZoneWeatherToWhatNow(zone: Zone, forecast: ZoneWeatherForecast) {
        val mappedWeather = if (forecast.isFogHazard || forecast.rainProbPercent >= 55) {
            CurrentWeather.RAIN_FOG
        } else {
            CurrentWeather.SUNNY
        }
        _whatNowQuery.value = _whatNowQuery.value.copy(
            zoneId = zone.id,
            weather = mappedWeather
        )
        _shuffleOffset.value = 0
        _isWeatherSheetOpen.value = false
        _isWhatNowOpen.value = true
    }

    fun openRecapSheet() {
        viewModelScope.launch {
            repository.refreshFuelAndRideStats()
            _isRecapSheetOpen.value = true
        }
    }

    fun closeRecapSheet() {
        _isRecapSheetOpen.value = false
    }

    fun openOfflineMapDialog() {
        offlineMapManager.refreshStatus()
        _isOfflineMapDialogOpen.value = true
    }

    fun closeOfflineMapDialog() {
        _isOfflineMapDialogOpen.value = false
    }

    fun startOfflineMapDownload() {
        offlineMapManager.startDownload(zones.value, places.value)
    }

    fun cancelOfflineMapDownload() {
        offlineMapManager.cancelDownload()
    }

    fun setPreferredMapLayer(style: MapLayerStyle?) {
        _preferredMapLayer.value = style
    }

    fun previewOfflineTopoLayer() {
        _preferredMapLayer.value = MapLayerStyle.OFFLINE_TOPO
        _isOfflineMapDialogOpen.value = false
        _selectedTab.value = 0
        showNotification("🗺️ Đang xem Lớp Bản đồ Địa hình Offline (filesDir)")
    }

    fun markTankRefueled() {
        viewModelScope.launch {
            val maxKm = FuelRangeGuard.defaultMaxRangeForVehicle(tripConfig.value.vehicle)
            val cur = fuelState.value
            repository.updateFuelState(
                cur.copy(
                    maxRangeKm = maxKm,
                    remainingKm = maxKm.toDouble(),
                    lastRefuelAt = System.currentTimeMillis()
                )
            )
            showNotification("⛽ Đã đặt lại bình xăng đầy (~${maxKm} km)!")
        }
    }

    fun setFuelFraction(fraction: Float) {
        viewModelScope.launch {
            val cur = fuelState.value
            val maxKm = cur.maxRangeKm.coerceAtLeast(50)
            repository.updateFuelState(
                cur.copy(
                    remainingKm = (maxKm * fraction.coerceIn(0.05f, 1f)).toDouble()
                )
            )
        }
    }

    fun saveEmergencyPhone(phone: String) {
        viewModelScope.launch {
            val cur = fuelState.value
            if (cur.emergencyContactPhone != phone) {
                repository.updateFuelState(cur.copy(emergencyContactPhone = phone))
            }
        }
    }

    fun addTripMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.addOrUpdateMember(
                TripMember(
                    id = "mem_${System.currentTimeMillis()}",
                    name = trimmed,
                    isMe = false
                )
            )
            showNotification("Đã thêm bạn đồng hành: $trimmed")
        }
    }

    fun removeTripMember(memberId: String) {
        if (memberId == "me") return
        viewModelScope.launch {
            repository.deleteMember(memberId)
            showNotification("Đã xóa thành viên khỏi danh sách chia tiền.")
        }
    }

    fun buildSplitbillSummaryText(): String {
        return SplitbillEngine.formatShareableSummary(
            expenses = expenses.value,
            balances = memberBalances.value,
            transfers = settlementTransfers.value
        )
    }

    fun notifyMessage(msg: String) {
        showNotification(msg)
    }

    // ====== Smart Time Pacer & Zero-Touch Auto Live Pacing Actions ======

    private fun currentSystemMinutesOfDay(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun setDayStartTime(minutesOfDay: Int) {
        _useCurrentClockForPacer.value = false
        _dayStartMinutes.value = minutesOfDay.coerceIn(5 * 60, 15 * 60)
    }

    fun enableLiveNowPacer(currentClockMinutes: Int = currentSystemMinutesOfDay()) {
        _liveClockMinutes.value = currentClockMinutes
        _useCurrentClockForPacer.value = true
        if (currentClockMinutes in (5 * 60 + 30)..(17 * 60 + 30)) {
            _dayStartMinutes.value = currentClockMinutes
            showNotification("🟢 Đã bật Điều độ Tự động theo giờ thực (${TimePacerEngine.formatMinutesOfDay(currentClockMinutes)}) & GPS Live!")
        } else {
            // Đang xem buổi tối/đêm -> Giữ mốc sáng 07:30 cho chuyến đi ban ngày và sẵn sàng đồng bộ Live khi trời sáng
            if (_dayStartMinutes.value > 15 * 60) {
                _dayStartMinutes.value = 7 * 60 + 30
            }
            showNotification("🟢 Đã bật Tự động Live! Vì hiện đang là ban đêm nên lịch neo mốc sáng ${TimePacerEngine.formatMinutesOfDay(_dayStartMinutes.value)} & ưu tiên tọa độ GPS thực.")
        }
    }

    fun setRiderPaceFactor(factor: Float) {
        val clamped = factor.coerceIn(0.75f, 1.45f)
        _riderPaceFactor.value = clamped
        val label = when {
            clamped <= 0.90f -> "Nhanh (${String.format(java.util.Locale.US, "%.2fx", clamped)})"
            clamped >= 1.15f -> "Thong thả (${String.format(java.util.Locale.US, "%.2fx", clamped)})"
            else -> "Chuẩn (1.0x)"
        }
        showNotification("🛵 Đã chỉnh nhịp độ tay lái: $label")
    }

    fun toggleAutoGeofencePacer() {
        val next = !_isAutoGeofenceEnabled.value
        _isAutoGeofenceEnabled.value = next
        if (next) {
            showNotification("🛰️ Đã bật Tự động nhận diện dừng/rời điểm qua GPS (250m)")
        } else {
            showNotification("Đã tắt tự động bấm giờ theo bán kính GPS")
        }
    }

    private fun evaluateAutoGeofencePitStop(
        lat: Double,
        lng: Double,
        speedKmh: Float,
        allPlaces: List<Place>
    ) {
        val currentTimer = _activeStopTimer.value
        if (currentTimer != null) {
            // Đang dừng tại 1 điểm -> Kiểm tra nếu người dùng đã nổ máy rời bán kính > 350m
            val stoppedPlace = allPlaces.firstOrNull { it.id == currentTimer.placeId } ?: return
            val distKm = LocationMath.calculateDistanceKm(lat, lng, stoppedPlace.lat, stoppedPlace.lng)
            val elapsedSec = (System.currentTimeMillis() - currentTimer.startedAtMillis) / 1000L
            if (distKm > 0.35 && (elapsedSec >= 90L || speedKmh >= 16f)) {
                completePitStop(stoppedPlace.id, isAutoTriggered = true)
            }
            return
        }

        // Chưa dừng -> Kiểm tra nếu người dùng tiến vào bán kính < 250m của 1 điểm chưa đi và giảm tốc < 12 km/h
        if (speedKmh > 14f) return
        val nearbyUnvisited = allPlaces
            .asSequence()
            .filter { !it.isVisited && it.id !in _skippedTodayPlaceIds.value }
            .map { p -> p to LocationMath.calculateDistanceKm(lat, lng, p.lat, p.lng) }
            .filter { it.second <= 0.25 }
            .minByOrNull { it.second }
            ?.first ?: return

        // Học tốc độ tay lái thực tế từ chặng vừa đi (nếu có điểm xuất phát trước đó)
        val prevPlace = lastDepartedPlace
        val prevDepartedMillis = lastDepartedAtMillis
        if (prevPlace != null && prevPlace.id != nearbyUnvisited.id && prevDepartedMillis > 0L) {
            val actualDriveMin = ((System.currentTimeMillis() - prevDepartedMillis) / 60_000L).toInt()
            val straightKm = LocationMath.calculateDistanceKm(prevPlace.lat, prevPlace.lng, nearbyUnvisited.lat, nearbyUnvisited.lng)
            val expectedMin = LocationMath.estimateMountainTravelTimeMin(straightKm, tripConfig.value.vehicle)
            if (actualDriveMin in 3..180 && expectedMin >= 4) {
                val observedRatio = (actualDriveMin.toFloat() / expectedMin.toFloat()).coerceIn(0.75f, 1.40f)
                // Làm mượt EMA 65% cũ + 35% mới
                val blended = (_riderPaceFactor.value * 0.65f + observedRatio * 0.35f).coerceIn(0.75f, 1.40f)
                _riderPaceFactor.value = blended
            }
        }

        _useCurrentClockForPacer.value = true
        _activeStopTimer.value = ActiveStopTimer(
            placeId = nearbyUnvisited.id,
            placeName = nearbyUnvisited.name,
            startedAtMillis = System.currentTimeMillis(),
            targetDurationMin = nearbyUnvisited.durationMin.coerceIn(10, 180),
            isAutoGeofence = true,
            hasNotifiedOverstay = false
        )
        showNotification("🛰️ GPS nhận diện bạn vừa đến ${nearbyUnvisited.name} • Tự động đếm ngược ${nearbyUnvisited.durationMin}p!")
    }

    private fun checkPitStopOverstayAndNotify() {
        val current = _activeStopTimer.value ?: return
        if (current.hasNotifiedOverstay) return
        val elapsedSec = (System.currentTimeMillis() - current.startedAtMillis) / 1000L
        val targetSec = current.targetDurationMin * 60L
        if (elapsedSec >= targetSec) {
            _activeStopTimer.value = current.copy(hasNotifiedOverstay = true)
            triggerOverstayHapticAndNotification(current)
            showNotification("⏰ Đã hết ${current.targetDurationMin}p dừng tại ${current.placeName}! Hãy lên xe để kịp về trước 17:15.")
        }
    }

    private fun triggerOverstayHapticAndNotification(timer: ActiveStopTimer) {
        val app = getApplication<Application>()
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0L, 280L, 160L, 380L), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500L)
                }
            }
        } catch (_: Exception) {}

        try {
            val openIntent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                app,
                2099,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(app, PACER_ALERT_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ Đã hết giờ dừng tại ${timer.placeName}")
                .setContentText("Bạn đã dừng đủ ${timer.targetDurationMin} phút. Nên lên xe đi tiếp để kịp về thị trấn trước 17:15!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(2099, notif)
        } catch (_: Exception) {}
    }

    private fun createPacerAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val app = getApplication<Application>()
            val channel = NotificationChannel(
                PACER_ALERT_CHANNEL_ID,
                "Nhắc giờ lên xe & Chống lố giờ đèo",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Cảnh báo rung và thông báo khi dừng quá giờ tại điểm check-in đèo Hà Giang"
                enableVibration(true)
            }
            val nm = app.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun toggleSkipPlaceToday(placeId: String) {
        val current = _skippedTodayPlaceIds.value.toMutableSet()
        val added = if (placeId in current) {
            current.remove(placeId)
            false
        } else {
            current.add(placeId)
            true
        }
        _skippedTodayPlaceIds.value = current
        val placeName = places.value.firstOrNull { it.id == placeId }?.name ?: "địa điểm"
        if (added) {
            showNotification("⏭️ Tạm bỏ qua '$placeName' để rút ngắn giờ về trước khi tối.")
        } else {
            showNotification("↩️ Đã đưa '$placeName' trở lại lịch trình hôm nay.")
        }
    }

    fun adjustPlaceStayDuration(placeId: String, deltaMinutes: Int) {
        viewModelScope.launch {
            val place = places.value.firstOrNull { it.id == placeId } ?: return@launch
            val newDuration = (place.durationMin + deltaMinutes).coerceIn(10, 180)
            if (newDuration != place.durationMin) {
                repository.savePlace(place.copy(durationMin = newDuration))
                val active = _activeStopTimer.value
                if (active != null && active.placeId == placeId) {
                    _activeStopTimer.value = active.copy(targetDurationMin = newDuration, hasNotifiedOverstay = false)
                }
            }
        }
    }

    fun applySmartAutoTrim(
        dayStops: List<TimePacerEngine.DayStopInput>,
        startMinutes: Int,
        isLiveNowMode: Boolean
    ) {
        viewModelScope.launch {
            val cfg = tripConfig.value
            val sunsetMin = cfg.sunsetCutoffHour * 60 + cfg.sunsetCutoffMinute
            val loc = currentLocation.value
            val currentSchedule = TimePacerEngine.computeDaySchedule(
                dayStops = dayStops,
                vehicle = cfg.vehicle,
                startMinutesOfDay = startMinutes,
                isLiveNowMode = isLiveNowMode,
                skippedPlaceIds = _skippedTodayPlaceIds.value,
                sunsetCutoffMinutes = sunsetMin,
                currentClockMinutes = _liveClockMinutes.value,
                liveGpsLat = loc?.first,
                liveGpsLng = loc?.second,
                isOutsideHaGiang = isOutsideHaGiang.value,
                activeStopTimer = _activeStopTimer.value,
                riderPaceFactor = _riderPaceFactor.value
            )
            val lastDaytimeIndex = dayStops.indexOfLast {
                it.slot != TimeSlot.EVENING && it.place.id !in _skippedTodayPlaceIds.value
            }
            val enRouteNodes = if (lastDaytimeIndex > 0) {
                currentSchedule.nodes.take(lastDaytimeIndex)
            } else {
                currentSchedule.nodes
            }
            val plan = TimePacerEngine.buildAutoTrimPlan(
                enRouteNodes = enRouteNodes,
                overtimeMinutes = currentSchedule.overtimeMinutes
            ) ?: return@launch

            for ((placeId, newMin) in plan.updatedDurations) {
                val place = places.value.firstOrNull { it.id == placeId }
                if (place != null) {
                    repository.savePlace(place.copy(durationMin = newMin))
                }
            }
            if (plan.suggestedSkipIds.isNotEmpty()) {
                _skippedTodayPlaceIds.value = _skippedTodayPlaceIds.value + plan.suggestedSkipIds
            }
            showNotification("✨ Đã tối ưu lịch trình: Tiết kiệm ~${plan.totalSavedMinutes} phút để về trước 17:15!")
        }
    }

    fun startPitStopTimer(place: Place, isAutoGeofence: Boolean = false) {
        _activeStopTimer.value = ActiveStopTimer(
            placeId = place.id,
            placeName = place.name,
            startedAtMillis = System.currentTimeMillis(),
            targetDurationMin = place.durationMin.coerceIn(10, 180),
            isAutoGeofence = isAutoGeofence,
            hasNotifiedOverstay = false
        )
        showNotification("⏱️ Bắt đầu đếm ngược ${place.durationMin} phút dừng tại ${place.name}")
    }

    fun addPitStopMinutes(extraMin: Int) {
        val current = _activeStopTimer.value ?: return
        val newTarget = (current.targetDurationMin + extraMin).coerceIn(10, 180)
        _activeStopTimer.value = current.copy(targetDurationMin = newTarget, hasNotifiedOverstay = false)
        adjustPlaceStayDuration(current.placeId, extraMin)
        showNotification("⏱️ Đã cộng thêm $extraMin phút tại ${current.placeName}")
    }

    fun completePitStop(placeId: String, isAutoTriggered: Boolean = false) {
        viewModelScope.launch {
            val current = places.value.firstOrNull { it.id == placeId }
            if (current != null) {
                lastDepartedPlace = current
                lastDepartedAtMillis = System.currentTimeMillis()
                if (!current.isVisited) {
                    repository.toggleVisited(placeId)
                }
            }
            _activeStopTimer.value = null
            _useCurrentClockForPacer.value = true
            _liveClockMinutes.value = currentSystemMinutesOfDay()
            val prefix = if (isAutoTriggered) "🛰️ GPS nhận diện bạn đã rời " else "✓ Đã hoàn thành điểm dừng "
            showNotification("$prefix${current?.name ?: ""} • Tự động tính giờ đi điểm kế tiếp!")
        }
    }

    fun cancelPitStopTimer() {
        _activeStopTimer.value = null
    }

    private fun showNotification(msg: String) {
        _notificationMessage.value = msg
    }

    companion object {
        private const val PACER_ALERT_CHANNEL_ID = "hagiang_pacer_alert"
    }
}
