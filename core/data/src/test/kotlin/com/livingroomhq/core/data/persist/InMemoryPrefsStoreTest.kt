package com.livingroomhq.core.data.persist

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InMemoryPrefsStoreTest {

    @Test
    fun `defaults are empty`() = runTest {
        val store = InMemoryPrefsStore()
        assertEquals(emptySet<String>(), store.favorites.first())
        assertEquals(emptyList<String>(), store.recents.first())
        assertNull(store.playlistUrl.first())
        assertNull(store.epgUrl.first())
        assertEquals(false, store.defaultPromptDismissed.first())
    }

    @Test
    fun `writes are observable`() = runTest {
        val store = InMemoryPrefsStore()
        store.setFavorites(setOf("a", "b"))
        store.setRecents(listOf("b", "a"))
        store.setPlaylistUrl("http://x/playlist.m3u")
        store.setEpgUrl("http://x/guide.xml")
        store.setDefaultPromptDismissed(true)
        assertEquals(setOf("a", "b"), store.favorites.first())
        assertEquals(listOf("b", "a"), store.recents.first())
        assertEquals("http://x/playlist.m3u", store.playlistUrl.first())
        assertEquals("http://x/guide.xml", store.epgUrl.first())
        assertEquals(true, store.defaultPromptDismissed.first())
    }
}
