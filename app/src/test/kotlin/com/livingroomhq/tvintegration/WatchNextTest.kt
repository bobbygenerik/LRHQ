package com.livingroomhq.tvintegration

import com.livingroomhq.core.data.model.MediaItem
import com.livingroomhq.core.data.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextTest {

    private fun item(progress: Float, type: MediaType = MediaType.MOVIE) = MediaItem(
        id = "m1", title = "Signal Lost", type = type,
        description = "desc", runtimeMinutes = 100, watchProgress = progress,
    )

    @Test
    fun `in-progress item maps with duration and position`() {
        val entry = item(0.5f).toWatchNextEntry()!!
        assertEquals("m1", entry.id)
        assertEquals("Signal Lost", entry.title)
        assertEquals(100 * 60_000L, entry.durationMillis)
        assertEquals(50 * 60_000L, entry.lastPositionMillis)
    }

    @Test
    fun `unwatched and finished items are excluded`() {
        assertNull(item(0f).toWatchNextEntry())
        assertNull(item(0.96f).toWatchNextEntry())
    }

    @Test
    fun `music is excluded from watch next`() {
        assertNull(item(0.5f, MediaType.MUSIC).toWatchNextEntry())
    }
}
