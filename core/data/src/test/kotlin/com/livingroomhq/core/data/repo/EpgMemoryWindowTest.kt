package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Program
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpgMemoryWindowTest {

    private val now = 1_000_000L
    private val windowEnd = epgMemoryWindowEnd(now)

    @Test
    fun `keeps currently airing programmes even when they started well before the window`() {
        val program = Program(
            channelId = "c1",
            title = "Long slot",
            description = "",
            startMillis = now - 48 * 60 * 60 * 1000L,
            endMillis = now + 60 * 60 * 1000L,
        )
        assertEquals(listOf(program), filterProgramsForMemoryCache(listOf(program), now))
    }

    @Test
    fun `keeps upcoming programmes within the window`() {
        val program = Program(
            channelId = "c1",
            title = "Soon",
            description = "",
            startMillis = now + 2 * 60 * 60 * 1000L,
            endMillis = now + 3 * 60 * 60 * 1000L,
        )
        assertEquals(listOf(program), filterProgramsForMemoryCache(listOf(program), now))
    }

    @Test
    fun `drops programmes that start after the window`() {
        val program = Program(
            channelId = "c1",
            title = "Far future",
            description = "",
            startMillis = now + EPG_MEMORY_WINDOW_HOURS * 60 * 60 * 1000L + 1,
            endMillis = now + EPG_MEMORY_WINDOW_HOURS * 60 * 60 * 1000L + 2,
        )
        assertEquals(emptyList<Program>(), filterProgramsForMemoryCache(listOf(program), now))
    }

    @Test
    fun `drops expired programmes`() {
        val program = Program(
            channelId = "c1",
            title = "Past",
            description = "",
            startMillis = now - 2 * 60 * 60 * 1000L,
            endMillis = now - 1,
        )
        assertEquals(emptyList<Program>(), filterProgramsForMemoryCache(listOf(program), now))
    }

    @Test
    fun `now next works for retained programmes`() {
        val current = Program("c1", "Now", "", now - 1_000L, now + 1_000L)
        val next = Program("c1", "Next", "", now + 1_000L, now + 2_000L)
        val far = Program("c1", "Later", "", windowEnd + 1, windowEnd + 2)
        val grouped = groupProgramsByChannel(filterProgramsForMemoryCache(listOf(current, next, far), now))
        val programs = grouped.getValue("c1")
        val airing = programs.firstOrNull { now in it.startMillis until it.endMillis }
        val upcoming = programs.firstOrNull { it.startMillis >= (airing?.endMillis ?: now) }
        assertEquals("Now", airing?.title)
        assertEquals("Next", upcoming?.title)
        assertNull(programs.firstOrNull { it.title == "Later" })
    }
}
