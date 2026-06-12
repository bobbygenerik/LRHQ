package com.livingroomhq.tvintegration

import android.content.Context
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
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

/**
 * Publishes continue-watching entries to the system Watch Next row, so this
 * launcher's library surfaces even when the stock Google TV launcher is
 * active. The provider is absent on some devices; every call is best-effort.
 */
class WatchNextPublisher(private val context: Context) {

    fun sync(entries: List<WatchNextEntry>) {
        runCatching {
            val resolver = context.contentResolver
            // Apps only see their own rows; null selection clears just ours.
            resolver.delete(TvContractCompat.WatchNextPrograms.CONTENT_URI, null, null)
            entries.forEach { entry ->
                val program = WatchNextProgram.Builder()
                    .setType(
                        if (entry.isEpisode) TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                        else TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                    )
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setInternalProviderId(entry.id)
                    .setTitle(entry.title)
                    .setDescription(entry.description)
                    .setDurationMillis(entry.durationMillis.toInt())
                    .setLastPlaybackPositionMillis(entry.lastPositionMillis.toInt())
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .build()
                resolver.insert(TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues())
            }
        }
    }
}
