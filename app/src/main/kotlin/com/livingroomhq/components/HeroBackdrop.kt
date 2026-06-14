package com.livingroomhq.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.blur
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
import kotlin.random.Random

/**
 * Renders the hero backdrop from a prioritized list of [BackdropSource]s and
 * cross-fades between them. When [cycle] is set (Ambient/idle), it randomly
 * rotates through the sources on [intervalMillis]; on Home it stays on the
 * first (contextual) source unless cycling is enabled.
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

    var index by remember(sources) {
        mutableIntStateOf(if (sources.size > 1) Random.nextInt(sources.size) else 0)
    }
    if (cycle && sources.size > 1) {
        LaunchedEffect(sources) {
            while (true) {
                delay(intervalMillis)
                index = randomNextIndex(sources.size, index)
            }
        }
    }

    val current = sources[index.coerceIn(0, sources.lastIndex)]

    Crossfade(targetState = current, animationSpec = tween(800), label = "heroBackdrop", modifier = modifier) { source ->
        when (source) {
            is BackdropSource.Live -> LivePreview(
                channel = source.channel,
                modifier = Modifier.fillMaxSize(),
                ownerTag = "home-hero",
                showLabel = false,
                deferStartupMillis = 800L,
            )
            BackdropSource.Painted -> SunsetCitySkyline(Modifier.fillMaxSize())
            is BackdropSource.Artwork -> ArtworkBackdrop(
                url = source.url,
                credit = source.credit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * TV-safe artwork layout: a blurred, cropped fill behind a sharp [ContentScale.Fit]
 * foreground. Portrait posters and movie art keep their full frame while the sides
 * (or top/bottom) are filled with a frosted version of the same image.
 */
@Composable
private fun ArtworkBackdrop(
    url: String,
    credit: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        SunsetCitySkyline(Modifier.fillMaxSize())

        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(56.dp)
                    } else {
                        Modifier
                    },
                ),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.06f)),
        )

        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        credit?.let { name ->
            Text(
                text = "Photo by $name on Unsplash",
                style = HqType.Label.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 9.sp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun randomNextIndex(size: Int, current: Int): Int {
    if (size <= 1) return 0
    var next = current
    while (next == current) {
        next = Random.nextInt(size)
    }
    return next
}
