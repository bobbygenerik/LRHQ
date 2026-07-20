package com.livingroomhq.backdrop

import com.livingroomhq.BuildConfig
import com.livingroomhq.core.data.fault.runLoggedCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches credited landscape stills from the Unsplash API for the ambient
 * backdrop cycle. The access key comes from [BuildConfig] (sourced from
 * local.properties, never committed). The demo tier allows 50 requests/hour
 * and each batch costs 1 fetch + [count] download pings, so results are cached
 * on disk and refreshed at most every [CACHE_TTL_MILLIS] — without the cache,
 * two launches in an hour exhaust the quota. Every failure falls back to the
 * cached batch, then to the bundled [AmbientBackdrops]. Each photo carries its
 * photographer + profile link so the UI can satisfy Unsplash's attribution
 * requirement.
 */
object UnsplashClient {

    private const val UTM = "?utm_source=LivingRoomHQ&utm_medium=referral"
    private const val CACHE_FILE_NAME = "unsplash_backdrops.json"
    private const val CACHE_TTL_MILLIS = 12 * 60 * 60 * 1000L

    suspend fun fetchLandscapePhotos(
        cacheDir: File? = null,
        count: Int = 24,
        query: String = "landscape,nature,cinematic,aerial",
    ): List<AmbientPhoto> = withContext(Dispatchers.IO) {
        val key = BuildConfig.UNSPLASH_ACCESS_KEY
        if (key.isBlank()) return@withContext emptyList()

        val cacheFile = cacheDir?.let { File(it, CACHE_FILE_NAME) }
        readCache(cacheFile, maxAgeMillis = CACHE_TTL_MILLIS)?.let { return@withContext it }

        runLoggedCatching("unsplash_fetch") {
            val q = URLEncoder.encode(query, "UTF-8")
            val endpoint = URL(
                "https://api.unsplash.com/photos/random" +
                    "?orientation=landscape&count=$count&query=$q&client_id=$key",
            )
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept-Version", "v1")
                connectTimeout = 8_000
                readTimeout = 8_000
            }
            try {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                val photos = ArrayList<AmbientPhoto>(arr.length())
                val downloadUrls = ArrayList<String>(arr.length())
                for (i in 0 until arr.length()) {
                    val photo = arr.getJSONObject(i)
                    val raw = photo.getJSONObject("urls").getString("raw")
                    val user = photo.getJSONObject("user")
                    val name = user.optString("name").takeIf { it.isNotBlank() }
                    val profile = user.optJSONObject("links")?.optString("html")?.takeIf { it.isNotBlank() }
                    photos.add(
                        AmbientPhoto(
                            url = "$raw&w=1920&q=80&fm=jpg&fit=crop",
                            photographer = name,
                            profileUrl = profile?.let { it + UTM },
                        ),
                    )
                    downloadUrls.add(photo.getJSONObject("links").getString("download_location"))
                }
                pingDownloadsParallel(downloadUrls, key)
                writeCache(cacheFile, photos)
                photos
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            // Stale cache beats no backdrops when the network or quota fails.
            readCache(cacheFile, maxAgeMillis = Long.MAX_VALUE) ?: emptyList()
        }
    }

    private fun readCache(cacheFile: File?, maxAgeMillis: Long): List<AmbientPhoto>? {
        if (cacheFile == null || !cacheFile.isFile) return null
        return runCatching {
            val json = JSONObject(cacheFile.readText())
            val age = System.currentTimeMillis() - json.optLong("fetchedAt")
            if (age !in 0..maxAgeMillis) return null
            val arr = json.getJSONArray("photos")
            val photos = ArrayList<AmbientPhoto>(arr.length())
            for (i in 0 until arr.length()) {
                val photo = arr.getJSONObject(i)
                photos.add(
                    AmbientPhoto(
                        url = photo.getString("url"),
                        photographer = photo.optString("photographer").takeIf { it.isNotBlank() },
                        profileUrl = photo.optString("profileUrl").takeIf { it.isNotBlank() },
                    ),
                )
            }
            photos.ifEmpty { null }
        }.getOrNull()
    }

    private fun writeCache(cacheFile: File?, photos: List<AmbientPhoto>) {
        if (cacheFile == null) return
        runCatching {
            val arr = JSONArray()
            photos.forEach { photo ->
                arr.put(
                    JSONObject()
                        .put("url", photo.url)
                        .put("photographer", photo.photographer.orEmpty())
                        .put("profileUrl", photo.profileUrl.orEmpty()),
                )
            }
            cacheFile.writeText(
                JSONObject()
                    .put("fetchedAt", System.currentTimeMillis())
                    .put("photos", arr)
                    .toString(),
            )
        }
    }

    private suspend fun pingDownloadsParallel(urls: List<String>, clientId: String) = coroutineScope {
        urls.map { dl ->
            async(Dispatchers.IO) {
                runLoggedCatching("unsplash_download_ping") {
                    val conn = (URL("$dl&client_id=$clientId").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                    }
                    try {
                        conn.inputStream.use { it.readBytes() }
                    } finally {
                        conn.disconnect()
                    }
                }
            }
        }.awaitAll()
    }
}
