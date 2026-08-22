package com.livingroomhq.core.data.repo

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.livingroomhq.core.data.db.ChannelEntity
import com.livingroomhq.core.data.db.GuideChannelEntity
import com.livingroomhq.core.data.db.IptvDao
import com.livingroomhq.core.data.db.ProgramEntity
import com.livingroomhq.core.data.fault.FaultLog
import com.livingroomhq.core.data.fault.runLoggedCatching
import com.livingroomhq.core.data.iptv.M3uParser
import com.livingroomhq.core.data.iptv.XmltvParser
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.net.ConditionalFetchResult
import com.livingroomhq.core.data.net.DEFAULT_INGEST_MAX_BYTES
import com.livingroomhq.core.data.net.LimitedInputStream
import com.livingroomhq.core.data.net.LrhqHttpClient
import com.livingroomhq.core.data.net.HttpSyncMetadata
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/** Max channels to resolve/prefetch per Live TV visit — avoids freezing on huge playlists. */
private const val EPG_PREFETCH_CHANNEL_CAP = 48

/** Size of the recents ring buffer. */
private const val RECENTS_CAP = 8

/** Room upsert batch size for streaming XMLTV ingest. */
private const val EPG_UPSERT_BATCH_SIZE = 500

/** SQLite caps host parameters at 999; stay under it for IN (:ids) queries. */
private const val SQLITE_MAX_BIND_ARGS = 900

/** Ceiling for playlist/EPG retry backoff — do not hammer dead hosts forever. */
private const val RETRY_BACKOFF_MAX_MILLIS = 15 * 60_000L

/**
 * [ChannelRepository] with persisted favorites, recents, playlist and EPG.
 */
