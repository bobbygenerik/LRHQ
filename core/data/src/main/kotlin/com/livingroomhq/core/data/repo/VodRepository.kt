package com.livingroomhq.core.data.repo

import android.content.Context
import com.livingroomhq.core.data.json.StreamingJsonReader
import com.livingroomhq.core.data.json.StreamingJsonWriter
import android.util.Log
import com.livingroomhq.core.data.model.Channel
import com.livingroomhq.core.data.model.EpisodeItem
import com.livingroomhq.core.data.model.SeriesCategory
import com.livingroomhq.core.data.model.SeriesDetail
import com.livingroomhq.core.data.model.SeriesItem
import com.livingroomhq.core.data.model.VodCategory
import com.livingroomhq.core.data.model.VodItem
import com.livingroomhq.core.data.net.LrhqHttpClient
import com.livingroomhq.core.data.persist.LauncherPrefsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

interface VodRepository {
    val categories: StateFlow<List<VodCategory>>
    val seriesCategories: StateFlow<List<SeriesCategory>>
        get() = MutableStateFlow(emptyList())
    val isLoading: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    fun refresh()
    suspend fun getSeriesDetail(seriesId: Int): SeriesDetail? = null
}

internal data class XtreamEndpoint(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    val cacheKey: String get() = "$baseUrl|$username"
}

