package com.reed.hagianggo.domain

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OfflineLocationTracker(private val context: Context) : LocationListener {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _manualOverrideZoneId = MutableStateFlow<String?>(null)
    val manualOverrideZoneId: StateFlow<String?> = _manualOverrideZoneId.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    private var isReceiverRegistered = false
    private var lastKnownEnabledState = false

    // Lắng nghe sự kiện bật/tắt Vị trí ngay lập tức từ thanh trạng thái (Quick Settings)
    private val locationSwitchReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == LocationManager.PROVIDERS_CHANGED_ACTION ||
                action == LocationManager.MODE_CHANGED_ACTION
            ) {
                syncProvidersAndRequestImmediateFix(forceRebind = true)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isSystemLocationOn(lm: LocationManager): Boolean {
        return try {
            val compatEnabled = LocationManagerCompat.isLocationEnabled(lm)
            val anyProvider = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    lm.allProviders.contains(LocationManager.FUSED_PROVIDER) &&
                    lm.isProviderEnabled(LocationManager.FUSED_PROVIDER))
            compatEnabled && anyProvider
        } catch (_: Exception) {
            false
        }
    }

    fun startListening() {
        registerLocationSwitchReceiver()
        syncProvidersAndRequestImmediateFix(forceRebind = true)
    }

    /**
     * Được gọi khi người dùng quay lại app (onResume), vừa cấp quyền xong,
     * hoặc bấm nút "Vị trí của tôi" trên bản đồ.
     */
    fun refreshLocationState(requestImmediate: Boolean = true) {
        registerLocationSwitchReceiver()
        val lm = locationManager ?: return
        val nowEnabled = isSystemLocationOn(lm)
        val transitionedOn = !lastKnownEnabledState && nowEnabled
        syncProvidersAndRequestImmediateFix(
            forceRebind = transitionedOn || _currentLocation.value == null,
            requestImmediate = requestImmediate
        )
    }

    private fun registerLocationSwitchReceiver() {
        if (isReceiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
                addAction(LocationManager.MODE_CHANGED_ACTION)
            }
            ContextCompat.registerReceiver(
                appContext,
                locationSwitchReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        } catch (_: Exception) {
            // Bỏ qua nếu thiết bị hạn chế
        }
    }

    @SuppressLint("MissingPermission")
    private fun syncProvidersAndRequestImmediateFix(
        forceRebind: Boolean = false,
        requestImmediate: Boolean = true
    ) {
        val lm = locationManager ?: return
        if (!hasLocationPermission()) {
            _isGpsEnabled.value = false
            lastKnownEnabledState = false
            return
        }

        val enabled = isSystemLocationOn(lm)
        _isGpsEnabled.value = enabled
        val justTurnedOn = !lastKnownEnabledState && enabled
        lastKnownEnabledState = enabled

        try {
            // Trên Realme UI / ColorOS / HyperOS, khi bật Vị trí từ trạng thái tắt,
            // cần re-bind lại requestLocationUpdates để hệ thống kích hoạt phần cứng GPS ngay.
            if (forceRebind || justTurnedOn) {
                try {
                    lm.removeUpdates(this)
                } catch (_: Exception) {}

                val available = lm.allProviders
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    available.contains(LocationManager.FUSED_PROVIDER)
                ) {
                    lm.requestLocationUpdates(
                        LocationManager.FUSED_PROVIDER,
                        5000L,
                        8f,
                        this,
                        Looper.getMainLooper()
                    )
                }
                if (available.contains(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        6000L,
                        10f,
                        this,
                        Looper.getMainLooper()
                    )
                }
                if (available.contains(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        8000L,
                        15f,
                        this,
                        Looper.getMainLooper()
                    )
                }
            }

            if (enabled) {
                // 1. Lấy ngay tọa độ cache mới nhất (nếu có) để hiển thị tức thì < 50ms
                val bestCached = getBestLastKnownLocation(lm)
                if (bestCached != null) {
                    _currentLocation.value = Pair(bestCached.latitude, bestCached.longitude)
                }

                // 2. Chủ động ép lấy 1 điểm GPS/Fused mới ngay lập tức (kể cả khi đang đứng yên 0m)
                if (requestImmediate || justTurnedOn || _currentLocation.value == null) {
                    requestImmediateSingleFix(lm)
                }
            }
        } catch (_: SecurityException) {
            // Chưa có quyền
        } catch (_: Exception) {
            // Bỏ qua lỗi thiết bị ngoại lệ
        }
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(lm: LocationManager): Location? {
        val candidates = mutableListOf<Location>()
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        for (provider in providers) {
            try {
                if (lm.allProviders.contains(provider)) {
                    lm.getLastKnownLocation(provider)?.let { candidates.add(it) }
                }
            } catch (_: Exception) {}
        }
        return candidates.maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private fun requestImmediateSingleFix(lm: LocationManager) {
        val providersToQuery = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
            if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            ) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (lm.allProviders.contains(LocationManager.GPS_PROVIDER) &&
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            ) {
                add(LocationManager.GPS_PROVIDER)
            }
        }

        val mainExecutor = ContextCompat.getMainExecutor(appContext)
        for (provider in providersToQuery) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lm.getCurrentLocation(provider, null, mainExecutor) { location ->
                        if (location != null) {
                            _currentLocation.value = Pair(location.latitude, location.longitude)
                            _isGpsEnabled.value = true
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, this, Looper.getMainLooper())
                }
            } catch (_: Exception) {}
        }
    }

    fun stopListening() {
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
        if (isReceiverRegistered) {
            try {
                appContext.unregisterReceiver(locationSwitchReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
    }

    fun setManualZoneOverride(zoneId: String?) {
        _manualOverrideZoneId.value = zoneId
    }

    override fun onLocationChanged(location: Location) {
        _currentLocation.value = Pair(location.latitude, location.longitude)
        _isGpsEnabled.value = true
    }

    override fun onProviderEnabled(provider: String) {
        _isGpsEnabled.value = true
        locationManager?.let { lm ->
            requestImmediateSingleFix(lm)
        }
    }

    override fun onProviderDisabled(provider: String) {
        val lm = locationManager ?: return
        _isGpsEnabled.value = isSystemLocationOn(lm)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
