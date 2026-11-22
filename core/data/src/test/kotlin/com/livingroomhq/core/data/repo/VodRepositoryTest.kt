package com.livingroomhq.core.data.repo

import android.content.ContextWrapper
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import com.livingroomhq.core.data.persist.InMemoryPrefsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VodRepositoryTest {

    private class FakeChannelRepository(initialChannels: List<Channel> = emptyList()) : ChannelRepository {
        override val channels = MutableStateFlow(initialChannels)
        override val recents = MutableStateFlow<List<Channel>>(emptyList())
        override val groups = MutableStateFlow<List<String>>(emptyList())
        override val channelsByGroup = MutableStateFlow<Map<String, List<Channel>>>(emptyMap())
        override val epgRevision = MutableStateFlow(0L)
        override fun epgNowNext(channelId: String) = Pair(null, null)
        override fun epgSchedule(channelId: String, startMillis: Long, endMillis: Long) = emptyList<Program>()
        override fun markWatched(channelId: String) {}
        override fun toggleFavorite(channelId: String) {}
        override suspend fun loadM3u(playlistUrl: String) {}
        override suspend fun loadXmltv(epgUrl: String) {}
        override suspend fun clearXmltv() {}
        override suspend fun runMaintenance() {}
        override suspend fun fetchEpgDetails(channelId: String) {}
        override suspend fun prefetchEpgForChannels(channelIds: List<String>) {}
        override suspend fun computeOnNowRail(excludeChannelId: String?, resultLimit: Int) = emptyList<Pair<Channel, Program>>()
    }

    @Test
    fun emptyChannelsYieldsNoCategoriesAndNoSampleCards() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val dummyContext = object : ContextWrapper(null) {}
        val prefs = InMemoryPrefsStore()
        val fakeChannelRepo = FakeChannelRepository(emptyList())

        val repo = DefaultVodRepository(dummyContext, prefs, fakeChannelRepo, scope, testDispatcher)
        val categories = repo.categories.first()

        assertTrue("Expected no sample cards or categories when channels are empty", categories.isEmpty())
    }

    @Test
    fun detectsIptvVodChannelsAndExcludesLiveAndTemp() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val dummyContext = object : ContextWrapper(null) {}
        val prefs = InMemoryPrefsStore()

        val sampleChannels = listOf(
            Channel(
                id = "live_1",
                number = 1,
                name = "Movie Premieres",
                group = "USA",
                streamUrl = "http://example.com/live/user/pass/101.ts",
            ),
            Channel(
                id = "temp_1",
                number = 2,
                name = "Unreleased Placeholder (2026)",
                group = "Temp",
                streamUrl = "http://example.com/movie/user/pass/999.mkv",
            ),
            Channel(
                id = "vod_1",
                number = 3,
                name = "Inception (2010)",
                group = "Action Movies",
                streamUrl = "http://example.com/movie/user/pass/123.mp4",
                logoUrl = "http://example.com/inception.jpg",
            ),
            Channel(
                id = "vod_2",
                number = 4,
                name = "Stranger Things S01E01",
                group = "Netflix Series",
                streamUrl = "http://example.com/series/user/pass/456.mkv",
            ),
        )

        val fakeChannelRepo = FakeChannelRepository(sampleChannels)
        val repo = DefaultVodRepository(dummyContext, prefs, fakeChannelRepo, scope, testDispatcher)
        val categories = repo.categories.first()

        assertEquals(2, categories.size)
        val actionCategory = categories.first { it.name == "Action Movies" }
        assertEquals(1, actionCategory.items.size)
        assertEquals("Inception", actionCategory.items[0].title)
        assertEquals(2010, actionCategory.items[0].year)

        val seriesCategory = categories.first { it.name == "Netflix Series" }
        assertEquals(1, seriesCategory.items.size)
        assertEquals("Stranger Things S01E01", seriesCategory.items[0].title)
    }

    @Test
    fun discoversUpstreamXtreamEndpointAndParsesVodCatalog() = runTest {
        val liveChannels = listOf(
            Channel(
                id = "ch1",
                number = 1,
                name = "ABC",
                group = "USA",
                streamUrl = "https://iptv.example.test/live/user_a/pass_a/78645.ts",
            ),
        )
        val endpoints = DefaultVodRepository.discoverXtreamEndpoints(
            liveChannels = liveChannels,
            epgUrl = "https://iptv.example.test/xmltv.php?username=user_a&password=pass_a",
            playlistUrl = "https://alt.example.test/get.php?username=user_b&password=pass_a&type=m3u_plus",
        )
        assertEquals(2, endpoints.size)
        assertEquals("https://iptv.example.test", endpoints[0].baseUrl)
        assertEquals("user_a", endpoints[0].username)

        val catsJson = """
            [
              {"category_id":"874","category_name":"4K Movies"},
              {"category_id":"338","category_name":"Temp"}
            ]
        """.trimIndent()
        val streamsJson = """
            [
              {"stream_id":27871,"name":"4K The Fantastic 4: First Steps (2025)","stream_icon":"https://img/f4.jpg","rating":7.4,"added":"1700000100","category_id":"874","container_extension":"mkv"},
              {"stream_id":99999,"name":"Dead Placeholder (2026)","stream_icon":"","rating":0,"added":"1700000200","category_id":"338","container_extension":"mp4"}
            ]
        """.trimIndent()

        val parsed = DefaultVodRepository.parseXtreamVodCatalog(endpoints[0], catsJson, streamsJson)
        assertEquals(1, parsed.size)
        assertEquals("4K Movies", parsed[0].name)
        assertEquals(1, parsed[0].items.size)
        val item = parsed[0].items[0]
        assertEquals("4K The Fantastic 4: First Steps", item.title)
        assertEquals(2025, item.year)
        assertEquals("★ 7.4", item.rating)
        assertEquals("https://iptv.example.test/movie/user_a/pass_a/27871.mkv", item.streamUrl)
    }

    @Test
    fun parsesXtreamSeriesCatalogAndSeriesDetail() = runTest {
        val endpoint = XtreamEndpoint(
            baseUrl = "https://iptv.example.test",
            username = "user_a",
            password = "test_password",
        )

        val seriesCatsJson = """
            [
              {"category_id":"835","category_name":"Netflix"},
              {"category_id":"837","category_name":"Disney+"}
            ]
        """.trimIndent()

        val seriesJson = """
            [
              {
                "series_id": 501,
                "name": "Stranger Things (2016)",
                "cover": "https://img/st.jpg",
                "plot": "A group of kids uncover government secrets.",
                "cast": "Winona Ryder, David Harbour",
                "director": "The Duffer Brothers",
                "genre": "Sci-Fi",
                "releaseDate": "2016-07-15",
                "last_modified": "1700000000",
                "rating": "8.7",
                "rating_5based": 4.4,
                "backdrop_path": ["https://img/st_bg.jpg"],
                "category_id": "835"
              }
            ]
        """.trimIndent()

        val parsedCategories = DefaultVodRepository.parseXtreamSeriesCatalog(endpoint, seriesCatsJson, seriesJson)
        assertEquals(1, parsedCategories.size)
        val netflix = parsedCategories[0]
        assertEquals("Netflix", netflix.name)
        assertEquals(1, netflix.series.size)
        val series = netflix.series[0]
        assertEquals(501, series.seriesId)
        assertEquals("Stranger Things", series.title)
        assertEquals(2016, series.year)
        assertEquals("★ 8.7", series.rating)
        assertEquals("https://img/st.jpg", series.posterUrl)
        assertEquals("https://img/st_bg.jpg", series.backdropUrl)

        val detailJson = """
            {
              "seasons": [{"season_number": 1}],
              "info": {
                "name": "Stranger Things",
                "cover": "https://img/st.jpg",
                "plot": "A group of kids uncover government secrets."
              },
              "episodes": {
                "1": [
                  {
                    "id": "1001",
                    "episode_num": 1,
                    "title": "Chapter One: The Vanishing of Will Byers",
                    "container_extension": "mkv",
                    "info": {
                      "duration_secs": 2880,
                      "plot": "Will vanishes on the way home."
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val detail = DefaultVodRepository.parseSeriesDetail(endpoint, series, detailJson)
        assertNotNull(detail)
        assertEquals(listOf(1), detail!!.seasons)
        val eps = detail.episodesBySeason[1]
        assertNotNull(eps)
        assertEquals(1, eps!!.size)
        val ep = eps[0]
        assertEquals(1, ep.episodeNum)
        assertEquals("Chapter One: The Vanishing of Will Byers", ep.title)
        assertEquals(48, ep.durationMinutes)
        assertEquals("https://iptv.example.test/series/user_a/test_password/1001.mkv", ep.streamUrl)

        // Flat array format: "episodes": [ { ... } ]
        val flatDetailJson = """
            {
              "seasons": [{"season_number": 1}],
              "info": {
                "name": "Stranger Things"
              },
              "episodes": [
                {
                  "id": "1002",
                  "episode_num": 2,
                  "season": 1,
                  "title": "Chapter Two: The Weirdo on Maple Street",
                  "container_extension": "mp4",
                  "info": {
                    "duration_secs": 3300,
                    "plot": "Mike hides Eleven in his room."
                  }
                }
              ]
            }
        """.trimIndent()
        val flatDetail = DefaultVodRepository.parseSeriesDetail(endpoint, series, flatDetailJson)
        assertNotNull(flatDetail)
        assertEquals(listOf(1), flatDetail!!.seasons)
        val flatEps = flatDetail.episodesBySeason[1]
        assertNotNull(flatEps)
        assertEquals(1, flatEps!!.size)
        assertEquals("Chapter Two: The Weirdo on Maple Street", flatEps[0].title)
        assertEquals(55, flatEps[0].durationMinutes)
        assertEquals("https://iptv.example.test/series/user_a/test_password/1002.mp4", flatEps[0].streamUrl)
    }

    @Test
    fun roundTripStreamingVodDiskCache() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val tempDir = java.nio.file.Files.createTempDirectory("vod_cache_test").toFile()
        val dummyContext = object : ContextWrapper(null) {
            override fun getCacheDir(): java.io.File = tempDir
        }
        val prefs = InMemoryPrefsStore()
        val fakeChannelRepo = FakeChannelRepository(emptyList())

        val repo = DefaultVodRepository(dummyContext, prefs, fakeChannelRepo, scope, testDispatcher)

        val sampleMovies = listOf(
            VodCategory(
                id = "cat_action",
                name = "Action Movies",
                items = listOf(
                    VodItem(
                        id = "m1",
                        title = "Inception",
                        description = "Dream within a dream",
                        posterUrl = "https://img/poster.jpg",
                        backdropUrl = "https://img/backdrop.jpg",
                        year = 2010,
                        rating = "PG-13",
                        genre = "Action",
                        durationMinutes = 148,
                        streamUrl = "http://streams/1.mp4",
                    ),
                ),
            ),
        )

        repo.saveDiskCache(sampleMovies)
        val loaded = repo.loadDiskCache()

        assertNotNull(loaded)
        assertEquals(1, loaded!!.size)
        assertEquals("cat_action", loaded[0].id)
        assertEquals("Action Movies", loaded[0].name)
        assertEquals(1, loaded[0].items.size)
        val item = loaded[0].items[0]
        assertEquals("m1", item.id)
        assertEquals("Inception", item.title)
        assertEquals(2010, item.year)
        assertEquals("PG-13", item.rating)
        assertEquals("http://streams/1.mp4", item.streamUrl)

        val sampleSeries = listOf(
            SeriesCategory(
                id = "cat_drama",
                name = "Drama Series",
                series = listOf(
                    SeriesItem(
                        id = "s1",
                        seriesId = 42,
                        title = "Breaking Bad",
                        plot = "Teacher turns to crime",
                        posterUrl = "https://img/bb.jpg",
                        backdropUrl = "https://img/bb_bg.jpg",
                        year = 2008,
                        rating = "TV-MA",
                        genre = "Drama",
                        categoryId = "cat_drama",
                        categoryName = "Drama Series",
                    ),
                ),
            ),
        )

        repo.saveSeriesDiskCache(sampleSeries)
        val loadedSeries = repo.loadSeriesDiskCache()

        assertNotNull(loadedSeries)
        assertEquals(1, loadedSeries!!.size)
        assertEquals("cat_drama", loadedSeries[0].id)
        assertEquals("Drama Series", loadedSeries[0].name)
        assertEquals(1, loadedSeries[0].series.size)
        val s = loadedSeries[0].series[0]
        assertEquals("s1", s.id)
        assertEquals(42, s.seriesId)
        assertEquals("Breaking Bad", s.title)
        assertEquals(2008, s.year)

        tempDir.deleteRecursively()
    }
}
