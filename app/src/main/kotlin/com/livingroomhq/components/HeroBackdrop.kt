package com.livingroomhq.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.backdrop.BackdropSource
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.LocalCustomSettings
import com.livingroomhq.player.LivePreview
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Renders the hero backdrop from a prioritized list of [BackdropSource]s and
 * cross-fades between them on black. When [cycle] is set (Ambient), it rotates
 * through artwork sources; on Home it stays on the first source unless cycling.
 */
@Composable
fun HeroBackdrop(
    sources: List<BackdropSource>,
    modifier: Modifier = Modifier,
    cycle: Boolean = false,
    intervalMillis: Long = 14_000L,
    applyBlur: Boolean = false,
) {
    val customSettings = LocalCustomSettings.current
    val reducedMotion = customSettings.animations != "Smooth"
    val blurRadius = if (applyBlur && !reducedMotion) 24.dp else 0.dp

    Box(
        modifier
            .background(Color.Black)
            .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
    ) {
        if (sources.isEmpty()) return@Box

        var index by remember(sources) {
            mutableIntStateOf(if (cycle && sources.size > 1) Random.nextInt(sources.size) else 0)
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
        val artworkOnly = sources.all { it is BackdropSource.Artwork }

        if (cycle && artworkOnly) {
            CyclingArtworkStack(
                sources = sources.filterIsInstance<BackdropSource.Artwork>(),
                index = index,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Crossfade(
                targetState = current,
                animationSpec = tween(450),
                label = "heroBackdrop",
                modifier = Modifier.fillMaxSize(),
            ) { source ->
                when (source) {
                    is BackdropSource.Live -> LivePreview(
                        channel = source.channel,
                        modifier = Modifier.fillMaxSize(),
                        ownerTag = "home-hero",
                        showLabel = false,
                        deferStartupMillis = 800L,
                    )
                    is BackdropSource.Artwork -> ArtworkBackdrop(
                        url = source.url,
                        credit = source.credit,
                        contained = source.contained,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Fades the incoming still over the outgoing one so nothing peeks through mid-transition. */
@Composable
private fun CyclingArtworkStack(
    sources: List<BackdropSource.Artwork>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return

    var baseIndex by remember { mutableIntStateOf(index.coerceIn(0, sources.lastIndex)) }
    var overlayIndex by remember { mutableIntStateOf(-1) }
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(sources) {
        baseIndex = index.coerceIn(0, sources.lastIndex)
        overlayIndex = -1
        overlayAlpha.snapTo(0f)
    }

    LaunchedEffect(index) {
        val next = index.coerceIn(0, sources.lastIndex)
        if (next == baseIndex) return@LaunchedEffect
        overlayIndex = next
        overlayAlpha.snapTo(0f)
        overlayAlpha.animateTo(1f, tween(1_200))
        baseIndex = next
        overlayIndex = -1
    }

    Box(modifier) {
        val base = sources[baseIndex]
        ArtworkBackdrop(base.url, base.credit, base.contained, Modifier.fillMaxSize())
        if (overlayIndex >= 0) {
            val overlay = sources[overlayIndex]
            ArtworkBackdrop(
                url = overlay.url,
                credit = overlay.credit,
                contained = overlay.contained,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = overlayAlpha.value },
            )
        }
    }
}

/** Full-bleed cropped background photo, or a contained logo on black. */
@Composable
private fun ArtworkBackdrop(
    url: String,
    credit: String?,
    contained: Boolean = false,
    modifier: Modifier = Modifier,
) {
    if (contained) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .sizeIn(maxWidth = 320.dp, maxHeight = 320.dp),
            )
        }
        return
    }

    Box(modifier = modifier.background(Color.Black)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        credit?.let { name ->
            Text(
                text = "Photo by $name on Unsplash",
                style = HqType.Label.copy(color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp),
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
