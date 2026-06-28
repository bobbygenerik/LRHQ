package com.livingroomhq.backdrop

import com.livingroomhq.BuildConfig
import com.livingroomhq.core.data.net.LrhqHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
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
            val endpoint =
                "https://api.unsplash.com/photos/random" +
                    "?orientation=landscape&count=$count&query=$q&client_id=$key"
            val request = Request.Builder()
                .url(endpoint)
                .header("Accept-Version", "v1")
                .build()
            val body = LrhqHttpClient.client.newCall(request).execute().use { it.body!!.string() }

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
                runCatching {
                    val dl = photo.getJSONObject("links").getString("download_location")
                    val pingRequest = Request.Builder().url("$dl&client_id=$key").build()
                    LrhqHttpClient.client.newCall(pingRequest).execute().close()
                }
            }
            photos
        }.getOrDefault(emptyList())
    }
}
