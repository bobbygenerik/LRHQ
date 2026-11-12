package com.livingroomhq.player

import android.content.Context
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.livingroomhq.core.data.fault.FaultLog
import com.livingroomhq.core.data.model.Channel

/**
 * Single shared preview player for the launcher. Home and Live TV must not each
 * spin up their own [ExoPlayer] — two decoders on the same stream causes audio
 * glitches, memory pressure, and transition freezes.
 *
 * Fullscreen reuses this same player (surface handoff) so IPTV does not re-buffer
 * when opening a channel that is already playing in preview.
 *
 * Lifecycle is owned here via [ProcessLifecycleOwner] — composables must not
 * pause/resume the engine themselves.
 */
sealed class PlayerState {
    data object Idle : PlayerState()

    data class Preview(
        val owner: String,
        val url: String?,
        val maxVideoWidth: Int,
        val maxVideoHeight: Int,
        val enableAudio: Boolean = false,
    ) : PlayerState()

    data class Fullscreen(val url: String) : PlayerState()

    /** Preview slot retained while the process is backgrounded. */
    data class Suspended(
        val owner: String,
        val url: String?,
        val maxVideoWidth: Int,
        val maxVideoHeight: Int,
        val enableAudio: Boolean = false,
    ) : PlayerState()
}

private data class PreviewSlot(
    val owner: String,
    val url: String?,
    val textureView: TextureView,
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
    val enableAudio: Boolean = false,
)

class LivePreviewEngine(context: Context) {
    private val appContext = context.applicationContext
    private var state: PlayerState = PlayerState.Idle
    private var previewSlot: PreviewSlot? = null
    private var boundView: TextureView? = null
    private var _fullscreenPlayerView: PlayerView? = null
    private var boundUrl: String? by mutableStateOf(null)
    private var appInForeground = true

    /** Increments each time fullscreen playback demotes back to preview. */
    var returnToPreviewNonce: Long by mutableStateOf(0L)
        private set

    /** Last live channel played in fullscreen (tracks in-player channel zapping). */
    var lastFullscreenChannel: Channel? by mutableStateOf(null)
        private set

    val fullscreenActive: Boolean
        get() = state is PlayerState.Fullscreen

    val activeUrl: String?
        get() = boundUrl

    /** Exposed for ChannelPlayerActivity to rebind during channel zapping. */
    val activePlayerView: PlayerView?
        get() = _fullscreenPlayerView

