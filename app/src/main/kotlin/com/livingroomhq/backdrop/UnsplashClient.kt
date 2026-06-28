package com.livingroomhq.backdrop

import com.livingroomhq.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches credited landscape stills from the Unsplash API for the ambient
 * backdrop cycle. The access key comes from [BuildConfig] (sourced from
 * local.properties, never committed). Call sparingly — the demo tier allows 50
 * requests/hour — so the app pulls one batch per launch and caches it. Every
 * failure returns an empty list, leaving the bundled [AmbientBackdrops] as the
 * fallback. Each photo carries its photographer + profile link so the UI can
 * satisfy Unsplash's attribution requirement.
 */
object UnsplashClient {

    private const val UTM = "?utm_source=LivingRoomHQ&utm_medium=referral"

    suspend fun fetchLandscapePhotos(
        count: Int = 24,
        query: String = "landscape,nature,cinematic,aerial",
    ): List<AmbientPhoto> = withContext(Dispatchers.IO) {
        val key = BuildConfig.UNSPLASH_ACCESS_KEY
        if (key.isBlank()) return@withContext emptyList()

        runCatching {
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
            val body = conn.inputStream.bufferedReader().use { it.readText() }

            val arr = JSONArray(body)
            val photos = ArrayList<AmbientPhoto>(arr.length())
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
                // Unsplash API guideline: ping the download endpoint on use.
                runCatching {
                    val dl = photo.getJSONObject("links").getString("download_location")
                    (URL("$dl&client_id=$key").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                    }.inputStream.close()
                }
            }
            photos
        }.getOrDefault(emptyList())
    }
}
