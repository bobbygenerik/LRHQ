package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.db.ChannelEntity
import com.livingroomhq.core.data.db.GuideChannelEntity
import com.livingroomhq.core.data.db.IptvDao
import com.livingroomhq.core.data.db.ProgramEntity
import com.livingroomhq.core.data.iptv.M3uParser
import com.livingroomhq.core.data.iptv.XmltvParser
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.Program
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/** Max channels to resolve/prefetch per Live TV visit — avoids freezing on huge playlists. */
private const val EPG_PREFETCH_CHANNEL_CAP = 48

/** Size of the recents ring buffer. */
private const val RECENTS_CAP = 8

/**
 * [ChannelRepository] with persisted favorites, recents, playlist and EPG.
 *
 * Safe design:
 * - In-memory channel cache avoids redundant Room reads.
 * - All I/O dispatched to [workDispatcher] (background).
 * - Mutex-guarded mutations prevent race conditions on the EPG cache.
 * - Atomic state updates via snapshots rather than in-place mutation.
 * - Startup loading is explicit via [restore].
 */
class PersistentChannelRepository(
    private val iptvDao: IptvDao,
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fetchPlaylistStream: suspend (String) -> InputStream = ::httpGetStream,
) : ChannelRepository {

    private val epgMutex = Mutex()
    private val dbFallbackMutex = Mutex()

    private val _loadedEpg = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    private val _loadedEpgAliases = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val _loadedEpgAliasIndex = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _epgRevision = MutableStateFlow(0L)

    private val resolvedGuideChannelIds = ConcurrentHashMap<String, String>()
    private val unresolvedGuideChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val unmappedGuideChannels = ConcurrentHashMap.newKeySet<String>()
    private val dbFallbackRequested = ConcurrentHashMap.newKeySet<String>()

    override val epgRevision: StateFlow<Long> = _epgRevision.asStateFlow()

    override val channels: StateFlow<List<Channel>> =
        iptvDao.getChannelsFlow()
            .map { list -> list.map { it.toModel() } }
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
        val programs = programsForChannel(channelId)
        if (programs.isEmpty()) return null to null
        val now = nowMillis()
        val current = programs.firstOrNull { now in it.startMillis until it.endMillis }
        val next = programs.firstOrNull { it.startMillis >= (current?.endMillis ?: now) }
        return current to next
    }

    override fun markWatched(channelId: String) {
        scope.launch(workDispatcher) {
            runCatching {
                val current = prefs.recents.first()
                val updated = (listOf(channelId) + current.filterNot { it == channelId }).take(RECENTS_CAP)
                prefs.setRecents(updated)
            }
        }
    }

    override fun toggleFavorite(channelId: String) {
        scope.launch(workDispatcher) {
            runCatching {
                val favs = prefs.favorites.first()
                val newFavs = if (channelId in favs) favs - channelId else favs + channelId
                prefs.setFavorites(newFavs)
                iptvDao.updateChannelFavorite(channelId, channelId in newFavs)
            }
        }
    }

    override suspend fun loadM3u(playlistUrl: String) = withContext(workDispatcher) {
        runCatching {
            val stream = fetchPlaylistStream(playlistUrl)
            val parsed = M3uParser.parse(stream)
            if (parsed.isEmpty()) return@runCatching
            val favs = prefs.favorites.first()
            val entities = parsed.map { channel ->
                ChannelEntity.fromModel(channel.copy(isFavorite = channel.id in favs))
            }
            clearGuideMatchCache()
            iptvDao.replaceChannels(entities)
            prefs.setPlaylistUrl(playlistUrl)
        }.onFailure { e ->
            android.util.Log.e("PersistentChannelRepo", "loadM3u failed for $playlistUrl", e)
        }
    }

    override suspend fun loadXmltv(epgUrl: String) = withContext(workDispatcher) {
        runCatching {
            val now = nowMillis()
            val windowEnd = epgMemoryWindowEnd(now)
            val memoryCachePrograms = mutableListOf<Program>()
            val guideAliases = mutableMapOf<String, Set<String>>()
            val guideDisplayNames = mutableMapOf<String, List<String>>()
            val parsedPrograms = mutableListOf<ProgramEntity>()

            XmltvParser.parse(
                inputStream = fetchPlaylistStream(epgUrl),
                onChannelParsed = { id, displayNames ->
                    guideDisplayNames[id] = displayNames
                    guideAliases[id] = guideAliasKeys(id, displayNames)
                },
                onProgramParsed = { program ->
                    parsedPrograms.add(ProgramEntity.fromModel(program))
                    if (program.endMillis > now && program.startMillis < windowEnd) {
                        memoryCachePrograms.add(program)
                    }
                },
            )
            require(parsedPrograms.isNotEmpty()) { "No programmes found in guide" }

            iptvDao.replacePrograms(parsedPrograms)
            ensureGuideAliasesForPrograms(guideAliases, guideDisplayNames, memoryCachePrograms.map { it.channelId })
            iptvDao.replaceGuideChannels(
                guideDisplayNames.map { (id, names) ->
                    GuideChannelEntity.fromAliases(id, names)
                },
            )
            applyGuideCache(
                programs = memoryCachePrograms,
                guideAliases = guideAliases,
            )
            val threshold = now - 24 * 60 * 60 * 1000L
            iptvDao.pruneOldPrograms(threshold)
            prefs.setEpgUrl(epgUrl)
        }.onFailure { e ->
            android.util.Log.e("PersistentChannelRepo", "loadXmltv failed for $epgUrl", e)
        }
    }

    private suspend fun applyGuideCache(
        programs: Iterable<Program>,
        guideAliases: Map<String, Set<String>>,
    ) = epgMutex.withLock {
        val now = nowMillis()
        _loadedEpg.value = groupProgramsByChannel(filterProgramsForMemoryCache(programs, now))
        _loadedEpgAliases.value = guideAliases
        _loadedEpgAliasIndex.value = buildGuideAliasIndex(guideAliases)
        clearGuideMatchCache()
        _epgRevision.value++
    }

    private suspend fun refreshMemoryEpgFromDatabase() = epgMutex.withLock {
        val now = nowMillis()
        val programs = iptvDao.getProgramsInWindow(now, epgMemoryWindowEnd(now))
        if (programs.isEmpty() && _loadedEpg.value.isEmpty()) return@withLock
        _loadedEpg.value = groupProgramsByChannel(filterProgramsForMemoryCache(
            programs.map { Program(it.channelId, it.title, it.startMillis, it.endMillis) },
            now,
        ))
        _epgRevision.value++
    }

    private fun scheduleDbFallback(playlistChannelId: String, guideChannelId: String) {
        val requestKey = "$playlistChannelId->$guideChannelId"
        if (!dbFallbackRequested.add(requestKey)) return
        scope.launch(workDispatcher + SupervisorJob()) {
            runCatching {
                val now = nowMillis()
                val programs = iptvDao.getProgramsForChannelInWindow(
                    guideChannelId,
                    now,
                    epgMemoryWindowEnd(now),
                ).map { it.toModel() }
                if (programs.isNotEmpty()) {
                    val sortedPrograms = programs.sortedBy { it.startMillis }
                    epgMutex.withLock {
                        val currentEpg = _loadedEpg.value
                        if (currentEpg[guideChannelId] != sortedPrograms) {
                            _loadedEpg.value = currentEpg.toMutableMap().apply {
                                put(guideChannelId, sortedPrograms)
                            }
                            _epgRevision.value++
                        }
                        resolvedGuideChannelIds[playlistChannelId] = guideChannelId
                        unmappedGuideChannels.remove(playlistChannelId)
                    }
                } else {
                    unmappedGuideChannels.add(playlistChannelId)
                }
            }
        }
    }

    private fun programsForChannel(channelId: String): List<Program> {
        if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return emptyList()

        val guide = _loadedEpg.value
        lookupGuidePrograms(guide, channelId)?.let { programs ->
            if (programs.isNotEmpty()) return programs
        }
        resolvedGuideChannelIds[channelId]?.let { resolvedId ->
            lookupGuidePrograms(guide, resolvedId)?.let { programs ->
                if (programs.isNotEmpty()) return programs
                scheduleDbFallback(channelId, resolvedId)
            }
            return emptyList()
        }

        val channel = channels.value.firstOrNull { it.id == channelId } ?: return emptyList()
        val aliases = channel.matchAliases()

        aliases.firstNotNullOfOrNull { alias -> _loadedEpgAliasIndex.value[alias] }?.let { matchedGuideId ->
            resolvedGuideChannelIds[channelId] = matchedGuideId
            lookupGuidePrograms(guide, matchedGuideId)?.let { programs ->
                if (programs.isNotEmpty()) return programs
            }
            scheduleDbFallback(channelId, matchedGuideId)
            return emptyList()
        }

        val matchedGuideId = _loadedEpgAliases.value.entries.firstOrNull { (guideChannelId, guideAliases) ->
            guideChannelId.guideMatchKeys().any { it.matchesAnyAlias(aliases) } ||
                guideAliases.any { it.matchesAnyAlias(aliases) }
        }?.key
        if (matchedGuideId == null) {
            unresolvedGuideChannelIds += channelId
        } else {
            resolvedGuideChannelIds[channelId] = matchedGuideId
            lookupGuidePrograms(guide, matchedGuideId)?.let { programs ->
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
            iptvDao.clearPrograms()
            iptvDao.clearGuideChannels()
            _loadedEpg.value = emptyMap()
            _loadedEpgAliases.value = emptyMap()
            _loadedEpgAliasIndex.value = emptyMap()
            clearGuideMatchCache()
        }
        prefs.setEpgUrl(null)
    }

    override suspend fun runMaintenance() {
        val now = nowMillis()
        val threshold = now - 24 * 60 * 60 * 1000L
        iptvDao.pruneOldPrograms(threshold)

        prefs.epgUrl.first()?.let { url ->
            runCatching { loadXmltv(url) }
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
                val currentEpg = _loadedEpg.value
                if (currentEpg[resolvedId] != sortedPrograms) {
                    _loadedEpg.value = currentEpg.toMutableMap().apply {
                        put(resolvedId, sortedPrograms)
                    }
                    _epgRevision.value++
                }
            }
        }
    }

    override suspend fun prefetchEpgForChannels(channelIds: List<String>) {
        if (channelIds.isEmpty()) return
        val cappedIds = channelIds.take(EPG_PREFETCH_CHANNEL_CAP)
        withContext(workDispatcher) {
            val indexSnapshot = _loadedEpgAliasIndex.value
            val guideChannelIds = cappedIds.mapNotNull { channelId ->
                if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return@mapNotNull null
                resolvedGuideChannelIds[channelId]?.let { return@mapNotNull it }

                val channel = channels.value.firstOrNull { it.id == channelId } ?: return@mapNotNull null
                val aliases = channel.matchAliases()

                aliases.firstNotNullOfOrNull { alias -> indexSnapshot[alias] }?.let { matchedGuideId ->
                    resolvedGuideChannelIds[channelId] = matchedGuideId
                    return@mapNotNull matchedGuideId
                }

                unresolvedGuideChannelIds += channelId
                null
            }.distinct()

            if (guideChannelIds.isEmpty()) return@withContext

            runCatching {
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
                            _loadedEpg.value = currentEpg.toMutableMap().apply {
                                putAll(grouped)
                            }
                            _epgRevision.value++
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

    /** Re-applies the persisted playlist and EPG guide with background retry loop. */
    fun restore() {
        scope.launch(workDispatcher) {
            runCatching {
                val guideDisplayNames = iptvDao.getAllGuideChannels()
                    .associate { entity -> entity.id to entity.displayNameList() }
                    .toMutableMap()
                if (guideDisplayNames.isEmpty()) {
                    iptvDao.getDistinctProgramChannelIds().forEach { channelId ->
                        guideDisplayNames.putIfAbsent(channelId, listOf(channelId))
                    }
                }
                val guideAliases = guideDisplayNames.mapValues { (id, names) ->
                    guideAliasKeys(id, names)
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
            while (true) {
                delay(15 * 60 * 1000L)
                runCatching { refreshMemoryEpgFromDatabase() }
            }
        }

        scope.launch(workDispatcher) {
            while (true) {
                delay(12 * 60 * 60 * 1000L)
                runCatching { runMaintenance() }
            }
        }

        // Collect playlist URL and reload/clear accordingly
        scope.launch(workDispatcher) {
            prefs.playlistUrl.collectLatest { url ->
                if (url == null) {
                    iptvDao.clearChannels()
                } else {
                    retryWithBackoff { loadM3u(url) }
                }
            }
        }

        // Collect EPG URL and reload/clear accordingly
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

private suspend fun retryWithBackoff(block: suspend () -> Unit) {
    var delayMillis = 2000L
    while (true) {
        try {
            block()
            break
        } catch (e: Exception) {
            android.util.Log.w("PersistentChannelRepo", "retry failed, retrying in ${delayMillis}ms", e)
            delay(delayMillis)
            delayMillis = (delayMillis * 2).coerceAtMost(30_000L)
        }
    }
}

private fun Channel.matchAliases(): Set<String> =
    buildSet {
        addAll(id.normalizedGuideKeys())
        addAll(id.guideMatchKeys())
        addAll(name.normalizedGuideKeys())
        addAll(name.substringBefore('(').normalizedGuideKeys())
        addAll(name.substringBefore('-').normalizedGuideKeys())
        tvgId?.let {
            addAll(it.normalizedGuideKeys())
            addAll(it.guideMatchKeys())
        }
        tvgName?.let { addAll(it.normalizedGuideKeys()) }
        tvgChno?.let { chno ->
            add(chno)
            add(chno.normalizedGuideKey())
        }
        add(number.toString())
    }.filterTo(mutableSetOf()) { it.isNotEmpty() }

private fun guideAliasKeys(id: String, displayNames: Collection<String>): Set<String> =
    buildSet {
        id.guideMatchKeys().forEach { add(it) }
        displayNames.forEach { name -> addAll(name.normalizedGuideKeys()) }
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
        guideAliases[channelId] = guideAliasKeys(channelId, displayNames)
    }
}

private fun lookupGuidePrograms(
    guide: Map<String, List<Program>>,
    key: String,
): List<Program>? {
    if (key.isBlank()) return null
    guide[key]?.let { return it }
    val stripped = key.substringBefore('@')
    if (stripped != key) {
        guide[stripped]?.let { return it }
    }
    return guide.entries.firstOrNull { (guideId, _) ->
        guideId.equals(key, ignoreCase = true) ||
            guideId.substringBefore('@').equals(key, ignoreCase = true) ||
            (stripped.isNotEmpty() && guideId.equals(stripped, ignoreCase = true))
    }?.value
}

private fun buildGuideAliasIndex(guideAliases: Map<String, Set<String>>): Map<String, String> =
    buildMap {
        guideAliases.forEach { (guideChannelId, aliases) ->
            aliases.forEach { alias -> putIfAbsent(alias, guideChannelId) }
        }
    }

private fun String.guideMatchKeys(): Set<String> {
    val stripped = substringBefore('@')
    return buildSet {
        add(this@guideMatchKeys)
        add(stripped)
        add(normalizedGuideKey())
        if (stripped != this@guideMatchKeys) add(stripped.normalizedGuideKey())
    }.filterTo(mutableSetOf()) { it.isNotEmpty() }
}

private fun String.normalizedGuideKeys(): Set<String> {
    val full = normalizedGuideKey()
    val tokens = split(Regex("[^A-Za-z0-9]+"))
        .map { it.normalizedGuideKey() }
        .filter { it.length >= 3 }
    return (listOf(full) + tokens).filterTo(mutableSetOf()) { it.isNotEmpty() }
}

private fun String.normalizedGuideKey(): String =
    lowercase()
        .replace(Regex("\\b(hd|fhd|uhd|sd|us|usa|east|west)\\b"), " ")
        .replace(Regex("[^a-z0-9]+"), "")

private fun String.matchesAnyAlias(aliases: Set<String>): Boolean =
    isNotEmpty() && aliases.any { alias ->
        this == alias || (length >= 4 && alias.contains(this)) || (alias.length >= 4 && contains(alias))
    }

private suspend fun httpGetStream(url: String): InputStream = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection().apply {
        connectTimeout = 15_000
        readTimeout = 15_000
        setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
    }
    val stream = connection.getInputStream()
    val contentEncoding = connection.contentEncoding.orEmpty()
    if (contentEncoding.equals("gzip", ignoreCase = true) || url.endsWith(".gz", ignoreCase = true)) {
        GZIPInputStream(stream)
    } else {
        stream
    }
}


