package com.livingroomhq.core.data.repo

import android.util.Log
import com.livingroomhq.core.data.model.DownloadJob
import com.livingroomhq.core.data.model.ServiceStatus
import com.livingroomhq.core.data.model.Weather
import com.livingroomhq.core.data.model.WeatherCondition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ambient information: weather, monitored services and download activity.
 * Provider-backed implementations can plug into weather, NAS, smart-home, and
 * download APIs behind this surface.
 */
interface AmbientInfoRepository {
    val weather: StateFlow<Weather?>
    val services: StateFlow<List<ServiceStatus>>
    fun downloads(): Flow<List<DownloadJob>>
}

class UnconfiguredAmbientInfoRepository : AmbientInfoRepository {

    private val _weather = MutableStateFlow<Weather?>(null)
    override val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _services = MutableStateFlow<List<ServiceStatus>>(emptyList())
    override val services: StateFlow<List<ServiceStatus>> = _services.asStateFlow()

    override fun downloads(): Flow<List<DownloadJob>> = flowOf(emptyList())
}

class RealAmbientInfoRepository(
    private val scope: CoroutineScope,
    private val fetchText: suspend (String) -> String = ::httpGet,
    private val refreshMillis: Long = 60 * 60 * 1000L,
) : AmbientInfoRepository {

    private val _weather = MutableStateFlow<Weather?>(null)
    override val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private val _services = MutableStateFlow<List<ServiceStatus>>(emptyList())
    override val services: StateFlow<List<ServiceStatus>> = _services.asStateFlow()

    init {
        scope.launch {
            while (true) {
                refreshWeather()
                delay(refreshMillis)
            }
        }
    }

    override fun downloads(): Flow<List<DownloadJob>> = flowOf(emptyList())

    suspend fun refreshWeather() {
        runCatching {
            val location = detectLocation()
            fetchWeather(location)
        }.onSuccess { current ->
            _weather.value = current
        }.onFailure { error ->
            Log.w(TAG, "Weather refresh failed", error)
        }
    }

    private suspend fun detectLocation(): WeatherLocation {
        return runCatching {
            val json = JSONObject(fetchText(IP_LOCATION_URL))
            WeatherLocation(
                latitude = json.getDouble("latitude"),
                longitude = json.getDouble("longitude"),
            )
        }.getOrElse { error ->
            Log.w(TAG, "IP location lookup failed; using fallback location", error)
            FALLBACK_LOCATION
        }
    }

    private suspend fun fetchWeather(location: WeatherLocation): Weather {
        val url = OPEN_METEO_URL.format(Locale.US, location.latitude, location.longitude)
        return parseOpenMeteoWeather(fetchText(url))
    }

    private data class WeatherLocation(
        val latitude: Double,
        val longitude: Double,
    )

    companion object {
        private const val TAG = "AmbientInfo"
        private const val IP_LOCATION_URL = "https://ipapi.co/json/"
        private val FALLBACK_LOCATION = WeatherLocation(latitude = 41.8240, longitude = -71.4128)
        private const val OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=%.5f&longitude=%.5f" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=fahrenheit" +
                "&timezone=auto" +
                "&forecast_days=1"

        fun parseOpenMeteoWeather(jsonText: String): Weather {
            val json = JSONObject(jsonText)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val code = current.getInt("weather_code")
            return Weather(
                temperatureF = current.getDouble("temperature_2m").roundToInt(),
                condition = code.toCondition(),
                summary = code.toSummary(),
                highF = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
                lowF = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            )
        }

        private fun Int.toCondition(): WeatherCondition = when (this) {
            0 -> WeatherCondition.CLEAR
            1, 2 -> WeatherCondition.PARTLY_CLOUDY
            3, 45, 48 -> WeatherCondition.CLOUDY
            in 51..67, in 80..82, 95, 96, 99 -> WeatherCondition.RAIN
            in 71..77, 85, 86 -> WeatherCondition.SNOW
            else -> WeatherCondition.CLOUDY
        }

        private fun Int.toSummary(): String = when (this) {
            0 -> "Clear"
            1 -> "Mostly Clear"
            2 -> "Partly Cloudy"
            3 -> "Cloudy"
            45, 48 -> "Fog"
            51, 53, 55 -> "Drizzle"
            56, 57 -> "Freezing Drizzle"
            61, 63, 65 -> "Rain"
            66, 67 -> "Freezing Rain"
            71, 73, 75 -> "Snow"
            77 -> "Snow Grains"
            80, 81, 82 -> "Rain Showers"
            85, 86 -> "Snow Showers"
            95, 96, 99 -> "Thunderstorm"
            else -> "Weather"
        }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    connection.requestMethod = "GET"
    connection.inputStream.bufferedReader().use { it.readText() }
}
