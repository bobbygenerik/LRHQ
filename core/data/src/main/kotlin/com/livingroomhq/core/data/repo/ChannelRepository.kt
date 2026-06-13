package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.flow.StateFlow

/**
 * IPTV channel source. A real M3U playlist must be configured before channels
 * are available.
 */
interface ChannelRepository {
    val channels: StateFlow<List<Channel>>
    val recents: StateFlow<List<Channel>>
    fun groups(): List<String>
    fun epgNowNext(channelId: String): Pair<Program?, Program?>
    fun markWatched(channelId: String)
    fun toggleFavorite(channelId: String)
    suspend fun loadM3u(playlistUrl: String)
    suspend fun loadXmltv(epgUrl: String)
    suspend fun clearXmltv()
}
