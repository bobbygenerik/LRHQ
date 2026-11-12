package com.livingroomhq.player.pcm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoundedPcmQueueTest {
    @Test fun overflowKeepsOriginalCaptureTimeOfTheNewestAudio() {
        val queue = BoundedPcmQueue(2)
        queue.offer(byteArrayOf(1), 100L)
        queue.offer(byteArrayOf(2), 200L)
        queue.offer(byteArrayOf(3), 300L)
        val first = queue.pollTimed()!!
        assertEquals(200L, first.capturedAtMs)
        assertEquals(2, first.bytes[0].toInt())
        assertEquals(300L, queue.pollTimed()!!.capturedAtMs)
        assertEquals(1L, queue.droppedCount)
        assertEquals(2L, queue.takenCount)
        assertNull(queue.pollTimed())
    }

    @Test fun byteArrayConsumersAndQueueResetStillWork() {
        val queue = BoundedPcmQueue(2)
        queue.offer(byteArrayOf(7), 100L)
        assertEquals(7, queue.poll()!![0].toInt())
        queue.offer(byteArrayOf(8), 200L)
        queue.clear()
        assertNull(queue.pollTimed())
    }
}
