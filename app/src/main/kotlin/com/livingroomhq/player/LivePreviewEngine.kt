package com.livingroomhq.player

import android.content.Context
import android.view.TextureView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.livingroomhq.core.data.model.Channel

/**
 * Single shared preview player for the launcher. Home and Live TV must not each
 * spin up their own [ExoPlayer] — two decoders on the same stream causes audio
 * glitches, memory pressure, and transition freezes.
 */
class LivePreviewEngine(context: Context) {
    private val appContext = context.applicationContext
    private var boundOwner: String? = null
    private var boundView: TextureView? = null
    private var boundUrl: String? = null

    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    appContext,
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
                ),
            ),
        )
        .build()
        .apply {
            playWhenReady = true
            volume = 0f
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                .build()
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
        if (boundView != null && boundView !== textureView) {
            player.clearVideoTextureView(boundView)
        }
        boundOwner = owner
        boundView = textureView
        player.setVideoTextureView(textureView)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        player.volume = 0f
        prepareChannel(channel?.streamUrl?.takeIf { it.isNotBlank() })
    }

    fun unbind(owner: String, textureView: TextureView) {
        if (boundOwner != owner) return
        if (boundView !== textureView) return
        player.clearVideoTextureView(textureView)
        player.stop()
        player.clearMediaItems()
        boundOwner = null
        boundView = null
        boundUrl = null
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        if (boundView != null) player.play()
    }

    fun release() {
        boundView?.let { player.clearVideoTextureView(it) }
        player.release()
        boundOwner = null
        boundView = null
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
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            boundUrl = url
        }
    }
}
