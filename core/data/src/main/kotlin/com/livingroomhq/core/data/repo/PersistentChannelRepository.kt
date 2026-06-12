package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.iptv.M3uParser
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.CoroutineScope
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

/**
 * [ChannelRepository] with persisted favorites, recents and playlist URL.
 * Starts on the bundled [DemoLineup]; [restore] swaps in the saved M3U
 * playlist at startup, and [loadM3u] configures a new one. EPG data is only
 * available for the demo lineup until an XMLTV source is added.
 */
class PersistentChannelRepository(
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val fetchPlaylist: suspend (String) -> String = ::httpGet,
) : ChannelRepository {

    private val lineup = MutableStateFlow(DemoLineup.channels())
    private val demoEpg = DemoLineup.epg()

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
        val now = System.currentTimeMillis()
        val programs = demoEpg[channelId].orEmpty().sortedBy { it.startMillis }
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
        val parsed = M3uParser.parse(fetchPlaylist(playlistUrl))
        if (parsed.isNotEmpty()) {
            lineup.value = parsed
            prefs.setPlaylistUrl(playlistUrl)
        }
    }

    /** Re-applies the persisted playlist; network errors keep the demo lineup. */
    fun restore() {
        scope.launch {
            prefs.playlistUrl.first()?.let { url ->
                runCatching { loadM3u(url) }
            }
        }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    URL(url).readText()
}
