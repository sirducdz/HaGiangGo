package com.reed.hagianggo.domain

import android.content.Context
import com.reed.hagianggo.data.HaGiangDatabase
import com.reed.hagianggo.model.ActiveStopTimer
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.VehicleType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Logic thuần Kotlin cho Auto Check-in, Geofence Pit-Stop & Học tốc độ tay lái.
 * Tách khỏi Android framework để đảm bảo kiểm thử đơn vị (JVM Unit Test) 100%.
 */
object AutoCheckInLogic {

    const val ARRIVE_RADIUS_KM = 0.25         // <= 250m tính là đã vào điểm
    const val DEPART_RADIUS_KM = 0.35         // > 350m tính là đã rời trạm dừng
    const val STOP_SPEED_THRESHOLD_KMH = 18f  // <= 18 km/h: đang giảm tốc / dừng chơi tại điểm
    const val DEPART_MIN_ELAPSED_SEC = 60L    // Dừng tối thiểu 60s mới tự động chốt rời trạm khi > 350m
    const val DEPART_SPEED_THRESHOLD_KMH = 16f

    sealed class TransitionAction {
        object None : TransitionAction()
        data class ArriveAndStartTimer(val place: Place) : TransitionAction()
        data class PassThroughCheckIn(val place: Place) : TransitionAction()
        data class DepartPitStop(val place: Place) : TransitionAction()
    }

    fun evaluateLocationTransition(
        lat: Double,
        lng: Double,
        speedKmh: Float,
        nowMillis: Long,
        activeTimer: ActiveStopTimer?,
        allPlaces: List<Place>,
        skippedPlaceIds: Set<String>,
        alreadyNotifiedIds: Set<String>
    ): TransitionAction {
        if (activeTimer != null) {
            val stoppedPlace = allPlaces.firstOrNull { it.id == activeTimer.placeId }
                ?: return TransitionAction.None
            val distKm = LocationMath.calculateDistanceKm(lat, lng, stoppedPlace.lat, stoppedPlace.lng)
            val elapsedSec = ((nowMillis - activeTimer.startedAtMillis) / 1000L).coerceAtLeast(0L)
            return if (distKm > DEPART_RADIUS_KM &&
                (elapsedSec >= DEPART_MIN_ELAPSED_SEC || speedKmh >= DEPART_SPEED_THRESHOLD_KMH)
            ) {
                TransitionAction.DepartPitStop(stoppedPlace)
            } else {
                TransitionAction.None
            }
        }

        val nearby = allPlaces
            .asSequence()
            .filter { !it.isVisited && it.id !in skippedPlaceIds && it.id !in alreadyNotifiedIds }
            .map { place -> place to LocationMath.calculateDistanceKm(lat, lng, place.lat, place.lng) }
            .filter { it.second <= ARRIVE_RADIUS_KM }
            .minByOrNull { it.second }
            ?.first ?: return TransitionAction.None

        return if (speedKmh <= STOP_SPEED_THRESHOLD_KMH) {
            TransitionAction.ArriveAndStartTimer(nearby)
        } else {
            TransitionAction.PassThroughCheckIn(nearby)
        }
    }

    fun computeUpdatedPaceFactor(
        currentFactor: Float,
        prevPlace: Place?,
        arrivedPlace: Place,
        prevDepartedAtMillis: Long,
        nowMillis: Long,
        vehicle: VehicleType
    ): Float {
        if (prevPlace == null || prevPlace.id == arrivedPlace.id || prevDepartedAtMillis <= 0L) {
            return currentFactor
        }
        val actualDriveMin = ((nowMillis - prevDepartedAtMillis) / 60_000L).toInt()
        val straightKm = LocationMath.calculateDistanceKm(
            prevPlace.lat,
            prevPlace.lng,
            arrivedPlace.lat,
            arrivedPlace.lng
        )
        val expectedMin = LocationMath.estimateMountainTravelTimeMin(straightKm, vehicle)
        if (actualDriveMin in 3..180 && expectedMin >= 4) {
            val observedRatio = (actualDriveMin.toFloat() / expectedMin.toFloat()).coerceIn(0.75f, 1.40f)
            return (currentFactor * 0.65f + observedRatio * 0.35f).coerceIn(0.75f, 1.40f)
        }
        return currentFactor
    }

    fun shouldTriggerOverstay(timer: ActiveStopTimer?, nowMillis: Long): Boolean {
        if (timer == null || timer.hasNotifiedOverstay) return false
        val elapsedSec = ((nowMillis - timer.startedAtMillis) / 1000L).coerceAtLeast(0L)
        return elapsedSec >= timer.targetDurationMin * 60L
    }
}

sealed class AutoCheckInEvent {
    data class ArrivedAndTimerStarted(
        val placeId: String,
        val placeName: String,
        val durationMin: Int,
        val isAuto: Boolean
    ) : AutoCheckInEvent()

    data class PassThroughCheckedIn(
        val placeId: String,
        val placeName: String
    ) : AutoCheckInEvent()

