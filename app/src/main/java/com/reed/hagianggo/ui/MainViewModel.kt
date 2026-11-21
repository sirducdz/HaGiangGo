package com.reed.hagianggo.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.reed.hagianggo.data.HaGiangDatabase
import com.reed.hagianggo.data.HaGiangRepository
import com.reed.hagianggo.data.JsonBackupManager
import com.reed.hagianggo.domain.AutoCheckInCoordinator
import com.reed.hagianggo.domain.AutoCheckInEvent
import com.reed.hagianggo.domain.FuelRangeGuard
import com.reed.hagianggo.domain.HybridRouteEngine
import com.reed.hagianggo.domain.ItineraryEditorLogic
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.MountainWeatherManager
import com.reed.hagianggo.domain.NavigationProgressTracker
import com.reed.hagianggo.domain.NavigationVoiceCoach
import com.reed.hagianggo.domain.OfflineLocationTracker
import com.reed.hagianggo.domain.OfflineMapManager
import com.reed.hagianggo.domain.PacerNotificationHelper
import com.reed.hagianggo.domain.SplitbillEngine
import com.reed.hagianggo.domain.TimePacerEngine
import com.reed.hagianggo.domain.WhatNowEngine
import com.reed.hagianggo.model.*
import com.reed.hagianggo.service.RideTrackerService
import com.reed.hagianggo.ui.components.MapLayerStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val activePlanCode = repository.activePlanCode
    val isItineraryCustomized = repository.isItineraryCustomized
    val nightStayVariant = repository.nightStayVariant
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
    val locationTelemetry = locationTracker.locationTelemetry
    val isGpsEnabled = locationTracker.isGpsEnabled

    private val hybridRouteEngine = HybridRouteEngine(application)
    private val navigationVoiceCoach = NavigationVoiceCoach(application)
    private val navPrefs = application.getSharedPreferences("hagiang_nav_prefs", Context.MODE_PRIVATE)

    private val _navMapPreference = MutableStateFlow(
        NavigationMapPreference.fromCode(navPrefs.getString("nav_map_preference", null))
    )
    val navMapPreference: StateFlow<NavigationMapPreference> = _navMapPreference.asStateFlow()

    private val _pendingNavChoicePlace = MutableStateFlow<Place?>(null)
    val pendingNavChoicePlace: StateFlow<Place?> = _pendingNavChoicePlace.asStateFlow()

    private val _activeNavigation = MutableStateFlow<ActiveNavigationState?>(null)
    val activeNavigation: StateFlow<ActiveNavigationState?> = _activeNavigation.asStateFlow()

    private var navigationRouteJob: Job? = null
    private var consecutiveOffRouteTicks = 0
    private var consecutiveWrongWayTicks = 0
    private var lastRerouteTimestampMs = 0L

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

    private val _quickSnapRequest = MutableStateFlow<QuickSnapRequest?>(null)
    val quickSnapRequest: StateFlow<QuickSnapRequest?> = _quickSnapRequest.asStateFlow()

    private var pendingItineraryTarget: Pair<Int, TimeSlot>? = null

    // ====== Smart Time Pacer & Zero-Touch Auto Live Pacing State ======
    private val _dayStartMinutes = MutableStateFlow(7 * 60 + 30) // Mặc định xuất phát 07:30 sáng
    val dayStartMinutes: StateFlow<Int> = _dayStartMinutes.asStateFlow()

    private val _useCurrentClockForPacer = MutableStateFlow(true)
    val useCurrentClockForPacer: StateFlow<Boolean> = _useCurrentClockForPacer.asStateFlow()

    private val _liveClockMinutes = MutableStateFlow(currentSystemMinutesOfDay())
    val liveClockMinutes: StateFlow<Int> = _liveClockMinutes.asStateFlow()

    val riderPaceFactor: StateFlow<Float> = AutoCheckInCoordinator.riderPaceFactor
    val isAutoGeofenceEnabled: StateFlow<Boolean> = AutoCheckInCoordinator.isAutoCheckInEnabled

    private val _skippedTodayPlaceIds = MutableStateFlow<Set<String>>(emptySet())
    val skippedTodayPlaceIds: StateFlow<Set<String>> = _skippedTodayPlaceIds.asStateFlow()

    val activeStopTimer: StateFlow<ActiveStopTimer?> = AutoCheckInCoordinator.activeStopTimer

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
        PacerNotificationHelper.ensureChannels(getApplication())
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
        // Đồng bộ sự kiện Auto Check-in / Pit-Stop từ Service nền & Notification Actions lên UI tức thì
        viewModelScope.launch {
            AutoCheckInCoordinator.events.collect { event ->
                when (event) {
                    is AutoCheckInEvent.ArrivedAndTimerStarted -> {
                        if (event.isAuto) {
                            repository.refreshPlaces()
                            _useCurrentClockForPacer.value = true
                            _liveClockMinutes.value = currentSystemMinutesOfDay()
                            showNotification("🛰️ GPS nhận diện bạn vừa đến ${event.placeName} • Đã đánh dấu Đã đi & đếm ngược ${event.durationMin}p!")
                        } else {
                            showNotification("⏱️ Bắt đầu đếm ngược ${event.durationMin} phút dừng tại ${event.placeName}")
                        }
                    }
                    is AutoCheckInEvent.PassThroughCheckedIn -> {
                        repository.refreshPlaces()
                        _useCurrentClockForPacer.value = true
                        _liveClockMinutes.value = currentSystemMinutesOfDay()
                        showNotification("✓ Đã tự động check-in: ${event.placeName}")
                    }
                    is AutoCheckInEvent.DepartedPitStop -> {
                        repository.refreshPlaces()
                        _useCurrentClockForPacer.value = true
                        _liveClockMinutes.value = currentSystemMinutesOfDay()
                        val prefix = if (event.isAuto) "🛰️ GPS nhận diện bạn đã rời " else "✓ Đã hoàn thành điểm dừng "
                        showNotification("$prefix${event.placeName} • Tự động tính giờ đi điểm kế tiếp!")
                    }
                    is AutoCheckInEvent.OverstayWarning -> {
                        showNotification("⏰ Đã hết ${event.targetDurationMin}p dừng tại ${event.placeName}! Hãy lên xe để kịp về trước 17:15.")
                    }
                    is AutoCheckInEvent.PitStopDurationExtended -> {
                        repository.refreshPlaces()
                        showNotification("⏱️ Đã cộng thêm ${event.deltaMin} phút tại ${event.placeName}")
                    }
                }
            }
        }
        // 1. Ticker cập nhật đồng hồ thực mỗi 15 giây & kiểm tra quá giờ dừng (khi Service nền chưa chạy)
        viewModelScope.launch {
            while (isActive) {
                val nowMin = currentSystemMinutesOfDay()
                _liveClockMinutes.value = nowMin
                if (_useCurrentClockForPacer.value && nowMin in (5 * 60 + 30)..(17 * 60 + 30)) {
                    _dayStartMinutes.value = nowMin
                }
                if (!RideTrackerService.liveRideState.value.isTracking) {
                    AutoCheckInCoordinator.checkOverstay(getApplication())
                }
                delay(15_000L)
            }
        }
        // 2. Nhận diện Đến điểm (<=250m) & Rời điểm (>350m) khi mở app mà chưa bật Chế độ Phượt nền (HUD)
        viewModelScope.launch {
            combine(
                currentLocation,
                RideTrackerService.liveRideState,
                places,
                isAutoGeofenceEnabled,
                tripConfig
            ) { loc, ride, allPlaces, autoEnabled, cfg ->
                if (!ride.isTracking && autoEnabled && loc != null && allPlaces.isNotEmpty()) {
                    Triple(loc, allPlaces, cfg.vehicle)
                } else {
                    null
                }
            }.collect { payload ->
                if (payload != null) {
                    val (loc, allPlaces, vehicle) = payload
                    withContext(Dispatchers.IO) {
                        val db = HaGiangDatabase.getInstance(getApplication())
                        AutoCheckInCoordinator.processLocation(
                            context = getApplication(),
                            database = db,
                            lat = loc.first,
                            lng = loc.second,
                            speedKmh = 0f,
                            allPlaces = allPlaces,
                            vehicle = vehicle
                        )
                    }
                }
            }
        }
        // 3. Theo dõi tiến trình Dẫn đường trực tiếp (Cự ly thực, ETA, Bước rẽ, Lệch tuyến, Đi ngược chiều & Giọng nói)
        viewModelScope.launch {
            locationTelemetry.filterNotNull().collect { telemetry ->
                val curNav = _activeNavigation.value ?: return@collect
                val route = curNav.route ?: return@collect
                if (curNav.isUsingZonePreviewOrigin) return@collect

                val ride = RideTrackerService.liveRideState.value
                val altM = if (ride.isTracking && ride.currentAltitude > 10) {
                    ride.currentAltitude.toInt()
                } else {
                    telemetry.altitudeM.toInt().takeIf { it > 10 }
                        ?: activeZone.value?.elevationMeters
                        ?: 120
                }
                val liveSpeedKmh = if (ride.isTracking && ride.currentSpeedKmh > 0.5f) {
                    ride.currentSpeedKmh
                } else {
                    telemetry.speedKmh
                }

                val progress = NavigationProgressTracker.evaluateProgress(
                    route = route,
                    lat = telemetry.lat,
                    lng = telemetry.lng,
                    currentAltitudeM = altM,
                    vehicle = tripConfig.value.vehicle,
                    riderPaceFactor = riderPaceFactor.value,
                    currentClockMinutesOfDay = _liveClockMinutes.value,
                    previousMatchedIndex = curNav.matchedSegmentIndex,
                    previousStepIndex = curNav.currentStepIndex,
                    riderBearingDeg = telemetry.bearingDeg,
                    hasRiderBearing = telemetry.hasBearing,
                    riderSpeedKmh = liveSpeedKmh,
                    bestMinRemainingMeters = curNav.bestMinRemainingMeters,
                    previousRemainingMeters = curNav.remainingDistanceMeters,
                    wasGoingWrongWay = curNav.isGoingWrongWay
                )

                val updatedNav = curNav.copy(
                    matchedSegmentIndex = progress.matchedSegmentIndex,
                    remainingDistanceMeters = progress.remainingDistanceMeters,
                    remainingDurationMin = progress.remainingDurationMin,
                    etaMinutesOfDay = progress.etaMinutesOfDay,
                    currentStepIndex = progress.currentStepIndex,
                    distanceToNextManeuverMeters = progress.distanceToNextManeuverMeters,
                    offRouteDistanceMeters = progress.offRouteDistanceMeters,
                    isOffRoute = progress.isOffRoute,
                    isGoingWrongWay = progress.isGoingWrongWay,
                    wrongWayBacktrackMeters = progress.wrongWayBacktrackMeters,
                    bestMinRemainingMeters = progress.bestMinRemainingMeters,
                    hasArrived = progress.hasArrived,
                    elevationDeltaMeters = progress.elevationDeltaMeters
                )
                _activeNavigation.value = updatedNav
                navigationVoiceCoach.onProgressUpdate(updatedNav)

                if (!curNav.hasArrived && updatedNav.hasArrived) {
                    showNotification("🎉 Bạn đã đến ${updatedNav.destination.name}!")
                }

                val nowMs = System.currentTimeMillis()
                if (progress.isOffRoute && !curNav.isRerouting && !curNav.isLoadingRoute) {
                    consecutiveOffRouteTicks++
                    consecutiveWrongWayTicks = 0
                    if (consecutiveOffRouteTicks >= 2 && nowMs - lastRerouteTimestampMs >= 20_000L) {
                        consecutiveOffRouteTicks = 0
                        lastRerouteTimestampMs = nowMs
                        rerouteActiveNavigation(telemetry.lat, telemetry.lng)
                    }
                } else if (progress.isGoingWrongWay && !curNav.isRerouting && !curNav.isLoadingRoute) {
                    consecutiveOffRouteTicks = 0
                    consecutiveWrongWayTicks++
                    if ((consecutiveWrongWayTicks >= 3 || progress.wrongWayBacktrackMeters >= 180.0) &&
                        nowMs - lastRerouteTimestampMs >= 20_000L
                    ) {
                        consecutiveWrongWayTicks = 0
                        lastRerouteTimestampMs = nowMs
                        rerouteActiveNavigation(telemetry.lat, telemetry.lng)
                    }
                } else {
                    consecutiveOffRouteTicks = 0
                    consecutiveWrongWayTicks = 0
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        navigationRouteJob?.cancel()
        navigationVoiceCoach.shutdown()
        locationTracker.setHighFrequencyNavigationMode(false)
        locationTracker.stopListening()
    }

    fun onAppResumed() {
        locationTracker.refreshLocationState(requestImmediate = true)
        viewModelScope.launch {
            repository.refreshPlaces()
        }
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
            val target = pendingItineraryTarget
            if (target != null) {
                pendingItineraryTarget = null
                val updated = ItineraryEditorLogic.addPlace(
                    slots = itinerarySlots.value,
                    planCode = activePlanCode.value,
                    dayNumber = target.first,
                    targetSlot = target.second,
                    placeId = place.id
                )
                repository.saveCustomItinerary(updated)
                closePlaceFormDialog()
                showNotification("Đã lưu '${place.name}' & thêm vào Ngày ${target.first}!")
            } else {
                closePlaceFormDialog()
                showNotification("Đã lưu địa điểm: ${place.name}")
            }
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
            val wasVisited = places.value.firstOrNull { it.id == placeId }?.isVisited == true
            repository.toggleVisited(placeId)
            if (wasVisited) {
                AutoCheckInCoordinator.resetNotifiedPlace(placeId)
            }
        }
    }

    fun updateTripConfig(config: TripConfig) {
        viewModelScope.launch {
            val prevVehicle = tripConfig.value.vehicle
            val prevDuration = tripConfig.value.duration
            repository.updateTripConfig(config)
            if (prevDuration != config.duration) {
                _skippedTodayPlaceIds.value = emptySet()
                AutoCheckInCoordinator.updateSkippedPlaceIds(emptySet())
            }
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
            members = members.value,
            itinerarySlots = itinerarySlots.value
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
        AutoCheckInCoordinator.setRiderPaceFactor(clamped)
        val label = when {
            clamped <= 0.90f -> "Nhanh (${String.format(java.util.Locale.US, "%.2fx", clamped)})"
            clamped >= 1.15f -> "Thong thả (${String.format(java.util.Locale.US, "%.2fx", clamped)})"
            else -> "Chuẩn (1.0x)"
        }
        showNotification("🛵 Đã chỉnh nhịp độ tay lái: $label")
    }

    fun toggleAutoGeofencePacer() {
        val next = !isAutoGeofenceEnabled.value
        AutoCheckInCoordinator.setAutoCheckInEnabled(next)
        if (next) {
            showNotification("🛰️ Đã bật Tự động check-in & nhận diện dừng/rời điểm qua GPS (250m)")
        } else {
            showNotification("Đã tắt tự động check-in & bấm giờ theo bán kính GPS")
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
        AutoCheckInCoordinator.updateSkippedPlaceIds(current)
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
                AutoCheckInCoordinator.syncPlaceDuration(placeId, newDuration)
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
                activeStopTimer = activeStopTimer.value,
                riderPaceFactor = riderPaceFactor.value
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
                    AutoCheckInCoordinator.syncPlaceDuration(placeId, newMin)
                }
            }
            if (plan.suggestedSkipIds.isNotEmpty()) {
                val updatedSkips = _skippedTodayPlaceIds.value + plan.suggestedSkipIds
                _skippedTodayPlaceIds.value = updatedSkips
                AutoCheckInCoordinator.updateSkippedPlaceIds(updatedSkips)
            }
            showNotification("✨ Đã tối ưu lịch trình: Tiết kiệm ~${plan.totalSavedMinutes} phút để về trước 17:15!")
        }
    }

    fun startPitStopTimer(place: Place) {
        AutoCheckInCoordinator.startManualPitStop(place)
    }

    fun addPitStopMinutes(extraMin: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = HaGiangDatabase.getInstance(getApplication())
                AutoCheckInCoordinator.addPitStopMinutes(getApplication(), db, extraMin)
            }
        }
    }

    fun completePitStop(placeId: String, isAutoTriggered: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val db = HaGiangDatabase.getInstance(getApplication())
                AutoCheckInCoordinator.completePitStop(
                    context = getApplication(),
                    database = db,
                    placeId = placeId,
                    allPlaces = places.value,
                    isAuto = isAutoTriggered
                )
            }
        }
    }

    fun cancelPitStopTimer() {
        AutoCheckInCoordinator.cancelPitStop(getApplication())
    }

    // ====== Custom Itinerary Editor Actions ======

    fun addPlaceToItinerary(dayNumber: Int, slot: TimeSlot, placeId: String) {
        viewModelScope.launch {
            val updated = ItineraryEditorLogic.addPlace(
                slots = itinerarySlots.value,
                planCode = activePlanCode.value,
                dayNumber = dayNumber,
                targetSlot = slot,
                placeId = placeId
            )
            repository.saveCustomItinerary(updated)
            val name = places.value.firstOrNull { it.id == placeId }?.name ?: "điểm mới"
            showNotification("✓ Đã thêm '$name' vào ${slot.label} Ngày $dayNumber")
        }
    }

    fun removePlaceFromItinerary(dayNumber: Int, placeId: String) {
        viewModelScope.launch {
            val updated = ItineraryEditorLogic.removePlace(itinerarySlots.value, dayNumber, placeId)
            repository.saveCustomItinerary(updated)
            val name = places.value.firstOrNull { it.id == placeId }?.name ?: "điểm"
            showNotification("Đã gỡ '$name' khỏi lịch Ngày $dayNumber")
        }
    }

    fun moveItineraryPlace(dayNumber: Int, placeId: String, direction: Int) {
        viewModelScope.launch {
            val updated = ItineraryEditorLogic.movePlaceWithinDay(
                slots = itinerarySlots.value,
                planCode = activePlanCode.value,
                dayNumber = dayNumber,
                placeId = placeId,
                direction = direction
            )
            if (updated != itinerarySlots.value) {
                repository.saveCustomItinerary(updated)
            }
        }
    }

    fun moveItineraryPlaceToDaySlot(fromDay: Int, toDay: Int, toSlot: TimeSlot, placeId: String) {
        viewModelScope.launch {
            val updated = ItineraryEditorLogic.movePlaceToDaySlot(
                slots = itinerarySlots.value,
                planCode = activePlanCode.value,
                fromDay = fromDay,
                toDay = toDay,
                toSlot = toSlot,
                placeId = placeId
            )
            repository.saveCustomItinerary(updated)
            val name = places.value.firstOrNull { it.id == placeId }?.name ?: "điểm"
            showNotification("Đã chuyển '$name' sang ${toSlot.label} Ngày $toDay")
        }
    }

    fun resetItineraryToDefault() {
        viewModelScope.launch {
            repository.resetItineraryToDefault()
            showNotification("↩️ Đã khôi phục lịch trình mẫu Hà Giang chuẩn!")
        }
    }

    fun setNightStayVariant(variant: NightStayVariant) {
        if (variant == nightStayVariant.value) return
        viewModelScope.launch {
            repository.setNightStayVariant(variant)
            _skippedTodayPlaceIds.value = emptySet()
            AutoCheckInCoordinator.updateSkippedPlaceIds(emptySet())
            val is4N3D = tripConfig.value.duration == TripDuration.DAYS_4_NIGHTS_3
            val msg = when {
                variant == NightStayVariant.LO_LO_CHAI && is4N3D ->
                    "🏡 Đã đổi sang Đêm 2 Lô Lô Chải! Sáng Ngày 3 săn mây Cực Bắc rồi xuống Đồng Văn."
                variant == NightStayVariant.LO_LO_CHAI ->
                    "🏡 Đã đổi sang Đêm 1 Lô Lô Chải! Mẹo: Đi từ 06:30 & đổ xăng Phố Cáo trước khi lên Cực Bắc."
                is4N3D ->
                    "🏮 Đã đổi sang Đêm 2 Phố cổ Đồng Văn! Tối thong thả dạo phố cổ."
                else ->
                    "🏮 Đã đổi sang Đêm 1 Phố cổ Đồng Văn! Lộ trình an toàn trước 17:15."
            }
            showNotification(msg)
        }
    }

    fun openAddCustomPlaceForItinerary(dayNumber: Int, slot: TimeSlot) {
        pendingItineraryTarget = dayNumber to slot
        openAddPlaceDialog()
    }

    // ====== Quick Snap (Altitude & GPS Watermark Photo) ======

    fun openQuickSnap(
        place: Place? = null,
        dayNumber: Int = tripConfig.value.currentDay,
        zoneId: String? = null
    ) {
        val resolvedZone = place?.zoneId ?: zoneId ?: activeZone.value?.id ?: "tp_ha_giang"
        _quickSnapRequest.value = QuickSnapRequest(
            place = place,
            dayNumber = dayNumber.coerceIn(1, tripConfig.value.durationDays),
            zoneId = resolvedZone,
            defaultTitle = ""
        )
    }

    fun closeQuickSnap() {
        _quickSnapRequest.value = null
    }

    // ====== In-App Turn-by-Turn & Offline Loop Navigation Controls ======

    fun setNavMapPreference(pref: NavigationMapPreference) {
        _navMapPreference.value = pref
        navPrefs.edit().putString("nav_map_preference", pref.code).apply()
        showNotification("🗺️ Chế độ dẫn đường: ${pref.title}")
    }

    fun requestNavigationTo(destination: Place) {
        when (_navMapPreference.value) {
            NavigationMapPreference.IN_APP -> startInAppNavigation(destination)
            NavigationMapPreference.GOOGLE_MAPS -> LocationMath.openNavigationIntent(
                getApplication(),
                destination.lat,
                destination.lng,
                destination.name
            )
            NavigationMapPreference.ASK_EVERY_TIME -> {
                _isWhatNowOpen.value = false
                _isSosSheetOpen.value = false
                _isCockpitOpen.value = false
                _pendingNavChoicePlace.value = destination
            }
        }
    }

    fun dismissNavChoice() {
        _pendingNavChoicePlace.value = null
    }

    fun confirmNavChoiceInApp(place: Place, rememberAsDefault: Boolean) {
        _pendingNavChoicePlace.value = null
        if (rememberAsDefault) {
            setNavMapPreference(NavigationMapPreference.IN_APP)
        }
        startInAppNavigation(place)
    }

    fun confirmNavChoiceGoogleMaps(place: Place, rememberAsDefault: Boolean) {
        _pendingNavChoicePlace.value = null
        if (rememberAsDefault) {
            setNavMapPreference(NavigationMapPreference.GOOGLE_MAPS)
        }
        LocationMath.openNavigationIntent(
            getApplication(),
            place.lat,
            place.lng,
            place.name
        )
    }

    fun startInAppNavigation(destination: Place, forceFromHaGiangOrigin: Boolean = false) {
        _isWhatNowOpen.value = false
        _isSosSheetOpen.value = false
        _isCockpitOpen.value = false
        _selectedTab.value = 0

        locationTracker.setHighFrequencyNavigationMode(true)
        consecutiveOffRouteTicks = 0
        consecutiveWrongWayTicks = 0
        lastRerouteTimestampMs = 0L

        val prevVoice = _activeNavigation.value?.isVoiceEnabled ?: false
        val loc = currentLocation.value
        val outside = isOutsideHaGiang.value
        val hasInternet = OfflineMapManager.hasValidatedInternet(getApplication())

        val useZonePreviewOrigin = loc == null || forceFromHaGiangOrigin || (outside && !hasInternet)
        val previewZone = if (forceFromHaGiangOrigin) {
            zones.value.firstOrNull { it.id == "tp_ha_giang" } ?: activeZone.value
        } else {
            activeZone.value ?: zones.value.firstOrNull()
        }

        val originLat = if (useZonePreviewOrigin) {
            previewZone?.centerLat ?: 22.827330
        } else {
            loc!!.first
        }
        val originLng = if (useZonePreviewOrigin) {
            previewZone?.centerLng ?: 104.984169
        } else {
            loc!!.second
        }
        val originLabel = if (useZonePreviewOrigin) {
            previewZone?.name ?: "TP. Hà Giang (Km0)"
        } else {
            "Vị trí của bạn"
        }

        _activeNavigation.value = ActiveNavigationState(
            destination = destination,
            route = null,
            isLoadingRoute = true,
            followMode = if (useZonePreviewOrigin) NavCameraFollowMode.FREE_PAN else NavCameraFollowMode.HEADING_UP,
            isVoiceEnabled = prevVoice,
            forceFromHaGiangOrigin = forceFromHaGiangOrigin,
            isUsingZonePreviewOrigin = useZonePreviewOrigin
        )

        navigationRouteJob?.cancel()
        navigationRouteJob = viewModelScope.launch {
            val route = hybridRouteEngine.computeRoute(
                originLat = originLat,
                originLng = originLng,
                destination = destination,
                vehicle = tripConfig.value.vehicle,
                originLabel = originLabel,
                preferOnline = hasInternet
            )
            navigationVoiceCoach.resetForRoute(route.computedAtMillis)

            val ride = RideTrackerService.liveRideState.value
            val altM = if (ride.isTracking && ride.currentAltitude > 10) {
                ride.currentAltitude.toInt()
            } else {
                locationTelemetry.value?.altitudeM?.toInt()?.takeIf { it > 10 }
                    ?: previewZone?.elevationMeters
                    ?: 120
            }

            val initialProgress = NavigationProgressTracker.evaluateProgress(
                route = route,
                lat = originLat,
                lng = originLng,
                currentAltitudeM = altM,
                vehicle = tripConfig.value.vehicle,
                riderPaceFactor = riderPaceFactor.value,
                currentClockMinutesOfDay = _liveClockMinutes.value,
                previousMatchedIndex = 0,
                previousStepIndex = 0
            )

            val cur = _activeNavigation.value
            if (cur != null && cur.destination.id == destination.id) {
                val readyState = cur.copy(
                    route = route,
                    isLoadingRoute = false,
                    isRerouting = false,
                    matchedSegmentIndex = initialProgress.matchedSegmentIndex,
                    remainingDistanceMeters = initialProgress.remainingDistanceMeters,
                    remainingDurationMin = initialProgress.remainingDurationMin,
                    etaMinutesOfDay = initialProgress.etaMinutesOfDay,
                    currentStepIndex = initialProgress.currentStepIndex,
                    distanceToNextManeuverMeters = initialProgress.distanceToNextManeuverMeters,
                    offRouteDistanceMeters = initialProgress.offRouteDistanceMeters,
                    isOffRoute = false,
                    isGoingWrongWay = false,
                    wrongWayBacktrackMeters = 0.0,
                    bestMinRemainingMeters = initialProgress.remainingDistanceMeters,
                    hasArrived = initialProgress.hasArrived,
                    elevationDeltaMeters = initialProgress.elevationDeltaMeters
                )
                _activeNavigation.value = readyState
                if (readyState.isVoiceEnabled) {
                    val firstStep = route.steps.getOrNull(readyState.currentStepIndex)?.instructionVi ?: destination.name
                    navigationVoiceCoach.speakImmediateHint(
                        "Bắt đầu dẫn đường tới ${destination.name}, $firstStep."
                    )
                }
            }
        }
    }

    fun forceRerouteNow() {
        val cur = _activeNavigation.value ?: return
        if (cur.isRerouting || cur.isLoadingRoute) return
        if (cur.isUsingZonePreviewOrigin) {
            startInAppNavigation(cur.destination, forceFromHaGiangOrigin = cur.forceFromHaGiangOrigin)
            return
        }
        val loc = locationTelemetry.value?.let { it.lat to it.lng } ?: currentLocation.value ?: return
        consecutiveOffRouteTicks = 0
        consecutiveWrongWayTicks = 0
        lastRerouteTimestampMs = System.currentTimeMillis()
        rerouteActiveNavigation(loc.first, loc.second)
    }

    private fun rerouteActiveNavigation(currentLat: Double, currentLng: Double) {
        val cur = _activeNavigation.value ?: return
        _activeNavigation.value = cur.copy(isRerouting = true)
        navigationRouteJob?.cancel()
        navigationRouteJob = viewModelScope.launch {
            val route = hybridRouteEngine.computeRoute(
                originLat = currentLat,
                originLng = currentLng,
                destination = cur.destination,
                vehicle = tripConfig.value.vehicle,
                originLabel = "Vị trí hiện tại",
                preferOnline = OfflineMapManager.hasValidatedInternet(getApplication())
            )
            navigationVoiceCoach.resetForRoute(route.computedAtMillis)

            val latest = _activeNavigation.value ?: return@launch
            if (latest.destination.id != cur.destination.id) return@launch
            val altM = locationTelemetry.value?.altitudeM?.toInt()?.takeIf { it > 10 }
                ?: activeZone.value?.elevationMeters
                ?: 120
            val progress = NavigationProgressTracker.evaluateProgress(
                route = route,
                lat = currentLat,
                lng = currentLng,
                currentAltitudeM = altM,
                vehicle = tripConfig.value.vehicle,
                riderPaceFactor = riderPaceFactor.value,
                currentClockMinutesOfDay = _liveClockMinutes.value
            )
            _activeNavigation.value = latest.copy(
                route = route,
                isLoadingRoute = false,
                isRerouting = false,
                isOffRoute = false,
                isGoingWrongWay = false,
                wrongWayBacktrackMeters = 0.0,
                bestMinRemainingMeters = progress.remainingDistanceMeters,
                matchedSegmentIndex = progress.matchedSegmentIndex,
                remainingDistanceMeters = progress.remainingDistanceMeters,
                remainingDurationMin = progress.remainingDurationMin,
                etaMinutesOfDay = progress.etaMinutesOfDay,
                currentStepIndex = progress.currentStepIndex,
                distanceToNextManeuverMeters = progress.distanceToNextManeuverMeters,
                offRouteDistanceMeters = progress.offRouteDistanceMeters,
                hasArrived = progress.hasArrived,
                elevationDeltaMeters = progress.elevationDeltaMeters
            )
        }
    }

    fun stopInAppNavigation() {
        navigationRouteJob?.cancel()
        navigationRouteJob = null
        consecutiveOffRouteTicks = 0
        consecutiveWrongWayTicks = 0
        locationTracker.setHighFrequencyNavigationMode(false)
        navigationVoiceCoach.stopSpeaking()
        _activeNavigation.value = null
    }

    fun toggleNavFollowMode() {
        val cur = _activeNavigation.value ?: return
        val next = when (cur.followMode) {
            NavCameraFollowMode.HEADING_UP -> NavCameraFollowMode.NORTH_UP_FOLLOW
            NavCameraFollowMode.NORTH_UP_FOLLOW -> NavCameraFollowMode.HEADING_UP
            NavCameraFollowMode.FREE_PAN -> NavCameraFollowMode.HEADING_UP
        }
        _activeNavigation.value = cur.copy(followMode = next)
    }

    fun setNavFreePan() {
        val cur = _activeNavigation.value ?: return
        if (cur.followMode != NavCameraFollowMode.FREE_PAN) {
            _activeNavigation.value = cur.copy(followMode = NavCameraFollowMode.FREE_PAN)
        }
    }

    fun recenterNavigation() {
        val cur = _activeNavigation.value ?: return
        _activeNavigation.value = cur.copy(followMode = NavCameraFollowMode.HEADING_UP)
    }

    fun toggleNavVoice() {
        val cur = _activeNavigation.value ?: return
        val next = !cur.isVoiceEnabled
        navigationVoiceCoach.setEnabled(next)
        _activeNavigation.value = cur.copy(isVoiceEnabled = next)
        showNotification(
            if (next) "🔊 Đã bật đọc hướng rẽ tiếng Việt (Offline)"
            else "🔇 Đã tắt giọng nói chỉ đường"
        )
    }

    fun toggleNavOriginMode() {
        val cur = _activeNavigation.value ?: return
        startInAppNavigation(
            destination = cur.destination,
            forceFromHaGiangOrigin = !cur.forceFromHaGiangOrigin
        )
    }

    fun findNextUnvisitedScheduledPlace(afterPlaceId: String): Place? {
        val currentDay = tripConfig.value.currentDay
        val planCode = activePlanCode.value
        val placesMap = places.value.associateBy { it.id }
        val orderedPlaces = itinerarySlots.value
            .filter { it.planCode == planCode && it.dayNumber == currentDay }
            .sortedBy { it.slot.ordinal }
            .flatMap { it.suggestedPlaceIds }
            .mapNotNull { placesMap[it] }
        if (orderedPlaces.isEmpty()) return null
        val idx = orderedPlaces.indexOfFirst { it.id == afterPlaceId }
        val candidates = if (idx >= 0) orderedPlaces.drop(idx + 1) else orderedPlaces
        return candidates.firstOrNull { !it.isVisited && it.id != afterPlaceId }
    }

    private fun showNotification(msg: String) {
        _notificationMessage.value = msg
    }
}
