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
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import com.livingroomhq.core.ui.components.GlassPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType

class ChannelPlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }

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

        setContent {
            ChannelPlayerScreen(
                app = app,
                initialChannel = channel,
            )
        }
    }

    override fun finish() {
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPlayerScreen(
    app: HqApplication,
    initialChannel: Channel,
) {
    val context = LocalContext.current
    val engine = remember { app.livePreviewEngine }
    var currentChannel by remember(initialChannel) { mutableStateOf(initialChannel) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }

    val allChannels by app.channels.channels.collectAsState(initial = emptyList())
    val channelsList = remember(allChannels, currentChannel) {
        allChannels.ifEmpty { listOf(currentChannel) }
    }

    val (nowProgram, nextProgram) = remember(currentChannel, app.channels.epgRevision.collectAsState().value) {
        app.channels.epgNowNext(currentChannel.id)
    }

    fun tuneChannel(delta: Int) {
        val size = channelsList.size
        if (size <= 1) return
        val currentIndex = channelsList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex < 0) return
        val nextIndex = (currentIndex + delta + size) % size
        playbackError = null
        currentChannel = channelsList[nextIndex]
    }

    // Live TV has no transport bar to seek, so the info overlay is the only chrome.
    // Show it briefly, then fade it out; any DPAD press brings it back and restarts the timer.
    var infoVisible by remember { mutableStateOf(true) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    LaunchedEffect(interactionNonce) {
        infoVisible = true
        delay(4_000)
        infoVisible = false
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(retryNonce) {
        if (retryNonce > 0) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    DisposableEffect(engine) {
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
                if (event.type == KeyEventType.KeyDown) {
                    interactionNonce++
                    when (event.key) {
                        Key.DirectionUp -> {
                            tuneChannel(-1)
                            true
                        }
                        Key.DirectionDown -> {
                            tuneChannel(1)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        if (playbackError == null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        engine.promoteToFullscreen(this, currentChannel)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    engine.promoteToFullscreen(view, currentChannel)
                },
            )
        } else {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    currentChannel.name,
                    style = HqType.Headline.copy(color = HqColors.TextPrimary),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    playbackError.orEmpty(),
                    style = HqType.Body.copy(color = HqColors.Critical),
                )
                Spacer(Modifier.height(20.dp))
                FocusableGlassCard(
                    onClick = {
                        playbackError = null
                        retryNonce++
                    },
                    modifier = Modifier
                        .height(44.dp)
                        .focusRequester(retryFocus),
                    cornerRadius = 8.dp,
                    sheenOnFocus = false,
                ) { focused ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Retry",
                            style = HqType.Label.copy(
                                color = if (focused) HqColors.Accent else HqColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Press Back to return to the channel list.",
                    style = HqType.Label.copy(color = HqColors.TextTertiary),
                )
            }
        }

        if (playbackError == null) {
            AnimatedVisibility(
                visible = infoVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(540.dp)
                        .padding(16.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(HqColors.Accent.copy(alpha = 0.2f))
                                        .border(1.dp, HqColors.Accent, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = currentChannel.number.toString().ifBlank { "TV" },
                                        style = HqType.Label.copy(
                                            color = HqColors.Accent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            shadow = playerTextShadow(),
                                        ),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = currentChannel.name,
                                    style = HqType.Headline.copy(
                                        color = HqColors.TextPrimary,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        shadow = playerTextShadow(),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            nowProgram?.let { program ->
                                Text(
                                    text = formatProgramWindow(context, program),
                                    style = HqType.Label.copy(
                                        color = HqColors.TextSecondary,
                                        fontSize = 12.sp,
                                        shadow = playerTextShadow(),
                                    ),
                                )
                            }
                        }

                        if (nowProgram != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = nowProgram.title,
                                style = HqType.Body.copy(
                                    color = HqColors.TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    shadow = playerTextShadow(),
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(8.dp))
                            val progress = nowProgram.progressAt(System.currentTimeMillis())
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x1FFFFFFF)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .fillMaxHeight()
                                        .background(HqColors.Accent),
                                )
                            }
                            nextProgram?.let { next ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Up Next: ${next.title}",
                                    style = HqType.Label.copy(
                                        color = HqColors.TextTertiary,
                                        fontSize = 11.sp,
                                        shadow = playerTextShadow(),
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "No Program Information",
                                style = HqType.Body.copy(
                                    color = HqColors.TextTertiary,
                                    fontSize = 13.sp,
                                    shadow = playerTextShadow(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatProgramWindow(context: android.content.Context, program: com.livingroomhq.core.data.model.Program): String {
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    val fmt = SimpleDateFormat(pattern, Locale.getDefault())
    val start = fmt.format(Date(program.startMillis))
    val end = fmt.format(Date(program.endMillis))
    return "$start – $end"
}

private fun playerTextShadow(): Shadow =
    Shadow(color = Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 8f)
