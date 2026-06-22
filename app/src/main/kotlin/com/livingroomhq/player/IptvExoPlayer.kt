package com.livingroomhq.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.livingroomhq.core.data.model.Channel

/** Shared ExoPlayer setup for IPTV streams (redirects + HLS). */
object IptvExoPlayer {
    /** Hold more live edge buffer so brief CDN gaps do not spin the UI spinner. */
    private val loadControl =
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 5_000,
            )
            .build()
    private fun mediaAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

    fun create(context: Context): ExoPlayer =
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(20_000)
                            // Live HLS can go quiet between segments; avoid mid-play read timeouts.
                            .setReadTimeoutMs(60_000),
                    ),
                    DefaultExtractorsFactory()
                        .setTsExtractorFlags(
                            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                            DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
                        )
                ),
            )
            .build()

    fun mediaItemForUrl(url: String): MediaItem =
        MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(3_000)
                    .setMinOffsetMs(1_500)
                    .setMaxOffsetMs(15_000)
                    .build(),
            )
            .build()

    fun prepareChannel(player: ExoPlayer, channel: Channel, playWhenReady: Boolean = true) {
        player.setMediaItem(mediaItemForUrl(channel.streamUrl))
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    fun configureForPreview(player: ExoPlayer, maxVideoWidth: Int, maxVideoHeight: Int) {
        player.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus= */ false)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxVideoWidth, maxVideoHeight)
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        player.volume = 0f
     }
 
     fun configureForFullscreen(player: ExoPlayer) {
         player.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus= */ true)
         player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
             .clearVideoSizeConstraints()
             .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
             .build()
         player.volume = 1f
         player.playWhenReady = true
     }

    /**
     * Enable audio once HLS exposes an audio track. HLS live manifests refresh often;
     * do not re-run [configureForFullscreen] on every [Player.Listener.onTracksChanged]
     * or mid-playback will stutter/rebuffer.
     */
    fun ensureFullscreenAudio(player: ExoPlayer, tracks: Tracks) {
        if (!player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_AUDIO)) return
        if (tracks.groups.none { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .build()
        player.volume = 1f
    }
}
