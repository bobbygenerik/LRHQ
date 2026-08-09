package com.livingroomhq.player.pcm

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink

/**
 * When [CaptionAudioPolicy.forcePcmDecode] is true, rejects encoded passthrough/offload
 * so [TeeAudioProcessor] receives PCM. When false, delegates fully (normal playback).
 */
@OptIn(UnstableApi::class)
internal class PcmForcedAudioSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun supportsFormat(format: Format): Boolean {
        if (!CaptionAudioPolicy.forcePcmDecode()) return super.supportsFormat(format)
        return MimeTypes.AUDIO_RAW == format.sampleMimeType && super.supportsFormat(format)
    }

    override fun getFormatSupport(format: Format): @AudioSink.SinkFormatSupport Int {
        if (!CaptionAudioPolicy.forcePcmDecode()) return super.getFormatSupport(format)
        return if (MimeTypes.AUDIO_RAW == format.sampleMimeType) {
            super.getFormatSupport(format)
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }
    }

    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport {
        if (!CaptionAudioPolicy.forcePcmDecode()) return super.getFormatOffloadSupport(format)
        return AudioOffloadSupport.DEFAULT_UNSUPPORTED
    }
}
