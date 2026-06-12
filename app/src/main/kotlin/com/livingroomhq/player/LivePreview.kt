package com.livingroomhq.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

/**
 * Always-on live preview surface. Plays [channel]'s stream with ExoPlayer,
 * muted by default (the launcher is ambient, not a TV in itself until the
 * user commits). Demo stream URLs fail silently into the styled placeholder,
 * so the UI is identical with or without a real playlist.
 */
@Composable
fun LivePreview(
    channel: Channel?,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = if (muted) 0f else 1f
        }
    }

    DisposableEffect(channel?.id) {
        channel?.let {
            player.setMediaItem(MediaItem.fromUri(it.streamUrl))
            player.prepare()
        }
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(HqColors.Slate),
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Placeholder wash + channel identity over (or instead of) video.
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
