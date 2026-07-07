package com.livingroomhq.player

import android.content.Context
import android.view.TextureView
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
    ) : PlayerState()

    data class Fullscreen(val url: String) : PlayerState()

    /** Preview slot retained while the process is backgrounded. */
    data class Suspended(
        val owner: String,
        val url: String?,
        val maxVideoWidth: Int,
        val maxVideoHeight: Int,
    ) : PlayerState()
}

private data class PreviewSlot(
    val owner: String,
    val textureView: TextureView,
    val maxVideoWidth: Int,
    val maxVideoHeight: Int,
)

class LivePreviewEngine(context: Context) {
    private val appContext = context.applicationContext
    private var state: PlayerState = PlayerState.Idle
    private var previewSlot: PreviewSlot? = null
    private var boundView: TextureView? = null
    private var _fullscreenPlayerView: PlayerView? = null
    private var boundUrl: String? = null
    private var appInForeground = true

    val fullscreenActive: Boolean
        get() = state is PlayerState.Fullscreen

    /** Exposed for ChannelPlayerActivity to rebind during channel zapping. */
    val activePlayerView: PlayerView?
        get() = _fullscreenPlayerView

    val player: ExoPlayer = IptvExoPlayer.create(appContext).apply {
        playWhenReady = true
        addListener(object : Player.Listener {
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
    ) {
        val url = channel?.streamUrl?.takeIf { it.isNotBlank() }
        previewSlot = PreviewSlot(owner, textureView, maxVideoWidth, maxVideoHeight)

        when (val current = state) {
            is PlayerState.Fullscreen -> return
            is PlayerState.Suspended -> {
                if (current.owner != owner) return
                state = PlayerState.Suspended(owner, url, maxVideoWidth, maxVideoHeight)
                return
            }
            else -> Unit
        }

        attachPreviewSurface(owner, textureView, maxVideoWidth, maxVideoHeight)
        state = PlayerState.Preview(owner, url, maxVideoWidth, maxVideoHeight)
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
                previewSlot = null
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
    fun promoteToFullscreen(playerView: PlayerView, channel: Channel) {
        val url = channel.streamUrl.takeIf { it.isNotBlank() } ?: return
        val current = state
        if (current is PlayerState.Fullscreen && _fullscreenPlayerView === playerView && boundUrl == url) {
            if (playerView.player != player) playerView.player = player
            return
        }

        boundView?.let { player.clearVideoTextureView(it) }
        boundView = null

        _fullscreenPlayerView = playerView
        playerView.player = player
        state = PlayerState.Fullscreen(url)

        IptvExoPlayer.configureForFullscreen(player)
        prepareChannel(url)
        player.playWhenReady = true
        player.play()
    }

    fun demoteFromFullscreen() {
        if (state !is PlayerState.Fullscreen) return

        player.pause()
        player.volume = 0f
        _fullscreenPlayerView?.player = null
        _fullscreenPlayerView = null

        val slot = previewSlot
        if (slot != null && appInForeground) {
            attachPreviewSurface(slot.owner, slot.textureView, slot.maxVideoWidth, slot.maxVideoHeight)
            val url = boundUrl
            state = PlayerState.Preview(slot.owner, url, slot.maxVideoWidth, slot.maxVideoHeight)
            IptvExoPlayer.configureForPreview(player, slot.maxVideoWidth, slot.maxVideoHeight)
            resumePreviewPlayback()
        } else if (slot != null) {
            stopPreviewDecoder()
            state = PlayerState.Suspended(
                slot.owner,
                boundUrl,
                slot.maxVideoWidth,
                slot.maxVideoHeight,
            )
        } else {
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
        stopPreviewDecoder()
        state = when (val current = state) {
            is PlayerState.Suspended -> current
            is PlayerState.Preview -> PlayerState.Suspended(
                current.owner,
                current.url,
                current.maxVideoWidth,
                current.maxVideoHeight,
            )
            else -> PlayerState.Idle
        }
    }

    fun ensureFullscreenAudio(tracks: Tracks) {
        if (state !is PlayerState.Fullscreen) return
        IptvExoPlayer.ensureFullscreenAudio(player, tracks)
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
                attachPreviewSurface(slot.owner, slot.textureView, slot.maxVideoWidth, slot.maxVideoHeight)
                state = PlayerState.Preview(
                    current.owner,
                    current.url,
                    current.maxVideoWidth,
                    current.maxVideoHeight,
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
        textureView: TextureView,
        maxVideoWidth: Int,
        maxVideoHeight: Int,
    ) {
        if (boundView != null && boundView !== textureView) {
            player.clearVideoTextureView(boundView)
        }
        boundView = textureView
        player.setVideoTextureView(textureView)
        IptvExoPlayer.configureForPreview(player, maxVideoWidth, maxVideoHeight)
        previewSlot = PreviewSlot(owner, textureView, maxVideoWidth, maxVideoHeight)
    }

    private fun detachPreviewSurface(textureView: TextureView) {
        if (boundView === textureView) {
            player.clearVideoTextureView(textureView)
            boundView = null
        }
    }

    private fun resumePreviewPlayback() {
        if (!appInForeground || state is PlayerState.Fullscreen) return
        if (boundView == null) return
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

    private fun prepareChannel(url: String?) {
        if (url == null) {
            stopPreviewDecoder()
            return
        }
        if (url == boundUrl) return
        runCatching {
            player.setMediaItem(IptvExoPlayer.mediaItemForUrl(url))
            player.prepare()
            boundUrl = url
        }.onFailure { FaultLog.record("LivePreviewEngine.prepare", it) }
    }
}
