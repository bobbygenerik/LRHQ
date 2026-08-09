package com.livingroomhq.core.data.persist

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.livingroomhq.core.data.net.HttpSyncMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore by preferencesDataStore(name = "launcher_prefs")

/** DataStore-backed [LauncherPrefsStore]. Recents keep order via a joined string. */
class DataStorePrefsStore(context: Context) : LauncherPrefsStore {

    private val store = context.applicationContext.launcherDataStore

    private object Keys {
        val FAVORITES = stringSetPreferencesKey("favorite_channel_ids")
        val RECENTS = stringPreferencesKey("recent_channel_ids")
        val APP_ORDER = stringPreferencesKey("app_order")
        val PLAYLIST_URL = stringPreferencesKey("playlist_url")
        val PLAYLIST_ETAG = stringPreferencesKey("playlist_etag")
        val PLAYLIST_LAST_MODIFIED = stringPreferencesKey("playlist_last_modified")
        val EPG_URL = stringPreferencesKey("epg_url")
        val EPG_ETAG = stringPreferencesKey("epg_etag")
        val EPG_LAST_MODIFIED = stringPreferencesKey("epg_last_modified")
        val PROMPT_DISMISSED = booleanPreferencesKey("default_prompt_dismissed")
        val THEME = stringPreferencesKey("setting_theme")
        val ACCENT_COLOR = stringPreferencesKey("setting_accent_color")
        val SHOW_LIVE_PREVIEW = booleanPreferencesKey("setting_show_live_preview")
        val SHOW_WEATHER = booleanPreferencesKey("setting_show_weather")
        val IDLE_TIME_SECONDS = intPreferencesKey("setting_idle_time_seconds")
        val ANIMATIONS = stringPreferencesKey("setting_animations")
        val SOUND_EFFECTS = booleanPreferencesKey("setting_sound_effects")
        val PINNED_APP_PACKAGES = stringPreferencesKey("pinned_app_packages")
        val LIVE_CAPTION_SERVER_URL = stringPreferencesKey("live_caption_server_url")
        val LIVE_CAPTION_SERVER_TOKEN = stringPreferencesKey("live_caption_server_token")
    }

    override val favorites: Flow<Set<String>> =
        store.data.map { it[Keys.FAVORITES] ?: emptySet() }
            .distinctUntilChanged()

