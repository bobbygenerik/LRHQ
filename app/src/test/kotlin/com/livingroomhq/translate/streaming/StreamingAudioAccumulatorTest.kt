package com.livingroomhq.translate.streaming

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingAudioAccumulatorTest {

    @Test
    fun testBufferAccumulationAndMaxWindowTrigger() {
        val accumulator = StreamingAudioAccumulator(
            sampleRate = 16000,
            minSpeechSec = 1.0f,
            maxWindowSec = 2.0f,
            speechRmsThreshold = 0.001f,
        )

        // Inject 1 second of speech (16000 samples)
        val speechAudio = FloatArray(16000) { 0.05f }
        accumulator.append(speechAudio)
        // Not yet max window, no pause
        assertNull(accumulator.pollSlice())

        // Inject another second (reach 2.0s max window)
        accumulator.append(speechAudio)
        val slice = accumulator.pollSlice()
        assertNotNull(slice)
        assertTrue(slice?.durationSec ?: 0f >= 1.9f)
    }

    @Test
    fun testPauseDetectTriggersBeforeMaxWindow() {
        val accumulator = StreamingAudioAccumulator(
            sampleRate = 16000,
            minSpeechSec = 1.0f,
            maxWindowSec = 3.0f,
            pauseSilenceSec = 0.4f,
            speechRmsThreshold = 0.005f,
            silenceRmsThreshold = 0.001f,
        )

        // Inject 1.2s of speech
        val speech = FloatArray(16000 * 12 / 10) { 0.05f }
        accumulator.append(speech)

        // Inject 0.5s of silence
        val silence = FloatArray(16000 * 5 / 10) { 0.0001f }
        accumulator.append(silence)

        // Should trigger immediately due to silence pause!
        val slice = accumulator.pollSlice()
        assertNotNull(slice)
        assertTrue(slice?.isUtteranceEnd == true)
    }
    @Test fun pauseAfterAMaxWindowEndsTheUtteranceEvenWhenOverlapIsShort() {
        val accumulator = legacyAccumulator()
        accumulator.append(FloatArray(28800) { .05f })
        val first = accumulator.pollSlice()
        assertNotNull(first)
        assertTrue(first?.isUtteranceEnd == false)
        accumulator.append(FloatArray(4800))
        val last = accumulator.pollSlice()
        assertNotNull(last)
        assertTrue(last?.isUtteranceEnd == true)
    }

    @Test fun silenceOnlyTailStillSeparatesTheNextUtterance() {
        val accumulator = legacyAccumulator()
        accumulator.append(FloatArray(28800) { .05f })
        val first = accumulator.pollSlice()!!
        // The PCM drain may coalesce enough silence to trim out all speech.
        accumulator.append(List(64000) { 0f })
        assertNull(accumulator.pollSlice())
        accumulator.append(FloatArray(11200) { .05f })
        accumulator.append(FloatArray(4800))
        val next = accumulator.pollSlice()!!
        assertTrue(next.isUtteranceEnd)
        assertTrue(next.utteranceId != first.utteranceId)
    }

    @Test fun translationKeepsAClauseTogetherAcrossShortPauses() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(FloatArray(32_000) { .05f })
        accumulator.append(FloatArray(2_400))
        assertNull(accumulator.pollSlice())
        accumulator.append(FloatArray(4_800) { .05f })
        accumulator.append(FloatArray(8_000))
        val slice = accumulator.pollSlice()!!
        assertTrue(slice.isUtteranceEnd)
        assertTrue(slice.durationSec < 3f)
        assertTrue(slice.pcm.count { it == .05f } == 36_800)
    }

    @Test fun shortRepliesAreDispatchedAfterThePauseInsteadOfWaitingForTheMaximum() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(FloatArray(4_800) { .05f })
        accumulator.append(FloatArray(8_000))
        val slice = accumulator.pollSlice()!!
        assertTrue(slice.isUtteranceEnd)
        assertTrue(slice.durationSec < 2f)
    }

    @Test fun quieterWordsAfterAStressedSyllableAreNotMistakenForSilence() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(FloatArray(8_000) { .15f })
        accumulator.append(FloatArray(16_000) { .01f })
        assertNull(accumulator.pollSlice())
        accumulator.append(FloatArray(8_000))
        val phrase = accumulator.pollSlice()!!
        assertTrue(phrase.isUtteranceEnd)
        assertTrue(phrase.pcm.count { it == .01f } == 16_000)
    }

    @Test fun continuousSpeechRetainsContextAndExactAudioOffsets() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(FloatArray(56_000) { .05f })
        val first = accumulator.pollSlice()!!
        assertTrue(first.startSample == 0L && first.endSample == 56_000L)
        accumulator.append(FloatArray(48_000) { .05f })
        val next = accumulator.pollSlice()!!
        assertTrue(next.startSample == 48_000L && next.endSample == 104_000L)
        accumulator.clear()
        accumulator.append(FloatArray(56_000) { .05f })
        assertTrue(accumulator.pollSlice()!!.startSample == 0L)
    }

    @Test fun musicUnderSpeechCannotForceTwelveSecondsOfCollection() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(FloatArray(55_680) { .05f })
        assertNull(accumulator.pollSlice())
        accumulator.append(FloatArray(320) { .05f })
        val slice = accumulator.pollSlice()!!
        assertTrue(slice.durationSec == 3.5f)
        assertTrue(!slice.isUtteranceEnd)
    }

    @Test fun drainingOldAudioDoesNotGiveItANewCaptureTime() {
        val accumulator = StreamingAudioAccumulator()
        accumulator.append(List(56_000) { .05f }, 1_000L, 500L)
        val first = accumulator.pollSlice()!!
        assertTrue(first.windowEndWallMs == 1_000L)
        assertTrue(first.windowEndElapsedMs == 500L)
        accumulator.discardBufferedAudio()
        accumulator.append(List(56_000) { .05f }, 2_000L, 1_500L)
        val next = accumulator.pollSlice()!!
        assertTrue(next.startSample == 56_000L)
        assertTrue(next.utteranceId != first.utteranceId)
        assertTrue(next.windowEndElapsedMs == 1_500L)
    }

    private fun legacyAccumulator() = StreamingAudioAccumulator(
        minSpeechSec = .6f, maxWindowSec = 1.8f, pauseSilenceSec = .25f, overlapSec = .4f,
        relativeSilenceRatio = .4f,
    )

}
