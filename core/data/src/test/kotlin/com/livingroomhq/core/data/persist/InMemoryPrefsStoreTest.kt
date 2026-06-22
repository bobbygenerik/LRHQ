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
        assertEquals("Dark", store.theme.first())
        assertEquals("Green", store.accentColor.first())
        assertEquals(true, store.showLivePreview.first())
        assertEquals(true, store.showWeather.first())
        assertEquals(300, store.idleTimeSeconds.first())
        assertEquals("Smooth", store.animations.first())
        assertEquals(true, store.soundEffects.first())
    }

    @Test
    fun `writes are observable`() = runTest {
        val store = InMemoryPrefsStore()
        store.setFavorites(setOf("a", "b"))
        store.setRecents(listOf("b", "a"))
        store.setPlaylistUrl("http://x/playlist.m3u")
        store.setEpgUrl("http://x/guide.xml")
        store.setDefaultPromptDismissed(true)
        store.setTheme("Light")
        store.setAccentColor("Blue")
        store.setShowLivePreview(false)
        store.setShowWeather(false)
        store.setIdleTimeSeconds(600)
        store.setAnimations("Fast")
        store.setSoundEffects(false)

        assertEquals(setOf("a", "b"), store.favorites.first())
        assertEquals(listOf("b", "a"), store.recents.first())
        assertEquals("http://x/playlist.m3u", store.playlistUrl.first())
        assertEquals("http://x/guide.xml", store.epgUrl.first())
        assertEquals(true, store.defaultPromptDismissed.first())
        assertEquals("Light", store.theme.first())
        assertEquals("Blue", store.accentColor.first())
        assertEquals(false, store.showLivePreview.first())
        assertEquals(false, store.showWeather.first())
        assertEquals(600, store.idleTimeSeconds.first())
        assertEquals("Fast", store.animations.first())
        assertEquals(false, store.soundEffects.first())
    }
}