class PersistentChannelRepository(
    private val iptvDao: IptvDao,
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Playlist/EPG downloads are read while parsing — keep that off the CPU pool. */
    private val ingestDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fetchPlaylistStream: suspend (String) -> InputStream = ::httpGetStream,
    private val conditionalFetch: (suspend (String, HttpSyncMetadata?) -> ConditionalFetchResult)? = null,
    /** Real DB in production so multi-statement writes run in one Room transaction. */
    private val db: RoomDatabase? = null,
) : ChannelRepository {

    /**
     * Runs multi-statement DB writes atomically. Room guarantees single
     * [androidx.room.Transaction] DAO methods are atomic, but sequences that
     * span several DAO calls (prune + replace, clear programs + guide) must be
     * wrapped here or a crash mid-sequence can leave partial state behind.
     */
    private suspend fun <T> dbWrite(block: suspend () -> T): T =
        if (db != null) db.withTransaction { block() } else block()

    private val epgMutex = Mutex()
    private val recentsMutex = Mutex()
    private val restoreGuard = java.util.concurrent.atomic.AtomicBoolean(false)

    private val _loadedEpg = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    private val _loadedEpgAliases = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val _loadedEpgAliasIndex = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _epgRevision = MutableStateFlow(0L)

    private val resolvedGuideChannelIds = ConcurrentHashMap<String, String>()
    private val unresolvedGuideChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val unmappedGuideChannels = ConcurrentHashMap.newKeySet<String>()
    private val dbFallbackRequested = ConcurrentHashMap.newKeySet<String>()

    private val _nowNextCache = MutableStateFlow<Map<String, Pair<Program?, Program?>>>(emptyMap())
    private var nowNextInvalidationJob: Job? = null

    override val epgRevision: StateFlow<Long> = _epgRevision.asStateFlow()

    private val _channelById = MutableStateFlow<Map<String, Channel>>(emptyMap())

    override val channels: StateFlow<List<Channel>> =
        iptvDao.getChannelsFlow()
            .map { list ->
                val models = list.map { it.toModel() }
                _channelById.value = models.associateBy { it.id }
                models
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val groups: StateFlow<List<String>> =
        channels
            .map { list ->
                list.map { it.group }
                    .filter { it.isNotEmpty() }
                    .distinct()
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val channelsByGroup: StateFlow<Map<String, List<Channel>>> =
        channels
            .map { list -> list.groupBy { channel -> channel.group.ifEmpty { "Other" } } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val recents: StateFlow<List<Channel>> =
        combine(channels, prefs.recents) { list, ids ->
            val byId = list.associateBy { it.id }
            ids.mapNotNull(byId::get)
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun epgNowNext(channelId: String): Pair<Program?, Program?> {
        _nowNextCache.value[channelId]?.let { cached ->
            if (!GuideMatcher.isNowNextStale(cached, nowMillis())) return cached
        }
        val programs = programsForChannel(channelId)
        if (programs.isEmpty()) return null to null
        return GuideMatcher.computeNowNext(programs, nowMillis()).also { computed ->
            val current = _nowNextCache.value
            if (current[channelId] != computed) {
                _nowNextCache.value = current + (channelId to computed)
            }
        }
    }

    override fun markWatched(channelId: String) {
        scope.launch(workDispatcher) {
            recentsMutex.withLock {
                runLoggedCatching("recents") {
                    val current = prefs.recents.first()
                    val updated = (listOf(channelId) + current.filterNot { it == channelId }).take(RECENTS_CAP)
                    prefs.setRecents(updated)
                }
            }
        }
    }

    override fun toggleFavorite(channelId: String) {
        scope.launch(workDispatcher) {
            runLoggedCatching("toggle_favorite") {
                val favs = prefs.favorites.first()
                val newFavs = if (channelId in favs) favs - channelId else favs + channelId
                prefs.setFavorites(newFavs)
                iptvDao.updateChannelFavorite(channelId, channelId in newFavs)
            }
        }
    }

    override suspend fun loadM3u(playlistUrl: String) = withContext(ingestDispatcher) {
        runLoggedCatching("playlist") {
            if (conditionalFetch == null) {
                ingestM3u(fetchPlaylistStream(playlistUrl), playlistUrl, null)
                return@runLoggedCatching
            }
            val prior = prefs.playlistSyncMetadata.first()
            when (val fetch = conditionalFetch(playlistUrl, prior)) {
                ConditionalFetchResult.NotModified -> {
                    if (iptvDao.getChannels().isNotEmpty()) return@runLoggedCatching
                    ingestM3u(fetchPlaylistStream(playlistUrl), playlistUrl, null)
                }
                is ConditionalFetchResult.Modified -> {
                    ingestM3u(fetch.stream, playlistUrl, fetch.metadata)
                }
            }
        }.onSuccess { FaultLog.recordPlaylistSuccess() }
            .onFailure { FaultLog.recordPlaylistFailure(it) }
            .getOrThrow()
    }

    private suspend fun ingestM3u(stream: InputStream, playlistUrl: String, metadata: HttpSyncMetadata?) {
        val parsed = M3uParser.parse(stream)
        if (parsed.isEmpty()) return
        val favs = prefs.favorites.first()
        val entities = parsed.map { channel ->
            ChannelEntity.fromModel(channel.copy(isFavorite = channel.id in favs))
        }
        clearGuideMatchCache()
        iptvDao.syncChannels(entities)
        prefs.setPlaylistUrl(playlistUrl)
        metadata?.let { prefs.setPlaylistSyncMetadata(it) }
    }

    override suspend fun loadXmltv(epgUrl: String) = withContext(ingestDispatcher) {
        runLoggedCatching("guide") {
            if (conditionalFetch == null) {
                ingestXmltv(fetchPlaylistStream(epgUrl), epgUrl, null)
                return@runLoggedCatching
            }
            val prior = prefs.epgSyncMetadata.first()
            when (val fetch = conditionalFetch(epgUrl, prior)) {
                ConditionalFetchResult.NotModified -> {
                    if (_loadedEpg.value.isNotEmpty()) return@runLoggedCatching
                    ingestXmltv(fetchPlaylistStream(epgUrl), epgUrl, null)
                }
                is ConditionalFetchResult.Modified -> {
                    ingestXmltv(fetch.stream, epgUrl, fetch.metadata)
                }
            }
        }.onSuccess { FaultLog.recordGuideSuccess() }
            .onFailure { FaultLog.recordGuideFailure(it) }
            .getOrThrow()
    }

    private suspend fun ingestXmltv(stream: InputStream, epgUrl: String, metadata: HttpSyncMetadata?) {
        val now = nowMillis()
        val windowEnd = epgMemoryWindowEnd(now)
        val memoryCachePrograms = mutableListOf<Program>()
        val guideAliases = mutableMapOf<String, Set<String>>()
        val guideDisplayNames = mutableMapOf<String, List<String>>()
        val batch = ArrayList<ProgramEntity>(EPG_UPSERT_BATCH_SIZE)
        val seenGuideChannelIds = mutableSetOf<String>()
        var programCount = 0

        suspend fun flushBatch() {
            if (batch.isNotEmpty()) {
                iptvDao.insertPrograms(batch.toList())
                batch.clear()
            }
        }

        XmltvParser.parse(
            inputStream = stream,
            onChannelParsed = { id, displayNames ->
                guideDisplayNames[id] = displayNames
                guideAliases[id] = GuideMatcher.guideAliasKeys(id, displayNames)
                seenGuideChannelIds.add(id)
            },
            onProgramParsed = { program ->
                programCount++
                seenGuideChannelIds.add(program.channelId)
                batch.add(ProgramEntity.fromModel(program))
                if (batch.size >= EPG_UPSERT_BATCH_SIZE) flushBatch()
                if (program.endMillis > now && program.startMillis < windowEnd) {
                    memoryCachePrograms.add(program)
                }
            },
        )
        flushBatch()
        require(programCount > 0) { "No programmes found in guide" }

        val threshold = now - 24 * 60 * 60 * 1000L
        ensureGuideAliasesForPrograms(guideAliases, guideDisplayNames, seenGuideChannelIds)
        dbWrite {
            iptvDao.pruneOldPrograms(threshold)
            // Chunked: guides commonly exceed SQLite's 999-bind-variable limit.
            seenGuideChannelIds.toList().chunked(SQLITE_MAX_BIND_ARGS).forEach { chunk ->
                iptvDao.pruneProgramsBeforeWindow(chunk, threshold)
            }
            iptvDao.syncGuideChannels(
                guideDisplayNames.map { (id, names) ->
                    GuideChannelEntity.fromAliases(id, names)
                },
            )
        }
        applyGuideCache(
            programs = memoryCachePrograms,
            guideAliases = guideAliases,
        )
        prefs.setEpgUrl(epgUrl)
        metadata?.let { prefs.setEpgSyncMetadata(it) }
    }

    private suspend fun applyGuideCache(
        programs: Iterable<Program>,
        guideAliases: Map<String, Set<String>>,
    ) = epgMutex.withLock {
        val now = nowMillis()
        _loadedEpg.value = groupProgramsByChannel(filterProgramsForMemoryCache(programs, now))
        _loadedEpgAliases.value = guideAliases
        _loadedEpgAliasIndex.value = GuideMatcher.buildAliasIndex(guideAliases)
        clearGuideMatchCache()
        _epgRevision.value++
        rebuildNowNextCache()
        scheduleNowNextInvalidation()
    }

    /** Called by [com.livingroomhq.core.data.sync.IptvSyncWorker] on a 15-minute cadence. */
    suspend fun refreshMemoryEpgFromDatabase() = epgMutex.withLock {
        val now = nowMillis()
        val programs = iptvDao.getProgramsInWindow(now, epgMemoryWindowEnd(now))
        if (programs.isEmpty() && _loadedEpg.value.isEmpty()) return@withLock
        _loadedEpg.value = groupProgramsByChannel(
            filterProgramsForMemoryCache(
                programs.map {
                    Program(
                        channelId = it.channelId,
                        title = it.title,
                        description = "",
                        startMillis = it.startMillis,
                        endMillis = it.endMillis,
                    )
                },
                now,
            ),
        )
        _epgRevision.value++
        rebuildNowNextCache()
        scheduleNowNextInvalidation()
    }

    private fun scheduleDbFallback(playlistChannelId: String, guideChannelId: String) {
        val requestKey = "$playlistChannelId->$guideChannelId"
        if (!dbFallbackRequested.add(requestKey)) return
        scope.launch(workDispatcher) {
            val result = runLoggedCatching("epg_db_fallback") {
                val now = nowMillis()
                val programs = iptvDao.getProgramsForChannelInWindow(
                    guideChannelId,
                    now,
                    epgMemoryWindowEnd(now),
                ).map { it.toModel() }
                if (programs.isNotEmpty()) {
                    val sortedPrograms = programs.sortedBy { it.startMillis }
                    epgMutex.withLock {
                        mergeEpgChannel(guideChannelId, sortedPrograms)
                        resolvedGuideChannelIds[playlistChannelId] = guideChannelId
                        unmappedGuideChannels.remove(playlistChannelId)
                        updateNowNextForGuide(guideChannelId, sortedPrograms)
                    }
                } else {
                    unmappedGuideChannels.add(playlistChannelId)
                }
            }
            if (result.isFailure) {
                dbFallbackRequested.remove(requestKey)
            }
        }
    }

    private fun mergeEpgChannel(guideChannelId: String, programs: List<Program>) {
        val currentEpg = _loadedEpg.value
        if (currentEpg[guideChannelId] != programs) {
            _loadedEpg.value = currentEpg.toMutableMap().apply { put(guideChannelId, programs) }
            _epgRevision.value++
        }
    }

    private fun updateNowNextForGuide(guideChannelId: String, programs: List<Program>) {
        val now = nowMillis()
        val entry = GuideMatcher.computeNowNext(programs, now)
        val updated = _nowNextCache.value.toMutableMap()
        updated[guideChannelId] = entry
        for ((playlistId, resolvedId) in resolvedGuideChannelIds) {
            if (resolvedId == guideChannelId) updated[playlistId] = entry
        }
        _nowNextCache.value = updated
        scheduleNowNextInvalidation()
    }

    private fun rebuildNowNextCache() {
        val now = nowMillis()
        val epg = _loadedEpg.value
        val cache = mutableMapOf<String, Pair<Program?, Program?>>()
        for ((guideId, programs) in epg) {
            cache[guideId] = GuideMatcher.computeNowNext(programs, now)
        }
        for ((playlistId, guideId) in resolvedGuideChannelIds.entries.toList()) {
            cache[playlistId] = cache[guideId] ?: (null to null)
        }
        _nowNextCache.value = cache
    }

    private fun scheduleNowNextInvalidation() {
        val nextAt = GuideMatcher.nextInvalidationMillis(_nowNextCache.value, nowMillis()) ?: return
        nowNextInvalidationJob?.cancel()
        nowNextInvalidationJob = scope.launch(workDispatcher) {
            delay((nextAt - nowMillis()).coerceAtLeast(0))
            epgMutex.withLock { invalidateStaleNowNext() }
            scheduleNowNextInvalidation()
        }
    }

    private fun invalidateStaleNowNext() {
        val now = nowMillis()
        val epg = _loadedEpg.value
        val updated = _nowNextCache.value.toMutableMap()
        var changed = false
        for ((id, pair) in _nowNextCache.value) {
            if (!GuideMatcher.isNowNextStale(pair, now)) continue
            val programs = epg[id] ?: resolvedGuideChannelIds[id]?.let { epg[it] } ?: continue
            val fresh = GuideMatcher.computeNowNext(programs, now)
            if (fresh != pair) {
                updated[id] = fresh
                changed = true
            }
        }
        for ((playlistId, guideId) in resolvedGuideChannelIds) {
            updated[playlistId] = updated[guideId] ?: (null to null)
        }
        if (changed) {
            _nowNextCache.value = updated
            _epgRevision.value++
        }
    }

    private fun programsForChannel(channelId: String): List<Program> {
        if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return emptyList()

        val guide = _loadedEpg.value
        GuideMatcher.lookupGuidePrograms(guide, channelId)?.let { programs ->
            if (programs.isNotEmpty()) return programs
        }
        resolvedGuideChannelIds[channelId]?.let { resolvedId ->
            GuideMatcher.lookupGuidePrograms(guide, resolvedId)?.let { programs ->
                if (programs.isNotEmpty()) return programs
                scheduleDbFallback(channelId, resolvedId)
            }
            return emptyList()
        }

        val channel = _channelById.value[channelId] ?: return emptyList()
        val matchedGuideId = GuideMatcher.resolveGuideChannelId(
            channel = channel,
            guideAliases = _loadedEpgAliases.value,
            aliasIndex = _loadedEpgAliasIndex.value,
        )
        if (matchedGuideId == null) {
            unresolvedGuideChannelIds += channelId
        } else {
            resolvedGuideChannelIds[channelId] = matchedGuideId
            GuideMatcher.lookupGuidePrograms(guide, matchedGuideId)?.let { programs ->
                if (programs.isNotEmpty()) return programs
                scheduleDbFallback(channelId, matchedGuideId)
            }
        }
        return emptyList()
    }

    private fun clearGuideMatchCache() {
        resolvedGuideChannelIds.clear()
        unresolvedGuideChannelIds.clear()
        unmappedGuideChannels.clear()
        dbFallbackRequested.clear()
    }

    override suspend fun clearXmltv() = withContext(workDispatcher) {
        epgMutex.withLock {
            dbWrite {
                iptvDao.clearPrograms()
                iptvDao.clearGuideChannels()
            }
            _loadedEpg.value = emptyMap()
            _loadedEpgAliases.value = emptyMap()
            _loadedEpgAliasIndex.value = emptyMap()
            clearGuideMatchCache()
            rebuildNowNextCache()
            nowNextInvalidationJob?.cancel()
        }
        prefs.setEpgUrl(null)
    }

    override suspend fun runMaintenance() {
        val now = nowMillis()
        val threshold = now - 24 * 60 * 60 * 1000L
        iptvDao.pruneOldPrograms(threshold)

        prefs.epgUrl.first()?.let { url ->
            runLoggedCatching("guide_maintenance") { loadXmltv(url) }
                .onSuccess { FaultLog.recordGuideSuccess() }
                .onFailure { FaultLog.recordGuideFailure(it) }
        }
    }

    override suspend fun fetchEpgDetails(channelId: String) {
        val resolvedId = resolvedGuideChannelIds[channelId] ?: channelId
        val programs = withContext(workDispatcher) {
            iptvDao.getProgramsForChannelInWindow(
                resolvedId,
                nowMillis(),
                epgMemoryWindowEnd(nowMillis()),
            ).map { it.toModel() }
        }
        if (programs.isNotEmpty()) {
            val sortedPrograms = programs.sortedBy { it.startMillis }
            epgMutex.withLock {
                mergeEpgChannel(resolvedId, sortedPrograms)
                updateNowNextForGuide(resolvedId, sortedPrograms)
            }
        }
    }

    override suspend fun prefetchEpgForChannels(channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        withContext(workDispatcher) {
            val indexSnapshot = _loadedEpgAliasIndex.value
            // Resolve playlist→guide aliases for every requested channel here:
            // the fuzzy fallback scan is far too slow for composition time.
            // Memoizing all of them now means grid cards beyond the DB-prefetch
            // cap below still get cache hits on the main thread.
            val resolved = HashMap<String, String>(channelIds.size)
            channelIds.forEach { channelId ->
                if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return@forEach
                resolvedGuideChannelIds[channelId]?.let {
                    resolved[channelId] = it
                    return@forEach
                }
                val channel = _channelById.value[channelId] ?: return@forEach
                GuideMatcher.resolveGuideChannelId(
                    channel = channel,
                    guideAliases = _loadedEpgAliases.value,
                    aliasIndex = indexSnapshot,
                )?.also { matchedGuideId ->
                    resolvedGuideChannelIds[channelId] = matchedGuideId
                    resolved[channelId] = matchedGuideId
                } ?: run { unresolvedGuideChannelIds += channelId }
            }

            val cappedIds = channelIds.take(EPG_PREFETCH_CHANNEL_CAP)
            val guideChannelIds = cappedIds.mapNotNull { resolved[it] }.distinct()

            if (guideChannelIds.isEmpty()) return@withContext

            runLoggedCatching("epg_prefetch") {
                val now = nowMillis()
                val programs = iptvDao.getProgramsForChannelsInWindow(
                    guideChannelIds,
                    now,
                    epgMemoryWindowEnd(now),
                ).map { it.toModel() }

                val grouped = groupProgramsByChannel(programs)

                val missingGuideIds = guideChannelIds.filter { it !in grouped }
                if (missingGuideIds.isNotEmpty()) {
                    cappedIds.forEach { channelId ->
                        val resolved = resolvedGuideChannelIds[channelId]
                        if (resolved in missingGuideIds) {
                            unmappedGuideChannels.add(channelId)
                        }
                    }
                }

                if (grouped.isNotEmpty()) {
                    epgMutex.withLock {
                        var changed = false
                        val currentEpg = _loadedEpg.value
                        for ((channelId, progs) in grouped) {
                            if (currentEpg[channelId] != progs) {
                                changed = true
                                break
                            }
                        }
                        if (changed) {
                            _loadedEpg.value = currentEpg.toMutableMap().apply { putAll(grouped) }
                            _epgRevision.value++
                            for ((guideId, progs) in grouped) {
                                updateNowNextForGuide(guideId, progs)
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun computeOnNowRail(
        excludeChannelId: String?,
        resultLimit: Int,
    ): List<Pair<Channel, Program>> = withContext(workDispatcher) {
        val channelList = channels.value
        if (channelList.isEmpty()) return@withContext emptyList()
        val recentList = recents.value
        val seen = LinkedHashSet<String>(resultLimit * 2)
        val candidates = ArrayList<Channel>(minOf(40, channelList.size))
        channelList.forEach { channel ->
            if (channel.isFavorite && seen.add(channel.id)) candidates.add(channel)
        }
        recentList.forEach { channel -> if (seen.add(channel.id)) candidates.add(channel) }
        for (channel in channelList) {
            if (candidates.size >= 40) break
            if (seen.add(channel.id)) candidates.add(channel)
        }
        candidates
            .asSequence()
            .filter { it.id != excludeChannelId }
            .mapNotNull { channel -> epgNowNext(channel.id).first?.let { channel to it } }
            .take(resultLimit)
            .toList()
    }

    fun restore() {
        if (!restoreGuard.compareAndSet(false, true)) return
        scope.launch(workDispatcher) {
            runLoggedCatching("restore_epg_cache") {
                val guideDisplayNames = iptvDao.getAllGuideChannels()
                    .associate { entity -> entity.id to entity.displayNameList() }
                    .toMutableMap()
                if (guideDisplayNames.isEmpty()) {
                    iptvDao.getDistinctProgramChannelIds().forEach { channelId ->
                        guideDisplayNames.putIfAbsent(channelId, listOf(channelId))
                    }
                }
                val guideAliases = guideDisplayNames.mapValues { (id, names) ->
                    GuideMatcher.guideAliasKeys(id, names)
                }.toMutableMap()
                val active = iptvDao.getProgramsInWindow(nowMillis(), epgMemoryWindowEnd(nowMillis()))
                ensureGuideAliasesForPrograms(guideAliases, guideDisplayNames, active.map { it.channelId })
                applyGuideCache(
                    programs = active.map { it.toModel() },
                    guideAliases = guideAliases,
                )
            }
        }

        scope.launch(workDispatcher) {
            prefs.playlistUrl.collectLatest { url ->
                if (url == null) {
                    iptvDao.clearChannels()
                } else {
                    retryWithBackoff { loadM3u(url) }
                }
            }
        }

        scope.launch(workDispatcher) {
            prefs.epgUrl.collectLatest { url ->
                if (url == null) {
                    clearXmltv()
                } else {
                    retryWithBackoff { loadXmltv(url) }
                }
            }
        }
    }
}

private const val RETRY_MAX_ATTEMPTS = 10

private suspend fun retryWithBackoff(block: suspend () -> Unit) {
    var delayMillis = 2000L
    repeat(RETRY_MAX_ATTEMPTS) { attempt ->
        try {
            block()
            return
        } catch (e: Exception) {
            FaultLog.record("retry_backoff", e)
            if (attempt == RETRY_MAX_ATTEMPTS - 1) return
            delay(delayMillis)
            delayMillis = (delayMillis * 2).coerceAtMost(RETRY_BACKOFF_MAX_MILLIS)
        }
    }
}

private fun ensureGuideAliasesForPrograms(
    guideAliases: MutableMap<String, Set<String>>,
    guideDisplayNames: MutableMap<String, List<String>>,
    programmeChannelIds: Collection<String>,
) {
    programmeChannelIds.distinct().forEach { channelId ->
        if (channelId.isBlank()) return@forEach
        guideDisplayNames.putIfAbsent(channelId, emptyList())
        val displayNames = guideDisplayNames.getValue(channelId)
        guideAliases[channelId] = GuideMatcher.guideAliasKeys(channelId, displayNames)
    }
}

private suspend fun httpGetStream(url: String): InputStream = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .build()
    val response = LrhqHttpClient.client.newCall(request).execute()
    if (!response.isSuccessful) {
        response.close()
        throw IOException("HTTP ${response.code} for $url")
    }
    val body = response.body ?: run {
        response.close()
        throw IOException("Empty response body for $url")
    }
    val raw = LimitedInputStream(body.byteStream(), DEFAULT_INGEST_MAX_BYTES)
    val contentEncoding = response.header("Content-Encoding").orEmpty()
    if (contentEncoding.equals("gzip", ignoreCase = true) || url.endsWith(".gz", ignoreCase = true)) {
        GZIPInputStream(raw)
    } else {
        raw
    }
}

