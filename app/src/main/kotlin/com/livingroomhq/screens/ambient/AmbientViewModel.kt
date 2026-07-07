package com.livingroomhq.screens.ambient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.backdrop.AmbientPhoto
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.repo.ChannelRepository
import com.livingroomhq.core.data.repo.MediaRepository
import com.livingroomhq.core.widget.WidgetPlugin
import com.livingroomhq.core.widget.WidgetRegistry
import com.livingroomhq.core.widget.WidgetZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AmbientUiState(
    val recents: List<Channel> = emptyList(),
    val libraryBackdropUrls: List<String> = emptyList(),
    val ambientPhotos: List<AmbientPhoto> = emptyList(),
    val ambientWidgets: List<WidgetPlugin> = emptyList(),
    val showWeather: Boolean = true,
) {
    val currentChannel: Channel? = recents.firstOrNull()
}

class AmbientViewModel(
    private val channels: ChannelRepository,
    media: MediaRepository,
    ambientPhotos: StateFlow<List<AmbientPhoto>>,
    widgets: WidgetRegistry,
    showWeather: Flow<Boolean>,
) : ViewModel() {

    val uiState: StateFlow<AmbientUiState> = combine(
        channels.recents,
        media.library,
        ambientPhotos,
        widgets.plugins,
        showWeather,
    ) { recents, library, photos, rawWidgets, weatherEnabled ->
        AmbientUiState(
            recents = recents,
            libraryBackdropUrls = library.mapNotNull { it.backdropUrl }.distinct(),
            ambientPhotos = photos,
            ambientWidgets = rawWidgets.filter { widget ->
                WidgetZone.AMBIENT in widget.zones &&
                    (widget.id != "builtin.weather" || weatherEnabled)
            },
            showWeather = weatherEnabled,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AmbientUiState())

    fun epgNowNext(channelId: String): Pair<Program?, Program?> =
        channels.epgNowNext(channelId)

    companion object {
        fun factory(
            channels: ChannelRepository,
            media: MediaRepository,
            ambientPhotos: StateFlow<List<AmbientPhoto>>,
            widgets: WidgetRegistry,
            showWeather: Flow<Boolean>,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AmbientViewModel(channels, media, ambientPhotos, widgets, showWeather) as T
        }
    }
}
