package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.DownloadJob
import com.livingroomhq.core.data.model.ServiceStatus
import com.livingroomhq.core.data.model.Weather
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

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
