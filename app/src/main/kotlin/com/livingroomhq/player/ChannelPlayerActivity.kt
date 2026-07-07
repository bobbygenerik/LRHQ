package com.livingroomhq.player

import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.livingroomhq.BuildConfig
import com.livingroomhq.HqApplication
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.hqAccent
import com.livingroomhq.translate.LiveCaptionClient
import com.livingroomhq.player.CaptionOverlay
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val INFO_OVERLAY_DURATION_MS = 4_000L
private const val WATCH_DEBOUNCE_MS = 5_000L
private const val CAPTION_POLL_MS = 500L

class ChannelPlayerActivity : ComponentActivity() {

    private var playerReleased = false
    private var memoryCallback: ComponentCallbacks2? = null

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

        val cb = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    app.livePreviewEngine.trimMemory()
                }
            }
            override fun onConfigurationChanged(c: Configuration) {}
            override fun onLowMemory() {
                app.livePreviewEngine.trimMemory()
            }
        }
        memoryCallback = cb
        registerComponentCallbacks(cb)

        setContent {
            ChannelPlayerScreen(
                app = app,
                initialChannel = channel,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        memoryCallback?.let { unregisterComponentCallbacks(it) }
        memoryCallback = null
        releasePlayerIfNeeded()
    }

    override fun finish() {
        releasePlayerIfNeeded()
        super.finish()
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun releasePlayerIfNeeded() {
        if (!playerReleased) {
            (application as HqApplication).livePreviewEngine.demoteFromFullscreen()
            playerReleased = true
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
    val accent = hqAccent()
    val engine = remember { app.livePreviewEngine }
    var currentChannel by remember(initialChannel) { mutableStateOf(initialChannel) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }

    val settingsCaptionUrl by app.prefs.liveCaptionServerUrl.collectAsState(initial = null)
    val captionBaseUrl = remember(settingsCaptionUrl) {
        settingsCaptionUrl?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CAPTION_SERVER_URL.takeIf { it.isNotBlank() }
    }
    val captionsAvailable = !captionBaseUrl.isNullOrBlank()
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf<String?>(null) }
    var captionStatus by remember { mutableStateOf<String?>(null) }

    val allChannels by app.channels.channels.collectAsState(initial = emptyList())
    val channelsList = remember(allChannels, currentChannel) {
        allChannels.ifEmpty { listOf(currentChannel) }
    }

    val (nowProgram, nextProgram) = remember(currentChannel, app.channels.epgRevision.collectAsState().value) {
        app.channels.epgNowNext(currentChannel.id)
    }

    var infoVisible by remember { mutableStateOf(true) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    fun tuneChannel(delta: Int) {
        val size = channelsList.size
        if (size <= 1) return
        val currentIndex = channelsList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex < 0) return
        val nextIndex = (currentIndex + delta + size) % size
        playbackError = null
        currentChannel = channelsList[nextIndex]
        interactionNonce++
    }

    LaunchedEffect(currentChannel.id) {
        delay(WATCH_DEBOUNCE_MS)
        app.channels.markWatched(currentChannel.id)
    }

    LaunchedEffect(interactionNonce) {
        infoVisible = true
        delay(INFO_OVERLAY_DURATION_MS)
        infoVisible = false
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(retryNonce) {
        if (retryNonce > 0) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }
    }

    LaunchedEffect(currentChannel.id, captionBaseUrl, captionsEnabled) {
        captionText = null
        captionStatus = null
        if (!captionsEnabled || captionBaseUrl.isNullOrBlank()) return@LaunchedEffect

        val client = LiveCaptionClient(captionBaseUrl)
        if (!client.isConfigured) return@LaunchedEffect

        var sessionId: String? = null
        try {
            captionStatus = "Starting captions..."
            val session = client.startSession(
                channelId = currentChannel.id,
                channelName = currentChannel.name,
                streamUrl = currentChannel.streamUrl,
            )
            sessionId = session.id
            captionStatus = "Captions active"
            var sinceSeq = 0L
            while (true) {
                val snapshot = client.fetchSnapshot(session.id, sinceSeq)
                sinceSeq = snapshot.cues.maxOfOrNull { it.seq } ?: sinceSeq
                val position = engine.player.currentPosition
                val activeCue = snapshot.cues.lastOrNull { position in it.startMs..it.endMs }
                captionText = activeCue?.text ?: snapshot.activeText
                delay(CAPTION_POLL_MS)
            }
        } catch (e: Exception) {
            captionStatus = e.localizedMessage ?: "Captions unavailable"
        } finally {
            sessionId?.let { runCatching { client.stopSession(it) } }
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
                        engine.retryFullscreen()
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
                                color = if (focused) accent else HqColors.TextPrimary,
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
                                        .background(accent.copy(alpha = 0.2f))
                                        .border(1.dp, accent, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = currentChannel.number.toString().ifBlank { "TV" },
                                        style = HqType.Label.copy(
                                            color = accent,
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
                                        .background(accent),
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

        if (playbackError == null && captionsAvailable) {
            var ccFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .onFocusChanged { ccFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            captionsEnabled -> accent.copy(alpha = 0.9f)
                            ccFocused -> Color(0x33FFFFFF)
                            else -> Color(0xCC000000)
                        },
                    )
                    .border(
                        width = if (ccFocused) 2.dp else 0.dp,
                        color = if (ccFocused) accent else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { captionsEnabled = !captionsEnabled }
                    .focusable()
                    .padding(12.dp),
            ) {
                Icon(
                    imageVector = if (captionsEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                    contentDescription = if (captionsEnabled) "Disable captions" else "Enable captions",
                    tint = if (captionsEnabled) Color.Black else HqColors.TextPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            captionStatus?.let { status ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 84.dp, end = 24.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        status,
                        style = HqType.Label.copy(color = HqColors.TextPrimary, fontSize = 12.sp),
                    )
                }
            }
        }

        CaptionOverlay(
            text = if (captionsEnabled) captionText else null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
