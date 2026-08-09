package com.livingroomhq.screens.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.repo.ChannelRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel as EffectChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val PREVIEW_FOCUS_DEBOUNCE_MS = 450L
private const val EPG_PREFETCH_DELAY_MS = 300L
private const val EPG_PREFETCH_CAP = 48

data class LiveTvUiState(
    val channels: List<Channel> = emptyList(),
    val recents: List<Channel> = emptyList(),
    val groups: List<String> = emptyList(),
    val channelsByGroup: Map<String, List<Channel>> = emptyMap(),
    val epgRevision: Long = 0L,
    val selectedCategoryId: String? = null,
    val focusedChannelId: String? = null,
    val previewChannelId: String? = null,
    val isGridFocused: Boolean = false,
) {
    val isEmpty: Boolean get() = channels.isEmpty()

    val visibleChannels: List<Channel>
        get() = when (selectedCategoryId) {
            null -> channels
            "favorites" -> channels.filter { it.isFavorite }
            "recent" -> recents
            else -> channelsByGroup[selectedCategoryId].orEmpty()
        }

    val previewChannel: Channel?
        get() = channels.firstOrNull { it.id == previewChannelId }

    fun epgTitleFor(channelId: String, lookup: (String) -> String): String =
        lookup(channelId)
}

sealed interface LiveTvEvent {
    data class SelectCategory(val id: String?) : LiveTvEvent
    data class FocusChannel(val channelId: String) : LiveTvEvent
    data class OpenChannel(val channel: Channel) : LiveTvEvent
    data class OpenPreviewFullscreen(val channel: Channel) : LiveTvEvent
    data class SetGridFocused(val focused: Boolean) : LiveTvEvent
    data object FocusCategories : LiveTvEvent
}

sealed interface LiveTvEffect {
    data class LaunchPlayer(val channel: Channel) : LiveTvEffect
    data class ArmGridFocus(val channelId: String) : LiveTvEffect
    data class ArmPreviewFocus(val channelId: String) : LiveTvEffect
    data object FocusCategories : LiveTvEffect
}

class LiveTvViewModel(
    private val channelRepository: ChannelRepository,
) : ViewModel() {

    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val focusedChannelId = MutableStateFlow<String?>(null)
    private val previewChannelId = MutableStateFlow<String?>(null)
    private val isGridFocused = MutableStateFlow(false)
    private val initializedSelection = AtomicBoolean(false)

    private val _effects = EffectChannel<LiveTvEffect>(capacity = EffectChannel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<LiveTvUiState> = combine(
        combine(
            channelRepository.channels,
            channelRepository.recents,
            channelRepository.groups,
            channelRepository.channelsByGroup,
            channelRepository.epgRevision,
        ) { channels, recents, groups, channelsByGroup, epgRevision ->
            LiveTvUiState(
                channels = channels,
                recents = recents,
                groups = groups,
                channelsByGroup = channelsByGroup,
                epgRevision = epgRevision,
            )
        },
        combine(
            selectedCategoryId,
            focusedChannelId,
            previewChannelId,
            isGridFocused,
        ) { categoryId, focusedId, previewId, gridFocused ->
            arrayOf(categoryId, focusedId, previewId, gridFocused)
        },
    ) { base, focus ->
        base.copy(
            selectedCategoryId = focus[0] as String?,
            focusedChannelId = focus[1] as String?,
            previewChannelId = focus[2] as String?,
            isGridFocused = focus[3] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTvUiState())

    init {
        combine(channelRepository.channels, channelRepository.recents) { channels, recents ->
            channels to recents
        }.onEach { (channels, recents) ->
            if (!initializedSelection.compareAndSet(false, true)) return@onEach
            if (channels.isEmpty()) return@onEach
            val initial = recents.firstOrNull()?.id
            previewChannelId.value = initial
            focusedChannelId.value = initial
        }.launchIn(viewModelScope)

        @OptIn(FlowPreview::class)
        focusedChannelId
            .debounce(PREVIEW_FOCUS_DEBOUNCE_MS)
            .distinctUntilChanged()
            .filterNotNull()
            .onEach { id ->
                if (focusedChannelId.value == id) {
                    previewChannelId.value = id
                }
            }
            .launchIn(viewModelScope)

        previewChannelId
            .filterNotNull()
            .distinctUntilChanged()
            .onEach { id ->
                runCatching { channelRepository.fetchEpgDetails(id) }
            }
            .launchIn(viewModelScope)

        combine(selectedCategoryId, uiState.map { it.visibleChannels }) { _: String?, visible: List<Channel> ->
            visible.take(EPG_PREFETCH_CAP).map { it.id }
        }.distinctUntilChanged().onEach { ids: List<String> ->
            if (ids.isEmpty()) return@onEach
            delay(EPG_PREFETCH_DELAY_MS)
            runCatching { channelRepository.prefetchEpgForChannels(ids) }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: LiveTvEvent) {
        when (event) {
            is LiveTvEvent.SelectCategory -> selectedCategoryId.value = event.id
            is LiveTvEvent.FocusChannel -> focusedChannelId.value = event.channelId
            is LiveTvEvent.SetGridFocused -> isGridFocused.value = event.focused
            is LiveTvEvent.OpenChannel -> {
                val channel = event.channel
                viewModelScope.launch {
                    focusedChannelId.value = channel.id
                    previewChannelId.value = channel.id
                    channelRepository.markWatched(channel.id)
                    _effects.send(LiveTvEffect.ArmGridFocus(channel.id))
                    _effects.send(LiveTvEffect.LaunchPlayer(channel))
                }
            }
            is LiveTvEvent.OpenPreviewFullscreen -> {
                val channel = event.channel
                viewModelScope.launch {
                    _effects.send(LiveTvEffect.ArmPreviewFocus(channel.id))
                    _effects.send(LiveTvEffect.LaunchPlayer(channel))
                }
            }
            LiveTvEvent.FocusCategories -> viewModelScope.launch {
                _effects.send(LiveTvEffect.FocusCategories)
            }
        }
    }

    fun epgNowNext(channelId: String) = channelRepository.epgNowNext(channelId)

    companion object {
        fun factory(channelRepository: ChannelRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LiveTvViewModel(channelRepository) as T
            }
    }
}
