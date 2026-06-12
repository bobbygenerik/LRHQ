package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Unified media library across movies, shows and music. Demo implementation
 * keeps everything in memory; server-backed implementations (Jellyfin, Plex)
 * conform to the same interface.
 */
interface MediaRepository {
    val library: StateFlow<List<MediaItem>>
    fun continueWatching(): List<MediaItem>
    fun recentlyAdded(limit: Int = 10): List<MediaItem>
    fun byType(type: MediaType): List<MediaItem>
}

class DemoMediaRepository : MediaRepository {

    private val _library = MutableStateFlow(demoLibrary())
    override val library: StateFlow<List<MediaItem>> = _library.asStateFlow()

    override fun continueWatching(): List<MediaItem> =
        _library.value.filter { it.watchProgress in 0.01f..0.95f }
            .sortedByDescending { it.watchProgress }

    override fun recentlyAdded(limit: Int): List<MediaItem> =
        _library.value.sortedByDescending { it.addedAtMillis }.take(limit)

    override fun byType(type: MediaType): List<MediaItem> =
        _library.value.filter { it.type == type }

    private fun demoLibrary(): List<MediaItem> {
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L
        return listOf(
            MediaItem(
                id = "m1", title = "Signal Lost", type = MediaType.MOVIE,
                description = "A deep-space relay engineer intercepts a transmission that should not exist.",
                runtimeMinutes = 128, year = 2025, watchProgress = 0.42f, addedAtMillis = now - 2 * day,
            ),
            MediaItem(
                id = "m2", title = "The Quiet Harbor", type = MediaType.MOVIE,
                description = "A retired detective settles in a fishing town where nothing is what it seems.",
                runtimeMinutes = 112, year = 2024, addedAtMillis = now - 9 * day,
            ),
            MediaItem(
                id = "s1", title = "Glasslands", type = MediaType.SHOW,
                description = "Frontier survival drama on a terraformed moon.",
                runtimeMinutes = 54, year = 2026, watchProgress = 0.7f,
                episodeInfo = "S2 · E5 “The Long Thaw”", addedAtMillis = now - day,
            ),
            MediaItem(
                id = "s2", title = "Counterweight", type = MediaType.SHOW,
                description = "Corporate espionage inside the world's first orbital elevator.",
                runtimeMinutes = 47, year = 2025, episodeInfo = "S1 · E1 “Tension”",
                addedAtMillis = now - 4 * day,
            ),
            MediaItem(
                id = "a1", title = "Midnight Drives", type = MediaType.MUSIC,
                description = "Synthwave for empty highways.",
                runtimeMinutes = 51, year = 2024, addedAtMillis = now - 12 * day,
            ),
            MediaItem(
                id = "a2", title = "Aurora Sessions", type = MediaType.MUSIC,
                description = "Live ambient set recorded under the northern lights.",
                runtimeMinutes = 63, year = 2026, addedAtMillis = now - 3 * day,
            ),
        )
    }
}
