package com.livingroomhq.translate

import androidx.media3.common.C
import com.livingroomhq.player.pcm.PcmCaptureHub
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class PcmAudioNormalizerTest {
    @Test fun `center dialogue survives surround downmix in every supported encoding`() {
        for (channels in listOf(3, 5, 6, 8)) {
            for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_8BIT)) {
                val output = normalize(channels, encoding) { channel -> if (channel == 2) .5f else 0f }
                assertTrue("center lost for $channels channels encoding $encoding", output.all { it > .3f })
            }
        }
    }

    @Test fun `quad rear channels survive and surround LFE is excluded`() {
        assertTrue(normalize(4, C.ENCODING_PCM_FLOAT) { if (it == 2) .5f else 0f }.all { it > 0f })
        assertTrue(normalize(6, C.ENCODING_PCM_FLOAT) { if (it == 3) .5f else 0f }.all { it == 0f })
    }

    @Test fun `mono and phase safe stereo are preserved and loud surround stays bounded`() {
        assertEquals(.5f, normalize(1, C.ENCODING_PCM_FLOAT) { .5f }.first(), .0001f)
        assertEquals(.5f, normalize(2, C.ENCODING_PCM_FLOAT) { if (it == 0) .5f else -.5f }.first(), .0001f)
        assertTrue(normalize(8, C.ENCODING_PCM_FLOAT) { 1f }.all { it in -1f..1f })
    }

    private fun normalize(channels: Int, encoding: Int, value: (Int) -> Float): List<Float> {
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_FLOAT -> 4
            C.ENCODING_PCM_16BIT -> 2
            else -> 1
        }
        val buffer = ByteBuffer.allocate(160 * channels * bytesPerSample).order(ByteOrder.LITTLE_ENDIAN)
        repeat(160) {
            repeat(channels) { channel ->
                val sample = value(channel)
                when (encoding) {
                    C.ENCODING_PCM_FLOAT -> buffer.putFloat(sample)
                    C.ENCODING_PCM_16BIT -> buffer.putShort((sample * 32768).toInt().toShort())
                    else -> buffer.put(((sample + 1) * 128).toInt().coerceIn(0, 255).toByte())
                }
            }
        }
        return mutableListOf<Float>().also {
            PcmAudioNormalizer.appendNormalized(buffer.array(), PcmCaptureHub.PcmFormat(16000, channels, encoding), it)
        }
    }
}
