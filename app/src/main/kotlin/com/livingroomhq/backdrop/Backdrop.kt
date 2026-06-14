package com.livingroomhq.backdrop

import com.livingroomhq.R
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
        /** Bundled drawable shown instead of [url] — always available, no network. */
        val resId: Int? = null,
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

/**
 * Bundled cinematic stills shipped inside the app — the home hero's resting
 * backdrop. Always available offline, so the hero never falls back to black
 * when channel logos or programme art fail to load. Channel identity lives in
 * the overlaid text, not the backdrop, so this stays consistent across every
 * channel.
 */
object HomeAmbientBackdrops {
    private val resIds: List<Int> = listOf(
        R.drawable.hero_ambient_01,
        R.drawable.hero_ambient_02,
        R.drawable.hero_ambient_03,
        R.drawable.hero_ambient_04,
        R.drawable.hero_ambient_05,
        R.drawable.hero_ambient_06,
        R.drawable.hero_ambient_07,
        R.drawable.hero_ambient_08,
        R.drawable.hero_ambient_09,
        R.drawable.hero_ambient_10,
        R.drawable.hero_ambient_11,
        R.drawable.hero_ambient_12,
        R.drawable.hero_ambient_13,
        R.drawable.hero_ambient_14,
    )

    val sources: List<BackdropSource> = resIds.map { BackdropSource.Artwork(url = "", resId = it) }
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
 * contextual (live stream, then this library's landscape art); Ambient cycles
 * the full set as a screensaver. Media backdrops use the dedicated landscape
 * artwork field — never posters — so the hero reads cinematic, not portrait.
 */
object BackdropProvider {

    /**
     * Home hero backdrop: live preview when focused, otherwise the bundled cinematic
     * stills that rotate as a calm, always-available backbone. Channel logos and
     * programme art are deliberately not used here — they load from the network and
     * read inconsistently across channels; the channel name in the overlay carries
     * identity instead.
     */
    fun forHome(
        channel: Channel?,
        heroLivePreview: Boolean,
    ): List<BackdropSource> = when {
        heroLivePreview && channel != null -> listOf(BackdropSource.Live(channel))
        else -> HomeAmbientBackdrops.sources
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
