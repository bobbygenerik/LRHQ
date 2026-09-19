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
                val frames = shorts.size / channels
                FloatArray(frames) { i ->
                    if (channels == 1) {
                        shorts[i] / 32768f
                    } else {
                        val c0 = shorts[i * channels] / 32768f
                        val c1 = shorts[i * channels + 1] / 32768f
                        // Phase-safe mono mix: if out-of-phase (c0 * c1 < 0), pick larger magnitude to prevent destructive cancellation
                        if (c0 * c1 < 0f) {
                            if (kotlin.math.abs(c0) >= kotlin.math.abs(c1)) c0 else c1
                        } else {
                            (c0 + c1) * 0.5f
                        }
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(chunk.size / 4)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                val frames = floats.size / channels
                FloatArray(frames) { i ->
                    if (channels == 1) {
                        floats[i]
                    } else {
                        val c0 = floats[i * channels]
                        val c1 = floats[i * channels + 1]
                        if (c0 * c1 < 0f) {
                            if (kotlin.math.abs(c0) >= kotlin.math.abs(c1)) c0 else c1
                        } else {
                            (c0 + c1) * 0.5f
                        }
                    }
                }
            }
            C.ENCODING_PCM_8BIT -> {
                val frames = chunk.size / channels
                FloatArray(frames) { i ->
                    if (channels == 1) {
                        (chunk[i].toInt() and 0xff) / 128f - 1f
                    } else {
                        val c0 = (chunk[i * channels].toInt() and 0xff) / 128f - 1f
                        val c1 = (chunk[i * channels + 1].toInt() and 0xff) / 128f - 1f
                        if (c0 * c1 < 0f) {
                            if (kotlin.math.abs(c0) >= kotlin.math.abs(c1)) c0 else c1
                        } else {
                            (c0 + c1) * 0.5f
                        }
                    }
                }
            }
            else -> null
        }
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
