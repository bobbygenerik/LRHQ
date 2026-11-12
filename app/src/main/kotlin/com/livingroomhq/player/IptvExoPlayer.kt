package com.livingroomhq.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Tracks
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.player.pcm.CaptionAudioPolicy
import com.livingroomhq.player.pcm.PcmCaptureHub
import com.livingroomhq.player.pcm.PcmForcedAudioSink

/** Shared ExoPlayer setup for IPTV streams (redirects + HLS). */
@OptIn(UnstableApi::class)
object IptvExoPlayer {
    private const val DEFAULT_USER_AGENT =
        "TiviMate/4.7.0 (Linux; Android 11)"

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

    /**
     * Custom factory: tee decoded PCM into [PcmCaptureHub] without altering samples.
     *
     * [PcmForcedAudioSink] rejects passthrough/offload only while
     * [CaptionAudioPolicy.forcePcmDecode] is true (captions on). Media3 1.5.0 already
     * defaults [DefaultAudioSink] offload mode to disabled.
     */
    private fun renderersFactory(context: Context): DefaultRenderersFactory =
        object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                val tee = TeeAudioProcessor(PcmCaptureHub)
                val pcmSink =
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf<AudioProcessor>(tee))
                        .build()
                return PcmForcedAudioSink(pcmSink)
            }
        }

    fun isLikelyVod(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
            lower.endsWith(".avi") || lower.endsWith(".mov") ||
            lower.endsWith(".m4v") || lower.endsWith(".flv") ||
            lower.contains("/movie/") || lower.contains("/series/") ||
            lower.contains("/vod/")
    }

    fun create(context: Context): ExoPlayer =
        ExoPlayer.Builder(context, renderersFactory(context.applicationContext))
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        context,
                        DefaultHttpDataSource.Factory()
                            .setUserAgent(DEFAULT_USER_AGENT)
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(20_000)
                            // Live HLS can go quiet between segments; avoid mid-play read timeouts.
                            .setReadTimeoutMs(60_000),
                    ),
                    DefaultExtractorsFactory()
                        .setTsExtractorFlags(
                            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
                        ),
                ),
            )
            .build()

    fun mediaItemForUrl(url: String, isLive: Boolean = !isLikelyVod(url)): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        if (isLive) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(3_000)
                    .setMinOffsetMs(1_500)
                    .setMaxOffsetMs(15_000)
                    .build(),
            )
        }
        return builder.build()
    }

    fun prepareChannel(player: ExoPlayer, channel: Channel, playWhenReady: Boolean = true) {
        player.setMediaItem(mediaItemForUrl(channel.streamUrl))
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    fun configureForPreview(
        player: ExoPlayer,
        maxVideoWidth: Int = 1920,
        maxVideoHeight: Int = 1080,
        enableAudio: Boolean = false,
    ) {
        player.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus = */ enableAudio)
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .apply {
                    if (maxVideoWidth in 1 until Int.MAX_VALUE && maxVideoHeight in 1 until Int.MAX_VALUE) {
                        setMaxVideoSize(maxVideoWidth, maxVideoHeight)
                    } else {
                        clearVideoSizeConstraints()
                    }
                }
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, !enableAudio)
                .build()
        player.volume = if (enableAudio) 1f else 0f
    }

    fun configureForFullscreen(player: ExoPlayer) {
        player.setAudioAttributes(mediaAudioAttributes(), /* handleAudioFocus = */ true)
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
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
        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .build()
        player.volume = 1f
    }

    /**
     * Sync [CaptionAudioPolicy] and force audio renderer to reconfigure so passthrough
     * is disabled only while live captions need PCM.
     */
    fun applyCaptionAudioMode(player: ExoPlayer, forcePcmDecode: Boolean) {
        CaptionAudioPolicy.setForcePcmDecode(forcePcmDecode)
        val base = player.trackSelectionParameters
        player.trackSelectionParameters =
            base.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
        player.trackSelectionParameters =
            base.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).build()
        player.volume = 1f
    }

    /**
     * Adjust live playback delay automatically so speech recognition (~2.5-3.0s latency)
     * aligns with audio/video output when captions are active.
     * The user does not need to seek manually.
     */
    fun applyCaptionDelay(player: ExoPlayer, enabled: Boolean) {
        val targetOffsetMs = if (enabled) 6_500L else 3_000L
        val minOffsetMs = if (enabled) 4_000L else 1_500L
        val maxOffsetMs = if (enabled) 18_000L else 15_000L
        player.currentMediaItem?.let { current ->
            val updated = current.buildUpon()
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(targetOffsetMs)
                        .setMinOffsetMs(minOffsetMs)
                        .setMaxOffsetMs(maxOffsetMs)
                        .build()
                )
                .build()
            player.setMediaItem(updated, /* resetPosition = */ false)
        }

        if (enabled) {
            // If live stream has buffered behind live edge, seek back 3000ms.
            // Otherwise, pause for 3000ms while ExoPlayer buffers the incoming stream.
            val currentPos = player.currentPosition
            val isLive = player.isCurrentMediaItemLive
            if (isLive && currentPos > 4_000L) {
                player.seekTo((currentPos - 3_000L).coerceAtLeast(0L))
            } else {
                player.pause()
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    player.play()
                }, 3_000L)
            }
        } else {
            // When captions are turned off, snap back to the live edge.
            if (player.isCurrentMediaItemLive) {
                player.seekToDefaultPosition()
            }
        }
    }
}
