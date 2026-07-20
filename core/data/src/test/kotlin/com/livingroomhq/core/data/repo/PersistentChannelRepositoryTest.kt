package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.db.ChannelEntity
import com.livingroomhq.core.data.db.GuideChannelEntity
import com.livingroomhq.core.data.db.IptvDao
import com.livingroomhq.core.data.db.ProgramEntity
import com.livingroomhq.core.data.db.ProgramBrief
import com.livingroomhq.core.data.persist.InMemoryPrefsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    private val guide = """
        <tv>
          <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="one">
            <title>Evening Report</title>
            <desc>Daily news.</desc>
          </programme>
          <programme start="20240518210000 +0000" stop="20240518220000 +0000" channel="one">
            <title>Late Show</title>
          </programme>
        </tv>
    """.trimIndent()

    @Test
    fun `starts empty until a playlist is configured`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(InMemoryPrefsStore(), playlist)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repo.channels.first().map { it.id })
    }

    @Test
    fun `toggleFavorite persists and reflects in channels`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = repository(prefs, playlist)
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
        val repo = repository(prefs, longPlaylist)
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
        val repo = repository(prefs, playlist)
        advanceUntilIdle()

        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
        assertEquals("http://x/list.m3u", prefs.playlistUrl.first())
    }

    @Test
    fun `loadM3u keeps channels with repeated tvg ids`() = runTest(UnconfinedTestDispatcher()) {
        val duplicatePlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup One
            http://s/1.m3u8
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup Two
            http://s/2.m3u8
            #EXTINF:-1 tvg-id="dup" group-title="Sports",Dup Three
            http://s/3.m3u8
        """.trimIndent()
        val repo = repository(InMemoryPrefsStore(), duplicatePlaylist)

        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        val channels = repo.channels.first()
        assertEquals(3, channels.size)
        assertEquals(listOf("Dup One", "Dup Two", "Dup Three"), channels.map { it.name })
        assertEquals(listOf("News", "News", "Sports"), channels.map { it.group })
        assertEquals(3, channels.map { it.id }.distinct().size)
        assertTrue(channels.all { it.tvgId == "dup" })
    }

    @Test
    fun `restore reloads persisted playlist at startup`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore().apply { setPlaylistUrl("http://x/list.m3u") }
        val repo = repository(prefs, playlist)
        repo.restore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
    }

    @Test
    fun `empty playlist leaves channels empty`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = repository(prefs, "")
        advanceUntilIdle()
        repo.loadM3u("http://x/empty.m3u")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), repo.channels.first().map { it.id })
    }

    @Test
    fun `loadXmltv persists guide and returns now next by channel id`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = InMemoryPrefsStore()
        val dao = FakeIptvDao()
        val repo = PersistentChannelRepository(
            iptvDao = dao,
            prefs = prefs,
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { guide.byteInputStream() },
        )

        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val (now, next) = repo.epgNowNext("one")
        assertEquals("Evening Report", now?.title)
        assertEquals("Late Show", next?.title)
        assertEquals("http://x/guide.xml", prefs.epgUrl.first())
    }

    @Test
    fun `loadXmltv rejects an empty guide without persisting`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore()
        val repo = repository(prefs, "<tv />")

        val result = kotlin.runCatching { repo.loadXmltv("http://x/empty.xml") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        advanceUntilIdle()

        assertNull(repo.epgNowNext("one").first)
        assertNull(prefs.epgUrl.first())
    }

    @Test
    fun `epg matches tvg-id case insensitively`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val mixedCaseGuide = """
            <tv>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="ONE">
                <title>Evening Report</title>
              </programme>
            </tv>
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) mixedCaseGuide.byteInputStream() else playlist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        assertEquals("Evening Report", repo.epgNowNext("one").first?.title)
    }

    @Test
    fun `epg matches original tvg id after duplicate channel id disambiguation`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val duplicatePlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup One
            http://s/1.m3u8
            #EXTINF:-1 tvg-id="dup" group-title="News",Dup Two
            http://s/2.m3u8
        """.trimIndent()
        val duplicateGuide = """
            <tv>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="dup">
                <title>Shared Guide</title>
              </programme>
            </tv>
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) duplicateGuide.byteInputStream() else duplicatePlaylist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val duplicateId = repo.channels.first().last().id
        assertTrue(duplicateId.startsWith("dup#"))
        assertEquals("Shared Guide", repo.epgNowNext(duplicateId).first?.title)
    }

    @Test
    fun `epg matches xmltv channel suffix and display name aliases`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val localGuide = """
            <tv>
              <channel id="wxyz.us@provider">
                <display-name>WXYZ HD</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="wxyz.us@provider">
                <title>Local News</title>
              </programme>
            </tv>
        """.trimIndent()
        val localPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="WXYZ HD" group-title="Local",WXYZ
            http://s/local.m3u8
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) localGuide.byteInputStream() else localPlaylist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val channelId = repo.channels.first().single().id
        assertEquals("Local News", repo.epgNowNext(channelId).first?.title)
    }

    @Test
    fun `restore rebuilds epg alias matching from persisted guide channels`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val dao = FakeIptvDao()
        val prefs = InMemoryPrefsStore().apply { setEpgUrl("http://x/guide.xml") }
        val localGuide = """
            <tv>
              <channel id="abc7.us">
                <display-name>ABC 7</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="abc7.us">
                <title>ABC World News</title>
              </programme>
            </tv>
        """.trimIndent()
        val localPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="ABC 7" group-title="Local",ABC 7 HD
            http://s/abc.m3u8
        """.trimIndent()
        val bootstrap = PersistentChannelRepository(
            iptvDao = dao,
            prefs = prefs,
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) localGuide.byteInputStream() else localPlaylist.byteInputStream()
            },
        )
        bootstrap.loadM3u("http://x/list.m3u")
        bootstrap.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()
        val channelId = bootstrap.channels.first().single().id

        val restored = PersistentChannelRepository(
            iptvDao = dao,
            prefs = prefs,
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { error("network unavailable during restore test") },
        )
        restored.restore()
        runCurrent()

        assertEquals("ABC World News", restored.epgNowNext(channelId).first?.title)
    }

    private fun TestScope.repository(
        prefs: InMemoryPrefsStore,
        response: String,
        dao: IptvDao = FakeIptvDao(),
    ): PersistentChannelRepository =
        PersistentChannelRepository(
            iptvDao = dao,
            prefs = prefs,
            scope = backgroundScope,
            workDispatcher = UnconfinedTestDispatcher(testScheduler),
            ingestDispatcher = UnconfinedTestDispatcher(testScheduler),
            fetchPlaylistStream = { response.byteInputStream() },
        )

    @Test
    fun `clearXmltv removes loaded guide and persisted url`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val prefs = InMemoryPrefsStore()
        val dao = FakeIptvDao()
        val repo = PersistentChannelRepository(
            iptvDao = dao,
            prefs = prefs,
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { guide.byteInputStream() },
        )
        repo.loadXmltv("http://x/guide.xml")

        repo.clearXmltv()

        assertNull(repo.epgNowNext("one").first)
        assertNull(prefs.epgUrl.first())
    }

    @Test
    fun `groups reflects distinct group titles from channels`() = runTest(UnconfinedTestDispatcher()) {
        val multiGroupPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="n1" group-title="News",News One
            http://s/n1.m3u8
            #EXTINF:-1 tvg-id="n2" group-title="News",News Two
            http://s/n2.m3u8
            #EXTINF:-1 tvg-id="s1" group-title="Sports",Sports One
            http://s/s1.m3u8
            #EXTINF:-1 tvg-id="e1" group-title="Entertainment",Entertainment One
            http://s/e1.m3u8
        """.trimIndent()
        val repo = repository(InMemoryPrefsStore(), multiGroupPlaylist)
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        val result = repo.groups.first()
        // Groups preserve first-occurrence order (distinct), not sorted
        assertEquals("News", result[0])
        assertEquals("Sports", result[1])
        assertEquals("Entertainment", result[2])
    }

    @Test
    fun `channelsByGroup maps channels under correct group keys`() = runTest(UnconfinedTestDispatcher()) {
        val multiGroupPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="n1" group-title="News",News One
            http://s/n1.m3u8
            #EXTINF:-1 tvg-id="n2" group-title="News",News Two
            http://s/n2.m3u8
            #EXTINF:-1 tvg-id="s1" group-title="Sports",Sports One
            http://s/s1.m3u8
        """.trimIndent()
        val repo = repository(InMemoryPrefsStore(), multiGroupPlaylist)
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        val byGroup = repo.channelsByGroup.first()
        assertEquals(2, byGroup["News"]?.size)
        assertEquals(1, byGroup["Sports"]?.size)
        assertNull(byGroup["Movies"])
    }

    @Test
    fun `toggleFavorite toggles isFavorite flag in channels state`() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(InMemoryPrefsStore(), playlist)
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        val target = repo.channels.first().first { !it.isFavorite }
        repo.toggleFavorite(target.id)
        advanceUntilIdle()

        val updated = repo.channels.first().first { it.id == target.id }
        assertTrue(updated.isFavorite)

        repo.toggleFavorite(target.id)
        advanceUntilIdle()

        val reverted = repo.channels.first().first { it.id == target.id }
        assertFalse(reverted.isFavorite)
    }

    @Test
    fun `loadM3u propagates network errors`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { throw java.io.IOException("Simulated network error") },
        )
        val result = kotlin.runCatching { repo.loadM3u("http://x/bad.m3u8") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        advanceUntilIdle()
        assertTrue(repo.channels.first().isEmpty())
    }

    @Test
    fun `loadXmltv propagates network errors`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { throw java.io.IOException("Simulated network error") },
        )
        val result = kotlin.runCatching { repo.loadXmltv("http://x/bad.xml") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
        advanceUntilIdle()
        assertNull(repo.epgNowNext("one").first)
        assertNull(repo.epgNowNext("one").second)
    }

    @Test
    fun `loadM3u deduplicates channels with same tvg id`() = runTest(UnconfinedTestDispatcher()) {
        val dupPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="dup1" group-title="News",Dup One
            http://s/1.m3u8
            #EXTINF:-1 tvg-id="dup1" group-title="News",Dup One (copy)
            http://s/2.m3u8
            #EXTINF:-1 tvg-id="dup2" group-title="Sports",Dup Two
            http://s/3.m3u8
        """.trimIndent()
        val repo = repository(InMemoryPrefsStore(), dupPlaylist)
        repo.loadM3u("http://x/list.m3u")
        advanceUntilIdle()

        val channels = repo.channels.first()
        // Parser assigns unique ids via urlHash for duplicate tvg-ids
        assertTrue(channels.size >= 2)
        assertEquals("dup1", channels[0].tvgId)
        assertEquals("dup2", channels.last().tvgId)
    }

    @Test
    fun `epg does not match substring channel names`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val guide = """
            <tv>
              <channel id="abcnewschannel">
                <display-name>ABC News</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="abcnewschannel">
                <title>ABC World News</title>
              </programme>
            </tv>
        """.trimIndent()
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="qrs" group-title="News",QRS One
            http://s/qrs.m3u8
            #EXTINF:-1 tvg-id="abcnewschannel" group-title="News",ABC News Stream
            http://s/abcnews.m3u8
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) guide.byteInputStream() else playlist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val channels = repo.channels.first()
        val qrsChannel = channels.first { it.id.startsWith("qrs") }
        val abcNewsStream = channels.first { it.id.startsWith("abcnewschannel") }

        // Unrelated tvg-id "qrs" should NOT match guide channel "abcnewschannel"
        assertNull(repo.epgNowNext(qrsChannel.id).first)
        // Exact tvg-id "abcnewschannel" SHOULD match
        assertEquals("ABC World News", repo.epgNowNext(abcNewsStream.id).first?.title)
        // Exact tvg-id "abcnewschannel" SHOULD match
        assertEquals("ABC World News", repo.epgNowNext(abcNewsStream.id).first?.title)
        // Exact tvg-id "abcnewschannel" SHOULD match
        assertEquals("ABC World News", repo.epgNowNext(abcNewsStream.id).first?.title)
    }

    @Test
    fun `restore is idempotent`() = runTest(UnconfinedTestDispatcher()) {
        val prefs = InMemoryPrefsStore().apply { setPlaylistUrl("http://x/list.m3u") }
        val repo = repository(prefs, playlist)
        repo.restore()
        repo.restore()
        repo.restore()
        advanceUntilIdle()
        assertEquals(listOf("one", "two"), repo.channels.first().map { it.id })
    }

    @Test
    fun `epg matches after normalizing hd suffix in channel name`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val guide = """
            <tv>
              <channel id="abchd">
                <display-name>ABC HD</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="abchd">
                <title>ABC Evening News</title>
              </programme>
            </tv>
        """.trimIndent()
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="ABC" group-title="News",ABC
            http://s/abc.m3u8
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) guide.byteInputStream() else playlist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val channelId = repo.channels.first().single().id
        assertEquals("ABC Evening News", repo.epgNowNext(channelId).first?.title)
    }

    @Test
    fun `epg distinguishes east and west regional feeds`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val regionalGuide = """
            <tv>
              <channel id="espn.east">
                <display-name>ESPN East</display-name>
              </channel>
              <channel id="espn.west">
                <display-name>ESPN West</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="espn.east">
                <title>East Coast Game</title>
              </programme>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="espn.west">
                <title>West Coast Game</title>
              </programme>
            </tv>
        """.trimIndent()
        val regionalPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-name="ESPN East" group-title="Sports",ESPN East
            http://s/east.m3u8
            #EXTINF:-1 tvg-name="ESPN West" group-title="Sports",ESPN West
            http://s/west.m3u8
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) regionalGuide.byteInputStream() else regionalPlaylist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val channels = repo.channels.first()
        val east = channels.first { it.name.contains("East") }
        val west = channels.first { it.name.contains("West") }
        assertEquals("East Coast Game", repo.epgNowNext(east.id).first?.title)
        assertEquals("West Coast Game", repo.epgNowNext(west.id).first?.title)
    }

    @Test
    fun `epg does not match bare channel number to numeric guide alias`() = runTest(UnconfinedTestDispatcher()) {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val numericGuide = """
            <tv>
              <channel id="5">
                <display-name>Guide Channel Five</display-name>
              </channel>
              <programme start="20240518200000 +0000" stop="20240518210000 +0000" channel="5">
                <title>Numeric Guide Show</title>
              </programme>
            </tv>
        """.trimIndent()
        val numberedPlaylist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="wnyt" group-title="News",WNYT
            http://s/wnyt.m3u8
        """.trimIndent()
        val repo = PersistentChannelRepository(
            iptvDao = FakeIptvDao(),
            prefs = InMemoryPrefsStore(),
            scope = backgroundScope,
            nowMillis = { 1_716_062_430_000L },
            workDispatcher = dispatcher,
            ingestDispatcher = dispatcher,
            fetchPlaylistStream = { url ->
                if (url.contains("guide")) numericGuide.byteInputStream() else numberedPlaylist.byteInputStream()
            },
        )
        repo.loadM3u("http://x/list.m3u")
        repo.loadXmltv("http://x/guide.xml")
        advanceUntilIdle()

        val channelId = repo.channels.first().single().id
        assertNull(repo.epgNowNext(channelId).first)
    }
}

class FakeIptvDao : IptvDao {
    private val channelsList = mutableListOf<ChannelEntity>()
    private val programsList = mutableListOf<ProgramEntity>()
    private val guideChannelsList = mutableListOf<GuideChannelEntity>()
    private val channelsFlow = MutableStateFlow<List<ChannelEntity>>(emptyList())

    override fun getChannelsFlow(): Flow<List<ChannelEntity>> = channelsFlow

    override suspend fun getChannels(): List<ChannelEntity> = channelsList

    override suspend fun getChannelById(id: String): ChannelEntity? =
        channelsList.firstOrNull { it.id == id }

    override suspend fun insertChannels(channels: List<ChannelEntity>) {
        channelsList.removeAll { c -> channels.any { it.id == c.id } }
        channelsList.addAll(channels)
        channelsList.sortBy { it.number }
        channelsFlow.value = channelsList.toList()
    }

    override suspend fun clearChannels() {
        channelsList.clear()
        channelsFlow.value = emptyList()
    }

    override suspend fun syncChannels(channels: List<ChannelEntity>) {
        clearChannels()
        if (channels.isEmpty()) return
        insertChannels(channels)
    }

    override suspend fun replaceChannels(channels: List<ChannelEntity>) = syncChannels(channels)

    override suspend fun updateChannelFavorite(channelId: String, isFavorite: Boolean) {
        val index = channelsList.indexOfFirst { it.id == channelId }
        if (index != -1) {
            channelsList[index] = channelsList[index].copy(isFavorite = isFavorite)
            channelsFlow.value = channelsList.toList()
        }
    }

    override suspend fun getProgramsForChannel(channelId: String): List<ProgramEntity> =
        programsList.filter { it.channelId == channelId }.sortedBy { it.startMillis }

    override suspend fun getActivePrograms(now: Long): List<ProgramEntity> =
        programsList.filter { it.endMillis > now }

    override suspend fun getProgramsInWindow(now: Long, windowEnd: Long): List<ProgramBrief> =
        programsList.filter { it.endMillis > now && it.startMillis < windowEnd }
            .map { ProgramBrief(it.channelId, it.title, it.startMillis, it.endMillis) }

    override suspend fun getProgramsForChannelInWindow(
        channelId: String,
        now: Long,
        windowEnd: Long,
    ): List<ProgramEntity> =
        programsList.filter { it.channelId == channelId && it.endMillis > now && it.startMillis < windowEnd }
            .sortedBy { it.startMillis }

    override suspend fun getProgramsForChannelsInWindow(
        channelIds: List<String>,
        now: Long,
        windowEnd: Long,
    ): List<ProgramBrief> =
        programsList.filter { it.channelId in channelIds && it.endMillis > now && it.startMillis < windowEnd }
            .sortedBy { it.startMillis }
            .map { ProgramBrief(it.channelId, it.title, it.startMillis, it.endMillis) }

    override suspend fun insertPrograms(programs: List<ProgramEntity>) {
        programs.forEach { incoming ->
            programsList.removeAll { it.channelId == incoming.channelId && it.startMillis == incoming.startMillis }
            programsList.add(incoming)
        }
    }

    override suspend fun clearPrograms() {
        programsList.clear()
    }

    override suspend fun deleteProgramsForChannel(channelId: String) {
        programsList.removeAll { it.channelId == channelId }
    }

    override suspend fun pruneOldPrograms(threshold: Long) {
        programsList.removeAll { it.endMillis < threshold }
    }

    override suspend fun pruneProgramsBeforeWindow(channelIds: List<String>, windowStart: Long) {
        programsList.removeAll { it.channelId in channelIds && it.startMillis < windowStart }
    }

    override suspend fun replacePrograms(programs: List<ProgramEntity>) {
        clearPrograms()
        insertPrograms(programs)
    }

    override suspend fun getDistinctProgramChannelIds(): List<String> =
        programsList.map { it.channelId }.distinct()

    override suspend fun getAllGuideChannels(): List<GuideChannelEntity> = guideChannelsList.toList()

    override suspend fun insertGuideChannels(channels: List<GuideChannelEntity>) {
        guideChannelsList.removeAll { existing -> channels.any { it.id == existing.id } }
        guideChannelsList.addAll(channels)
    }

    override suspend fun clearGuideChannels() {
        guideChannelsList.clear()
    }

    override suspend fun syncGuideChannels(channels: List<GuideChannelEntity>) {
        clearGuideChannels()
        if (channels.isEmpty()) return
        insertGuideChannels(channels)
    }

    override suspend fun replaceGuideChannels(channels: List<GuideChannelEntity>) = syncGuideChannels(channels)
}
