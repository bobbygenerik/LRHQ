package com.livingroomhq.core.data.model

/** A live IPTV channel from an M3U/Xtream source. */
data class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val group: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
)

/** EPG program entry. */
data class Program(
    val channelId: String,
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
) {
    fun progressAt(now: Long): Float =
        ((now - startMillis).toFloat() / (endMillis - startMillis).toFloat()).coerceIn(0f, 1f)
}

enum class MediaType { MOVIE, SHOW, MUSIC }

/** A library item for the Media zone. */
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val description: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val runtimeMinutes: Int = 0,
    val year: Int = 0,
    /** 0f..1f watched, for Continue Watching. */
    val watchProgress: Float = 0f,
    val episodeInfo: String? = null,
    val addedAtMillis: Long = 0L,
)

/** Live system metrics for the Command Center. */
data class SystemStats(
    val cpuPercent: Float,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val storageUsedBytes: Long,
    val storageTotalBytes: Long,
    val networkDownKbps: Long,
    val networkUpKbps: Long,
    val vpnActive: Boolean,
    val uptimeMillis: Long,
) {
    val ramPercent: Float get() = if (ramTotalMb > 0) ramUsedMb.toFloat() / ramTotalMb else 0f
    val storagePercent: Float get() =
        if (storageTotalBytes > 0) storageUsedBytes.toFloat() / storageTotalBytes else 0f
}

enum class WeatherCondition { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, STORM }

data class Weather(
    val temperatureF: Int,
    val condition: WeatherCondition,
    val summary: String,
    val highF: Int,
    val lowF: Int,
)

/** An installed launchable application. */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val isTvApp: Boolean,
)

enum class ServiceHealth { ONLINE, DEGRADED, OFFLINE }

/** Status of a monitored service (media server, NAS, smart home hub...). */
data class ServiceStatus(
    val name: String,
    val health: ServiceHealth,
    val detail: String,
)

data class DownloadJob(
    val name: String,
    val progress: Float,
    val speedKbps: Long,
)