    val player: ExoPlayer = IptvExoPlayer.create(appContext).apply {
        playWhenReady = true
        addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val s = state
                if (s is PlayerState.Fullscreen || (s is PlayerState.Preview && s.enableAudio)) {
                    IptvExoPlayer.ensureFullscreenAudio(this@apply, tracks)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                FaultLog.record("LivePreviewEngine", error)
            }
        })
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> onAppBackgrounded()
                    Lifecycle.Event.ON_START -> onAppForegrounded()
                    else -> Unit
                }
            },
        )
    }

    fun bind(
        owner: String,
        channel: Channel?,
        textureView: TextureView,
        maxVideoWidth: Int,
        maxVideoHeight: Int,
        enableAudio: Boolean = false,
    ) {
        val url = channel?.streamUrl?.takeIf { it.isNotBlank() }
        previewSlot = PreviewSlot(owner, url, textureView, maxVideoWidth, maxVideoHeight, enableAudio)

        when (val current = state) {
            is PlayerState.Fullscreen -> return
            is PlayerState.Suspended -> {
                if (current.owner != owner) return
                state = PlayerState.Suspended(owner, url, maxVideoWidth, maxVideoHeight, enableAudio)
                return
            }
            else -> Unit
        }

        attachPreviewSurface(owner, url, textureView, maxVideoWidth, maxVideoHeight, enableAudio)
        state = PlayerState.Preview(owner, url, maxVideoWidth, maxVideoHeight, enableAudio)
        if (appInForeground) {
            prepareChannel(url)
            resumePreviewPlayback()
        } else {
            player.pause()
        }
    }

    fun unbind(owner: String, textureView: TextureView) {
        val slot = previewSlot
        if (slot?.owner != owner || slot.textureView !== textureView) return

        when (val current = state) {
            is PlayerState.Fullscreen -> {
                // When in Fullscreen, MainActivity's preview surface may unbind temporarily.
                // Retain previewSlot data so demoteFromFullscreen knows which channel to restore.
                return
            }
            is PlayerState.Suspended -> {
                if (current.owner == owner) {
                    previewSlot = null
                    state = PlayerState.Idle
                }
                return
            }
            else -> Unit
        }

        detachPreviewSurface(textureView)
        previewSlot = null
        stopPreviewDecoder()
        state = PlayerState.Idle
    }

    /** Move the live decoder to a fullscreen [PlayerView] without restarting the stream. */
    fun promoteToFullscreen(playerView: PlayerView, channel: Channel, isVod: Boolean = false) {
        val url = channel.streamUrl.takeIf { it.isNotBlank() } ?: return
        val isLive = !isVod && !IptvExoPlayer.isLikelyVod(url)
        if (isLive) {
            lastFullscreenChannel = channel
            previewSlot = previewSlot?.copy(url = url)
        }
        val current = state
        if (current is PlayerState.Fullscreen && _fullscreenPlayerView === playerView && boundUrl == url) {
            if (playerView.player != player) playerView.player = player
            return
        }

        val isSameStream = boundUrl != null && boundUrl == url && isLive

        if (!isSameStream && boundUrl != null) {
            // Stop previous stream before opening the new one so 1-connection IPTV servers
            // immediately release the previous socket and never reject with HTTP 401.
            boundView?.let { player.clearVideoTextureView(it) }
            stopPreviewDecoder()
        }
        boundView = null

        _fullscreenPlayerView = playerView
        if (playerView.player !== player) {
            playerView.player = player
        }
        state = PlayerState.Fullscreen(url)

        IptvExoPlayer.configureForFullscreen(player)
        if (!isSameStream) {
            prepareChannel(url, isLive = isLive)
        }
        player.playWhenReady = true
        player.play()
    }

    fun demoteFromFullscreen() {
        if (state !is PlayerState.Fullscreen) return

        returnToPreviewNonce++
        _fullscreenPlayerView?.player = null
        _fullscreenPlayerView = null

        val slot = previewSlot
        val targetUrl = slot?.url ?: boundUrl
        val sameLiveStream = targetUrl != null && targetUrl == boundUrl && !IptvExoPlayer.isLikelyVod(boundUrl.orEmpty())

        if (slot != null && appInForeground && slot.textureView.isAttachedToWindow) {
            if (!sameLiveStream) {
                stopPreviewDecoder()
            }
            attachPreviewSurface(slot.owner, targetUrl, slot.textureView, slot.maxVideoWidth, slot.maxVideoHeight, slot.enableAudio)
            state = PlayerState.Preview(slot.owner, targetUrl, slot.maxVideoWidth, slot.maxVideoHeight, slot.enableAudio)
            if (!sameLiveStream) {
                prepareChannel(targetUrl)
            }
            resumePreviewPlayback()
        } else if (slot != null) {
            player.volume = 0f
            stopPreviewDecoder()
            state = PlayerState.Suspended(
                slot.owner,
                slot.url,
                slot.maxVideoWidth,
                slot.maxVideoHeight,
                slot.enableAudio,
            )
        } else {
            player.volume = 0f
            stopPreviewDecoder()
            state = PlayerState.Idle
        }
    }

    /** Retry playback after a [PlaybackException] without creating a new media item. */
    fun retryFullscreen() {
        if (state !is PlayerState.Fullscreen) return
        runCatching { player.prepare() }
    }

    /** Release decoder resources under memory pressure without fully destroying the player. */
    fun trimMemory() {
        if (state is PlayerState.Fullscreen) return
        boundView?.let { player.clearVideoTextureView(it) }
        boundView = null
        stopPreviewDecoder()
        state = when (val current = state) {
            is PlayerState.Suspended -> current
            is PlayerState.Preview -> PlayerState.Suspended(
                current.owner,
                current.url,
                current.maxVideoWidth,
                current.maxVideoHeight,
                current.enableAudio,
            )
            else -> PlayerState.Idle
        }
    }

    fun ensureFullscreenAudio(tracks: Tracks) {
        if (state !is PlayerState.Fullscreen) return
        IptvExoPlayer.ensureFullscreenAudio(player, tracks)
    }

    /**
     * After [CaptionAudioPolicy] toggles PCM-force, nudge audio track selection so
     * [PcmForcedAudioSink] re-evaluates passthrough support. Brief glitch OK on CC toggle only.
     */
    fun applyCaptionAudioMode(forcePcmDecode: Boolean) {
        if (state !is PlayerState.Fullscreen) return
        IptvExoPlayer.applyCaptionAudioMode(player, forcePcmDecode)
    }

    /**
     * Delay video/audio playback when live captions are active so subtitles sync with speech.
     */
    fun applyCaptionDelay(enabled: Boolean) {
        if (state !is PlayerState.Fullscreen) return
        IptvExoPlayer.applyCaptionDelay(player, enabled)
    }

    fun pause() {
        if (state is PlayerState.Fullscreen) return
        player.pause()
    }

    fun resume() {
        if (state is PlayerState.Fullscreen) return
        if (boundView == null) return
        resumePreviewPlayback()
    }

    fun release() {
        demoteFromFullscreen()
        boundView?.let { player.clearVideoTextureView(it) }
        player.release()
        previewSlot = null
        boundView = null
        boundUrl = null
        state = PlayerState.Idle
    }

    private fun onAppBackgrounded() {
        appInForeground = false
        if (state is PlayerState.Fullscreen) return
        player.pause()
        when (val current = state) {
            is PlayerState.Preview -> {
                stopPreviewDecoder()
                state = PlayerState.Suspended(
                    current.owner,
                    current.url,
                    current.maxVideoWidth,
                    current.maxVideoHeight,
                    current.enableAudio,
                )
            }
            else -> Unit
        }
    }

    private fun onAppForegrounded() {
        appInForeground = true
        when (val current = state) {
            is PlayerState.Suspended -> {
                val slot = previewSlot ?: return
                if (slot.owner != current.owner) return
                attachPreviewSurface(slot.owner, current.url, slot.textureView, slot.maxVideoWidth, slot.maxVideoHeight, current.enableAudio)
                state = PlayerState.Preview(
                    current.owner,
                    current.url,
                    current.maxVideoWidth,
                    current.maxVideoHeight,
                    current.enableAudio,
                )
                prepareChannel(current.url)
                resumePreviewPlayback()
            }
            is PlayerState.Preview -> resumePreviewPlayback()
            else -> Unit
        }
    }

    private fun attachPreviewSurface(
        owner: String,
        url: String?,
        textureView: TextureView,
        maxVideoWidth: Int,
        maxVideoHeight: Int,
        enableAudio: Boolean = false,
    ) {
        if (boundView != null && boundView !== textureView) {
            player.clearVideoTextureView(boundView)
        }
        boundView = textureView
        player.setVideoTextureView(textureView)
        IptvExoPlayer.configureForPreview(player, maxVideoWidth, maxVideoHeight, enableAudio = enableAudio)
        previewSlot = PreviewSlot(owner, url, textureView, maxVideoWidth, maxVideoHeight, enableAudio)
    }

    private fun detachPreviewSurface(textureView: TextureView) {
        if (boundView === textureView) {
            player.clearVideoTextureView(textureView)
            boundView = null
        }
    }

    private fun resumePreviewPlayback() {
        if (!appInForeground || state is PlayerState.Fullscreen) return
        val view = boundView ?: return
        if (!view.isAttachedToWindow) return
        if (player.mediaItemCount == 0) {
            val url = (state as? PlayerState.Preview)?.url ?: (state as? PlayerState.Suspended)?.url
            if (url != null) prepareChannel(url)
        }
        player.playWhenReady = true
        player.play()
    }

    private fun stopPreviewDecoder() {
        player.stop()
        player.clearMediaItems()
        boundUrl = null
    }

    private fun prepareChannel(url: String?, isLive: Boolean = url != null && !IptvExoPlayer.isLikelyVod(url)) {
        if (url == null) {
            stopPreviewDecoder()
            return
        }
        if (url == boundUrl) return
        runCatching {
            player.setMediaItem(IptvExoPlayer.mediaItemForUrl(url, isLive = isLive))
            player.prepare()
            boundUrl = url
        }.onFailure { FaultLog.record("LivePreviewEngine.prepare", it) }
    }
}
