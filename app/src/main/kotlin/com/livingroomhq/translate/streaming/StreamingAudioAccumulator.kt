package com.livingroomhq.translate.streaming

import com.livingroomhq.translate.LiveTranslatePipeline
import com.livingroomhq.translate.PcmAudioNormalizer

/**
 * Adaptive audio segmenter for live streaming ASR.
 *
 * Rather than waiting for a rigid fixed window (~2.0s), triggers speech processing
 * as soon as a speaker pauses (silence pause 400-600ms) after minimum speech (1.2s),
 * or when max window cap (2.2s) is reached.
 */
class StreamingAudioAccumulator(
    private val sampleRate: Int = PcmAudioNormalizer.TARGET_RATE,
    private val minSpeechSec: Float = 0.6f,
    private val maxWindowSec: Float = 1.8f,
    private val pauseSilenceSec: Float = 0.25f,
    private val overlapSec: Float = 0.40f,
    private val speechRmsThreshold: Float = LiveTranslatePipeline.SPEECH_RMS_THRESHOLD,
    private val silenceRmsThreshold: Float = LiveTranslatePipeline.LIVE_SILENCE_RMS_THRESHOLD,
) {
    data class AudioSlice(
        val pcm: FloatArray,
        val windowEndWallMs: Long,
        val isUtteranceEnd: Boolean,
        val durationSec: Float,
    )

    private val minSpeechSamples = (minSpeechSec * sampleRate).toInt()
    private val maxWindowSamples = (maxWindowSec * sampleRate).toInt()
    private val pauseSilenceSamples = (pauseSilenceSec * sampleRate).toInt()
    private val overlapSamples = (overlapSec * sampleRate).toInt()
    private val vadFrameSamples = sampleRate / 20 // 50ms frames

    private val buffer = ArrayList<Float>(maxWindowSamples * 2)
    private var speechDetectedInUtterance = false
    private var silenceSampleCount = 0

    fun append(samples: FloatArray) {
        for (s in samples) {
            buffer.add(s)
        }
    }

    fun append(samples: List<Float>) {
        buffer.addAll(samples)
        val maxAllowed = (maxWindowSamples * 1.5f).toInt()
        if (buffer.size > maxAllowed) {
            val dropCount = buffer.size - maxWindowSamples
            LiveTranslatePipeline.dropFront(buffer, dropCount)
        }
    }

    private var peakSpeechRmsInUtterance = 0f

    /**
     * Inspects current buffer and yields an [AudioSlice] if speech boundary or max window met.
     * Returns null if more audio needed.
     */
    fun pollSlice(): AudioSlice? {
        if (buffer.size < vadFrameSamples) return null

        val trailingSilence = computeTrailingSilenceSamples()
        val speechSamples = buffer.size - trailingSilence

        val isPauseAfterSpeech = speechSamples >= minSpeechSamples && trailingSilence >= pauseSilenceSamples
        val isMaxWindowReached = buffer.size >= maxWindowSamples

        if (!isPauseAfterSpeech && !isMaxWindowReached) {
            return null
        }

        // Check if slice actually contains speech energy before dispatching
        val sliceSize = minOf(buffer.size, maxWindowSamples)
        val sliceStart = buffer.size - sliceSize
        val slice = FloatArray(sliceSize)
        for (i in 0 until sliceSize) {
            slice[i] = buffer[sliceStart + i]
        }

        val energy = LiveTranslatePipeline.rms(slice)
        val peakFrameRms = LiveTranslatePipeline.maxFrameRms(slice, LiveTranslatePipeline.LIVE_VAD_SAMPLES)
        val hasSpeechEnergy = energy >= speechRmsThreshold || peakFrameRms >= LiveTranslatePipeline.SPEECH_PEAK_RMS_THRESHOLD

        val nowWallMs = System.currentTimeMillis()
        val durationSec = sliceSize.toFloat() / sampleRate
        val isUtteranceEnd = isPauseAfterSpeech

        if (isUtteranceEnd) {
            // Speaker paused: retain minimal overlap or clear
            buffer.clear()
            speechDetectedInUtterance = false
            peakSpeechRmsInUtterance = 0f
            silenceSampleCount = 0
        } else {
            // Reached max window while speaking: retain overlap tail for continuity
            val keep = minOf(overlapSamples, buffer.size)
            val drop = buffer.size - keep
            LiveTranslatePipeline.dropFront(buffer, drop)
            silenceSampleCount = 0
        }

        return if (hasSpeechEnergy) {
            AudioSlice(
                pcm = slice,
                windowEndWallMs = nowWallMs,
                isUtteranceEnd = isUtteranceEnd,
                durationSec = durationSec,
            )
        } else {
            null
        }
    }

    fun clear() {
        buffer.clear()
        speechDetectedInUtterance = false
        peakSpeechRmsInUtterance = 0f
        silenceSampleCount = 0
    }

    private fun computeTrailingSilenceSamples(): Int {
        var trailing = 0
        var pos = buffer.size
        // First compute peak frame RMS in current buffer to know speech level
        var currentPeak = 0f
        var scanPos = 0
        while (scanPos + vadFrameSamples <= buffer.size) {
            var sum = 0.0
            for (i in scanPos until scanPos + vadFrameSamples) {
                val s = buffer[i]
                sum += (s * s).toDouble()
            }
            val r = kotlin.math.sqrt(sum / vadFrameSamples).toFloat()
            if (r > currentPeak) currentPeak = r
            scanPos += vadFrameSamples
        }
        if (currentPeak > peakSpeechRmsInUtterance) {
            peakSpeechRmsInUtterance = currentPeak
        }

        // Relative silence cutoff: 40% of peak speech or fallback threshold
        val relativeSilenceCutoff = maxOf(silenceRmsThreshold, peakSpeechRmsInUtterance * 0.40f)

        while (pos >= vadFrameSamples) {
            val frameStart = pos - vadFrameSamples
            var sum = 0.0
            for (i in frameStart until pos) {
                val s = buffer[i]
                sum += (s * s).toDouble()
            }
            val frameRms = kotlin.math.sqrt(sum / vadFrameSamples).toFloat()
            if (frameRms < relativeSilenceCutoff) {
                trailing += vadFrameSamples
                pos -= vadFrameSamples
            } else {
                break
            }
        }
        return trailing
    }

    val currentBufferSize: Int get() = buffer.size
}
