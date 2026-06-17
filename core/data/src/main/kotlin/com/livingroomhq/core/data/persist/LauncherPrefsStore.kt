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

    suspend fun setFavorites(ids: Set<String>)
    suspend fun setRecents(ids: List<String>)
    suspend fun setAppOrder(packageNames: List<String>)
    suspend fun setPlaylistUrl(url: String?)
    suspend fun setEpgUrl(url: String?)
    suspend fun setDefaultPromptDismissed(dismissed: Boolean)
}

class InMemoryPrefsStore : LauncherPrefsStore {
    override val favorites = MutableStateFlow<Set<String>>(emptySet())
    override val recents = MutableStateFlow<List<String>>(emptyList())
    override val appOrder = MutableStateFlow<List<String>>(emptyList())
    override val playlistUrl = MutableStateFlow<String?>(null)
    override val epgUrl = MutableStateFlow<String?>(null)
    override val defaultPromptDismissed = MutableStateFlow(false)

    override suspend fun setFavorites(ids: Set<String>) { favorites.value = ids }
    override suspend fun setRecents(ids: List<String>) { recents.value = ids }
    override suspend fun setAppOrder(packageNames: List<String>) { appOrder.value = packageNames }
    override suspend fun setPlaylistUrl(url: String?) { playlistUrl.value = url }
    override suspend fun setEpgUrl(url: String?) { epgUrl.value = url }
    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) { defaultPromptDismissed.value = dismissed }
}
