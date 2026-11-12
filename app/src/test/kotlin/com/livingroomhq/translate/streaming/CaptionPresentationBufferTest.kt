package com.livingroomhq.translate.streaming

import org.junit.Assert.*
import org.junit.Test

class CaptionPresentationBufferTest {
    @Test fun quickReplyDoesNotEraseAThirteenWordCaptionAfterOneSecond() {
        val buffer = CaptionPresentationBuffer()
        val line = "I'm not going to say anything, but I'm going to say something new."
        buffer.publish(line, 0L)
        // At 1020ms, previous line is still active so both lines roll together:
        assertEquals("$line\nYes.", buffer.publish("Yes.", 1_020L))
        // Once the first line expires (> 5000ms from start, i.e. 5001ms), only "Yes." remains (published at 1020ms, expires at ~4700ms):
        assertEquals("Yes.", buffer.display(4_500L))
        assertNull(buffer.display(5_600L))
    }

    @Test fun longFinalCaptionExpiresCleanlyWithoutLingering() {
        val buffer = CaptionPresentationBuffer()
        buffer.publish((1..24).joinToString(" ") { "word$it" }, 0L)
        assertNotNull(buffer.display(4_500L))
        assertNull(buffer.display(5_100L))
    }

    @Test fun sessionResetRemovesAllPreviousDialogueAndDuplicatesDoNotStack() {
        val buffer = CaptionPresentationBuffer()
        buffer.publish("Good morning.", 0L)
        assertEquals("Good morning.", buffer.publish("Good morning.", 500L))
        buffer.clear()
        assertNull(buffer.display(501L))
        assertEquals("Hello.", buffer.publish("Hello.", 502L))
    }
}
