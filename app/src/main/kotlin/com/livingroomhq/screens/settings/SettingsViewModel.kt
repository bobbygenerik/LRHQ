package com.livingroomhq.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.backdrop.AmbientPhotoCacheRepository
import com.livingroomhq.backdrop.GooglePhotosPickerClient
import com.livingroomhq.core.data.fault.FaultLog
import com.livingroomhq.core.data.fault.FaultLog.SyncStatus
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.ChannelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val playlistUrl: String? = null,
    val epgUrl: String? = null,
    val pinnedAppPackages: List<String> = emptyList(),
    val liveCaptionServerUrl: String? = null,
    val liveCaptionServerToken: String? = null,
    val syncStatus: SyncStatus = SyncStatus(),
    val syncStatusText: String = "",
)

sealed interface SettingsEvent {
    data class LoadPlaylist(val url: String) : SettingsEvent
    data class LoadGuide(val url: String) : SettingsEvent
    data class LoadSamplePlaylist(val url: String, val name: String) : SettingsEvent
    data class SavePinnedAppPackages(val packages: List<String>) : SettingsEvent
    data class SaveLiveCaptionServer(val url: String?, val token: String?) : SettingsEvent
    data object RunMaintenance : SettingsEvent
    data object ClearPlaylist : SettingsEvent
    data object ClearGuide : SettingsEvent
    data object ClearPhotoCache : SettingsEvent
}

sealed class SettingsActionResult {
    data object Idle : SettingsActionResult()
    data class Loading(val message: String) : SettingsActionResult()
    data class Success(val message: String) : SettingsActionResult()
    data class Failure(val message: String) : SettingsActionResult()
}

class SettingsViewModel(
    private val channels: ChannelRepository,
    private val prefs: LauncherPrefsStore,
    private val photoCache: AmbientPhotoCacheRepository,
    private val googlePhotosPicker: GooglePhotosPickerClient,
) : ViewModel() {

    private val _playlistStatus = MutableStateFlow<SettingsActionResult>(SettingsActionResult.Idle)
    val playlistStatus: StateFlow<SettingsActionResult> = _playlistStatus.asStateFlow()
    private val _guideStatus = MutableStateFlow<SettingsActionResult>(SettingsActionResult.Idle)
    val guideStatus: StateFlow<SettingsActionResult> = _guideStatus.asStateFlow()
    private val _maintenanceStatus = MutableStateFlow<SettingsActionResult>(SettingsActionResult.Idle)
    val maintenanceStatus: StateFlow<SettingsActionResult> = _maintenanceStatus.asStateFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.playlistUrl,
        prefs.epgUrl,
        prefs.pinnedAppPackages,
        combine(prefs.liveCaptionServerUrl, prefs.liveCaptionServerToken) { url, token -> url to token },
        FaultLog.syncStatus,
    ) { playlist, epg, pinned, (captionUrl, captionToken), sync ->
        SettingsUiState(
            playlistUrl = playlist,
            epgUrl = epg,
            pinnedAppPackages = pinned,
            liveCaptionServerUrl = captionUrl,
            liveCaptionServerToken = captionToken,
            syncStatus = sync,
            syncStatusText = FaultLog.formatSyncStatus(sync),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.LoadPlaylist -> loadPlaylist(event.url)
            is SettingsEvent.LoadGuide -> loadGuide(event.url)
            is SettingsEvent.LoadSamplePlaylist -> loadSample(event.url, event.name)
            is SettingsEvent.SavePinnedAppPackages -> viewModelScope.launch {
                prefs.setPinnedAppPackages(event.packages)
            }
            is SettingsEvent.SaveLiveCaptionServer -> viewModelScope.launch {
                prefs.setLiveCaptionServerUrl(event.url)
                prefs.setLiveCaptionServerToken(event.token)
            }
            SettingsEvent.RunMaintenance -> runMaintenance()
            SettingsEvent.ClearPlaylist -> clearPlaylist()
            SettingsEvent.ClearGuide -> clearGuide()
            SettingsEvent.ClearPhotoCache -> viewModelScope.launch { photoCache.clear() }
        }
    }

    fun startGooglePhotosImport() {
        viewModelScope.launch { googlePhotosPicker.startPickerImport() }
    }

    fun refreshGooglePhotosImport() {
        viewModelScope.launch { googlePhotosPicker.refreshPickerImport() }
    }

    fun importPhotosFromText(text: String, onImported: () -> Unit) {
        viewModelScope.launch {
            val result = photoCache.importFromText(text)
            if (result.photoCount > 0) onImported()
        }
    }

    private fun loadPlaylist(url: String) {
        viewModelScope.launch {
            _playlistStatus.value = SettingsActionResult.Loading("Loading stream playlist...")
            runCatching { channels.loadM3u(url.trim()) }
                .onSuccess { _playlistStatus.value = SettingsActionResult.Success("IPTV channels loaded successfully!") }
                .onFailure { _playlistStatus.value = SettingsActionResult.Failure("Failed: ${it.localizedMessage ?: "Invalid URL or Network error"}") }
        }
    }

    private fun loadGuide(url: String) {
        viewModelScope.launch {
            _guideStatus.value = SettingsActionResult.Loading("Loading guide...")
            runCatching { channels.loadXmltv(url.trim()) }
                .onSuccess { _guideStatus.value = SettingsActionResult.Success("Guide loaded successfully!") }
                .onFailure { _guideStatus.value = SettingsActionResult.Failure("Failed: ${it.localizedMessage ?: "Invalid URL or network error"}") }
        }
    }

    private fun loadSample(url: String, name: String) {
        viewModelScope.launch {
            _playlistStatus.value = SettingsActionResult.Loading("Loading $name...")
            runCatching { channels.loadM3u(url) }
                .onSuccess { _playlistStatus.value = SettingsActionResult.Success("$name loaded successfully!") }
                .onFailure { _playlistStatus.value = SettingsActionResult.Failure("Failed: ${it.localizedMessage ?: "Invalid URL or network error"}") }
        }
    }

    private fun runMaintenance() {
        viewModelScope.launch {
            _maintenanceStatus.value = SettingsActionResult.Loading("Running device maintenance...")
            runCatching {
                channels.runMaintenance()
                photoCache.trimToCacheLimit()
            }
                .onSuccess {
                    _maintenanceStatus.value = SettingsActionResult.Success(
                        "Maintenance completed: Pruned old programs. Cache size optimized.",
                    )
                }
                .onFailure {
                    _maintenanceStatus.value = SettingsActionResult.Failure("Maintenance failed: ${it.localizedMessage}")
                }
        }
    }

    private fun clearPlaylist() {
        viewModelScope.launch {
            prefs.setPlaylistUrl(null)
            prefs.setRecents(emptyList())
            _playlistStatus.value = SettingsActionResult.Success("Playlist cleared.")
        }
    }

    private fun clearGuide() {
        viewModelScope.launch {
            channels.clearXmltv()
            _guideStatus.value = SettingsActionResult.Success("Guide cleared.")
        }
    }

    companion object {
        fun factory(
            channels: ChannelRepository,
            prefs: LauncherPrefsStore,
            photoCache: AmbientPhotoCacheRepository,
            googlePhotosPicker: GooglePhotosPickerClient,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(channels, prefs, photoCache, googlePhotosPicker) as T
        }
    }
}
