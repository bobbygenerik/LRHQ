package com.livingroomhq.translate

import com.livingroomhq.core.data.net.LrhqHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class LiveCaptionSession(
    val id: String,
    val status: String,
)

data class LiveCaptionCue(
    val seq: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class LiveCaptionSnapshot(
    val status: String,
    val activeText: String?,
    val cues: List<LiveCaptionCue>,
)

class LiveCaptionClient(
    private val baseUrl: String,
    private val token: String? = null,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    val isConfigured: Boolean = baseUrl.isNotBlank()

    private fun Request.Builder.withAuth(): Request.Builder = apply {
        token?.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
    }

    suspend fun startSession(
        channelId: String,
        channelName: String,
        streamUrl: String,
        targetLanguage: String = "en",
    ): LiveCaptionSession = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("channelId", channelId)
            .put("channelName", channelName)
            .put("streamUrl", streamUrl)
            .put("targetLanguage", targetLanguage)
            .toString()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/sessions")
            .post(payload.toRequestBody(jsonMediaType))
            .header("User-Agent", "LRHQ LiveCaptionClient")
            .withAuth()
            .build()

        LrhqHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Caption server rejected session (${response.code})")
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            LiveCaptionSession(
                id = json.getString("sessionId"),
                status = json.optString("status", "starting"),
            )
        }
    }

    suspend fun fetchSnapshot(sessionId: String, sinceSeq: Long): LiveCaptionSnapshot =
        withContext(Dispatchers.IO) {
            val encodedId = URLEncoder.encode(sessionId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/sessions/$encodedId/cues?sinceSeq=$sinceSeq")
                .get()
                .header("User-Agent", "LRHQ LiveCaptionClient")
                .withAuth()
                .build()

            LrhqHttpClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Caption server cue fetch failed (${response.code})")
                parseSnapshot(response.body?.string().orEmpty())
            }
        }

    suspend fun stopSession(sessionId: String) {
        withContext(Dispatchers.IO) {
            val encodedId = URLEncoder.encode(sessionId, "UTF-8")
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/sessions/$encodedId")
                .delete()
                .header("User-Agent", "LRHQ LiveCaptionClient")
                .withAuth()
                .build()

            runCatching {
                LrhqHttpClient.client.newCall(request).execute().close()
            }
        }
    }

    private fun parseSnapshot(body: String): LiveCaptionSnapshot {
        val json = JSONObject(body)
        return LiveCaptionSnapshot(
            status = json.optString("status", "running"),
            activeText = json.optString("activeText").takeIf { it.isNotBlank() },
            cues = json.optJSONArray("cues").orEmpty().map { cue ->
                LiveCaptionCue(
                    seq = cue.optLong("seq"),
                    startMs = cue.optLong("startMs"),
                    endMs = cue.optLong("endMs"),
                    text = cue.optString("text"),
                )
            },
        )
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    private fun <T> JSONArray.map(transform: (JSONObject) -> T): List<T> {
        val items = mutableListOf<T>()
        for (index in 0 until length()) {
            optJSONObject(index)?.let { items.add(transform(it)) }
        }
        return items
    }
}