class DefaultVodRepository(
    private val context: Context,
    private val prefs: LauncherPrefsStore,
    private val channelRepository: ChannelRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val fetchXtreamJson: (suspend (String) -> String?)? = null,
) : VodRepository {

    private val _refreshTrigger = MutableStateFlow(0)
    private val _xtreamCategories = MutableStateFlow<List<VodCategory>>(emptyList())
    private val _seriesCategories = MutableStateFlow<List<SeriesCategory>>(emptyList())
    // Starts true: nothing is known until the disk cache and the channel store have been read.
    private val _isLoading = MutableStateFlow(true)
    private var lastLoadedKey: String? = null
    private var activeEndpoint: XtreamEndpoint? = null
    private val seriesDetailCache = ConcurrentHashMap<Int, SeriesDetail>()
    private val seriesItemLookup = ConcurrentHashMap<Int, SeriesItem>()

    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    override val seriesCategories: StateFlow<List<SeriesCategory>> = _seriesCategories.asStateFlow()

    init {
        scope.launch {
            withContext(ioDispatcher) {
                loadDiskCache()?.let { cached ->
                    if (_xtreamCategories.value.isEmpty() && cached.isNotEmpty()) {
                        _xtreamCategories.value = cached
                    }
                }
                loadSeriesDiskCache()?.let { cachedSeries ->
                    if (_seriesCategories.value.isEmpty() && cachedSeries.isNotEmpty()) {
                        _seriesCategories.value = cachedSeries
                        for (cat in cachedSeries) {
                            for (s in cat.series) {
                                seriesItemLookup[s.seriesId] = s
                            }
                        }
                    }
                }
            }

            combine(
                channelRepository.channels,
                channelRepository.channelsLoaded,
                prefs.epgUrl,
                prefs.playlistUrl,
                _refreshTrigger,
            ) { liveChannels, channelsLoaded, epgUrl, playlistUrl, refreshSeq ->
                val endpoints = discoverXtreamEndpoints(liveChannels, epgUrl, playlistUrl)
                Triple(endpoints, refreshSeq, channelsLoaded || liveChannels.isNotEmpty())
            }.collectLatest { (endpoints, refreshSeq, channelsLoaded) ->
                // Until the channel store has been read we can't tell "no playlist" from "not loaded yet".
                if (!channelsLoaded) return@collectLatest
                if (endpoints.isEmpty()) {
                    _isLoading.value = false
                    return@collectLatest
                }
                val primaryKey = endpoints.joinToString(";") { it.cacheKey }
                if (refreshSeq == 0 && primaryKey == lastLoadedKey && _xtreamCategories.value.isNotEmpty() && _seriesCategories.value.isNotEmpty()) {
                    _isLoading.value = false
                    return@collectLatest
                }
                Log.i("VodRepository", "Discovered ${endpoints.size} Xtream endpoints: ${endpoints.map { it.cacheKey }}")
                _isLoading.value = true
                try {
                    for (endpoint in endpoints) {
                        Log.i("VodRepository", "Querying endpoint ${endpoint.cacheKey} for VOD & Series...")
                        val fetchedMovies = if (_xtreamCategories.value.isEmpty() || refreshSeq > 0) {
                            withContext(ioDispatcher) { fetchXtreamVodCatalog(endpoint) }
                        } else {
                            _xtreamCategories.value
                        }
                        val fetchedSeries = if (_seriesCategories.value.isEmpty() || refreshSeq > 0) {
                            withContext(ioDispatcher) { fetchXtreamSeriesCatalog(endpoint) }
                        } else {
                            _seriesCategories.value
                        }

                        Log.i("VodRepository", "Fetched ${fetchedMovies.size} movie categories and ${fetchedSeries.size} series categories")
                        if (fetchedMovies.isNotEmpty() || fetchedSeries.isNotEmpty()) {
                            activeEndpoint = endpoint
                            lastLoadedKey = primaryKey

                            if (fetchedMovies.isNotEmpty()) {
                                _xtreamCategories.value = fetchedMovies
                                withContext(ioDispatcher) { saveDiskCache(fetchedMovies) }
                            }
                            if (fetchedSeries.isNotEmpty()) {
                                _seriesCategories.value = fetchedSeries
                                for (cat in fetchedSeries) {
                                    for (s in cat.series) {
                                        seriesItemLookup[s.seriesId] = s
                                    }
                                }
                                withContext(ioDispatcher) { saveSeriesDiskCache(fetchedSeries) }
                            }
                            break
                        }
                    }
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    override val categories: StateFlow<List<VodCategory>> = combine(
        _xtreamCategories,
        channelRepository.vodChannels,
        channelRepository.channels,
        _refreshTrigger,
    ) { xtreamCats, vodChannels, liveChannels, _ ->
        if (xtreamCats.isNotEmpty()) {
            xtreamCats
        } else {
            val sourceChannels = if (vodChannels.isNotEmpty()) vodChannels else liveChannels
            buildM3uVodCategories(sourceChannels, liveChannels)
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    override fun refresh() {
        lastLoadedKey = null
        seriesDetailCache.clear()
        _refreshTrigger.value += 1
    }

    override suspend fun getSeriesDetail(seriesId: Int): SeriesDetail? = withContext(ioDispatcher) {
        seriesDetailCache[seriesId]?.let { return@withContext it }

        val endpoint = activeEndpoint ?: run {
            val endpoints = discoverXtreamEndpoints(
                channelRepository.channels.value,
                prefs.epgUrl.firstOrNull(),
                prefs.playlistUrl.firstOrNull(),
            )
            endpoints.firstOrNull()
        } ?: return@withContext null

        runCatching {
            val url =
                "${endpoint.baseUrl}/player_api.php?username=${endpoint.username}&password=${endpoint.password}&action=get_series_info&series_id=$seriesId"
            val jsonStr = executeGet(url) ?: return@withContext null
            val seriesItem = seriesItemLookup[seriesId] ?: SeriesItem(
                id = "xtream_series_$seriesId",
                seriesId = seriesId,
                title = "Series $seriesId",
            )
            parseSeriesDetail(endpoint, seriesItem, jsonStr)?.also {
                seriesDetailCache[seriesId] = it
            }
        }.getOrNull()
    }

    private suspend fun fetchXtreamVodCatalog(endpoint: XtreamEndpoint): List<VodCategory> {
        return runCatching {
            val catsUrl =
                "${endpoint.baseUrl}/player_api.php?username=${endpoint.username}&password=${endpoint.password}&action=get_vod_categories"
            val streamsUrl =
                "${endpoint.baseUrl}/player_api.php?username=${endpoint.username}&password=${endpoint.password}&action=get_vod_streams"

            val catsJsonStr = executeGet(catsUrl) ?: return emptyList()
            val streamsJsonStr = executeGet(streamsUrl) ?: return emptyList()

            parseXtreamVodCatalog(endpoint, catsJsonStr, streamsJsonStr)
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchXtreamSeriesCatalog(endpoint: XtreamEndpoint): List<SeriesCategory> {
        return runCatching {
            val catsUrl =
                "${endpoint.baseUrl}/player_api.php?username=${endpoint.username}&password=${endpoint.password}&action=get_series_categories"
            val seriesUrl =
                "${endpoint.baseUrl}/player_api.php?username=${endpoint.username}&password=${endpoint.password}&action=get_series"

            val catsJsonStr = executeGet(catsUrl) ?: return emptyList()
            val seriesJsonStr = executeGet(seriesUrl) ?: return emptyList()

            parseXtreamSeriesCatalog(endpoint, catsJsonStr, seriesJsonStr)
        }.getOrDefault(emptyList())
    }

    private suspend fun executeGet(url: String): String? {
        if (fetchXtreamJson != null) return fetchXtreamJson.invoke(url)
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank() || host == "example.com" || host.endsWith(".example") || host == "s" || host == "x") {
            return null
        }
        return withContext(ioDispatcher) {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", IPTV_USER_AGENT)
                .build()
            LrhqHttpClient.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }
    }

    internal fun loadDiskCache(): List<VodCategory>? = runCatching {
        val dir = context.cacheDir ?: return null
        val file = File(dir, CACHE_FILE_NAME)
        if (!file.exists() || file.length() <= 0L) return null
        val categories = mutableListOf<VodCategory>()
        file.bufferedReader().use { bufferedReader ->
            val json = StreamingJsonReader(bufferedReader)
            json.beginArray()
            while (json.hasNext()) {
                var catId = ""
                var catName = ""
                val items = mutableListOf<VodItem>()
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "id" -> catId = json.nextStringOrNull().orEmpty()
                        "name" -> catName = json.nextStringOrNull().orEmpty()
                        "items" -> {
                            json.beginArray()
                            while (json.hasNext()) {
                                var id = ""
                                var title = ""
                                var description = ""
                                var posterUrl: String? = null
                                var backdropUrl: String? = null
                                var year = 0
                                var rating: String? = null
                                var genre = "Movies"
                                var durationMinutes = 0
                                var streamUrl = ""
                                json.beginObject()
                                while (json.hasNext()) {
                                    when (json.nextName()) {
                                        "id" -> id = json.nextStringOrNull().orEmpty()
                                        "title" -> title = json.nextStringOrNull().orEmpty()
                                        "description" -> description = json.nextStringOrNull().orEmpty()
                                        "posterUrl" -> posterUrl = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "backdropUrl" -> backdropUrl = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "year" -> year = json.nextIntOrDefault(0)
                                        "rating" -> rating = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "genre" -> genre = json.nextStringOrNull()?.ifBlank { "Movies" } ?: "Movies"
                                        "durationMinutes" -> durationMinutes = json.nextIntOrDefault(0)
                                        "streamUrl" -> streamUrl = json.nextStringOrNull().orEmpty()
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                if (id.isNotBlank() && streamUrl.isNotBlank()) {
                                    items.add(
                                        VodItem(
                                            id = id,
                                            title = title,
                                            description = description,
                                            posterUrl = posterUrl,
                                            backdropUrl = backdropUrl,
                                            year = year,
                                            rating = rating,
                                            genre = genre,
                                            durationMinutes = durationMinutes,
                                            streamUrl = streamUrl,
                                        ),
                                    )
                                }
                            }
                            json.endArray()
                        }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (items.isNotEmpty()) {
                    categories.add(
                        VodCategory(
                            id = catId,
                            name = catName,
                            items = items,
                        ),
                    )
                }
            }
            json.endArray()
        }
        categories
    }.getOrNull()

    internal fun saveDiskCache(categories: List<VodCategory>) {
        runCatching {
            val dir = context.cacheDir ?: return
            val file = File(dir, CACHE_FILE_NAME)
            val tmpFile = File(dir, "$CACHE_FILE_NAME.tmp")
            tmpFile.bufferedWriter().use { bufferedWriter ->
                val json = StreamingJsonWriter(bufferedWriter)
                json.beginArray()
                for (cat in categories) {
                    json.beginObject()
                    json.name("id").value(cat.id)
                    json.name("name").value(cat.name)
                    json.name("items").beginArray()
                    for (item in cat.items) {
                        json.beginObject()
                        json.name("id").value(item.id)
                        json.name("title").value(item.title)
                        json.name("description").value(item.description)
                        json.name("posterUrl").value(item.posterUrl.orEmpty())
                        json.name("backdropUrl").value(item.backdropUrl.orEmpty())
                        json.name("year").value(item.year)
                        json.name("rating").value(item.rating.orEmpty())
                        json.name("genre").value(item.genre)
                        json.name("durationMinutes").value(item.durationMinutes)
                        json.name("streamUrl").value(item.streamUrl)
                        json.endObject()
                    }
                    json.endArray()
                    json.endObject()
                }
                json.endArray()
            }
            if (tmpFile.exists()) {
                tmpFile.renameTo(file)
            }
        }
    }

    internal fun loadSeriesDiskCache(): List<SeriesCategory>? = runCatching {
        val dir = context.cacheDir ?: return null
        val file = File(dir, SERIES_CACHE_FILE_NAME)
        if (!file.exists() || file.length() <= 0L) return null
        val categories = mutableListOf<SeriesCategory>()
        file.bufferedReader().use { bufferedReader ->
            val json = StreamingJsonReader(bufferedReader)
            json.beginArray()
            while (json.hasNext()) {
                var catId = ""
                var catName = ""
                val seriesList = mutableListOf<SeriesItem>()
                json.beginObject()
                while (json.hasNext()) {
                    when (json.nextName()) {
                        "id" -> catId = json.nextStringOrNull().orEmpty()
                        "name" -> catName = json.nextStringOrNull().orEmpty()
                        "series" -> {
                            json.beginArray()
                            while (json.hasNext()) {
                                var id = ""
                                var seriesId = 0
                                var title = ""
                                var plot = ""
                                var posterUrl: String? = null
                                var backdropUrl: String? = null
                                var year = 0
                                var rating: String? = null
                                var genre = ""
                                var categoryId = ""
                                var categoryName = ""
                                json.beginObject()
                                while (json.hasNext()) {
                                    when (json.nextName()) {
                                        "id" -> id = json.nextStringOrNull().orEmpty()
                                        "seriesId" -> seriesId = json.nextIntOrDefault(0)
                                        "title" -> title = json.nextStringOrNull().orEmpty()
                                        "plot" -> plot = json.nextStringOrNull().orEmpty()
                                        "posterUrl" -> posterUrl = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "backdropUrl" -> backdropUrl = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "year" -> year = json.nextIntOrDefault(0)
                                        "rating" -> rating = json.nextStringOrNull()?.takeIf { it.isNotBlank() }
                                        "genre" -> genre = json.nextStringOrNull().orEmpty()
                                        "categoryId" -> categoryId = json.nextStringOrNull().orEmpty()
                                        "categoryName" -> categoryName = json.nextStringOrNull().orEmpty()
                                        else -> json.skipValue()
                                    }
                                }
                                json.endObject()
                                if (id.isNotBlank() && seriesId > 0) {
                                    seriesList.add(
                                        SeriesItem(
                                            id = id,
                                            seriesId = seriesId,
                                            title = title,
                                            plot = plot,
                                            posterUrl = posterUrl,
                                            backdropUrl = backdropUrl,
                                            year = year,
                                            rating = rating,
                                            genre = genre,
                                            categoryId = categoryId,
                                            categoryName = categoryName,
                                        ),
                                    )
                                }
                            }
                            json.endArray()
                        }
                        else -> json.skipValue()
                    }
                }
                json.endObject()
                if (seriesList.isNotEmpty()) {
                    categories.add(
                        SeriesCategory(
                            id = catId,
                            name = catName,
                            series = seriesList,
                        ),
                    )
                }
            }
            json.endArray()
        }
        categories
    }.getOrNull()

    internal fun saveSeriesDiskCache(categories: List<SeriesCategory>) {
        runCatching {
            val dir = context.cacheDir ?: return
            val file = File(dir, SERIES_CACHE_FILE_NAME)
            val tmpFile = File(dir, "$SERIES_CACHE_FILE_NAME.tmp")
            tmpFile.bufferedWriter().use { bufferedWriter ->
                val json = StreamingJsonWriter(bufferedWriter)
                json.beginArray()
                for (cat in categories) {
                    json.beginObject()
                    json.name("id").value(cat.id)
                    json.name("name").value(cat.name)
                    json.name("series").beginArray()
                    for (s in cat.series) {
                        json.beginObject()
                        json.name("id").value(s.id)
                        json.name("seriesId").value(s.seriesId)
                        json.name("title").value(s.title)
                        json.name("plot").value(s.plot)
                        json.name("posterUrl").value(s.posterUrl.orEmpty())
                        json.name("backdropUrl").value(s.backdropUrl.orEmpty())
                        json.name("year").value(s.year)
                        json.name("rating").value(s.rating.orEmpty())
                        json.name("genre").value(s.genre)
                        json.name("categoryId").value(s.categoryId)
                        json.name("categoryName").value(s.categoryName)
                        json.endObject()
                    }
                    json.endArray()
                    json.endObject()
                }
                json.endArray()
            }
            if (tmpFile.exists()) {
                tmpFile.renameTo(file)
            }
        }
    }

    companion object {
        private const val CACHE_FILE_NAME = "xtream_vod_cache_v2.json"
        private const val SERIES_CACHE_FILE_NAME = "xtream_series_cache_v2.json"
        private const val MAX_ITEMS_PER_RAIL = 150
        internal const val IPTV_USER_AGENT = "TiviMate/4.7.0 (Linux; Android 11)"

        private val liveStreamCredsRegex =
            Regex("^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/\\d+(?:\\.[a-zA-Z0-9]+)?$")
        private val movieOrSeriesCredsRegex =
            Regex("^(https?://[^/]+)/(?:movie|series)/([^/]+)/([^/]+)/[^/]+$")
        private val titleYearRegex =
            Regex("^(.*?)\\s*\\(((?:19|20)\\d{2})\\)(?:\\s*\\(\\d{4}\\))*\\s*$")

        internal fun discoverXtreamEndpoints(
            liveChannels: List<Channel>,
            epgUrl: String?,
            playlistUrl: String?,
        ): List<XtreamEndpoint> {
            val endpoints = LinkedHashSet<XtreamEndpoint>()

            for (ch in liveChannels.asSequence().take(50)) {
                val m = liveStreamCredsRegex.matchEntire(ch.streamUrl.trim())
                if (m != null) {
                    val base = m.groupValues[1]
                    val user = m.groupValues[2]
                    val pass = m.groupValues[3]
                    if (!user.equals("live", ignoreCase = true) && !user.equals("movie", ignoreCase = true)) {
                        endpoints.add(XtreamEndpoint(base, user, pass))
                        break
                    }
                }
            }

            parseQueryCreds(epgUrl)?.let { endpoints.add(it) }
            parseQueryCreds(playlistUrl)?.let { endpoints.add(it) }

            return endpoints.toList()
        }

        private fun parseQueryCreds(url: String?): XtreamEndpoint? {
            if (url.isNullOrBlank()) return null
            return runCatching {
                val uri = URI(url.trim())
                val scheme = uri.scheme ?: return null
                val authority = uri.RawAuthorityOrAuthority() ?: return null
                val query = uri.rawQuery ?: return null
                val params = query.split('&').associate { pair ->
                    val idx = pair.indexOf('=')
                    if (idx > 0) {
                        pair.substring(0, idx).lowercase() to pair.substring(idx + 1)
                    } else {
                        pair.lowercase() to ""
                    }
                }
                val user = params["username"]?.takeIf { it.isNotBlank() } ?: return null
                val pass = params["password"]?.takeIf { it.isNotBlank() } ?: return null
                XtreamEndpoint("$scheme://$authority", user, pass)
            }.getOrNull()
        }

        private fun URI.RawAuthorityOrAuthority(): String? =
            rawAuthority?.takeIf { it.isNotBlank() } ?: authority?.takeIf { it.isNotBlank() }

        internal fun parseXtreamVodCatalog(
            endpoint: XtreamEndpoint,
            categoriesJson: String,
            streamsJson: String,
        ): List<VodCategory> {
            val catsArray = JSONArray(categoriesJson)
            val categoryNames = LinkedHashMap<String, String>()
            for (i in 0 until catsArray.length()) {
                val obj = catsArray.optJSONObject(i) ?: continue
                val id = obj.optString("category_id").trim()
                val name = obj.optString("category_name").trim()
                if (id.isNotEmpty() && name.isNotEmpty() && !isIgnoredCategoryName(name)) {
                    categoryNames[id] = name
                }
            }

            val streamsArray = JSONArray(streamsJson)
            data class RawStreamRef(
                val obj: JSONObject,
                val addedTimestamp: Long,
            )

            val byCategory = LinkedHashMap<String, MutableList<RawStreamRef>>()
            for (i in 0 until streamsArray.length()) {
                val obj = streamsArray.optJSONObject(i) ?: continue
                val catId = obj.optString("category_id").trim()
                if (!categoryNames.containsKey(catId)) continue
                val streamId = obj.optString("stream_id").trim()
                if (streamId.isEmpty()) continue
                val rawName = obj.optString("name").trim()
                if (rawName.isEmpty()) continue
                val added = obj.optString("added", "0").toLongOrNull() ?: 0L
                byCategory.getOrPut(catId) { ArrayList() }.add(
                    RawStreamRef(obj, added),
                )
            }

            val result = ArrayList<VodCategory>()
            for ((catId, catName) in categoryNames) {
                val rawList = byCategory[catId] ?: continue
                if (rawList.isEmpty()) continue
                val sortedItems = rawList
                    .sortedByDescending { it.addedTimestamp }
                    .take(MAX_ITEMS_PER_RAIL)
                    .map { ref ->
                        val obj = ref.obj
                        val streamId = obj.optString("stream_id").trim()
                        val rawName = obj.optString("name").trim()
                        val ext = obj.optString("container_extension", "mp4").trim().ifEmpty { "mp4" }
                        val icon = obj.optString("stream_icon").trim().takeIf { it.isNotBlank() }
                        val ratingVal = obj.optDouble("rating", 0.0)
                        val ratingStr = if (!ratingVal.isNaN() && ratingVal > 0.0) {
                            String.format(Locale.US, "★ %.1f", ratingVal)
                        } else {
                            null
                        }
                        val (cleanTitle, year) = extractTitleAndYear(rawName)
                        val streamUrl = "${endpoint.baseUrl}/movie/${endpoint.username}/${endpoint.password}/$streamId.$ext"

                        VodItem(
                            id = "xtream_${streamId}",
                            title = cleanTitle,
                            description = buildString {
                                append(catName)
                                if (year > 0) append(" • $year")
                                if (ratingStr != null) append(" • $ratingStr")
                            },
                            posterUrl = icon,
                            backdropUrl = icon,
                            year = year,
                            rating = ratingStr,
                            genre = catName,
                            streamUrl = streamUrl,
                        )
                    }
                result.add(
                    VodCategory(
                        id = "xtream_cat_$catId",
                        name = catName,
                        items = sortedItems,
                    ),
                )
            }
            return result
        }

        internal fun parseXtreamSeriesCatalog(
            endpoint: XtreamEndpoint,
            categoriesJson: String,
            seriesJson: String,
        ): List<SeriesCategory> {
            val catsArray = JSONArray(categoriesJson)
            val categoryNames = LinkedHashMap<String, String>()
            for (i in 0 until catsArray.length()) {
                val obj = catsArray.optJSONObject(i) ?: continue
                val id = obj.optString("category_id").trim()
                val name = obj.optString("category_name").trim()
                if (id.isNotEmpty() && name.isNotEmpty() && !isIgnoredCategoryName(name)) {
                    categoryNames[id] = name
                }
            }

            val seriesArray = JSONArray(seriesJson)
            data class RawSeriesRef(
                val obj: JSONObject,
                val lastModified: Long,
            )

            val byCategory = LinkedHashMap<String, MutableList<RawSeriesRef>>()
            for (i in 0 until seriesArray.length()) {
                val obj = seriesArray.optJSONObject(i) ?: continue
                val catId = obj.optString("category_id").trim()
                if (!categoryNames.containsKey(catId)) continue
                val seriesId = obj.optInt("series_id", 0)
                if (seriesId <= 0) continue
                val rawName = obj.optString("name").trim()
                if (rawName.isEmpty()) continue
                val lastMod = obj.optString("last_modified", "0").toLongOrNull() ?: 0L
                byCategory.getOrPut(catId) { ArrayList() }.add(
                    RawSeriesRef(obj, lastMod),
                )
            }

            val result = ArrayList<SeriesCategory>()
            for ((catId, catName) in categoryNames) {
                val rawList = byCategory[catId] ?: continue
                if (rawList.isEmpty()) continue
                val sortedSeries = rawList
                    .sortedByDescending { it.lastModified }
                    .take(MAX_ITEMS_PER_RAIL)
                    .map { ref ->
                        val obj = ref.obj
                        val seriesId = obj.optInt("series_id", 0)
                        val rawName = obj.optString("name").trim()
                        val (cleanTitle, year) = extractTitleAndYear(rawName)
                        val releaseYear = obj.optString("releaseDate").take(4).toIntOrNull() ?: year
                        val cover = obj.optString("cover").trim().takeIf { it.isNotBlank() }
                        val backdrop = obj.optJSONArray("backdrop_path")?.let { arr ->
                            if (arr.length() > 0) arr.optString(0) else null
                        }?.takeIf { it.isNotBlank() } ?: cover

                        val ratingVal = obj.optDouble("rating", 0.0)
                        val ratingStr = if (!ratingVal.isNaN() && ratingVal > 0.0) {
                            String.format(Locale.US, "★ %.1f", ratingVal)
                        } else null

                        val plot = obj.optString("plot", "").trim()
                        val genre = obj.optString("genre", "").trim().ifEmpty { catName }

                        SeriesItem(
                            id = "xtream_series_$seriesId",
                            seriesId = seriesId,
                            title = cleanTitle,
                            plot = plot,
                            posterUrl = cover,
                            backdropUrl = backdrop,
                            year = releaseYear,
                            rating = ratingStr,
                            genre = genre,
                            categoryId = catId,
                            categoryName = catName,
                        )
                    }
                result.add(
                    SeriesCategory(
                        id = "xtream_series_cat_$catId",
                        name = catName,
                        series = sortedSeries,
                    ),
                )
            }
            return result
        }

        internal fun parseSeriesDetail(
            endpoint: XtreamEndpoint,
            series: SeriesItem,
            detailJson: String,
        ): SeriesDetail? = runCatching {
            val root = JSONObject(detailJson)
            val episodesObj = root.optJSONObject("episodes") ?: return null
            val seasonsList = mutableListOf<Int>()
            val episodesBySeason = LinkedHashMap<Int, MutableList<EpisodeItem>>()

            val seasonKeys = episodesObj.keys()
            val sortedSeasonNums = mutableListOf<Int>()
            while (seasonKeys.hasNext()) {
                val key = seasonKeys.next()
                key.toIntOrNull()?.let { sortedSeasonNums.add(it) }
            }
            sortedSeasonNums.sort()

            for (seasonNum in sortedSeasonNums) {
                val epArray = episodesObj.optJSONArray(seasonNum.toString()) ?: continue
                if (epArray.length() == 0) continue
                seasonsList.add(seasonNum)
                val epList = episodesBySeason.getOrPut(seasonNum) { ArrayList() }

                for (i in 0 until epArray.length()) {
                    val epObj = epArray.optJSONObject(i) ?: continue
                    val id = epObj.optString("id").trim()
                    if (id.isEmpty()) continue
                    val epNum = epObj.optInt("episode_num", i + 1)
                    val rawTitle = epObj.optString("title", "").trim()
                    val title = rawTitle.ifEmpty { "Episode $epNum" }
                    val ext = epObj.optString("container_extension", "mkv").trim().ifEmpty { "mkv" }
                    val streamUrl = "${endpoint.baseUrl}/series/${endpoint.username}/${endpoint.password}/$id.$ext"

                    val infoObj = epObj.optJSONObject("info")
                    val epPlot = infoObj?.optString("plot", "")?.trim().orEmpty()
                    val epPoster = infoObj?.optString("movie_image", "")?.trim()?.takeIf { it.isNotBlank() } ?: series.posterUrl
                    val durationSecs = infoObj?.optInt("duration_secs", 0) ?: 0
                    val durationMins = if (durationSecs > 0) durationSecs / 60 else 0

                    epList.add(
                        EpisodeItem(
                            id = "xtream_ep_$id",
                            episodeNum = epNum,
                            seasonNum = seasonNum,
                            title = title,
                            plot = epPlot,
                            posterUrl = epPoster,
                            durationMinutes = durationMins,
                            streamUrl = streamUrl,
                        ),
                    )
                }
            }

            if (seasonsList.isEmpty()) return null
            SeriesDetail(
                series = series,
                seasons = seasonsList,
                episodesBySeason = episodesBySeason,
            )
        }.getOrNull()

        internal fun buildM3uVodCategories(
            channels: List<Channel>,
            liveReferenceChannels: List<Channel> = channels,
        ): List<VodCategory> {
            val result = mutableListOf<VodCategory>()

            val activeLiveUsersByHost = mutableMapOf<String, String>()
            for (ch in liveReferenceChannels.asSequence().take(50)) {
                val m = liveStreamCredsRegex.matchEntire(ch.streamUrl.trim())
                if (m != null) {
                    activeLiveUsersByHost[m.groupValues[1].lowercase()] = m.groupValues[2]
                }
            }

            val vodChannels = channels.filter { ch ->
                val g = ch.group.lowercase().trim()
                val url = ch.streamUrl.lowercase().trim()

                if (isIgnoredCategoryName(g)) return@filter false
                if (url.contains("/live/") || url.endsWith(".ts") || url.endsWith(".m3u8")) {
                    return@filter false
                }

                val credsMatch = movieOrSeriesCredsRegex.matchEntire(ch.streamUrl.trim())
                if (credsMatch != null) {
                    val host = credsMatch.groupValues[1].lowercase()
                    val vodUser = credsMatch.groupValues[2]
                    val activeUser = activeLiveUsersByHost[host]
                    if (activeUser != null && !activeUser.equals(vodUser, ignoreCase = true)) {
                        return@filter false
                    }
                }

                val hasVodUrl = url.endsWith(".mp4") || url.endsWith(".mkv") || url.endsWith(".avi") ||
                    url.endsWith(".mov") || url.endsWith(".m4v") || url.endsWith(".flv") ||
                    url.contains("/movie/") || url.contains("/series/") || url.contains("/vod/")

                val hasVodGroup = g.contains("vod") || g.contains("movie") || g.contains("cinema") ||
                    g.contains("film") || g.contains("series") || g.contains("cine") ||
                    g.contains("filmes") || g.contains("serie") || g.contains("pelicula") ||
                    g.contains("on demand") || g.contains("demand") || g.contains("shows") ||
                    g.contains("documentar") || g.contains("anime") || g.contains("animation")

                hasVodUrl || hasVodGroup
            }

            if (vodChannels.isNotEmpty()) {
                val groupedByGroup = vodChannels.groupBy {
                    val grp = it.group.trim()
                    if (grp.isBlank()) "Movies & Series" else grp
                }
                for ((groupName, chList) in groupedByGroup) {
                    val items = chList.take(MAX_ITEMS_PER_RAIL).map { ch ->
                        val (cleanTitle, year) = extractTitleAndYear(ch.name)
                        val genre = if (ch.group.isNotBlank()) ch.group else "VOD"
                        VodItem(
                            id = "iptv_${ch.id}",
                            title = cleanTitle,
                            description = buildString {
                                append(genre)
                                if (year > 0) append(" • $year")
                            },
                            posterUrl = ch.logoUrl,
                            backdropUrl = ch.logoUrl,
                            year = year,
                            genre = genre,
                            streamUrl = ch.streamUrl,
                        )
                    }
                    result.add(
                        VodCategory(
                            id = "iptv_group_${groupName.hashCode()}",
                            name = groupName,
                            items = items,
                        ),
                    )
                }
            }

            return result
        }

        private fun isIgnoredCategoryName(name: String): Boolean {
            val lower = name.trim().lowercase()
            return lower == "temp" || lower == "unlabeled movies" || lower == "tv series"
        }

        internal fun extractTitleAndYear(rawName: String): Pair<String, Int> {
            val trimmed = rawName.trim()
                .replace("“", "")
                .replace("”", "")
                .trim()
            val match = titleYearRegex.matchEntire(trimmed)
            return if (match != null) {
                val title = match.groupValues[1].trim().ifEmpty { trimmed }
                val year = match.groupValues[2].toIntOrNull() ?: 0
                title to year
            } else {
                trimmed to 0
            }
        }
    }
}
