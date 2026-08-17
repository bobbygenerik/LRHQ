package com.livingroomhq.backdrop

import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePhotosPickerClientTest {

    @Test
    fun testDurationSecondsParsing() {
        assertEquals(3L, "3s".durationSeconds())
        assertEquals(3L, "3.5s".durationSeconds())
        assertEquals(10L, "10s".durationSeconds())
        assertEquals(5L, "5".durationSeconds())
        assertEquals(10L, " 10s ".durationSeconds())
        assertEquals(5L, "".durationSeconds())
        assertEquals(5L, "invalid".durationSeconds())
    }
}
