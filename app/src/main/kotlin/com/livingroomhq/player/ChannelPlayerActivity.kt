package com.livingroomhq.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

class ChannelPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val app = application as HqApplication
        val channelId = intent.getStringExtra(ChannelPlayer.EXTRA_CHANNEL_ID)
        val channel = channelId?.let { id ->
            app.channels.channels.value.firstOrNull { it.id == id }
        }
        if (channel == null || channel.streamUrl.isBlank()) {
            finish()
            return
        }

        app.livePreviewEngine.standDownForFullscreenPlayback()
        val (nowProgram, _) = app.channels.epgNowNext(channel.id)

        setContent {
            ChannelPlayerScreen(
                channel = channel,
                nowTitle = nowProgram?.title,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPlayerScreen(
    channel: Channel,
    nowTitle: String?,
) {
    val context = LocalContext.current
    val exoPlayer = remember(channel.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(channel.streamUrl))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    BackHandler { (context as? ComponentActivity)?.finish() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerShowTimeoutMs = 4_000
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = exoPlayer },
        )

        Column(
            Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        ) {
            Text(
                "CH ${channel.number} · ${channel.name}",
                style = HqType.Headline.copy(color = HqColors.TextPrimary),
            )
            nowTitle?.let {
                Text(
                    it,
                    style = HqType.Body.copy(color = HqColors.TextSecondary),
                    maxLines = 1,
                )
            }
        }
    }
}
