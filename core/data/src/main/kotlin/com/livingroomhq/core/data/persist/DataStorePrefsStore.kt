package com.livingroomhq.core.data.persist

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore(name = "launcher_prefs")

/** DataStore-backed [LauncherPrefsStore]. Recents keep order via a joined string. */
class DataStorePrefsStore(context: Context) : LauncherPrefsStore {

    private val store = context.applicationContext.launcherDataStore

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_channel_ids")
        val RECENTS = stringPreferencesKey("recent_channel_ids")
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val PROMPT_DISMISSED = booleanPreferencesKey("default_prompt_dismissed")
    }

    override val favorites: Flow<Set<String>> =
        store.data.map { it[Keys.FAVORITES] ?: emptySet() }

    override val recents: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.RECENTS]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        }

    override val playlistUrl: Flow<String?> =
        store.data.map { it[Keys.PLAYLIST_URL] }

    override val defaultPromptDismissed: Flow<Boolean> =
        store.data.map { it[Keys.PROMPT_DISMISSED] ?: false }

    override suspend fun setFavorites(ids: Set<String>) {
        store.edit { it[Keys.FAVORITES] = ids }
    }

    override suspend fun setRecents(ids: List<String>) {
        store.edit { it[Keys.RECENTS] = ids.joinToString("\n") }
    }

    override suspend fun setPlaylistUrl(url: String?) {
        store.edit { prefs ->
            if (url == null) prefs.remove(Keys.PLAYLIST_URL) else prefs[Keys.PLAYLIST_URL] = url
        }
    }

    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) {
        store.edit { it[Keys.PROMPT_DISMISSED] = dismissed }
    }
}
