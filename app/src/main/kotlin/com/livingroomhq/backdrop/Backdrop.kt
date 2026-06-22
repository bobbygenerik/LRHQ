package com.livingroomhq.backdrop

import com.livingroomhq.core.data.model.Channel

/**
 * A landscape still with optional attribution. Unsplash requires crediting the
 * photographer when its photos are displayed, so the credit travels with the URL.
 */
data class AmbientPhoto(
    val url: String,
    val photographer: String? = null,
    val profileUrl: String? = null,
)

/**
 * A single thing the hero can show, in priority order: a live stream or a piece
 * of landscape artwork (media backdrop or ambient still, optionally credited).
 */
sealed interface BackdropSource {
    data class Live(val channel: Channel) : BackdropSource
    data class Artwork(
        val url: String,
        val credit: String? = null,
        val creditUrl: String? = null,
        /** Square channel logos — fit on black instead of cinematic full-bleed crop. */
        val contained: Boolean = false,
    ) : BackdropSource
}

/**
 * Curated 16:9 landscape stills used as the ambient backbone when there's no
 * live stream and no media backdrop. The live Unsplash API replaces these with
 * per-photo credited stills at runtime.
 */
/** Google Photos cache first, then Unsplash; deduped by URL. */
fun mergeAmbientPhotos(cached: List<AmbientPhoto>, remote: List<AmbientPhoto>): List<AmbientPhoto> {
    val seen = mutableSetOf<String>()
    return (cached + remote).filter { seen.add(it.url) }
}

object AmbientBackdrops {
    val photos: List<AmbientPhoto> = listOf(
        AmbientPhoto("https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1441974231531-c6227db76b6e?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1469474968028-56623f02e42e?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1500534314209-a25ddb2bd429?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1426604966848-d7adac402bff?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=1920&q=80", "Unsplash", "https://unsplash.com"),
        AmbientPhoto("https://images.unsplash.com/photo-1518173946687-a4c8892bbd9f?w=1920&q=80", "Unsplash", "https://unsplash.com"),
    )
}

/**
 * Resolves the ordered list of backdrop sources for a zone. Home stays
 * constrained to live preview while focused and bundled hero backdrop images
 * while unfocused. Ambient cycles the full screensaver set.
 */
object BackdropProvider {

    /**
     * Home hero backdrop: live preview only while focused; otherwise only the
     * bundled hero backdrop images. No programme art, media art, Unsplash, or logos.
     */
    fun forHome(
        channel: Channel?,
        heroLivePreview: Boolean,
        heroBackdrops: List<AmbientPhoto>,
    ): List<BackdropSource> {
        return when {
            heroLivePreview && channel != null -> listOf(BackdropSource.Live(channel))
            else -> emptyList()
        }
    }

    fun forAmbient(
        mediaBackdrops: List<String>,
        ambient: List<AmbientPhoto> = AmbientBackdrops.photos,
    ): List<BackdropSource> = artwork(mediaBackdrops, ambient)

    private fun artwork(mediaBackdrops: List<String>, ambient: List<AmbientPhoto>): List<BackdropSource> {
        val items = mediaBackdrops.map { BackdropSource.Artwork(it) } +
            ambient.map { BackdropSource.Artwork(it.url, it.photographer, it.profileUrl) }
        val deduped = items.distinctBy { it.url }
        return deduped
    }
}
