package com.livingroomhq.screens.commandcenter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.core.data.model.SystemStats
import com.livingroomhq.core.data.repo.SystemMonitor
import kotlinx.coroutines.channels.Channel as EffectChannel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CommandCenterUiState(
    val stats: SystemStats? = null,
    val localIp: String = "Unavailable",
    val tailscaleIp: String? = null,
    val deviceModel: String = "",
    val appVersion: String = "",
    val androidRelease: String = "",
    val sdkInt: Int = 0,
    val securityPatch: String = "",
)

sealed interface CommandCenterEvent {
    data class OpenSettings(val action: String, val data: Uri? = null) : CommandCenterEvent
}

sealed interface CommandCenterEffect {
    data class OpenSettings(val action: String, val data: Uri? = null) : CommandCenterEffect
    data object SettingsOpenFailed : CommandCenterEffect
}

class CommandCenterViewModel(
    systemMonitor: SystemMonitor,
    deviceInfo: CommandCenterUiState,
) : ViewModel() {

    val uiState: StateFlow<CommandCenterUiState> =
        systemMonitor.stats()
            .map { stats -> deviceInfo.copy(stats = stats) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                deviceInfo,
            )

    private val _effects = EffectChannel<CommandCenterEffect>(capacity = EffectChannel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: CommandCenterEvent) {
        when (event) {
            is CommandCenterEvent.OpenSettings -> viewModelScope.launch {
                _effects.send(CommandCenterEffect.OpenSettings(event.action, event.data))
            }
        }
    }

    fun onSettingsOpenFailed() {
        viewModelScope.launch {
            _effects.send(CommandCenterEffect.SettingsOpenFailed)
        }
    }

    companion object {
        fun factory(
            systemMonitor: SystemMonitor,
            deviceInfo: CommandCenterUiState,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CommandCenterViewModel(systemMonitor, deviceInfo) as T
        }
    }
}
