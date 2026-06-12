package com.livingroomhq.widgets

import com.livingroomhq.HqApplication
import com.livingroomhq.core.widget.WidgetPlugin
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.core.widget.WidgetSize
import com.livingroomhq.core.widget.WidgetStat
import com.livingroomhq.core.widget.WidgetState
import com.livingroomhq.core.widget.WidgetZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The built-in card set. Each widget turns a repository flow into a
 * [WidgetState] so apps surface live information before they are opened —
 * the core promise of the card system.
 */
fun registerBuiltInWidgets(registry: WidgetRegistry, app: HqApplication) {
    registry.register(storageWidget(app))
    registry.register(downloadsWidget(app))
    registry.register(weatherWidget(app))
    registry.register(smartHomeWidget(app))
    registry.register(continueWatchingWidget(app))
    registry.register(mediaServerWidget(app))
}

private fun widget(
    id: String,
    zones: Set<WidgetZone>,
    size: WidgetSize,
    state: Flow<WidgetState>,
): WidgetPlugin = object : WidgetPlugin {
    override val id = id
    override val zones = zones
    override val size = size
    override val state = state
}

private fun storageWidget(app: HqApplication) = widget(
    id = "builtin.storage",
    zones = setOf(WidgetZone.HOME, WidgetZone.TOOLS, WidgetZone.COMMAND_CENTER),
    size = WidgetSize.MEDIUM,
    state = app.systemMonitor.stats().map { stats ->
        val pct = (stats.storagePercent * 100).toInt()
        WidgetState(
            title = "File Manager",
            headline = "Internal Storage",
            stats = listOf(
                WidgetStat("Used", "$pct%", stats.storagePercent),
                WidgetStat("NAS", "Connected"),
            ),
            launchPackage = "com.android.documentsui",
        )
    },
)

private fun downloadsWidget(app: HqApplication) = widget(
    id = "builtin.downloads",
    zones = setOf(WidgetZone.TOOLS, WidgetZone.COMMAND_CENTER),
    size = WidgetSize.MEDIUM,
    state = app.ambientInfo.downloads().map { jobs ->
        WidgetState(
            title = "Downloads",
            headline = "${jobs.count { it.progress < 1f }} Active",
            stats = jobs.map { WidgetStat(it.name.take(28), "${(it.progress * 100).toInt()}%", it.progress) },
        )
    },
)

private fun weatherWidget(app: HqApplication) = widget(
    id = "builtin.weather",
    zones = setOf(WidgetZone.HOME, WidgetZone.TOOLS, WidgetZone.AMBIENT),
    size = WidgetSize.SMALL,
    state = app.ambientInfo.weather.map { weather ->
        WidgetState(
            title = "Weather",
            headline = "${weather.temperatureF}°F",
            stats = listOf(
                WidgetStat("Now", weather.summary),
                WidgetStat("Range", "${weather.lowF}° – ${weather.highF}°"),
            ),
        )
    },
)

private fun smartHomeWidget(app: HqApplication) = widget(
    id = "builtin.smarthome",
    zones = setOf(WidgetZone.TOOLS, WidgetZone.COMMAND_CENTER),
    size = WidgetSize.MEDIUM,
    state = app.ambientInfo.services.map { services ->
        val hub = services.firstOrNull { it.name == "Smart Home Hub" }
        WidgetState(
            title = "Smart Home",
            headline = hub?.detail ?: "No hub",
            stats = listOf(
                WidgetStat("Lights", "On · Living Room"),
                WidgetStat("Scenes", "Movie Night ready"),
            ),
            isHealthy = hub != null,
        )
    },
)

private fun continueWatchingWidget(app: HqApplication) = widget(
    id = "builtin.continue",
    zones = setOf(WidgetZone.HOME),
    size = WidgetSize.WIDE,
    state = app.media.library.map {
        val item = app.media.continueWatching().firstOrNull()
        if (item == null) {
            WidgetState(title = "Video Player", headline = "Nothing in progress")
        } else {
            val remaining = (item.runtimeMinutes * (1f - item.watchProgress)).toInt()
            WidgetState(
                title = "Continue Watching",
                headline = item.title,
                stats = listOf(
                    WidgetStat(item.episodeInfo ?: "Movie", "${remaining}m remaining", item.watchProgress),
                ),
            )
        }
    },
)

private fun mediaServerWidget(app: HqApplication) = widget(
    id = "builtin.mediaserver",
    zones = setOf(WidgetZone.COMMAND_CENTER),
    size = WidgetSize.MEDIUM,
    state = combine(app.ambientInfo.services, app.media.library) { services, library ->
        val server = services.firstOrNull { it.name == "Media Server" }
        WidgetState(
            title = "Media Server",
            headline = server?.detail ?: "Offline",
            stats = listOf(WidgetStat("Library", "${library.size} items")),
            isHealthy = server != null,
        )
    },
)
