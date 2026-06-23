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
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * [ChannelRepository] with persisted favorites, recents, playlist and EPG.
 * Stores data in Room Database [IptvDao] for offline startup.
 */
class PersistentChannelRepository(
    private val iptvDao: IptvDao,
    private val prefs: LauncherPrefsStore,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val fetchPlaylistStream: suspend (String) -> InputStream = ::httpGetStream,
) : ChannelRepository {

    /** Cache of active guide programmes grouped by channel id. */
    private val loadedEpg = MutableStateFlow<Map<String, List<Program>>>(emptyMap())
    private val loadedEpgAliases = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val loadedEpgAliasIndex = MutableStateFlow<Map<String, String>>(emptyMap())
    private val resolvedGuideChannelIds = ConcurrentHashMap<String, String>()
    private val unresolvedGuideChannelIds = ConcurrentHashMap.newKeySet<String>()
    private val unmappedGuideChannels = ConcurrentHashMap.newKeySet<String>()
    private val dbFallbackRequested = ConcurrentHashMap.newKeySet<String>()
    private val _epgRevision = MutableStateFlow(0L)

    override val epgRevision: StateFlow<Long> = _epgRevision.asStateFlow()

    override val channels: StateFlow<List<Channel>> =
        iptvDao.getChannelsFlow()
            .map { list -> list.map { it.toModel() } }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val recents: StateFlow<List<Channel>> =
        combine(channels, prefs.recents) { list, ids ->
            ids.mapNotNull { id -> list.firstOrNull { it.id == id } }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun groups(): List<String> =
        channels.value.map { it.group }.distinct()

    override fun epgNowNext(channelId: String): Pair<Program?, Program?> {
        val programs = programsForChannel(channelId)
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
            val newFavs = if (channelId in favs) favs - channelId else favs + channelId
            prefs.setFavorites(newFavs)
            iptvDao.updateChannelFavorite(channelId, channelId in newFavs)
        }
    }

    override suspend fun loadM3u(playlistUrl: String) {
        val parsed = withContext(workDispatcher) {
            val stream = fetchPlaylistStream(playlistUrl)
            M3uParser.parse(stream)
        }
        if (parsed.isNotEmpty()) {
            val favs = prefs.favorites.first()
            val entities = parsed.map { channel ->
                ChannelEntity.fromModel(channel.copy(isFavorite = channel.id in favs))
            }
            clearGuideMatchCache()
            iptvDao.replaceChannels(entities)
            prefs.setPlaylistUrl(playlistUrl)
        }
    }

    override suspend fun loadXmltv(epgUrl: String) {
        val now = nowMillis()
        val windowEnd = epgMemoryWindowEnd(now)
        val memoryCachePrograms = mutableListOf<Program>()
        val guideAliases = mutableMapOf<String, Set<String>>()
        val guideDisplayNames = mutableMapOf<String, List<String>>()
        val parsedPrograms = mutableListOf<ProgramEntity>()

        withContext(workDispatcher) {
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
        }
        require(parsedPrograms.isNotEmpty()) { "No programmes found in guide" }

        withContext(workDispatcher) {
            iptvDao.clearPrograms()
            val chunkSize = 5000
            parsedPrograms.chunked(chunkSize).forEach { chunk ->
                iptvDao.insertPrograms(chunk)
            }
        }
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
    }

    private fun applyGuideCache(
        programs: Iterable<Program>,
        guideAliases: Map<String, Set<String>>,
    ) {
        val now = nowMillis()
        loadedEpg.value = groupProgramsByChannel(filterProgramsForMemoryCache(programs, now))
        loadedEpgAliases.value = guideAliases
        loadedEpgAliasIndex.value = buildGuideAliasIndex(guideAliases)
        clearGuideMatchCache()
        _epgRevision.value++
    }

    private suspend fun refreshMemoryEpgFromDatabase() {
        val now = nowMillis()
        val programs = iptvDao.getProgramsInWindow(now, epgMemoryWindowEnd(now))
        if (programs.isEmpty() && loadedEpg.value.isEmpty()) return
        applyGuideCache(programs.map { it.toModel() }, loadedEpgAliases.value)
    }

    private fun scheduleDbFallback(playlistChannelId: String, guideChannelId: String) {
        val requestKey = "$playlistChannelId->$guideChannelId"
        if (!dbFallbackRequested.add(requestKey)) return
        scope.launch(workDispatcher) {
            runCatching {
                val now = nowMillis()
                val programs = iptvDao.getProgramsForChannelInWindow(
                    guideChannelId,
                    now,
                    epgMemoryWindowEnd(now),
                ).map { it.toModel() }
                if (programs.isNotEmpty()) {
                    loadedEpg.value = loadedEpg.value.toMutableMap().apply {
                        put(guideChannelId, programs.sortedBy { it.startMillis })
                    }
                    resolvedGuideChannelIds[playlistChannelId] = guideChannelId
                    unmappedGuideChannels.remove(playlistChannelId)
                    _epgRevision.value++
                } else {
                    unmappedGuideChannels.add(playlistChannelId)
                }
            }
        }
    }

    private fun programsForChannel(channelId: String): List<Program> {
        if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return emptyList()

        val guide = loadedEpg.value
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

        aliases.firstNotNullOfOrNull { alias -> loadedEpgAliasIndex.value[alias] }?.let { matchedGuideId ->
            resolvedGuideChannelIds[channelId] = matchedGuideId
            lookupGuidePrograms(guide, matchedGuideId)?.let { programs ->
                if (programs.isNotEmpty()) return programs
            }
            scheduleDbFallback(channelId, matchedGuideId)
            return emptyList()
        }

        val matchedGuideId = loadedEpgAliases.value.entries.firstOrNull { (guideChannelId, guideAliases) ->
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

    override suspend fun clearXmltv() {
        iptvDao.clearPrograms()
        iptvDao.clearGuideChannels()
        loadedEpg.value = emptyMap()
        loadedEpgAliases.value = emptyMap()
        loadedEpgAliasIndex.value = emptyMap()
        clearGuideMatchCache()
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
            loadedEpg.value = loadedEpg.value.toMutableMap().apply {
                put(resolvedId, programs.sortedBy { it.startMillis })
            }
            _epgRevision.value++
        }
    }

    override suspend fun prefetchEpgForChannels(channelIds: List<String>) {
        val guideChannelIds = channelIds.mapNotNull { channelId ->
            if (channelId in unresolvedGuideChannelIds || channelId in unmappedGuideChannels) return@mapNotNull null
            resolvedGuideChannelIds[channelId]?.let { return@mapNotNull it }
            
            val channel = channels.value.firstOrNull { it.id == channelId } ?: return@mapNotNull null
            val aliases = channel.matchAliases()
            
            aliases.firstNotNullOfOrNull { alias -> loadedEpgAliasIndex.value[alias] }?.let { matchedGuideId ->
                resolvedGuideChannelIds[channelId] = matchedGuideId
                return@mapNotNull matchedGuideId
            }
            
            val matchedGuideId = loadedEpgAliases.value.entries.firstOrNull { (guideChannelId, guideAliases) ->
                guideChannelId.guideMatchKeys().any { it.matchesAnyAlias(aliases) } ||
                    guideAliases.any { it.matchesAnyAlias(aliases) }
            }?.key
            
            if (matchedGuideId == null) {
                unresolvedGuideChannelIds += channelId
                null
            } else {
                resolvedGuideChannelIds[channelId] = matchedGuideId
                matchedGuideId
            }
        }.distinct()
        
        if (guideChannelIds.isEmpty()) return
        
        withContext(workDispatcher) {
            runCatching {
                val now = nowMillis()
                val programs = iptvDao.getProgramsForChannelsInWindow(
                    guideChannelIds,
                    now,
                    epgMemoryWindowEnd(now)
                ).map { it.toModel() }
                
                val grouped = groupProgramsByChannel(programs)
                
                val missingGuideIds = guideChannelIds.filter { it !in grouped }
                if (missingGuideIds.isNotEmpty()) {
                    channelIds.forEach { channelId ->
                        val resolved = resolvedGuideChannelIds[channelId]
                        if (resolved in missingGuideIds) {
                            unmappedGuideChannels.add(channelId)
                        }
                    }
                }
                
                loadedEpg.value = loadedEpg.value.toMutableMap().apply {
                    putAll(grouped)
                }
                _epgRevision.value++
            }
        }
    }

    /** Re-applies the persisted playlist and EPG guide with background retry loop. */
    fun restore() {
        // Load persisted guide metadata + active programmes so EPG works before network refresh.
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
                delay(12 * 60 * 60 * 1000L) // 12 hours
                runCatching { runMaintenance() }
            }
        }

        // Collect playlist URL and reload/clear accordingly
        scope.launch {
            prefs.playlistUrl.collectLatest { url ->
                if (url == null) {
                    iptvDao.clearChannels()
                } else {
                    var delayMillis = 2000L
                    while (true) {
                        try {
                            loadM3u(url)
                            break
                        } catch (e: Exception) {
                            delay(delayMillis)
                            delayMillis = (delayMillis * 2).coerceAtMost(30_000L)
                        }
                    }
                }
            }
        }

        // Collect EPG URL and reload/clear accordingly
        scope.launch {
            prefs.epgUrl.collectLatest { url ->
                if (url == null) {
                    clearXmltv()
                } else {
                    var delayMillis = 2000L
                    while (true) {
                        try {
                            loadXmltv(url)
                            break
                        } catch (e: Exception) {
                            delay(delayMillis)
                            delayMillis = (delayMillis * 2).coerceAtMost(30_000L)
                        }
                    }
                }
            }
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
