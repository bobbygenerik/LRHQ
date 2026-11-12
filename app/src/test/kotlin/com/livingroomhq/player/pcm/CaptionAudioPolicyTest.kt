package com.livingroomhq.player.pcm

import org.junit.Assert.*
import org.junit.Test

class CaptionAudioPolicyTest {
    @Test fun `old cleanup cannot disable PCM decoding for a newer caption session`() {
        val old = Any()
        val replacement = Any()
        try {
            CaptionAudioPolicy.acquire(old)
            CaptionAudioPolicy.acquire(replacement)
            CaptionAudioPolicy.release(old)
            assertTrue(CaptionAudioPolicy.forcePcmDecode())
            CaptionAudioPolicy.release(replacement)
            assertFalse(CaptionAudioPolicy.forcePcmDecode())
        } finally {
            CaptionAudioPolicy.release(replacement)
        }
    }

    @Test fun `repeated cleanup is harmless`() {
        val owner = Any()
        CaptionAudioPolicy.acquire(owner)
        CaptionAudioPolicy.release(owner)
        CaptionAudioPolicy.release(owner)
        assertFalse(CaptionAudioPolicy.forcePcmDecode())
    }
}
