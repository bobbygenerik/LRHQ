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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.livingroomhq.backdrop.BackdropBlurTransformation
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
 * TV-safe artwork layout: sharp [ContentScale.Fit] foreground with a scaled,
 * blurred copy bleeding into pillarbox/letterbox gaps. Portrait Unsplash stills
 * need extra scale so side bars pick up color from the photo instead of a frosted wash.
 */
@Composable
private fun ArtworkBackdrop(
    url: String,
    credit: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val useComposeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Box(modifier) {
        SunsetCitySkyline(Modifier.fillMaxSize())

        SubcomposeAsyncImage(
            model = if (useComposeBlur) {
                url
            } else {
                ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(true)
                    .transformations(BackdropBlurTransformation(context, radius = 22f, sampling = 2f))
                    .build()
            },
            contentDescription = null,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        ) {
            val size = painter.intrinsicSize
            val bleed = if (size == Size.Unspecified) {
                1.20f
            } else {
                val isPortrait = size.height > size.width
                when {
                    isPortrait -> 1.58f
                    size.width > size.height * 1.2f -> 1.08f
                    else -> 1.20f
                }
            }
            val layerModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = bleed
                    scaleY = bleed
                }
                .then(if (useComposeBlur) Modifier.blur(42.dp) else Modifier)

            SubcomposeAsyncImageContent(modifier = layerModifier)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
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
