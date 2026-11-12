package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * IPTV channel source. A real M3U playlist must be configured before channels
 * are available.
 */
interface ChannelRepository {
    val channels: StateFlow<List<Channel>>
    val vodChannels: StateFlow<List<Channel>>
        get() = channels
    /** True once the persisted channel list has been read at least once (empty may then be real). */
    val channelsLoaded: StateFlow<Boolean>
        get() = MutableStateFlow(true)
    val recents: StateFlow<List<Channel>>
    /** Distinct M3U group titles, maintained when the channel list changes. */
    val groups: StateFlow<List<String>>
    /** Channels indexed by `group-title` for fast category filtering. */
    val channelsByGroup: StateFlow<Map<String, List<Channel>>>
    /** Bumps when the in-memory EPG cache is rebuilt (for lazy UI refresh). */
    val epgRevision: StateFlow<Long>
    fun epgNowNext(channelId: String): Pair<Program?, Program?>
    /** Cached, conservatively matched schedule; never performs database work on the UI thread. */
    fun epgSchedule(channelId: String, startMillis: Long, endMillis: Long): List<Program>
    fun markWatched(channelId: String)
    fun toggleFavorite(channelId: String)
    suspend fun loadM3u(playlistUrl: String)
    suspend fun loadXmltv(epgUrl: String)
    suspend fun clearXmltv()
    suspend fun runMaintenance()
    suspend fun fetchEpgDetails(channelId: String)
    suspend fun prefetchEpgForChannels(channelIds: List<String>)
    /** Builds the home "On now" rail off the main thread. */
    suspend fun computeOnNowRail(excludeChannelId: String?, resultLimit: Int = 20): List<Pair<Channel, Program>>
}