    data class DepartedPitStop(
        val placeId: String,
        val placeName: String,
        val isAuto: Boolean
    ) : AutoCheckInEvent()

    data class OverstayWarning(
        val placeId: String,
        val placeName: String,
        val targetDurationMin: Int
    ) : AutoCheckInEvent()

    data class PitStopDurationExtended(
        val placeId: String,
        val placeName: String,
        val newDurationMin: Int,
        val deltaMin: Int
    ) : AutoCheckInEvent()
}

/**
 * Nguồn sự thật duy nhất (Single Source of Truth) điều phối Auto Check-in,
 * Đồng hồ dừng chân (Pit-Stop Timer) và Thông báo màn hình khóa giữa Service nền & UI.
 */
object AutoCheckInCoordinator {

    private val lock = Any()

    private val _activeStopTimer = MutableStateFlow<ActiveStopTimer?>(null)
    val activeStopTimer: StateFlow<ActiveStopTimer?> = _activeStopTimer.asStateFlow()

    private val _isAutoCheckInEnabled = MutableStateFlow(true)
    val isAutoCheckInEnabled: StateFlow<Boolean> = _isAutoCheckInEnabled.asStateFlow()

    private val _riderPaceFactor = MutableStateFlow(1.0f)
    val riderPaceFactor: StateFlow<Float> = _riderPaceFactor.asStateFlow()

    private val _events = MutableSharedFlow<AutoCheckInEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AutoCheckInEvent> = _events.asSharedFlow()

    private val notifiedPlaceIds = mutableSetOf<String>()
    private var skippedPlaceIds: Set<String> = emptySet()
    private var lastDepartedPlace: Place? = null
    private var lastDepartedAtMillis: Long = 0L

    fun setAutoCheckInEnabled(enabled: Boolean) {
        synchronized(lock) {
            _isAutoCheckInEnabled.value = enabled
        }
    }

    fun setRiderPaceFactor(factor: Float) {
        synchronized(lock) {
            _riderPaceFactor.value = factor.coerceIn(0.75f, 1.45f)
        }
    }

    fun updateSkippedPlaceIds(ids: Set<String>) {
        synchronized(lock) {
            skippedPlaceIds = ids
        }
    }

    fun resetNotifiedPlace(placeId: String) {
        synchronized(lock) {
            notifiedPlaceIds.remove(placeId)
        }
    }

    fun syncPlaceDuration(placeId: String, newDurationMin: Int) {
        synchronized(lock) {
            val active = _activeStopTimer.value
            if (active != null && active.placeId == placeId) {
                _activeStopTimer.value = active.copy(
                    targetDurationMin = newDurationMin.coerceIn(10, 180),
                    hasNotifiedOverstay = false
                )
            }
        }
    }

    fun processLocation(
        context: Context,
        database: HaGiangDatabase,
        lat: Double,
        lng: Double,
        speedKmh: Float,
        allPlaces: List<Place>,
        vehicle: VehicleType
    ): Place? {
        val nowMillis = System.currentTimeMillis()
        synchronized(lock) {
            if (!_isAutoCheckInEnabled.value || allPlaces.isEmpty()) return null

            val action = AutoCheckInLogic.evaluateLocationTransition(
                lat = lat,
                lng = lng,
                speedKmh = speedKmh,
                nowMillis = nowMillis,
                activeTimer = _activeStopTimer.value,
                allPlaces = allPlaces,
                skippedPlaceIds = skippedPlaceIds,
                alreadyNotifiedIds = notifiedPlaceIds
            )

            return when (action) {
                is AutoCheckInLogic.TransitionAction.ArriveAndStartTimer -> {
                    val place = action.place
                    val targetMin = place.durationMin.coerceIn(10, 180)
                    notifiedPlaceIds.add(place.id)
                    database.togglePlaceVisited(place.id, true)
                    _riderPaceFactor.value = AutoCheckInLogic.computeUpdatedPaceFactor(
                        currentFactor = _riderPaceFactor.value,
                        prevPlace = lastDepartedPlace,
                        arrivedPlace = place,
                        prevDepartedAtMillis = lastDepartedAtMillis,
                        nowMillis = nowMillis,
                        vehicle = vehicle
                    )
                    _activeStopTimer.value = ActiveStopTimer(
                        placeId = place.id,
                        placeName = place.name,
                        startedAtMillis = nowMillis,
                        targetDurationMin = targetMin,
                        isAutoGeofence = true,
                        hasNotifiedOverstay = false
                    )
                    PacerNotificationHelper.notifyArrivedWithTimer(
                        context = context,
                        placeId = place.id,
                        placeName = place.name,
                        durationMin = targetMin,
                        shouldVibrate = true
                    )
                    _events.tryEmit(
                        AutoCheckInEvent.ArrivedAndTimerStarted(
                            placeId = place.id,
                            placeName = place.name,
                            durationMin = targetMin,
                            isAuto = true
                        )
                    )
                    place
                }

                is AutoCheckInLogic.TransitionAction.PassThroughCheckIn -> {
                    val place = action.place
                    notifiedPlaceIds.add(place.id)
                    database.togglePlaceVisited(place.id, true)
                    _riderPaceFactor.value = AutoCheckInLogic.computeUpdatedPaceFactor(
                        currentFactor = _riderPaceFactor.value,
                        prevPlace = lastDepartedPlace,
                        arrivedPlace = place,
                        prevDepartedAtMillis = lastDepartedAtMillis,
                        nowMillis = nowMillis,
                        vehicle = vehicle
                    )
                    lastDepartedPlace = place
                    lastDepartedAtMillis = nowMillis
                    PacerNotificationHelper.notifyPassThroughCheckIn(context, place.name)
                    _events.tryEmit(AutoCheckInEvent.PassThroughCheckedIn(place.id, place.name))
                    place
                }

                is AutoCheckInLogic.TransitionAction.DepartPitStop -> {
                    val place = action.place
                    _activeStopTimer.value = null
                    lastDepartedPlace = place
                    lastDepartedAtMillis = nowMillis
                    PacerNotificationHelper.notifyDepartedPitStop(context, place.name)
                    _events.tryEmit(AutoCheckInEvent.DepartedPitStop(place.id, place.name, isAuto = true))
                    null
                }

                AutoCheckInLogic.TransitionAction.None -> null
            }
        }
    }

