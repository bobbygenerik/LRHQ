package com.livingroomhq.screens.live

import com.livingroomhq.core.data.model.Program
import org.junit.Assert.*
import org.junit.Test

class GuideTimelineTest {
    private fun program(title: String, start: Long, end: Long) = Program("ch", title, "", start, end)

    @Test fun `clips programs to viewport and preserves real schedule gaps`() {
        val slots = guideSlots(listOf(program("A", -10, 20), program("B", 40, 120)), 0, 100)
        assertEquals(listOf(0L to 20L, 20L to 40L, 40L to 100L), slots.map { it.startMillis to it.endMillis })
        assertNull(slots[1].program)
        assertEquals("B", guideSlotAt(slots, 40)?.program?.title)
        assertNull(guideSlotAt(slots, 100))
    }

    @Test fun `short shows retain duration and malformed overlapping listings do not stack`() {
        val slots = guideSlots(listOf(program("B", 10, 40), program("A", 0, 20), program("Bad", 50, 40), program("Short", 40, 41)), 0, 60)
        assertEquals(listOf(0L to 20L, 20L to 40L, 40L to 41L, 41L to 60L), slots.map { it.startMillis to it.endMillis })
        assertEquals(listOf("A", "B", "Short", null), slots.map { it.program?.title })
    }

    @Test fun `up down uses broadcast time across channels with different durations`() {
        val a = guideSlots(listOf(program("News", 0, 60), program("Movie", 60, 180)), 0, 180)
        val b = guideSlots(listOf(program("Sport", 0, 90), program("Weather", 90, 180)), 0, 180)
        val time = moveGuideTime(a, 30, 1)
        assertEquals(60L, time)
        assertEquals("Sport", guideSlotAt(b, time)?.program?.title)
        assertEquals("Movie", guideSlotAt(a, time)?.program?.title)
        assertEquals("News", guideSlotAt(a, moveGuideTime(a, time, -1))?.program?.title)
    }

    @Test fun `empty schedule remains navigable within the horizon`() {
        val slots = guideSlots(emptyList(), 0, GUIDE_HORIZON)
        assertEquals(GUIDE_HALF_HOUR, moveGuideTime(slots, 0, 1))
        assertEquals(0L, moveGuideTime(slots, 0, -1))
        assertEquals(GUIDE_HORIZON - 1, moveGuideTime(slots, GUIDE_HORIZON - 1, 1))
    }

    @Test fun `timeline window follows cursor without changing the shared time scale`() {
        assertEquals(0L, guideWindowFor(GUIDE_HALF_HOUR, 0, 0))
        assertEquals(GUIDE_HALF_HOUR, guideWindowFor(GUIDE_VIEW_DURATION - 1, 0, 0))
        assertEquals(2 * GUIDE_HALF_HOUR, guideWindowFor(GUIDE_VIEW_DURATION, 0, 0))
        assertEquals(0L, guideWindowFor(0, GUIDE_HALF_HOUR, 0))
        assertEquals(GUIDE_HORIZON - GUIDE_VIEW_DURATION, guideWindowFor(GUIDE_HORIZON - 1, 0, 0))
    }

    @Test fun `half hour ruler remains aligned across midnight`() {
        val midnight = 86_400_000L
        assertEquals(midnight - GUIDE_HALF_HOUR, guideStart(midnight - 1))
        assertEquals(midnight, guideStart(midnight + 100))
    }
}
