package com.livingroomhq.screens.live

import androidx.lifecycle.ViewModelStore
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.repo.ChannelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveTvViewModelTest {
    private val first = Channel("one", 1, "One", "News", "https://example.test/one")
    private val second = Channel("two", 2, "Two", "Sport", "https://example.test/two")

    private fun TestScope.withModel(repo: FakeGuideRepository, block: TestScope.(LiveTvViewModel) -> Unit) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val model = LiveTvViewModel(repo)
            store.put("live", model)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
            runCurrent()
            block(model)
        } finally {
            store.clear()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    @Test fun `asynchronous playlist startup initializes the first available preview`() = runTest {
        val repo = FakeGuideRepository()
        withModel(repo) { model ->
            assertNull(model.uiState.value.previewChannelId)
            repo.channels.value = listOf(first, second)
            runCurrent()
            assertEquals(first.id, model.uiState.value.previewChannelId)
            assertEquals(first.id, model.uiState.value.focusedChannelId)
        }
    }

    @Test fun `browsing a different channel and category preserves the tuned preview`() = runTest {
        val repo = FakeGuideRepository(listOf(first, second))
        withModel(repo) { model ->
            model.onEvent(LiveTvEvent.FocusChannel(second.id))
            model.onEvent(LiveTvEvent.SelectCategory("Sport"))
            advanceTimeBy(500)
            runCurrent()
            assertEquals(second.id, model.uiState.value.focusedChannelId)
            assertEquals(first.id, model.uiState.value.previewChannelId)
            assertTrue(repo.watched.isEmpty())
        }
    }

    @Test fun `opening a channel tunes it and arms fullscreen focus restoration`() = runTest {
        val repo = FakeGuideRepository(listOf(first, second))
        withModel(repo) { model ->
            val effects = mutableListOf<LiveTvEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.effects.collect { effects += it } }
            model.onEvent(LiveTvEvent.OpenChannel(second))
            runCurrent()
            assertEquals(second.id, model.uiState.value.previewChannelId)
            assertEquals(listOf(second.id), repo.watched)
            assertEquals(listOf(LiveTvEffect.ArmGridFocus(second.id), LiveTvEffect.LaunchPlayer(second)), effects)
        }
    }

    @Test fun `recently watched channel seeds the preview`() = runTest {
        val repo = FakeGuideRepository(listOf(first, second)).apply { recents.value = listOf(second) }
        withModel(repo) { model -> assertEquals(second.id, model.uiState.value.previewChannelId) }
    }
}

private class FakeGuideRepository(initial: List<Channel> = emptyList()) : ChannelRepository {
    override val channels = MutableStateFlow(initial)
    override val recents = MutableStateFlow(emptyList<Channel>())
    override val groups = MutableStateFlow(initial.map { it.group }.distinct())
    override val channelsByGroup = MutableStateFlow(initial.groupBy { it.group })
    override val epgRevision = MutableStateFlow(0L)
    val watched = mutableListOf<String>()
    override fun epgNowNext(channelId: String): Pair<Program?, Program?> = null to null
    override fun epgSchedule(channelId: String, startMillis: Long, endMillis: Long) = emptyList<Program>()
    override fun markWatched(channelId: String) { watched += channelId }
    override fun toggleFavorite(channelId: String) {}
    override suspend fun loadM3u(playlistUrl: String) {}
    override suspend fun loadXmltv(epgUrl: String) {}
    override suspend fun clearXmltv() {}
    override suspend fun runMaintenance() {}
    override suspend fun fetchEpgDetails(channelId: String) {}
    override suspend fun prefetchEpgForChannels(channelIds: List<String>) {}
    override suspend fun computeOnNowRail(excludeChannelId: String?, resultLimit: Int) = emptyList<Pair<Channel, Program>>()
}
