package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.DownloadJob
import com.livingroomhq.core.data.model.ServiceHealth
import com.livingroomhq.core.data.model.ServiceStatus
import com.livingroomhq.core.data.model.Weather
import com.livingroomhq.core.data.model.WeatherCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Ambient information: weather, monitored services and download activity.
 * Demo implementation emits plausible data so every dashboard is alive
 * out of the box; real providers (Open-Meteo, NAS APIs, *arr stack) swap in
 * behind the same surface.
 */
interface AmbientInfoRepository {
    val weather: StateFlow<Weather>
    val services: StateFlow<List<ServiceStatus>>
    fun downloads(): Flow<List<DownloadJob>>
}

class DemoAmbientInfoRepository : AmbientInfoRepository {

    private val _weather = MutableStateFlow(
        Weather(temperatureF = 72, condition = WeatherCondition.PARTLY_CLOUDY,
            summary = "Partly Cloudy", highF = 78, lowF = 61)
    )
    override val weather: StateFlow<Weather> = _weather.asStateFlow()

    private val _services = MutableStateFlow(
        listOf(
            ServiceStatus("Media Server", ServiceHealth.ONLINE, "12 streams cached"),
            ServiceStatus("NAS", ServiceHealth.ONLINE, "14.2 TB free"),
            ServiceStatus("Smart Home Hub", ServiceHealth.ONLINE, "23 devices"),
            ServiceStatus("VPN Gateway", ServiceHealth.DEGRADED, "High latency"),
        )
    )
    override val services: StateFlow<List<ServiceStatus>> = _services.asStateFlow()

    override fun downloads(): Flow<List<DownloadJob>> = flow {
        var first = 0.34f
        var second = 0.81f
        while (true) {
            emit(
                listOf(
                    DownloadJob("glasslands.s02e06.2160p.mkv", first, 4_800),
                    DownloadJob("aurora-sessions-flac.zip", second, 1_250),
                )
            )
            first = (first + 0.004f).coerceAtMost(1f)
            second = (second + 0.002f).coerceAtMost(1f)
            delay(1_500)
        }
    }
}