    override val recents: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.RECENTS]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        }.distinctUntilChanged()

    override val appOrder: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.APP_ORDER]?.split('\n')?.filter { it.isNotEmpty() } ?: emptyList()
        }.distinctUntilChanged()

    override val playlistUrl: Flow<String?> =
        store.data
            .map { it[Keys.PLAYLIST_URL] }
            .distinctUntilChanged()

    override val epgUrl: Flow<String?> =
        store.data
            .map { it[Keys.EPG_URL] }
            .distinctUntilChanged()

    override val playlistSyncMetadata: Flow<HttpSyncMetadata> =
        store.data.map { prefs ->
            HttpSyncMetadata(
                etag = prefs[Keys.PLAYLIST_ETAG],
                lastModified = prefs[Keys.PLAYLIST_LAST_MODIFIED],
            )
        }.distinctUntilChanged()

    override val epgSyncMetadata: Flow<HttpSyncMetadata> =
        store.data.map { prefs ->
            HttpSyncMetadata(
                etag = prefs[Keys.EPG_ETAG],
                lastModified = prefs[Keys.EPG_LAST_MODIFIED],
            )
        }.distinctUntilChanged()

    override val defaultPromptDismissed: Flow<Boolean> =
        store.data.map { it[Keys.PROMPT_DISMISSED] ?: false }
            .distinctUntilChanged()

    override val theme: Flow<String> =
        store.data.map { it[Keys.THEME] ?: "Dark" }
            .distinctUntilChanged()

    override val accentColor: Flow<String> =
        store.data.map {
            when (val accent = it[Keys.ACCENT_COLOR] ?: "Green") {
                "Neon", "Ice", "Cyan" -> "Green"
                else -> accent
            }
        }
            .distinctUntilChanged()

    override val showLivePreview: Flow<Boolean> =
        store.data.map { it[Keys.SHOW_LIVE_PREVIEW] ?: true }
            .distinctUntilChanged()

    override val showWeather: Flow<Boolean> =
        store.data.map { it[Keys.SHOW_WEATHER] ?: true }
            .distinctUntilChanged()

    override val idleTimeSeconds: Flow<Int> =
        store.data.map { it[Keys.IDLE_TIME_SECONDS] ?: 300 }
            .distinctUntilChanged()

    override val animations: Flow<String> =
        store.data.map { it[Keys.ANIMATIONS] ?: "Smooth" }
            .distinctUntilChanged()

    override val soundEffects: Flow<Boolean> =
        store.data.map { it[Keys.SOUND_EFFECTS] ?: true }
            .distinctUntilChanged()

    override val pinnedAppPackages: Flow<List<String>> =
        store.data.map { prefs ->
            prefs[Keys.PINNED_APP_PACKAGES]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        }.distinctUntilChanged()

    override val liveCaptionServerUrl: Flow<String?> =
        store.data
            .map { it[Keys.LIVE_CAPTION_SERVER_URL] }
            .distinctUntilChanged()

    override val liveCaptionServerToken: Flow<String?> =
        store.data
            .map { it[Keys.LIVE_CAPTION_SERVER_TOKEN] }
            .distinctUntilChanged()

    override suspend fun setFavorites(ids: Set<String>) {
        store.edit { it[Keys.FAVORITES] = ids }
    }

    override suspend fun setRecents(ids: List<String>) {
        store.edit { it[Keys.RECENTS] = ids.joinToString("\n") }
    }

    override suspend fun setAppOrder(packageNames: List<String>) {
        store.edit { it[Keys.APP_ORDER] = packageNames.joinToString("\n") }
    }

    override suspend fun setPlaylistUrl(url: String?) {
        store.edit { prefs ->
            if (url == null) prefs.remove(Keys.PLAYLIST_URL) else prefs[Keys.PLAYLIST_URL] = url
        }
    }

    override suspend fun setEpgUrl(url: String?) {
        store.edit { prefs ->
            if (url == null) prefs.remove(Keys.EPG_URL) else prefs[Keys.EPG_URL] = url
        }
    }

    override suspend fun setPlaylistSyncMetadata(metadata: HttpSyncMetadata) {
        store.edit { prefs ->
            metadata.etag?.let { prefs[Keys.PLAYLIST_ETAG] = it } ?: prefs.remove(Keys.PLAYLIST_ETAG)
            metadata.lastModified?.let { prefs[Keys.PLAYLIST_LAST_MODIFIED] = it }
                ?: prefs.remove(Keys.PLAYLIST_LAST_MODIFIED)
        }
    }

    override suspend fun setEpgSyncMetadata(metadata: HttpSyncMetadata) {
        store.edit { prefs ->
            metadata.etag?.let { prefs[Keys.EPG_ETAG] = it } ?: prefs.remove(Keys.EPG_ETAG)
            metadata.lastModified?.let { prefs[Keys.EPG_LAST_MODIFIED] = it }
                ?: prefs.remove(Keys.EPG_LAST_MODIFIED)
        }
    }

    override suspend fun setDefaultPromptDismissed(dismissed: Boolean) {
        store.edit { it[Keys.PROMPT_DISMISSED] = dismissed }
    }

    override suspend fun setTheme(value: String) {
        store.edit { it[Keys.THEME] = value }
    }

    override suspend fun setAccentColor(value: String) {
        store.edit { it[Keys.ACCENT_COLOR] = value }
    }

    override suspend fun setShowLivePreview(value: Boolean) {
        store.edit { it[Keys.SHOW_LIVE_PREVIEW] = value }
    }

    override suspend fun setShowWeather(value: Boolean) {
        store.edit { it[Keys.SHOW_WEATHER] = value }
    }

    override suspend fun setIdleTimeSeconds(value: Int) {
        store.edit { it[Keys.IDLE_TIME_SECONDS] = value }
    }

    override suspend fun setAnimations(value: String) {
        store.edit { it[Keys.ANIMATIONS] = value }
    }

    override suspend fun setSoundEffects(value: Boolean) {
        store.edit { it[Keys.SOUND_EFFECTS] = value }
    }

    override suspend fun setPinnedAppPackages(packages: List<String>) {
        store.edit { it[Keys.PINNED_APP_PACKAGES] = packages.joinToString("\n") }
    }

    override suspend fun setLiveCaptionServerUrl(url: String?) {
        store.edit { prefs ->
            if (url.isNullOrBlank()) prefs.remove(Keys.LIVE_CAPTION_SERVER_URL)
            else prefs[Keys.LIVE_CAPTION_SERVER_URL] = url.trim()
        }
    }

    override suspend fun setLiveCaptionServerToken(token: String?) {
        store.edit { prefs ->
            if (token.isNullOrBlank()) prefs.remove(Keys.LIVE_CAPTION_SERVER_TOKEN)
            else prefs[Keys.LIVE_CAPTION_SERVER_TOKEN] = token.trim()
        }
    }
}
