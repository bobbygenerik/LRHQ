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
        return when (format.encoding) {
            C.ENCODING_PCM_16BIT -> {
                val shorts = ShortArray(chunk.size / 2)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                val frames = shorts.size / channels
                FloatArray(frames) { i ->
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += shorts[i * channels + c] / 32768f
                    }
                    sum / channels
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                val floats = FloatArray(chunk.size / 4)
                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats)
                val frames = floats.size / channels
                FloatArray(frames) { i ->
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += floats[i * channels + c]
                    }
                    sum / channels
                }
            }
            C.ENCODING_PCM_8BIT -> {
                val frames = chunk.size / channels
                FloatArray(frames) { i ->
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += (chunk[i * channels + c].toInt() and 0xff) / 128f - 1f
                    }
                    sum / channels
                }
            }
            else -> null
        }
    }

    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (input.isEmpty()) return input
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
