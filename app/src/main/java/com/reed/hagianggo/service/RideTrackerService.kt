package com.reed.hagianggo.service

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reed.hagianggo.MainActivity
import com.reed.hagianggo.data.HaGiangDatabase
import com.reed.hagianggo.domain.AutoCheckInCoordinator
import com.reed.hagianggo.domain.LocationMath
import com.reed.hagianggo.domain.MilestoneDetector
import com.reed.hagianggo.domain.PacerNotificationHelper
import com.reed.hagianggo.model.LiveRideState
import com.reed.hagianggo.model.Place
import com.reed.hagianggo.model.RidePoint
import com.reed.hagianggo.model.VehicleType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

class RideTrackerService : Service(), LocationListener {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var locationManager: LocationManager? = null
    private lateinit var database: HaGiangDatabase

    private var currentSessionId: Long = 0L
    private var lastLocation: Location? = null
    private var totalDistanceKm: Double = 0.0
    private var maxSpeedKmh: Float = 0f
    private var maxAltitude: Double = 0.0
    private var minAltitude: Double = 9999.0
    private var startTimeMillis: Long = 0L
    private var isPaused: Boolean = false

    private val recentPoints = mutableListOf<Pair<Double, Double>>()
    private var cachedPlaces: List<Place> = emptyList()
    private var cachedVehicle: VehicleType = VehicleType.MOTORBIKE
    private var isProviderReceiverRegistered = false

