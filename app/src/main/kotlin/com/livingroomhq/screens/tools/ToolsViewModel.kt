package com.livingroomhq.screens.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livingroomhq.core.data.model.LaunchableApp
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import com.livingroomhq.core.data.repo.InstalledAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class ToolsUiState(
    val detected: List<LaunchableApp> = emptyList(),
    val savedOrder: List<String> = emptyList(),
    val hostResumeTick: Int = 0,
    val isLoading: Boolean = true,
) {
    val apps: List<LaunchableApp> = mergeOrder(detected, savedOrder)
}

class ToolsViewModel(
    private val installedApps: InstalledAppsRepository,
    private val prefs: LauncherPrefsStore,
) : ViewModel() {

    private val detected = MutableStateFlow<List<LaunchableApp>>(emptyList())
    private val isLoading = MutableStateFlow(true)

    val uiState: StateFlow<ToolsUiState> = combine(
        detected,
        prefs.appOrder,
        installedApps.hostResumeTick,
        isLoading,
    ) { apps, order, resumeTick, loading ->
        ToolsUiState(
            detected = apps,
            savedOrder = order,
            hostResumeTick = resumeTick,
            isLoading = loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ToolsUiState())

    init {
        refresh()
        viewModelScope.launch {
            prefs.pinnedAppPackages.drop(1).collect { refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            detected.value = runCatching { installedApps.launchableApps() }
                .onFailure { /* silently logged by InstalledAppsRepository */ }
                .getOrDefault(emptyList())
            isLoading.value = false
        }
    }

    fun saveOrder(packageNames: List<String>) {
        viewModelScope.launch { prefs.setAppOrder(packageNames) }
    }

    fun canLaunch(): Boolean = installedApps.canLaunch()

    fun launch(packageName: String, context: Context): Boolean =
        installedApps.launch(packageName, context)

    fun openAppSettings(packageName: String): Boolean =
        installedApps.openAppSettings(packageName)

    companion object {
        fun factory(
            installedApps: InstalledAppsRepository,
            prefs: LauncherPrefsStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ToolsViewModel(installedApps, prefs) as T
        }
    }
}

private fun mergeOrder(
    detected: List<LaunchableApp>,
    savedOrder: List<String>,
): List<LaunchableApp> {
    if (savedOrder.isEmpty()) return detected
    val byPackage = detected.associateBy { it.packageName }
    val ordered = savedOrder.mapNotNull { byPackage[it] }
    val savedSet = savedOrder.toHashSet()
    val rest = detected.filter { it.packageName !in savedSet }
    return ordered + rest
}