    fun checkOverstay(context: Context) {
        val nowMillis = System.currentTimeMillis()
        synchronized(lock) {
            val current = _activeStopTimer.value ?: return
            if (!AutoCheckInLogic.shouldTriggerOverstay(current, nowMillis)) return

            val updated = current.copy(hasNotifiedOverstay = true)
            _activeStopTimer.value = updated
            PacerNotificationHelper.notifyOverstay(context, updated)
            _events.tryEmit(
                AutoCheckInEvent.OverstayWarning(
                    placeId = updated.placeId,
                    placeName = updated.placeName,
                    targetDurationMin = updated.targetDurationMin
                )
            )
        }
    }

    fun startManualPitStop(place: Place) {
        val targetMin = place.durationMin.coerceIn(10, 180)
        synchronized(lock) {
            _activeStopTimer.value = ActiveStopTimer(
                placeId = place.id,
                placeName = place.name,
                startedAtMillis = System.currentTimeMillis(),
                targetDurationMin = targetMin,
                isAutoGeofence = false,
                hasNotifiedOverstay = false
            )
        }
        _events.tryEmit(
            AutoCheckInEvent.ArrivedAndTimerStarted(
                placeId = place.id,
                placeName = place.name,
                durationMin = targetMin,
                isAuto = false
            )
        )
    }

    fun addPitStopMinutes(
        context: Context,
        database: HaGiangDatabase,
        extraMin: Int
    ) {
        synchronized(lock) {
            val current = _activeStopTimer.value ?: return
            val newTarget = (current.targetDurationMin + extraMin).coerceIn(10, 180)
            _activeStopTimer.value = current.copy(
                targetDurationMin = newTarget,
                hasNotifiedOverstay = false
            )
            database.updatePlaceDurationMin(current.placeId, newTarget)
            PacerNotificationHelper.cancelOverstayAlert(context)
            PacerNotificationHelper.notifyArrivedWithTimer(
                context = context,
                placeId = current.placeId,
                placeName = current.placeName,
                durationMin = newTarget,
                shouldVibrate = false
            )
            _events.tryEmit(
                AutoCheckInEvent.PitStopDurationExtended(
                    placeId = current.placeId,
                    placeName = current.placeName,
                    newDurationMin = newTarget,
                    deltaMin = extraMin
                )
            )
        }
    }

    fun completePitStop(
        context: Context,
        database: HaGiangDatabase,
        placeId: String,
        allPlaces: List<Place>,
        isAuto: Boolean = false
    ) {
        synchronized(lock) {
            val targetPlace = allPlaces.firstOrNull { it.id == placeId }
            val placeName = targetPlace?.name ?: _activeStopTimer.value?.placeName ?: ""
            if (placeId.isNotBlank()) {
                notifiedPlaceIds.add(placeId)
                database.togglePlaceVisited(placeId, true)
            }
            if (targetPlace != null) {
                lastDepartedPlace = targetPlace
                lastDepartedAtMillis = System.currentTimeMillis()
            }
            _activeStopTimer.value = null
            if (isAuto) {
                PacerNotificationHelper.notifyDepartedPitStop(context, placeName)
            } else {
                PacerNotificationHelper.cancelPitStopAlerts(context)
            }
            _events.tryEmit(AutoCheckInEvent.DepartedPitStop(placeId, placeName, isAuto = isAuto))
        }
    }

    fun cancelPitStop(context: Context) {
        synchronized(lock) {
            _activeStopTimer.value = null
            PacerNotificationHelper.cancelPitStopAlerts(context)
        }
    }
}
