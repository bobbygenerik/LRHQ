package com.livingroomhq.tvintegration

import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType

/** Provider-agnostic Watch Next row entry; pure so the mapping is unit-testable. */
data class WatchNextEntry(
    val id: String,
    val title: String,
    val description: String,
    val durationMillis: Long,
    val lastPositionMillis: Long,
    val isEpisode: Boolean,
)

/** Null when the item doesn't belong in Watch Next (music, unwatched, finished). */
fun MediaItem.toWatchNextEntry(): WatchNextEntry? {
    if (type == MediaType.MUSIC) return null
    if (watchProgress !in 0.01f..0.95f) return null
    val duration = runtimeMinutes * 60_000L
    return WatchNextEntry(
        id = id,
        title = title,
        description = description,
        durationMillis = duration,
        lastPositionMillis = (duration * watchProgress).toLong(),
        isEpisode = type == MediaType.SHOW,
    )
}
