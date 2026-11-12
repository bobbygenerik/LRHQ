package com.livingroomhq.translate

import com.livingroomhq.translate.whisper.WhisperAudioInput
import org.junit.Assert.*
import org.junit.Test

class WhisperAudioInputTest {
    @Test fun shortRepliesKeepTheirAudioAndGainOnlyTrailingSilence() {
        val reply = FloatArray(8_000) { if (it % 2 == 0) .2f else -.2f }
        val padded = WhisperAudioInput.prepare(reply)
        assertEquals(17_600, padded.size)
        assertArrayEquals(reply, padded.copyOfRange(0, reply.size), 0f)
        assertTrue(padded.drop(reply.size).all { it == 0f })
        assertEquals(8_000, reply.size)
    }

    @Test fun completeWindowsAndEmptyInputAreNotCopiedOrInvented() {
        for (size in listOf(0, 17_600, 96_000)) {
            val pcm = FloatArray(size)
            assertSame(pcm, WhisperAudioInput.prepare(pcm))
        }
    }

    @Test fun exactlyOneSecondMustAlsoCrossTheNativeStopBoundary() {
        assertEquals(17_600, WhisperAudioInput.prepare(FloatArray(16_000)).size)
    }
}