    private val providerSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == LocationManager.PROVIDERS_CHANGED_ACTION ||
                action == LocationManager.MODE_CHANGED_ACTION
            ) {
                if (_liveRideState.value.isTracking && !isPaused) {
                    bindRideLocationUpdates()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "hagiang_ride_tracker"
        const val NOTIFICATION_ID = 2026

        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"

        private val _liveRideState = MutableStateFlow(LiveRideState())
        val liveRideState: StateFlow<LiveRideState> = _liveRideState.asStateFlow()

        fun startRide(context: Context) {
            val intent = Intent(context, RideTrackerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseRide(context: Context) {
            val intent = Intent(context, RideTrackerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeRide(context: Context) {
            val intent = Intent(context, RideTrackerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopRide(context: Context) {
            val intent = Intent(context, RideTrackerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        database = HaGiangDatabase.getInstance(this)
        createNotificationChannel()
        PacerNotificationHelper.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (_liveRideState.value.isTracking) return

        startTimeMillis = System.currentTimeMillis()
        totalDistanceKm = 0.0
        maxSpeedKmh = 0f
        maxAltitude = 0.0
        minAltitude = 9999.0
        isPaused = false
        recentPoints.clear()

        serviceScope.launch(Dispatchers.IO) {
            currentSessionId = database.createRideSession()
            cachedPlaces = database.getAllPlaces()
            cachedVehicle = database.getTripConfig().vehicle
        }

        val notification = buildNotification(0f, 0.0, 0.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        _liveRideState.value = LiveRideState(
            isTracking = true,
            isPaused = false,
            sessionId = currentSessionId
        )

        // Lắng nghe bật/tắt GPS giữa chừng & đăng ký cập nhật GPS liên tục
        if (!isProviderReceiverRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                    addAction(LocationManager.MODE_CHANGED_ACTION)
                }
                ContextCompat.registerReceiver(
                    this,
                    providerSwitchReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isProviderReceiverRegistered = true
            } catch (_: Exception) {}
        }
        bindRideLocationUpdates()

        // Bắt đầu ticker tính thời gian trôi qua mỗi giây
        startTimer()
    }

    @SuppressLint("MissingPermission")
    private fun bindRideLocationUpdates() {
        val lm = locationManager ?: return
        try {
            try {
                lm.removeUpdates(this)
            } catch (_: Exception) {}

            val all = lm.allProviders
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                all.contains(LocationManager.FUSED_PROVIDER)
            ) {
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 2500L, 4f, this, Looper.getMainLooper())
            }
            if (all.contains(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2500L, 4f, this, Looper.getMainLooper())
            }
            if (all.contains(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000L, 10f, this, Looper.getMainLooper())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val exec = ContextCompat.getMainExecutor(this)
                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.getCurrentLocation(LocationManager.GPS_PROVIDER, null, exec) { loc ->
                        if (loc != null) onLocationChanged(loc)
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    all.contains(LocationManager.FUSED_PROVIDER) &&
                    lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
                ) {
                    lm.getCurrentLocation(LocationManager.FUSED_PROVIDER, null, exec) { loc ->
                        if (loc != null) onLocationChanged(loc)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun pauseTracking() {
        isPaused = true
        _liveRideState.value = _liveRideState.value.copy(isPaused = true)
        updateNotification(_liveRideState.value.currentSpeedKmh, _liveRideState.value.currentAltitude, totalDistanceKm)
    }

    private fun resumeTracking() {
        isPaused = false
        _liveRideState.value = _liveRideState.value.copy(isPaused = false)
        updateNotification(_liveRideState.value.currentSpeedKmh, _liveRideState.value.currentAltitude, totalDistanceKm)
    }

    private fun stopTracking() {
        locationManager?.removeUpdates(this)
        if (isProviderReceiverRegistered) {
            try {
                unregisterReceiver(providerSwitchReceiver)
            } catch (_: Exception) {}
            isProviderReceiverRegistered = false
        }

        val sessionToFinish = currentSessionId
        val distToSave = totalDistanceKm
        val maxSpdToSave = maxSpeedKmh
        val maxAltToSave = maxAltitude
        val minAltToSave = if (minAltitude == 9999.0) 0.0 else minAltitude
        val startedMs = startTimeMillis

        if (sessionToFinish > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                val avgSpeed = if (distToSave > 0) {
                    val hours = ((System.currentTimeMillis() - startedMs) / 3600000.0).coerceAtLeast(0.001)
                    (distToSave / hours).toFloat()
                } else 0f

                database.finishRideSession(
                    sessionId = sessionToFinish,
                    totalDistKm = distToSave,
                    maxSpeedKmh = maxSpdToSave,
                    avgSpeedKmh = avgSpeed,
                    maxAlt = maxAltToSave,
                    minAlt = minAltToSave
                )
            }
        }

        serviceScope.cancel()
        _liveRideState.value = LiveRideState(isTracking = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private var timerJob: Job? = null
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            var overstayTick = 0
            while (isActive) {
                delay(1000L)
                if (!isPaused && _liveRideState.value.isTracking) {
                    val elapsed = (System.currentTimeMillis() - startTimeMillis) / 1000L
                    _liveRideState.value = _liveRideState.value.copy(elapsedTimeSeconds = elapsed)
                    overstayTick++
                    if (overstayTick >= 15) {
                        overstayTick = 0
                        AutoCheckInCoordinator.checkOverstay(this@RideTrackerService)
                        if (AutoCheckInCoordinator.activeStopTimer.value != null) {
                            val st = _liveRideState.value
                            updateNotification(st.currentSpeedKmh, st.currentAltitude, st.totalDistanceKm)
                        }
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (isPaused) return

        val lat = location.latitude
        val lng = location.longitude
        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6f).coerceAtLeast(0f) else 0f
        val altitudeMeters = if (location.hasAltitude()) location.altitude else 0.0

        // Cập nhật thống kê cực đại
        if (speedKmh > maxSpeedKmh) maxSpeedKmh = speedKmh
        if (altitudeMeters > maxAltitude) maxAltitude = altitudeMeters
        if (altitudeMeters in 1.0..minAltitude) minAltitude = altitudeMeters

        // Tính khoảng cách di chuyển
        var deltaKmMoved = 0.0
        val prev = lastLocation
        if (prev != null) {
            val dist = LocationMath.calculateDistanceKm(prev.latitude, prev.longitude, lat, lng)
            if (dist in 0.008..5.0) { // Lọc nhiễu < 8m và teleport > 5km
                totalDistanceKm += dist
                deltaKmMoved = dist
            }
        }
        lastLocation = location

        // Lưu vệt tọa độ
        recentPoints.add(Pair(lat, lng))
        if (recentPoints.size > 200) {
            recentPoints.removeAt(0)
        }

        // Ghi điểm vào SQLite
        serviceScope.launch(Dispatchers.IO) {
            if (deltaKmMoved > 0.0) {
                database.deductFuelKm(deltaKmMoved)
            }
            database.insertRidePoint(
                RidePoint(
                    sessionId = currentSessionId,
                    lat = lat,
                    lng = lng,
                    altitude = altitudeMeters,
                    speedKmh = speedKmh
                )
            )

            // Hợp nhất Auto Check-in (<= 250m) + Auto Pit-Stop Timer + Thông báo màn hình khóa
            val checkedInPlace = AutoCheckInCoordinator.processLocation(
                context = this@RideTrackerService,
                database = database,
                lat = lat,
                lng = lng,
                speedKmh = speedKmh,
                allPlaces = cachedPlaces,
                vehicle = cachedVehicle
            )
            if (checkedInPlace != null) {
                cachedPlaces = database.getAllPlaces()
            }

            // Tìm điểm đến kế tiếp theo đúng phương tiện
            val nextMilestone = MilestoneDetector.findNextMilestone(lat, lng, cachedPlaces, cachedVehicle)

            withContext(Dispatchers.Main) {
                _liveRideState.value = _liveRideState.value.copy(
                    currentSpeedKmh = speedKmh,
                    currentAltitude = altitudeMeters,
                    totalDistanceKm = totalDistanceKm,
                    maxSpeedKmh = maxSpeedKmh,
                    maxAltitude = maxAltitude,
                    nextMilestonePlace = nextMilestone?.first,
                    distanceToNextKm = nextMilestone?.second ?: 0.0,
                    recentPoints = recentPoints.toList(),
                    lastVisitedPlaceName = checkedInPlace?.name ?: _liveRideState.value.lastVisitedPlaceName
                )
            }
        }

        updateNotification(speedKmh, altitudeMeters, totalDistanceKm)
    }

    private fun buildNotification(speedKmh: Float, altitude: Double, distanceKm: Double): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, RideTrackerService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RideTrackerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val activeStop = AutoCheckInCoordinator.activeStopTimer.value
        val statusText = when {
            isPaused -> "Đang tạm dừng chặng"
            activeStop != null -> {
                val elapsedMin = ((System.currentTimeMillis() - activeStop.startedAtMillis) / 60_000L).toInt().coerceAtLeast(0)
                val remainMin = activeStop.targetDurationMin - elapsedMin
                val timerStr = if (remainMin >= 0) "còn ${remainMin}p" else "lố ${-remainMin}p"
                "⏱️ Đang dừng tại ${activeStop.placeName} ($timerStr) • Đã đi: ${String.format(java.util.Locale.US, "%.1f", distanceKm)} km"
            }
            else -> "Vận tốc: ${speedKmh.toInt()} km/h • Cao độ: ${altitude.toInt()}m • Đã đi: ${String.format(java.util.Locale.US, "%.1f", distanceKm)} km"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Hà Giang Go • Đang ghi hành trình")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPaused) "Tiếp tục" else "Tạm dừng",
                pausePendingIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Kết thúc", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(speedKmh: Float, altitude: Double, distanceKm: Double) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(speedKmh, altitude, distanceKm))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hành trình phượt Hà Giang",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Theo dõi tốc độ, độ cao và vệt đường thời gian thực"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        if (isProviderReceiverRegistered) {
            try {
                unregisterReceiver(providerSwitchReceiver)
            } catch (_: Exception) {}
            isProviderReceiverRegistered = false
        }
        serviceScope.cancel()
    }
}
