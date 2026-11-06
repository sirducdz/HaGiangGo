package com.reed.hagianggo.domain

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OfflineLocationTracker(private val context: Context) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val currentLocation: StateFlow<Pair<Double, Double>?> = _currentLocation.asStateFlow()

    private val _manualOverrideZoneId = MutableStateFlow<String?>(null)
    val manualOverrideZoneId: StateFlow<String?> = _manualOverrideZoneId.asStateFlow()

    private val _isGpsEnabled = MutableStateFlow(false)
    val isGpsEnabled: StateFlow<Boolean> = _isGpsEnabled.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startListening() {
        val lm = locationManager ?: return
        try {
            val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            _isGpsEnabled.value = gpsEnabled || netEnabled

            // Lấy ngay last known location
            val lastGps = if (gpsEnabled) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNet = if (netEnabled) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            val bestLast = lastGps ?: lastNet
            if (bestLast != null) {
                _currentLocation.value = Pair(bestLast.latitude, bestLast.longitude)
            }

            // Đăng ký cập nhật định kỳ (mỗi 10s hoặc di chuyển 15m)
            if (gpsEnabled) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000L, 15f, this)
            }
            if (netEnabled) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000L, 15f, this)
            }
        } catch (_: SecurityException) {
            // Chưa được cấp quyền location
        } catch (_: Exception) {
            // Không hỗ trợ
        }
    }

    fun stopListening() {
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {
            // Ignore
        }
    }

    fun setManualZoneOverride(zoneId: String?) {
        _manualOverrideZoneId.value = zoneId
    }

    override fun onLocationChanged(location: Location) {
        _currentLocation.value = Pair(location.latitude, location.longitude)
    }

    override fun onProviderEnabled(provider: String) {
        _isGpsEnabled.value = true
    }

    override fun onProviderDisabled(provider: String) {
        val lm = locationManager ?: return
        val gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val netEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        _isGpsEnabled.value = gpsEnabled || netEnabled
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}
