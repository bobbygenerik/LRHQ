package com.livingroomhq.core.data.persist

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
    val defaultPromptDismissed: Flow<Boolean>
    val theme: Flow<String>
    val accentColor: Flow<String>
    val showLivePreview: Flow<Boolean>
    val showWeather: Flow<Boolean>
    val idleTimeSeconds: Flow<Int>
    val animations: Flow<String>
    val soundEffects: Flow<Boolean>

    suspend fun setFavorites(ids: Set<String>)
    suspend fun setRecents(ids: List<String>)
    suspend fun setAppOrder(packageNames: List<String>)
    suspend fun setPlaylistUrl(url: String?)
    suspend fun setEpgUrl(url: String?)
    suspend fun setDefaultPromptDismissed(dismissed: Boolean)
    suspend fun setTheme(value: String)
    suspend fun setAccentColor(value: String)
    suspend fun setShowLivePreview(value: Boolean)
    suspend fun setShowWeather(value: Boolean)
    suspend fun setIdleTimeSeconds(value: Int)
    suspend fun setAnimations(value: String)
    suspend fun setSoundEffects(value: Boolean)
}

class InMemoryPrefsStore : LauncherPrefsStore {
    override val favorites = MutableStateFlow<Set<String>>(emptySet())
    override val recents = MutableStateFlow<List<String>>(emptyList())
    override val appOrder = MutableStateFlow<List<String>>(emptyList())
    override val playlistUrl = MutableStateFlow<String?>(null)
    override val epgUrl = MutableStateFlow<String?>(null)
    override val defaultPromptDismissed = MutableStateFlow(false)
    override val theme = MutableStateFlow("Dark")
    override val accentColor = MutableStateFlow("Green")
    override val showLivePreview = MutableStateFlow(true)
    override val showWeather = MutableStateFlow(true)
    override val idleTimeSeconds = MutableStateFlow(300)
    override val animations = MutableStateFlow("Smooth")
    override val soundEffects = MutableStateFlow(true)

    override suspend fun setFavorites(ids: Set<String>) { favorites.value = ids }
    override suspend fun setRecents(ids: List<String>) { recents.value = ids }
    override suspend fun setAppOrder(packageNames: List<String>) { appOrder.value = packageNames }
    override suspend fun setPlaylistUrl(url: String?) { playlistUrl.value = url }
    override suspend fun setEpgUrl(url: String?) { epgUrl.value = url }
    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) { defaultPromptDismissed.value = dismissed }
    override suspend fun setTheme(value: String) { theme.value = value }
    override suspend fun setAccentColor(value: String) { accentColor.value = value }
    override suspend fun setShowLivePreview(value: Boolean) { showLivePreview.value = value }
    override suspend fun setShowWeather(value: Boolean) { showWeather.value = value }
    override suspend fun setIdleTimeSeconds(value: Int) { idleTimeSeconds.value = value }
    override suspend fun setAnimations(value: String) { animations.value = value }
    override suspend fun setSoundEffects(value: Boolean) { soundEffects.value = value }
}
