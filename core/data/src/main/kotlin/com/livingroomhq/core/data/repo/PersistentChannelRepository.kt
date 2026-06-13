package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.iptv.M3uParser
import com.livingroomhq.core.data.iptv.XmltvParser
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.util.zip.GZIPInputStream

/**
 * [ChannelRepository] with persisted favorites, recents and playlist URL.
 * A saved M3U playlist is restored at startup, and [loadM3u] configures a new
 * one. EPG stays empty until an XMLTV source is added.
 */
class PersistentChannelRepository(
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fetchPlaylist: suspend (String) -> String = ::httpGet,
) : ChannelRepository {

    private val lineup = MutableStateFlow<List<Channel>>(emptyList())

    /** Programmes keyed by channel id (tvg-id) from a loaded XMLTV guide. */
    private val loadedEpg = MutableStateFlow<Map<String, List<Program>>>(emptyMap())

    override val channels: StateFlow<List<Channel>> =
        combine(lineup, prefs.favorites) { list, favs ->
            list.map { it.copy(isFavorite = it.id in favs) }
        }.stateIn(scope, SharingStarted.Eagerly, lineup.value)

    override val recents: StateFlow<List<Channel>> =
        combine(channels, prefs.recents) { list, ids ->
            ids.mapNotNull { id -> list.firstOrNull { it.id == id } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun groups(): List<String> =
        channels.value.map { it.group }.distinct()

    override fun epgNowNext(channelId: String): Pair<Program?, Program?> {
        val programs = loadedEpg.value[channelId].orEmpty()
        if (programs.isEmpty()) return null to null
        val now = nowMillis()
        val current = programs.firstOrNull { now in it.startMillis until it.endMillis }
        val next = programs.firstOrNull { it.startMillis >= (current?.endMillis ?: now) }
        return current to next
    }

    override fun markWatched(channelId: String) {
        scope.launch {
            val current = prefs.recents.first()
            prefs.setRecents((listOf(channelId) + current.filterNot { it == channelId }).take(8))
        }
    }

    override fun toggleFavorite(channelId: String) {
        scope.launch {
            val favs = prefs.favorites.first()
            prefs.setFavorites(if (channelId in favs) favs - channelId else favs + channelId)
        }
    }

    override suspend fun loadM3u(playlistUrl: String) {
        val parsed = withContext(workDispatcher) {
            M3uParser.parse(fetchPlaylist(playlistUrl))
        }
        if (parsed.isNotEmpty()) {
            lineup.value = parsed
            prefs.setPlaylistUrl(playlistUrl)
        }
    }

    override suspend fun loadXmltv(epgUrl: String) {
        val parsed = withContext(workDispatcher) {
            XmltvParser.parse(fetchPlaylist(epgUrl))
        }
        require(parsed.isNotEmpty()) { "No programmes found in guide" }
        loadedEpg.value = parsed
        prefs.setEpgUrl(epgUrl)
    }

    override suspend fun clearXmltv() {
        loadedEpg.value = emptyMap()
        prefs.setEpgUrl(null)
    }

    /** Re-applies the persisted playlist and EPG guide; network errors are swallowed. */
    fun restore() {
        scope.launch {
            prefs.playlistUrl.first()?.let { url -> runCatching { loadM3u(url) } }
        }
        scope.launch {
            prefs.epgUrl.first()?.let { url -> runCatching { loadXmltv(url) } }
        }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection()
    val stream = connection.getInputStream()
    val contentEncoding = connection.contentEncoding.orEmpty()
    val input = if (contentEncoding.equals("gzip", ignoreCase = true) || url.endsWith(".gz", ignoreCase = true)) {
        GZIPInputStream(stream)
    } else {
        stream
    }
    input.bufferedReader(UTF_8).use { it.readText() }
}
