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
}
