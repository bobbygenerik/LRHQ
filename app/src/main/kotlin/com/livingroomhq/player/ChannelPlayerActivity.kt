package com.livingroomhq.player

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionDisabled
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
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
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.livingroomhq.BuildConfig
import com.livingroomhq.HqApplication
import com.livingroomhq.R
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.ui.components.FocusableGlassCard
import com.livingroomhq.core.ui.theme.HqColors
import com.livingroomhq.core.ui.theme.HqDimens
import com.livingroomhq.core.ui.theme.HqType
import com.livingroomhq.core.ui.theme.hqAccent
import com.livingroomhq.translate.LiveTranslatePipeline
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val INFO_OVERLAY_DURATION_MS = 4_500L
private const val WATCH_DEBOUNCE_MS = 5_000L

data class VodPlaybackMeta(
    val isVod: Boolean = false,
    val year: Int = 0,
    val rating: String? = null,
    val genre: String = "",
    val description: String = "",
    val posterUrl: String? = null,
)

class ChannelPlayerActivity : ComponentActivity() {

    private var playerReleased = false
    private var memoryCallback: ComponentCallbacks2? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, android.R.anim.fade_in, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
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
        val isVod = intent.getBooleanExtra(ChannelPlayer.EXTRA_IS_VOD, false) ||
            IptvExoPlayer.isLikelyVod(streamUrl)

        val vodMeta = VodPlaybackMeta(
            isVod = isVod,
            year = intent.getIntExtra(ChannelPlayer.EXTRA_VOD_YEAR, 0),
            rating = intent.getStringExtra(ChannelPlayer.EXTRA_VOD_RATING),
            genre = intent.getStringExtra(ChannelPlayer.EXTRA_VOD_GENRE).orEmpty(),
            description = intent.getStringExtra(ChannelPlayer.EXTRA_VOD_DESCRIPTION).orEmpty(),
            posterUrl = intent.getStringExtra(ChannelPlayer.EXTRA_VOD_POSTER_URL),
        )

