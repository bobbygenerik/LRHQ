package com.livingroomhq.translate.streaming

import com.livingroomhq.translate.LiveTranslatePipeline
import com.livingroomhq.translate.PcmAudioNormalizer

/**
 * Adaptive audio segmenter for live streaming ASR.
 *
 * Keeps a phrase together until a sustained pause, with a bounded maximum for
 * continuous speech. Short word-sized windows lose context needed for translation.
 */
class StreamingAudioAccumulator(
    private val sampleRate: Int = PcmAudioNormalizer.TARGET_RATE,
    private val minSpeechSec: Float = 0.25f,
    private val maxWindowSec: Float = 3.5f,
    private val pauseSilenceSec: Float = 0.45f,
    private val overlapSec: Float = 0.5f,
    private val speechRmsThreshold: Float = LiveTranslatePipeline.SPEECH_RMS_THRESHOLD,
    private val silenceRmsThreshold: Float = LiveTranslatePipeline.LIVE_SILENCE_RMS_THRESHOLD,
    private val relativeSilenceRatio: Float = 0f,
) {
    data class AudioSlice(
        val pcm: FloatArray,
        val windowEndWallMs: Long,
        val isUtteranceEnd: Boolean,
        val durationSec: Float,
        val utteranceId: Long = 0,
        val startSample: Long = 0,
        val endSample: Long = pcm.size.toLong(),
        val windowEndElapsedMs: Long = 0L,
    )

    private val minSpeechSamples = (minSpeechSec * sampleRate).toInt()
    private val maxWindowSamples = (maxWindowSec * sampleRate).toInt()
    private val pauseSilenceSamples = (pauseSilenceSec * sampleRate).toInt()
    private val overlapSamples = (overlapSec * sampleRate).toInt()
    private val vadFrameSamples = sampleRate / 20 // 50ms frames

    private val buffer = ArrayList<Float>(maxWindowSamples * 2)
    private var speechDetectedInUtterance = false
    private var utteranceId = 0L
    private var silenceSampleCount = 0
    private var bufferStartSample = 0L
    private var totalSamples = 0L
    private var audioEndWallMs = 0L
    private var audioEndElapsedMs = 0L

    fun append(samples: FloatArray) {
        append(samples.asList())
    }

    fun append(
        samples: List<Float>,
        endWallMs: Long = System.currentTimeMillis(),
        endElapsedMs: Long = System.nanoTime() / 1_000_000L,
    ) {
        if (samples.isEmpty()) return
        audioEndWallMs = endWallMs
        audioEndElapsedMs = endElapsedMs
        buffer.addAll(samples)
        totalSamples += samples.size
        val maxAllowed = (maxWindowSamples * 1.5f).toInt()
        if (buffer.size > maxAllowed) {
            val dropCount = buffer.size - maxWindowSamples
            LiveTranslatePipeline.dropFront(buffer, dropCount)
            bufferStartSample += dropCount
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

        val hasUtteranceSpeech = speechSamples >= minSpeechSamples ||
            speechDetectedInUtterance
        val isPauseAfterSpeech = hasUtteranceSpeech && trailingSilence >= pauseSilenceSamples
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

        val nowWallMs = audioEndWallMs
        val durationSec = sliceSize.toFloat() / sampleRate
        val isUtteranceEnd = isPauseAfterSpeech
        val sliceUtteranceId = utteranceId
        val sliceStartSample = bufferStartSample + sliceStart

        if (hasSpeechEnergy) speechDetectedInUtterance = true
        if (isUtteranceEnd) {
            // Speaker paused: retain minimal overlap or clear
            buffer.clear()
            bufferStartSample = totalSamples
            utteranceId++
            speechDetectedInUtterance = false
            peakSpeechRmsInUtterance = 0f
            silenceSampleCount = 0
        } else {
            // Reached max window while speaking: retain overlap tail for continuity
            val keep = minOf(overlapSamples, buffer.size)
            val drop = buffer.size - keep
            LiveTranslatePipeline.dropFront(buffer, drop)
            bufferStartSample += drop
            silenceSampleCount = 0
        }

        return if (hasSpeechEnergy) {
            AudioSlice(
                pcm = slice,
                windowEndWallMs = nowWallMs,
                isUtteranceEnd = isUtteranceEnd,
                durationSec = durationSec,
                utteranceId = sliceUtteranceId,
                startSample = sliceStartSample,
                endSample = sliceStartSample + sliceSize,
                windowEndElapsedMs = audioEndElapsedMs,
            )
        } else {
            null
        }
    }

    fun clear() {
        totalSamples = 0L
        discardBufferedAudio()
    }

    /** Preserve the evidence sample counter while ending a discontinuous utterance. */
    fun discardBufferedAudio() {
        buffer.clear()
        bufferStartSample = totalSamples
        utteranceId++
        speechDetectedInUtterance = false
        peakSpeechRmsInUtterance = 0f
        silenceSampleCount = 0
    }

    private fun computeTrailingSilenceSamples(): Int {
        var trailing = 0
        var pos = buffer.size
        // Absolute silence by default: a stressed syllable must not make quieter
        // words look silent. A relative ratio is retained for baseline replay.
        // Avoid rescanning the entire buffer on every PCM drain.
        if (relativeSilenceRatio > 0f) {
            var scanPos = 0
            while (scanPos + vadFrameSamples <= buffer.size) {
                var sum = 0.0
                for (i in scanPos until scanPos + vadFrameSamples) {
                    val s = buffer[i]
                    sum += (s * s).toDouble()
                }
                peakSpeechRmsInUtterance = maxOf(peakSpeechRmsInUtterance, kotlin.math.sqrt(sum / vadFrameSamples).toFloat())
                scanPos += vadFrameSamples
            }
        }
        val relativeSilenceCutoff = maxOf(silenceRmsThreshold, peakSpeechRmsInUtterance * relativeSilenceRatio)

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
