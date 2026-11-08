package com.reed.hagianggo.domain

import com.reed.hagianggo.data.HaGiangRepository
import com.reed.hagianggo.model.DailyZoneTemp
import com.reed.hagianggo.model.Zone
import com.reed.hagianggo.model.ZoneWeatherForecast
import com.reed.hagianggo.model.elevationMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dự báo Thời tiết Đèo Cao & Tỷ lệ Săn Mây 8 Cụm (MountainWeatherManager).
 * - Đồng bộ dữ liệu thực theo đúng tọa độ + độ cao từng đèo từ Open-Meteo API (Miễn phí, không cần API Key).
 * - Lưu cache vào SQLite (weather_cache) để đọc Offline 100% khi mất sóng trên núi.
 * - Tính điểm Săn Mây Sáng Sớm (05:00 - 08:00) và Cảnh báo Sương Mù Dày Khuất Tầm Nhìn.
 */
class MountainWeatherManager(private val repository: HaGiangRepository) {

    private val _forecasts = MutableStateFlow<Map<String, ZoneWeatherForecast>>(emptyMap())
    val forecasts: StateFlow<Map<String, ZoneWeatherForecast>> = _forecasts.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    suspend fun loadCachedOrBaseline(zones: List<Zone>) = withContext(Dispatchers.IO) {
        if (zones.isEmpty()) return@withContext
        val cachedRaw = repository.getCachedWeatherRaw()
        val result = LinkedHashMap<String, ZoneWeatherForecast>()
        for (zone in zones) {
            val pair = cachedRaw[zone.id]
            val parsed = if (pair != null) {
                parseOpenMeteoJson(zone, pair.first, pair.second)
            } else {
                null
            }
            result[zone.id] = parsed ?: buildElevationBaseline(zone)
        }
        _forecasts.value = result
    }

