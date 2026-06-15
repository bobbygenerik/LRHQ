package com.livingroomhq.player

import android.content.Context
import android.view.TextureView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.livingroomhq.core.data.model.Channel

/**
 * Single shared preview player for the launcher. Home and Live TV must not each
 * spin up their own [ExoPlayer] — two decoders on the same stream causes audio
 * glitches, memory pressure, and transition freezes.
 *
 * Fullscreen reuses this same player (surface handoff) so IPTV does not re-buffer
 * when opening a channel that is already playing in preview.
 */
class LivePreviewEngine(context: Context) {
    private val appContext = context.applicationContext
    private var boundOwner: String? = null
    private var boundView: TextureView? = null
    private var previewTextureView: TextureView? = null
    private var fullscreenPlayerView: PlayerView? = null
    private var boundUrl: String? = null
    private var previewMaxVideoWidth: Int = 1280
    private var previewMaxVideoHeight: Int = 720
    private var fullscreenActive = false

    val player: ExoPlayer = IptvExoPlayer.create(appContext).apply {
        playWhenReady = true
        addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) = Unit
        })
    }

    fun bind(
        owner: String,
        channel: Channel?,
        textureView: TextureView,
        maxVideoWidth: Int,
        maxVideoHeight: Int,
    ) {
        previewMaxVideoWidth = maxVideoWidth
        previewMaxVideoHeight = maxVideoHeight
        previewTextureView = textureView
        boundOwner = owner

        if (fullscreenActive) return

        if (boundView != null && boundView !== textureView) {
            player.clearVideoTextureView(boundView)
        }
        boundView = textureView
        player.setVideoTextureView(textureView)
        IptvExoPlayer.configureForPreview(player, maxVideoWidth, maxVideoHeight)
        prepareChannel(channel?.streamUrl?.takeIf { it.isNotBlank() })
    }

    fun unbind(owner: String, textureView: TextureView) {
        if (boundOwner != owner) return
        if (fullscreenActive) {
            previewTextureView = textureView
            return
        }
        if (boundView !== textureView) return
        player.clearVideoTextureView(textureView)
        player.stop()
        player.clearMediaItems()
        boundOwner = null
        boundView = null
        boundUrl = null
    }

    /** Move the live decoder to a fullscreen [PlayerView] without restarting the stream. */
    fun promoteToFullscreen(playerView: PlayerView, channel: Channel) {
        val url = channel.streamUrl.takeIf { it.isNotBlank() } ?: return
        if (fullscreenActive && fullscreenPlayerView === playerView && boundUrl == url) {
            if (playerView.player != player) playerView.player = player
            return
        }

        fullscreenActive = true
        boundView?.let { player.clearVideoTextureView(it) }
        boundView = null

        fullscreenPlayerView = playerView
        playerView.player = player

        IptvExoPlayer.configureForFullscreen(player)
        prepareChannel(url)
        player.playWhenReady = true
        player.play()
    }

    fun demoteFromFullscreen() {
        if (!fullscreenActive) return
        fullscreenActive = false

        player.pause()
        player.volume = 0f
        fullscreenPlayerView?.player = null
        fullscreenPlayerView = null

        val textureView = previewTextureView ?: boundView
        if (textureView != null) {
            boundView = textureView
            player.setVideoTextureView(textureView)
            IptvExoPlayer.configureForPreview(player, previewMaxVideoWidth, previewMaxVideoHeight)
            player.playWhenReady = true
            player.play()
        } else {
            IptvExoPlayer.configureForPreview(player, previewMaxVideoWidth, previewMaxVideoHeight)
            player.stop()
            player.clearMediaItems()
            boundUrl = null
        }
    }

    fun ensureFullscreenAudio(tracks: Tracks) {
        if (!fullscreenActive) return
        IptvExoPlayer.ensureFullscreenAudio(player, tracks)
    }

    fun pause() {
        if (fullscreenActive) return
        player.pause()
    }

    fun resume() {
        if (fullscreenActive) return
        if (boundView == null) return
        if (player.mediaItemCount == 0) {
            val url = boundUrl
            if (url != null) {
                boundUrl = null
                prepareChannel(url)
            }
        }
        player.play()
    }

    fun release() {
        demoteFromFullscreen()
        boundView?.let { player.clearVideoTextureView(it) }
        player.release()
        boundOwner = null
        boundView = null
        previewTextureView = null
        boundUrl = null
    }

    private fun prepareChannel(url: String?) {
        if (url == null) {
            player.stop()
            player.clearMediaItems()
            boundUrl = null
            return
        }
        if (url == boundUrl) return
        runCatching {
            player.setMediaItem(IptvExoPlayer.mediaItemForUrl(url))
            player.prepare()
            boundUrl = url
        }
    }
}
