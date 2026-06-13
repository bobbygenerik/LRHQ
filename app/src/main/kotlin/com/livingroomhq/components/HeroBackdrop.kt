package com.livingroomhq.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.backdrop.BackdropSource
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.player.LivePreview
import com.livingroomhq.screens.SunsetCitySkyline
import kotlinx.coroutines.delay

/**
 * Renders the hero backdrop from a prioritized list of [BackdropSource]s and
 * cross-fades between them. When [cycle] is set (Ambient/idle), it rotates
 * through the sources on [intervalMillis]; on Home it stays on the first
 * (contextual) source.
 *
 * Artwork is drawn over the painted skyline, so a slow-loading or failed image
 * (offline, dead URL) degrades gracefully to the skyline instead of a blank
 * rectangle — the reliability the launcher needs for an always-on surface.
 */
@Composable
fun HeroBackdrop(
    sources: List<BackdropSource>,
    modifier: Modifier = Modifier,
    cycle: Boolean = false,
    intervalMillis: Long = 14_000L,
) {
    if (sources.isEmpty()) {
        SunsetCitySkyline(modifier)
        return
    }

    var index by remember(sources) { mutableIntStateOf(0) }
    if (cycle && sources.size > 1) {
        LaunchedEffect(sources) {
            while (true) {
                delay(intervalMillis)
                index = (index + 1) % sources.size
            }
        }
    }

    val current = sources[index.coerceIn(0, sources.lastIndex)]

    Crossfade(targetState = current, animationSpec = tween(800), label = "heroBackdrop", modifier = modifier) { source ->
        when (source) {
            is BackdropSource.Live -> LivePreview(channel = source.channel, modifier = Modifier.fillMaxSize())
            BackdropSource.Painted -> SunsetCitySkyline(Modifier.fillMaxSize())
            is BackdropSource.Artwork -> Box(Modifier.fillMaxSize()) {
                // Painted floor underneath; the image covers it once loaded.
                SunsetCitySkyline(Modifier.fillMaxSize())
                AsyncImage(
                    model = source.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Unsplash attribution — required when displaying their photos.
                source.credit?.let { credit ->
                    Text(
                        text = "Photo by $credit on Unsplash",
                        style = HqType.Label.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}