        val channel = if (!isVod && channelId != null) {
            app.channels.channels.value.firstOrNull { it.id == channelId }
        } else {
            null
        } ?: if (streamUrl.isNotBlank() && channelId != null) {
            Channel(
                id = channelId,
                number = 0,
                name = channelName.ifBlank { if (isVod) "On-Demand Movie" else "Live TV" },
                group = vodMeta.genre,
                streamUrl = streamUrl,
                logoUrl = vodMeta.posterUrl,
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
                vodMeta = vodMeta,
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
        val app = application as HqApplication
        val shrinkToLiveTv = app.nav.underlyingZone == com.livingroomhq.navigation.Zone.LIVE
        releasePlayerIfNeeded()
        super.finish()
        val enterAnim = if (shrinkToLiveTv) 0 else android.R.anim.fade_in
        val exitAnim = if (shrinkToLiveTv) 0 else android.R.anim.fade_out
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun releasePlayerIfNeeded() {
        if (!playerReleased) {
            val app = application as HqApplication
            app.fullscreenFocusReturn.onFullscreenExited()
            app.livePreviewEngine.demoteFromFullscreen()
            playerReleased = true
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ChannelPlayerScreen(
    app: HqApplication,
    initialChannel: Channel,
    vodMeta: VodPlaybackMeta,
) {
    val context = LocalContext.current
    val accent = hqAccent()
    val engine = remember { app.livePreviewEngine }
    var currentChannel by remember(initialChannel) { mutableStateOf(initialChannel) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var retryNonce by remember { mutableIntStateOf(0) }
    val isVod = vodMeta.isVod

    val settingsCaptionUrl by app.prefs.liveCaptionServerUrl.collectAsState(initial = null)
    val captionBaseUrl = remember(settingsCaptionUrl) {
        settingsCaptionUrl?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CAPTION_SERVER_URL.takeIf { it.isNotBlank() }
    }
    val settingsCaptionToken by app.prefs.liveCaptionServerToken.collectAsState(initial = null)
    val captionToken = remember(settingsCaptionToken) {
        settingsCaptionToken?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CAPTION_SERVER_TOKEN.takeIf { it.isNotBlank() }
    }
    val captionsAvailable = !isVod
    var captionsEnabled by remember { mutableStateOf(false) }
    var captionText by remember { mutableStateOf<String?>(null) }
    var captionStatus by remember { mutableStateOf<String?>(null) }
    val translatePipeline = remember { LiveTranslatePipeline(context) }
    val englishCaption by translatePipeline.englishCaption.collectAsState()
    val offlineStatus by translatePipeline.status.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            translatePipeline.stop()
            engine.applyCaptionAudioMode(false)
            engine.applyCaptionDelay(false)
        }
    }

    LaunchedEffect(captionsEnabled) {
        captionText = null
        if (!captionsEnabled) {
            translatePipeline.stop()
            engine.applyCaptionAudioMode(false)
            engine.applyCaptionDelay(false)
            captionStatus = null
            return@LaunchedEffect
        }
        translatePipeline.setSourceLanguage("auto")
        translatePipeline.onChannelChanged(currentChannel.id)
        engine.applyCaptionAudioMode(true)
        engine.applyCaptionDelay(true)
        translatePipeline.start(app.appScope)
        try {
            awaitCancellation()
        } finally {
            translatePipeline.stop()
            engine.applyCaptionAudioMode(false)
            engine.applyCaptionDelay(false)
        }
    }

    LaunchedEffect(currentChannel.id) {
        if (!captionsEnabled) return@LaunchedEffect
        captionText = null
        translatePipeline.onChannelChanged(currentChannel.id)
    }

    LaunchedEffect(englishCaption, captionsEnabled) {
        if (!captionsEnabled) return@LaunchedEffect
        captionText = englishCaption
    }

    LaunchedEffect(offlineStatus, captionsEnabled) {
        if (!captionsEnabled) {
            captionStatus = null
            return@LaunchedEffect
        }
        captionStatus = offlineStatus
        if (offlineStatus?.contains("Live", ignoreCase = true) == true) {
            delay(3000)
            captionStatus = null
        }
    }

    var infoVisible by remember { mutableStateOf(true) }
    var interactionNonce by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    // VOD playback position / duration state
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(isVod, currentChannel.id) {
        if (!isVod) return@LaunchedEffect
        while (true) {
            currentPosMs = engine.player.currentPosition.coerceAtLeast(0L)
            val dur = engine.player.duration
            durationMs = if (dur > 0L) dur else 0L
            isPlaying = engine.player.isPlaying
            delay(250L)
        }
    }

    LaunchedEffect(captionsEnabled, interactionNonce) {
        if (!captionsEnabled) return@LaunchedEffect
        delay(4L * 60 * 60 * 1000)
        captionsEnabled = false
    }

    val allChannels by app.channels.channels.collectAsState(initial = emptyList())
    val channelsList = remember(allChannels, currentChannel) {
        allChannels.ifEmpty { listOf(currentChannel) }
    }

    val (nowProgram, nextProgram) = remember(currentChannel, isVod, app.channels.epgRevision.collectAsState().value) {
        if (isVod) null to null else app.channels.epgNowNext(currentChannel.id)
    }

    fun tuneChannel(delta: Int) {
        if (isVod) return
        val size = channelsList.size
        if (size <= 1) return
        val currentIndex = channelsList.indexOfFirst { it.id == currentChannel.id }
        if (currentIndex < 0) return
        val nextIndex = (currentIndex + delta + size) % size
        playbackError = null
        val nextChannel = channelsList[nextIndex]
        currentChannel = nextChannel
        app.fullscreenFocusReturn.updateLiveChannel(nextChannel.id)
        interactionNonce++
    }

    LaunchedEffect(currentChannel.id, isVod) {
        if (isVod) return@LaunchedEffect
        delay(WATCH_DEBOUNCE_MS)
        app.channels.markWatched(currentChannel.id)
    }

    LaunchedEffect(interactionNonce, isPlaying) {
        infoVisible = true
        if (isVod && !isPlaying) return@LaunchedEffect
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

    DisposableEffect(engine) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                engine.ensureFullscreenAudio(tracks)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
            .background(HqColors.Void)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    interactionNonce++
                    when (event.key) {
                        Key.DirectionUp -> {
                            if (isVod) {
                                infoVisible = !infoVisible
                                true
                            } else {
                                tuneChannel(-1)
                                true
                            }
                        }
                        Key.DirectionDown -> {
                            if (isVod) {
                                infoVisible = !infoVisible
                                true
                            } else {
                                tuneChannel(1)
                                true
                            }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            if (isVod && playbackError == null) {
                                if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                                isPlaying = engine.player.isPlaying
                                true
                            } else {
                                false
                            }
                        }
                        Key.MediaPlayPause -> {
                            if (engine.player.isPlaying) engine.player.pause() else engine.player.play()
                            isPlaying = engine.player.isPlaying
                            true
                        }
                        Key.MediaPlay -> {
                            engine.player.play()
                            isPlaying = true
                            true
                        }
                        Key.MediaPause -> {
                            engine.player.pause()
                            isPlaying = false
                            true
                        }
                        Key.MediaFastForward -> {
                            val target = engine.player.currentPosition + 30_000L
                            val dur = engine.player.duration
                            engine.player.seekTo(if (dur > 0L) target.coerceAtMost(dur) else target)
                            currentPosMs = engine.player.currentPosition.coerceAtLeast(0L)
                            true
                        }
                        Key.MediaRewind -> {
                            engine.player.seekTo((engine.player.currentPosition - 30_000L).coerceAtLeast(0L))
                            currentPosMs = engine.player.currentPosition.coerceAtLeast(0L)
                            true
                        }
                        Key.DirectionLeft -> {
                            if (isVod || (!engine.player.isCurrentMediaItemLive && engine.player.duration > 0)) {
                                engine.player.seekTo((engine.player.currentPosition - 10_000L).coerceAtLeast(0L))
                                currentPosMs = engine.player.currentPosition.coerceAtLeast(0L)
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (isVod || (!engine.player.isCurrentMediaItemLive && engine.player.duration > 0)) {
                                val target = engine.player.currentPosition + 10_000L
                                val dur = engine.player.duration
                                engine.player.seekTo(if (dur > 0L) target.coerceAtMost(dur) else target)
                                currentPosMs = engine.player.currentPosition.coerceAtLeast(0L)
                                true
                            } else false
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
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view_texture, null) as PlayerView).apply {
                        useController = false
                        val isWarmStream = !isVod && currentChannel.streamUrl.isNotBlank() && engine.activeUrl == currentChannel.streamUrl
                        setShowBuffering(if (isWarmStream) PlayerView.SHOW_BUFFERING_NEVER else PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        engine.promoteToFullscreen(this, currentChannel, isVod = isVod)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    engine.promoteToFullscreen(view, currentChannel, isVod = isVod)
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
                    cornerRadius = HqDimens.CornerSm,
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
                    if (isVod) "Press Back to return to the VOD catalog." else "Press Back to return to the channel list.",
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
                    .fillMaxWidth(),
            ) {
                if (isVod) {
                    VodPlayerOsd(
                        title = currentChannel.name,
                        meta = vodMeta,
                        isPlaying = isPlaying,
                        currentPosMs = currentPosMs,
                        durationMs = durationMs,
                        accent = accent,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp),
                        contentAlignment = Alignment.BottomCenter,
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
                                                text = currentChannel.name.trim().take(1).uppercase().ifBlank { "TV" },
                                                style = HqType.Badge.copy(
                                                    color = accent,
                                                    shadow = playerTextShadow(),
                                                ),
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = currentChannel.name,
                                            style = HqType.Headline.copy(
                                                color = HqColors.TextPrimary,
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
                                                shadow = playerTextShadow(),
                                            ),
                                        )
                                    }
                                }

                                if (nowProgram != null) {
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        text = nowProgram.title,
                                        style = HqType.CardTitle.copy(
                                            color = HqColors.TextSecondary,
                                            shadow = playerTextShadow(),
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    val progress = remember(infoVisible, nowProgram) {
                                        nowProgram.progressAt(System.currentTimeMillis())
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(HqColors.IconWell),
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
                                            style = HqType.Meta.copy(
                                                color = HqColors.TextTertiary,
                                                shadow = playerTextShadow(),
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Live Broadcast",
                                        style = HqType.CardCaption.copy(
                                            color = HqColors.TextTertiary,
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

        if (playbackError == null && captionsAvailable) {
            var ccFocused by remember { mutableStateOf(false) }
            val controlsVisible = infoVisible || ccFocused
            val controlsAlpha by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f,
                label = "cc_alpha",
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp)
                    .alpha(controlsAlpha)
                    .focusProperties {
                        canFocus = controlsVisible
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .onFocusChanged { ccFocused = it.isFocused }
                        .clip(RoundedCornerShape(HqDimens.CornerSm))
                        .background(
                            when {
                                captionsEnabled -> accent.copy(alpha = 0.9f)
                                ccFocused -> HqColors.Track
                                else -> HqColors.Scrim
                            },
                        )
                        .border(
                            width = if (ccFocused) 2.dp else 0.dp,
                            color = if (ccFocused) accent else Color.Transparent,
                            shape = RoundedCornerShape(HqDimens.CornerSm),
                        )
                        .clickable { captionsEnabled = !captionsEnabled }
                        .focusable()
                        .padding(12.dp),
                ) {
                    Icon(
                        imageVector = if (captionsEnabled) Icons.Default.ClosedCaption else Icons.Default.ClosedCaptionDisabled,
                        contentDescription = if (captionsEnabled) "Disable captions" else "Enable captions",
                        tint = if (captionsEnabled) HqColors.OnAccent else HqColors.TextPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = captionStatus != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 84.dp, start = 24.dp),
            ) {
                captionStatus?.let { status ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(HqDimens.CornerSm))
                            .background(HqColors.Scrim)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            status,
                            style = HqType.Label.copy(color = HqColors.TextPrimary),
                        )
                    }
                }
            }
        }

        CaptionOverlay(
            text = if (captionsEnabled) captionText else null,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun VodPlayerOsd(
    title: String,
    meta: VodPlaybackMeta,
    isPlaying: Boolean,
    currentPosMs: Long,
    durationMs: Long,
    accent: Color,
) {
    val progress = if (durationMs > 0L) {
        (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        HqColors.Void.copy(alpha = 0.78f),
                        HqColors.Void.copy(alpha = 0.96f),
                    ),
                ),
            )
            .padding(horizontal = 64.dp, vertical = 36.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (!meta.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = meta.posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(84.dp)
                        .height(126.dp)
                        .clip(RoundedCornerShape(HqDimens.CornerSm))
                        .border(1.dp, HqColors.GlassStroke, RoundedCornerShape(HqDimens.CornerSm)),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "VOD",
                            style = HqType.CardCaption,
                            color = HqColors.OnAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                    if (meta.genre.isNotBlank()) {
                        Text(
                            text = meta.genre,
                            style = HqType.CardCaption.copy(color = HqColors.TextSecondary),
                        )
                    }
                    if (meta.year > 0) {
                        Text(
                            text = "•  ${meta.year}",
                            style = HqType.CardCaption.copy(color = HqColors.TextSecondary),
                        )
                    }
                    if (!meta.rating.isNullOrBlank()) {
                        Text(
                            text = "•  ${meta.rating}",
                            style = HqType.CardCaption.copy(color = accent, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f))
                            .border(1.5.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Playing" else "Paused",
                            tint = accent,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Text(
                        text = title,
                        style = HqType.Headline.copy(
                            color = HqColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            shadow = playerTextShadow(),
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Seek progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(HqColors.IconWell),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(accent),
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (durationMs > 0L) {
                            "${formatDurationMs(currentPosMs)} / ${formatDurationMs(durationMs)}"
                        } else {
                            formatDurationMs(currentPosMs)
                        },
                        style = HqType.Label.copy(
                            color = HqColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                        ),
                    )

                    Text(
                        text = "◀ ▶ Seek 10s   •   OK Play/Pause   •   Back Return to Catalog",
                        style = HqType.CardCaption.copy(color = HqColors.TextTertiary),
                    )
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
    Shadow(color = HqColors.Void.copy(alpha = 0.85f), offset = Offset(0f, 2f), blurRadius = 8f)
