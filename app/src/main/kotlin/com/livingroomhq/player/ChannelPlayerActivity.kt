package com.livingroomhq.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
        val streamUrl = intent.getStringExtra(ChannelPlayer.EXTRA_STREAM_URL).orEmpty()
        val channelName = intent.getStringExtra(ChannelPlayer.EXTRA_CHANNEL_NAME).orEmpty()
        val channel = channelId?.let { id ->
            app.channels.channels.value.firstOrNull { it.id == id }
        } ?: if (streamUrl.isNotBlank() && channelId != null) {
            Channel(
                id = channelId,
                number = 0,
                name = channelName.ifBlank { "Live TV" },
                group = "",
                streamUrl = streamUrl,
            )
        } else {
            null
        }
        if (channel == null || channel.streamUrl.isBlank()) {
            finish()
            return
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
    val engine = remember { (context.applicationContext as HqApplication).livePreviewEngine }
    var playbackError by remember { mutableStateOf<String?>(null) }

    // Live TV has no transport bar to seek, so the info overlay is the only chrome.
    // Show it briefly, then fade it out; any DPAD press brings it back and restarts the timer.
    var infoVisible by remember { mutableStateOf(true) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(interactionNonce) {
        infoVisible = true
        delay(4_000)
        infoVisible = false
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    DisposableEffect(channel.id, engine) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                engine.ensureFullscreenAudio(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Playback failed."
            }
        }
        engine.player.addListener(listener)
        onDispose {
            engine.player.removeListener(listener)
            engine.demoteFromFullscreen()
        }
    }

    BackHandler { (context as? ComponentActivity)?.finish() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) interactionNonce++
                false
            },
    ) {
        if (playbackError == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        engine.promoteToFullscreen(this, channel)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (view.player != engine.player) {
                        engine.promoteToFullscreen(view, channel)
                    }
                },
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
            ) {
                Text(
                    channel.name,
                    style = HqType.Headline.copy(color = HqColors.TextPrimary),
                )
                Text(
                    playbackError.orEmpty(),
                    style = HqType.Body.copy(color = HqColors.Critical),
                )
            }
        }

        if (playbackError == null) {
            AnimatedVisibility(
                visible = infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        channel.name,
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
    }
}
