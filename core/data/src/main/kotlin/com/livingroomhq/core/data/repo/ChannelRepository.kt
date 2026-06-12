package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IPTV channel source. The default implementation serves a demo lineup so the
 * launcher is fully navigable before a playlist is configured; [loadM3u]
 * replaces it with a parsed playlist.
 */
interface ChannelRepository {
    val channels: StateFlow<List<Channel>>
    val recents: StateFlow<List<Channel>>
    fun groups(): List<String>
    fun epgNowNext(channelId: String): Pair<Program?, Program?>
    fun markWatched(channelId: String)
    fun toggleFavorite(channelId: String)
    suspend fun loadM3u(playlistUrl: String)
}

class DemoChannelRepository : ChannelRepository {

    private val _channels = MutableStateFlow(DemoLineup.channels())
    override val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _recents = MutableStateFlow<List<Channel>>(emptyList())
    override val recents: StateFlow<List<Channel>> = _recents.asStateFlow()

    private val epg = DemoLineup.epg()

    override fun groups(): List<String> =
        _channels.value.map { it.group }.distinct()

    override fun epgNowNext(channelId: String): Pair<Program?, Program?> {
        val now = System.currentTimeMillis()
        val programs = epg[channelId].orEmpty().sortedBy { it.startMillis }
        val current = programs.firstOrNull { now in it.startMillis until it.endMillis }
        val next = programs.firstOrNull { it.startMillis >= (current?.endMillis ?: now) }
        return current to next
    }

    override fun markWatched(channelId: String) {
        val channel = _channels.value.firstOrNull { it.id == channelId } ?: return
        _recents.value = (listOf(channel) + _recents.value.filterNot { it.id == channelId }).take(8)
    }

    override fun toggleFavorite(channelId: String) {
        _channels.value = _channels.value.map {
            if (it.id == channelId) it.copy(isFavorite = !it.isFavorite) else it
        }
    }

    override suspend fun loadM3u(playlistUrl: String) {
        // Parsing of remote playlists plugs in here; demo build keeps the bundled lineup.
    }
}
