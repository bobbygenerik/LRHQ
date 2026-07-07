package com.livingroomhq.core.data.persist

import com.livingroomhq.core.data.net.HttpSyncMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Persistence seam for launcher state. Production uses DataStore
 * ([DataStorePrefsStore]); tests use [InMemoryPrefsStore].
 */
interface LauncherPrefsStore {
    val favorites: Flow<Set<String>>
    val recents: Flow<List<String>>
    val appOrder: Flow<List<String>>
    val playlistUrl: Flow<String?>
    val epgUrl: Flow<String?>
    val playlistSyncMetadata: Flow<HttpSyncMetadata>
    val epgSyncMetadata: Flow<HttpSyncMetadata>
    val defaultPromptDismissed: Flow<Boolean>
    val theme: Flow<String>
    val accentColor: Flow<String>
    val showLivePreview: Flow<Boolean>
    val showWeather: Flow<Boolean>
    val idleTimeSeconds: Flow<Int>
    val animations: Flow<String>
    val soundEffects: Flow<Boolean>
    val pinnedAppPackages: Flow<List<String>>
    val liveCaptionServerUrl: Flow<String?>

    suspend fun setFavorites(ids: Set<String>)
    suspend fun setRecents(ids: List<String>)
    suspend fun setAppOrder(packageNames: List<String>)
    suspend fun setPlaylistUrl(url: String?)
    suspend fun setEpgUrl(url: String?)
    suspend fun setPlaylistSyncMetadata(metadata: HttpSyncMetadata)
    suspend fun setEpgSyncMetadata(metadata: HttpSyncMetadata)
    suspend fun setDefaultPromptDismissed(dismissed: Boolean)
    suspend fun setTheme(value: String)
    suspend fun setAccentColor(value: String)
    suspend fun setShowLivePreview(value: Boolean)
    suspend fun setShowWeather(value: Boolean)
    suspend fun setIdleTimeSeconds(value: Int)
    suspend fun setAnimations(value: String)
    suspend fun setSoundEffects(value: Boolean)
    suspend fun setPinnedAppPackages(packages: List<String>)
    suspend fun setLiveCaptionServerUrl(url: String?)
}

class InMemoryPrefsStore : LauncherPrefsStore {
    override val favorites = MutableStateFlow<Set<String>>(emptySet())
    override val recents = MutableStateFlow<List<String>>(emptyList())
    override val appOrder = MutableStateFlow<List<String>>(emptyList())
    override val playlistUrl = MutableStateFlow<String?>(null)
    override val epgUrl = MutableStateFlow<String?>(null)
    override val playlistSyncMetadata = MutableStateFlow(HttpSyncMetadata())
    override val epgSyncMetadata = MutableStateFlow(HttpSyncMetadata())
    override val defaultPromptDismissed = MutableStateFlow(false)
    override val theme = MutableStateFlow("Dark")
    override val accentColor = MutableStateFlow("Green")
    override val showLivePreview = MutableStateFlow(true)
    override val showWeather = MutableStateFlow(true)
    override val idleTimeSeconds = MutableStateFlow(300)
    override val animations = MutableStateFlow("Smooth")
    override val soundEffects = MutableStateFlow(true)
    override val pinnedAppPackages = MutableStateFlow<List<String>>(emptyList())
    override val liveCaptionServerUrl = MutableStateFlow<String?>(null)

    override suspend fun setFavorites(ids: Set<String>) { favorites.value = ids }
    override suspend fun setRecents(ids: List<String>) { recents.value = ids }
    override suspend fun setAppOrder(packageNames: List<String>) { appOrder.value = packageNames }
    override suspend fun setPlaylistUrl(url: String?) { playlistUrl.value = url }
    override suspend fun setEpgUrl(url: String?) { epgUrl.value = url }
    override suspend fun setPlaylistSyncMetadata(metadata: HttpSyncMetadata) { playlistSyncMetadata.value = metadata }
    override suspend fun setEpgSyncMetadata(metadata: HttpSyncMetadata) { epgSyncMetadata.value = metadata }
    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) { defaultPromptDismissed.value = dismissed }
    override suspend fun setTheme(value: String) { theme.value = value }
    override suspend fun setAccentColor(value: String) { accentColor.value = value }
    override suspend fun setShowLivePreview(value: Boolean) { showLivePreview.value = value }
    override suspend fun setShowWeather(value: Boolean) { showWeather.value = value }
    override suspend fun setIdleTimeSeconds(value: Int) { idleTimeSeconds.value = value }
    override suspend fun setAnimations(value: String) { animations.value = value }
    override suspend fun setSoundEffects(value: Boolean) { soundEffects.value = value }
    override suspend fun setPinnedAppPackages(packages: List<String>) { pinnedAppPackages.value = packages }
    override suspend fun setLiveCaptionServerUrl(url: String?) { liveCaptionServerUrl.value = url }
}
