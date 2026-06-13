package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.persist.InMemoryPrefsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersistentChannelRepositoryTest {

    private val playlist = """
        #EXTM3U
        #EXTINF:-1 tvg-id="one" group-title="News",One
        http://s/1.m3u8
        #EXTINF:-1 tvg-id="two" group-title="Sports",Two
        http://s/2.m3u8
    """.trimIndent()

    private val longPlaylist = buildString {
        appendLine("#EXTM3U")
        repeat(10) { index ->
            appendLine("#EXTINF:-1 tvg-id=\"ch$index\" group-title=\"All\",Channel $index")
            appendLine("http://s/$index.m3u8")
        }
    }

    @Test
    fun `starts empty until a playlist is configured`() = runTest(UnconfinedTestDispatcher()) {
        val repo = PersistentChannelRepository(InMemoryPrefsStore(), backgroundScope) { playlist }
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repo.channels.first().map { it.id })
    }

    @Test
    fun `toggleFavorite persists and reflects in channels`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()
        val target = repo.channels.first().first { !it.isFavorite }

        repo.toggleFavorite(target.id)
        advanceUntilIdle()
        assertTrue(target.id in prefs.favorites.first())
        assertTrue(repo.channels.first().first { it.id == target.id }.isFavorite)

        repo.toggleFavorite(target.id)
        advanceUntilIdle()
        assertFalse(target.id in prefs.favorites.first())
    }

    @Test
    fun `markWatched orders recents newest-first, dedupes, caps at 8`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { longPlaylist }
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()
        val ids = repo.channels.first().map { it.id }

        ids.forEach { repo.markWatched(it); advanceUntilIdle() }
        repo.markWatched(ids[5]); advanceUntilIdle()

        val recents = repo.recents.first().map { it.id }
        assertEquals(8, recents.size)
        assertEquals(ids[5], recents.first())
        assertEquals(1, recents.count { it == ids[5] })
    }

    @Test
    fun `loadM3u replaces lineup and persists url`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        advanceUntilIdle()

        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
        assertEquals("http://x/list.m3u", prefs.playlistUrl.first())
    }

    @Test
    fun `restore reloads persisted playlist at startup`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore().apply { setPlaylistUrl("http://x/list.m3u") }
        val repo = PersistentChannelRepository(prefs, backgroundScope) { playlist }
        repo.restore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
    }

    @Test
    fun `empty playlist leaves channels empty`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = PersistentChannelRepository(prefs, backgroundScope) { "" }
        advanceUntilIdle()
        repo.loadM3u("http://x/empty.m3u")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repo.channels.first().map { it.id })
        assertEquals(null, prefs.playlistUrl.first())
    }
}
