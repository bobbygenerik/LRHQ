package com.livingroomhq.translate

import androidx.media3.common.C
import com.livingroomhq.player.pcm.PcmCaptureHub
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Converts tee PCM chunks into 16 kHz mono float32 for whisper. */
object PcmAudioNormalizer {
    const val TARGET_RATE = 16_000

    fun appendNormalized(
        chunk: ByteArray,
        format: PcmCaptureHub.PcmFormat,
        out: MutableList<Float>,
    ) {
        if (format.sampleRateHz <= 0 || format.channelCount <= 0) return
        val floats = decodeToFloatMono(chunk, format) ?: return
        if (format.sampleRateHz == TARGET_RATE) {
            out.addAll(floats.asList())
        } else {
            out.addAll(resampleLinear(floats, format.sampleRateHz, TARGET_RATE).asList())
        }
    }

    private fun decodeToFloatMono(chunk: ByteArray, format: PcmCaptureHub.PcmFormat): FloatArray? {
        val channels = format.channelCount
        if (channels <= 0) return null
        return when (format.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val shorts = ShortArray(chunk.size / 2)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                FloatArray(shorts.size / channels) { frame ->
                    mixFrame(channels) { channel -> shorts[frame * channels + channel] / 32768f }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(chunk.size / 4)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                FloatArray(floats.size / channels) { frame ->
                    mixFrame(channels) { channel -> floats[frame * channels + channel] }
                }
            }
            C.ENCODING_PCM_8BIT -> {
                FloatArray(chunk.size / channels) { frame ->
                    mixFrame(channels) { channel -> (chunk[frame * channels + channel].toInt() and 0xff) / 128f - 1f }
                }
            }
            else -> null
        }
    }

    private inline fun mixFrame(channels: Int, sample: (Int) -> Float): Float {
        if (channels == 1) return sample(0)
        val left = sample(0)
        val right = sample(1)
        // Preserve the existing phase-safe stereo behavior.
        var mono = if (left * right < 0f) {
            if (kotlin.math.abs(left) >= kotlin.math.abs(right)) left else right
        } else {
            (left + right) * .5f
        }
        // Android interleaved PCM: L/R/C for 3 channels, quad L/R/rears,
        // and L/R/C/LFE/surrounds for 5.1 and 7.1. LFE carries no dialogue.
        if (channels == 3 || channels >= 5) mono += sample(2) * .70710677f
        val firstSurround = when {
            channels == 4 -> 2
            channels == 5 -> 3
            channels >= 6 -> 4
            else -> channels
        }
        for (channel in firstSurround until channels) mono += sample(channel) * .35355338f
        return mono.coerceIn(-1f, 1f)
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty()) return input
        // Exact 3:1 decimation (48 kHz -> 16 kHz) with low-pass anti-aliasing box filter
        if (srcRate == 48_000 && dstRate == 16_000) {
            val outLen = input.size / 3
            if (outLen <= 0) return input
            val out = FloatArray(outLen)
            for (i in 0 until outLen) {
                val idx = i * 3
                out[i] = (input[idx] + input[idx + 1] + input[idx + 2]) / 3.0f
            }
            return out
        }
        val outLen = maxOf(1, (input.size.toLong() * dstRate / srcRate).toInt())
        val out = FloatArray(outLen)
        val ratio = srcRate.toDouble() / dstRate
        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, input.lastIndex)
            val i1 = min(i0 + 1, input.lastIndex)
            val frac = (srcPos - i0).toFloat()
            out[i] = input[i0] * (1f - frac) + input[i1] * frac
        }
        return out
    }
}
