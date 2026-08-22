package com.livingroomhq.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.core.data.fault.runLoggedCatching
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.model.Weather
import com.livingroomhq.core.data.repo.AmbientInfoRepository
import com.livingroomhq.core.data.repo.ChannelRepository
import kotlinx.coroutines.channels.Channel as EffectChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val channels: List<Channel> = emptyList(),
    val recents: List<Channel> = emptyList(),
    val epgRevision: Long = 0L,
    val weather: Weather? = null,
    val onNow: List<Pair<Channel, Program>> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
) {
    val currentChannel: Channel?
        get() = recents.firstOrNull() ?: channels.firstOrNull()

    val recentList: List<Channel>
        get() = recents.ifEmpty { channels.take(6) }
}

sealed interface HomeEvent {
    data class OpenChannel(val channel: Channel, val focusTarget: String) : HomeEvent
    data object WatchHero : HomeEvent
}

sealed interface HomeEffect {
    data class LaunchPlayer(val channel: Channel) : HomeEffect
    data class ArmFocus(val targetKey: String) : HomeEffect
    data object NavigateToLive : HomeEffect
}

class HomeViewModel(
    private val channels: ChannelRepository,
    private val ambientInfo: AmbientInfoRepository,
) : ViewModel() {

    private val nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val onNow = MutableStateFlow<List<Pair<Channel, Program>>>(emptyList())
    private val _effects = EffectChannel<HomeEffect>(capacity = EffectChannel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            channels.channels,
            channels.recents,
            channels.epgRevision,
        ) { channelList, recents, revision -> Triple(channelList, recents, revision) },
        combine(ambientInfo.weather, onNow, nowMillis) { weather, rail, tick -> Triple(weather, rail, tick) },
    ) { (channelList, recents, revision), (weather, rail, tick) ->
        HomeUiState(
            channels = channelList,
            recents = recents,
            epgRevision = revision,
            weather = weather,
            onNow = rail,
            nowMillis = tick,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch {
            combine(
                channels.channels,
                channels.recents,
                channels.epgRevision,
                nowMillis,
            ) { channelList, recents, _, _ ->
                recents.firstOrNull() ?: channelList.firstOrNull()
            }.collect { current ->
                current?.id?.let { id ->
                    runLoggedCatching("home_epg") { channels.fetchEpgDetails(id) }
                }
            }
        }

        viewModelScope.launch {
            combine(
                channels.channels,
                channels.recents,
                channels.epgRevision,
                nowMillis,
            ) { channelList, recents, _, tick ->
                val current = recents.firstOrNull() ?: channelList.firstOrNull()
                current?.id to tick
            }.collect { (excludeId, _) ->
                onNow.value = channels.computeOnNowRail(excludeChannelId = excludeId)
            }
        }
    }

    fun tickClock(now: Long) {
        nowMillis.value = now
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.OpenChannel -> viewModelScope.launch {
                channels.markWatched(event.channel.id)
                _effects.send(HomeEffect.ArmFocus(event.focusTarget))
                _effects.send(HomeEffect.LaunchPlayer(event.channel))
            }
            HomeEvent.WatchHero -> viewModelScope.launch {
                val current = uiState.value.currentChannel
                if (current != null) {
                    _effects.send(HomeEffect.ArmFocus("home:hero"))
                    _effects.send(HomeEffect.LaunchPlayer(current))
                } else {
                    _effects.send(HomeEffect.NavigateToLive)
                }
            }
        }
    }

    fun epgNowNext(channelId: String) = channels.epgNowNext(channelId)

    companion object {
        fun factory(
            channels: ChannelRepository,
            ambientInfo: AmbientInfoRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(channels, ambientInfo) as T
        }
    }
}
