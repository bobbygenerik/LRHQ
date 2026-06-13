package com.livingroomhq.player

import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Text
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * Always-on live preview surface, tuned for use as a hero/ambient backdrop on
 * Shield-class hardware:
 *  - **TextureView** (not SurfaceView) so the player composites in the view
 *    tree and can be alpha-cross-faded smoothly instead of popping.
 *  - **Capped to 720p** — no point decoding 4K to sit behind frosted glass; it
 *    saves bandwidth and power on an always-on launcher.
 *  - **Lifecycle-aware** — playback pauses when the app is backgrounded or the
 *    screen sleeps, and resumes on return.
 *  - Muted by default and resilient: a failed stream surfaces as a player error
 *    behind the placeholder rather than crashing.
 *
 * The surface is left unclipped; callers round the corners if they need to.
 */
@Composable
fun LivePreview(
    channel: Channel?,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = if (muted) 0f else 1f
            // Backdrop preview: cap the selected video track to 720p.
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setMaxVideoSize(1280, 720)
                .build()
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    // Swallow — the styled placeholder stays visible behind the surface.
                }
            })
        }
    }

    DisposableEffect(channel?.id) {
        // Guard malformed/blank URIs (e.g. an unconfigured channel) — these throw
        // synchronously from setMediaItem/prepare and would otherwise crash.
        val url = channel?.streamUrl?.takeIf { it.isNotBlank() }
        if (url != null) {
            runCatching {
                player.setMediaItem(MediaItem.fromUri(url))
                player.prepare()
            }
        } else {
            player.stop()
            player.clearMediaItems()
        }
        onDispose { }
    }

    // Pause decode when backgrounded / screen off; resume when the launcher returns.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(modifier.background(HqColors.Slate)) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { texture -> player.setVideoTextureView(texture) }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color(0xCC000000),
                    )
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Text(
                channel?.let { "CH ${it.number} · ${it.name}" } ?: "No channel",
                style = HqType.Label,
            )
        }
    }
}