    suspend fun syncLiveForecasts(zones: List<Zone>, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (zones.isEmpty() || _isSyncing.value) return@withContext false

        // Nếu vừa đồng bộ trong vòng 45 phút và không bấm làm mới thủ công thì dùng cache luôn cho tiết kiệm pin
        if (!force) {
            val latestSync = _forecasts.value.values.maxOfOrNull { it.fetchedAt } ?: 0L
            if (latestSync > 0L && System.currentTimeMillis() - latestSync < 45L * 60L * 1000L) {
                return@withContext true
            }
        }

        _isSyncing.value = true
        try {
            val now = System.currentTimeMillis()
            val updated = LinkedHashMap<String, ZoneWeatherForecast>(_forecasts.value)

            coroutineScope {
                zones.map { zone ->
                    async {
                        val rawJson = fetchZoneOpenMeteo(zone)
                        if (rawJson != null) {
                            val parsed = parseOpenMeteoJson(zone, rawJson, now)
                            if (parsed != null) {
                                repository.saveCachedWeatherRaw(zone.id, rawJson, now)
                                synchronized(updated) {
                                    updated[zone.id] = parsed
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            if (updated.isNotEmpty()) {
                _forecasts.value = updated
            }
            updated.values.any { it.isFromLiveSync }
        } catch (_: Exception) {
            false
        } finally {
            _isSyncing.value = false
        }
    }

    private fun fetchZoneOpenMeteo(zone: Zone): String? {
        val urlStr = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&elevation=%d" +
                "&current=temperature_2m,weather_code,cloud_cover" +
                "&hourly=temperature_2m,precipitation_probability,cloud_cover_low,visibility" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=Asia%%2FBangkok&forecast_days=4",
            zone.centerLat,
            zone.centerLng,
            zone.elevationMeters
        )
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 8000
                setRequestProperty("User-Agent", "HaGiangGo/1.2 (Android)")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseOpenMeteoJson(zone: Zone, jsonStr: String, fetchedAt: Long): ZoneWeatherForecast? {
        return try {
            val root = JSONObject(jsonStr)
            val currentObj = root.optJSONObject("current")
            val currentTemp = currentObj?.optDouble("temperature_2m", 18.0)?.roundToInt() ?: 18
            val weatherCode = currentObj?.optInt("weather_code", 1) ?: 1

            val dailyObj = root.optJSONObject("daily")
            val dates = dailyObj?.optJSONArray("time")
            val maxArr = dailyObj?.optJSONArray("temperature_2m_max")
            val minArr = dailyObj?.optJSONArray("temperature_2m_min")
            val rainArr = dailyObj?.optJSONArray("precipitation_probability_max")

            val dailyList = mutableListOf<DailyZoneTemp>()
            if (dates != null && maxArr != null && minArr != null) {
                val len = minOf(dates.length(), maxArr.length(), minArr.length())
                for (i in 0 until len) {
                    val rawDate = dates.optString(i, "")
                    val shortDate = if (rawDate.length >= 10) {
                        "${rawDate.substring(8, 10)}/${rawDate.substring(5, 7)}"
                    } else rawDate
                    dailyList.add(
                        DailyZoneTemp(
                            dateLabel = if (i == 0) "Hôm nay ($shortDate)" else shortDate,
                            minTempC = minArr.optDouble(i, 14.0).roundToInt(),
                            maxTempC = maxArr.optDouble(i, 23.0).roundToInt(),
                            rainProbPercent = rainArr?.optInt(i, 15) ?: 15
                        )
                    )
                }
            }

            val todayMin = dailyList.firstOrNull()?.minTempC ?: (currentTemp - 4)
            val todayMax = dailyList.firstOrNull()?.maxTempC ?: (currentTemp + 4)
            val todayRain = dailyList.firstOrNull()?.rainProbPercent ?: 20

            // Phân tích khung giờ sáng sớm (05:00 - 08:00 sáng mai hoặc sáng nay) để tính tỷ lệ Săn Mây & Sương Mù
            val hourlyObj = root.optJSONObject("hourly")
            val lowCloudArr = hourlyObj?.optJSONArray("cloud_cover_low")
            val visArr = hourlyObj?.optJSONArray("visibility")
            val rainHourlyArr = hourlyObj?.optJSONArray("precipitation_probability")

            var morningLowCloudAvg = 45.0
            var minVisibilityMeters = 8000.0
            var morningRainAvg = 20.0

            if (lowCloudArr != null && lowCloudArr.length() >= 33) {
                // Giờ thứ 29..32 tương ứng 05:00..08:00 sáng tiếp theo
                var sumCloud = 0.0
                var sumRain = 0.0
                for (h in 29..32) {
                    sumCloud += lowCloudArr.optDouble(h, 40.0)
                    sumRain += (rainHourlyArr?.optDouble(h, 15.0) ?: 15.0)
                }
                morningLowCloudAvg = sumCloud / 4.0
                morningRainAvg = sumRain / 4.0
            }
            if (visArr != null) {
                val checkLen = minOf(24, visArr.length())
                for (h in 0 until checkLen) {
                    val v = visArr.optDouble(h, 10000.0)
                    if (v < minVisibilityMeters) minVisibilityMeters = v
                }
            }

            val elevBonus = when {
                zone.elevationMeters >= 1400 -> 24
                zone.elevationMeters >= 1000 -> 15
                zone.elevationMeters >= 600 -> 6
                else -> -8
            }

            // Biển mây đẹp nhất khi mây tầng thấp đạt 35%..82% và xác suất mưa thấp (< 35%)
            val rawCloudScore = when {
                morningRainAvg >= 60 -> 18
                morningLowCloudAvg in 35.0..85.0 -> 66 + elevBonus - (morningRainAvg * 0.35).roundToInt()
                morningLowCloudAvg > 85.0 -> 52 + (elevBonus / 2) - (morningRainAvg * 0.4).roundToInt()
                else -> 32 + elevBonus
            }.coerceIn(12, 96)

            val isFogHazard = weatherCode in listOf(45, 48, 51, 53, 55, 61, 63, 65) ||
                minVisibilityMeters < 1800.0 ||
                (zone.elevationMeters >= 1050 && todayRain >= 65)

            val summary = when {
                weatherCode in listOf(45, 48) || minVisibilityMeters < 1500.0 -> "Sương mù dày trên đèo"
                todayRain >= 65 || weatherCode >= 61 -> "Có mưa rào vùng núi"
                rawCloudScore >= 75 && zone.elevationMeters >= 1000 -> "Đẹp để săn biển mây sớm"
                currentTemp <= 14 -> "Trời rét vùng cao, khô ráo"
                else -> "Quang mây, tầm nhìn thoáng"
            }

            ZoneWeatherForecast(
                zoneId = zone.id,
                currentTempC = currentTemp,
                minTempC = todayMin,
                maxTempC = todayMax,
                rainProbPercent = todayRain,
                cloudHuntScore = rawCloudScore,
                isFogHazard = isFogHazard,
                summaryLabel = summary,
                dailyForecasts = dailyList,
                isFromLiveSync = true,
                fetchedAt = fetchedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dữ liệu chuẩn theo quy luật giảm nhiệt độ theo độ cao (Lapse rate -6.5°C / 1000m)
     * khi người dùng mở ứng dụng lần đầu ở nơi hoàn toàn không có mạng.
     */
    private fun buildElevationBaseline(zone: Zone): ZoneWeatherForecast {
        val elev = zone.elevationMeters
        val lapseDrop = ((elev - 120) * 0.0065).roundToInt()
        val baseMax = (25 - lapseDrop).coerceAtLeast(13)
        val baseMin = (18 - lapseDrop).coerceAtLeast(7)
        val baseCur = ((baseMax + baseMin) / 2)
        val cloudScore = when {
            elev >= 1400 -> 78
            elev >= 1000 -> 68
            elev >= 600 -> 52
            else -> 35
        }
        return ZoneWeatherForecast(
            zoneId = zone.id,
            currentTempC = baseCur,
            minTempC = baseMin,
            maxTempC = baseMax,
            rainProbPercent = if (elev >= 1200) 25 else 15,
            cloudHuntScore = cloudScore,
            isFogHazard = false,
            summaryLabel = if (elev >= 1000) "Khí hậu đèo cao (~${elev}m)" else "Thung lũng êm dịu (~${elev}m)",
            dailyForecasts = emptyList(),
            isFromLiveSync = false,
            fetchedAt = 0L
        )
    }
}
