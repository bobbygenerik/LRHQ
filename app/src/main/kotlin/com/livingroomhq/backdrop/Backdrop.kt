package com.livingroomhq.backdrop

import com.livingroomhq.core.data.model.Channel

/**
 * A single thing the hero can show, in priority order: a live stream, a piece
 * of landscape artwork (media backdrop or ambient still), or the painted
 * skyline floor that always works offline.
 */
sealed interface BackdropSource {
    data class Live(val channel: Channel) : BackdropSource
    data class Artwork(val url: String) : BackdropSource
    data object Painted : BackdropSource
}

/**
 * Curated 16:9 landscape stills used as the ambient backbone when there's no
 * live stream and no media backdrop. These are direct Unsplash CDN URLs
 * (stable hotlinks, not the retired source.unsplash.com endpoint), sized to
 * 1920w. They are the *reliable* layer — any that fail to load fall through to
 * the painted skyline, so the hero is never blank. Swap/extend freely; for a
 * fully offline build, bundle these as drawables instead.
 */
object AmbientBackdrops {
    val urls: List<String> = listOf(
        "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1920&q=80",
        "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1920&q=80",
        "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1920&q=80",
        "https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1920&q=80",
        "https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=1920&q=80",
        "https://images.unsplash.com/photo-1426604966848-d7adac402bff?w=1920&q=80",
        "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=1920&q=80",
        "https://images.unsplash.com/photo-1518173946687-a4c8892bbd9f?w=1920&q=80",
    )
}

/**
 * Resolves the ordered list of backdrop sources for a zone. Home stays
 * contextual (live stream, then this library's landscape art); Ambient cycles
 * the full set as a screensaver. Media backdrops use the dedicated landscape
 * artwork field — never posters — so the hero reads cinematic, not portrait.
 */
object BackdropProvider {

    fun forHome(
        channel: Channel?,
        showLive: Boolean,
        mediaBackdrops: List<String>,
    ): List<BackdropSource> = when {
        showLive && channel != null -> listOf(BackdropSource.Live(channel))
        else -> artwork(mediaBackdrops)
    }

    fun forAmbient(mediaBackdrops: List<String>): List<BackdropSource> =
        artwork(mediaBackdrops)

    private fun artwork(mediaBackdrops: List<String>): List<BackdropSource> {
        val urls = (mediaBackdrops + AmbientBackdrops.urls).distinct()
        return if (urls.isEmpty()) listOf(BackdropSource.Painted)
        else urls.map { BackdropSource.Artwork(it) }
    }
}
