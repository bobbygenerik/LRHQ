package com.livingroomhq.backdrop

import com.livingroomhq.BuildConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.max

data class GooglePhotosPickerState(
    val isConfigured: Boolean = false,
    val isBusy: Boolean = false,
    val userCode: String = "",
    val verificationUrl: String = "",
    val verificationUrlComplete: String = "",
    val pickerUri: String = "",
    val status: String = "Google Photos Picker is not connected.",
    val error: String? = null,
)

private data class DeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val verificationUrlComplete: String,
    val intervalSeconds: Long,
    val expiresInSeconds: Long,
)

private data class OAuthToken(
    val accessToken: String,
)

private data class PickerSession(
    val id: String,
    val pickerUri: String,
    val mediaItemsSet: Boolean,
    val pollIntervalSeconds: Long,
    val timeoutSeconds: Long,
)

class GooglePhotosPickerClient(
    private val cache: AmbientPhotoCacheRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val clientId = BuildConfig.GOOGLE_PHOTOS_CLIENT_ID
    private val clientSecret = BuildConfig.GOOGLE_PHOTOS_CLIENT_SECRET

    private val _state = MutableStateFlow(
        GooglePhotosPickerState(
            isConfigured = clientId.isNotBlank(),
            status = if (clientId.isBlank()) {
                "Add Google Photos credentials to local.properties."
            } else {
                "Ready to connect Google Photos."
            },
        ),
    )
    val state: StateFlow<GooglePhotosPickerState> = _state.asStateFlow()

    suspend fun startPickerImport() = runPickerImport(sync = false)

    suspend fun refreshPickerImport() = runPickerImport(sync = true)

    private suspend fun runPickerImport(sync: Boolean) = withContext(dispatcher) {
        if (clientId.isBlank()) {
            _state.value = _state.value.copy(
                isConfigured = false,
                isBusy = false,
                error = "Missing googlePhotos.clientId in local.properties.",
            )
            return@withContext
        }

        runCatching {
            _state.value = _state.value.copy(
                isBusy = true,
                userCode = "",
                pickerUri = "",
                error = null,
                status = if (sync) {
                    "Requesting Google device code to refresh album..."
                } else {
                    "Requesting Google device code..."
                },
            )

            val device = requestDeviceCode()
            _state.value = _state.value.copy(
                isBusy = true,
                userCode = device.userCode,
                verificationUrl = device.verificationUrl,
                verificationUrlComplete = device.verificationUrlComplete,
                status = "On your phone, open ${device.verificationUrl} and enter ${device.userCode}.",
            )

            val token = pollForToken(device)
            _state.value = _state.value.copy(
                status = if (sync) {
                    "Creating Google Photos picker session for refresh..."
                } else {
                    "Creating Google Photos picker session..."
                },
            )

            val session = createPickerSession(token.accessToken)
            _state.value = _state.value.copy(
                pickerUri = session.pickerUri,
                status = if (sync) {
                    "Open the picker link, re-select your LRHQ album, then tap Done."
                } else {
                    "Open the picker link, select your LRHQ album/photos, then tap Done."
                },
            )

            val completed = pollSession(token.accessToken, session)
            _state.value = _state.value.copy(
                status = if (sync) {
                    "Refreshing cached Google Photos..."
                } else {
                    "Caching selected Google Photos..."
                },
            )

            val picked = listPickedMediaItems(token.accessToken, completed.id)
            val stats = cache.importPickedPhotos(picked, sync = sync)
            deleteSession(token.accessToken, completed.id)

            _state.value = _state.value.copy(
                isBusy = false,
                status = if (sync) {
                    "Album refreshed: ${stats.photoCount} photos · ${stats.sizeMegabytes} MB."
                } else {
                    "Google Photos cached: ${stats.photoCount} photos · ${stats.sizeMegabytes} MB."
                },
                error = null,
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(
                isBusy = false,
                status = if (sync) {
                    "Album refresh stopped. Existing cache kept."
                } else {
                    "Google Photos Picker stopped."
                },
                error = error.localizedMessage ?: error.javaClass.simpleName,
            )
        }
    }

    private fun requestDeviceCode(): DeviceCodeResponse {
        val json = postForm(
            url = "https://oauth2.googleapis.com/device/code",
            params = mapOf(
                "client_id" to clientId,
                "scope" to "https://www.googleapis.com/auth/photospicker.mediaitems.readonly",
            ),
        )
        return DeviceCodeResponse(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.getString("verification_url"),
            verificationUrlComplete = json.optString("verification_url_complete"),
            intervalSeconds = json.optLong("interval", 5L),
            expiresInSeconds = json.optLong("expires_in", 900L),
        )
    }

    private suspend fun pollForToken(device: DeviceCodeResponse): OAuthToken {
        var intervalSeconds = max(device.intervalSeconds, 5L)
        val deadline = System.currentTimeMillis() + device.expiresInSeconds * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)
            val params = buildMap {
                put("client_id", clientId)
                if (clientSecret.isNotBlank()) put("client_secret", clientSecret)
                put("device_code", device.deviceCode)
                put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            }
            val response = postFormResult("https://oauth2.googleapis.com/token", params)
            val error = response.optString("error")
            when {
                response.has("access_token") -> return OAuthToken(response.getString("access_token"))
                error == "authorization_pending" -> Unit
                error == "slow_down" -> intervalSeconds += 5L
                error.isNotBlank() -> error(error)
            }
        }
        error("Google sign-in timed out.")
    }

    private fun createPickerSession(accessToken: String): PickerSession {
        val requestId = UUID.randomUUID().toString()
        val json = postJson(
            url = "https://photospicker.googleapis.com/v1/sessions?requestId=$requestId",
            accessToken = accessToken,
            body = JSONObject().put("pickingConfig", JSONObject().put("maxItemCount", "2000")),
        )
        return json.toPickerSession()
    }

    private suspend fun pollSession(accessToken: String, firstSession: PickerSession): PickerSession {
        var session = firstSession
        val startedAt = System.currentTimeMillis()
        while (!session.mediaItemsSet) {
            val timeoutMillis = session.timeoutSeconds * 1000L
            if (timeoutMillis == 0L || System.currentTimeMillis() - startedAt > timeoutMillis) {
                error("Picker session timed out.")
            }
            delay(max(session.pollIntervalSeconds, 3L) * 1000L)
            session = getJson(
                url = "https://photospicker.googleapis.com/v1/sessions/${session.id}",
                accessToken = accessToken,
            ).toPickerSession()
        }
        return session
    }

    private fun listPickedMediaItems(accessToken: String, sessionId: String): List<AmbientPhotoCacheSource> {
        val result = mutableListOf<AmbientPhotoCacheSource>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("https://photospicker.googleapis.com/v1/mediaItems")
                append("?sessionId=").append(sessionId.urlEncode())
                append("&pageSize=100")
                pageToken?.let { append("&pageToken=").append(it.urlEncode()) }
            }
            val json = getJson(url, accessToken)
            val items = json.optJSONArray("mediaItems")
            if (items != null) {
                repeat(items.length()) { index ->
                    val item = items.optJSONObject(index) ?: return@repeat
                    val mediaFile = item.optJSONObject("mediaFile")
                    val mimeType = mediaFile?.optString("mimeType").orEmpty()
                    if (!mimeType.startsWith("image/")) return@repeat
                    val baseUrl = mediaFile?.optString("baseUrl").orEmpty().ifBlank {
                        item.optString("baseUrl")
                    }
                    if (baseUrl.isNotBlank()) {
                        result += AmbientPhotoCacheSource(
                            id = item.optString("id").ifBlank { baseUrl },
                            url = baseUrl,
                            bearerToken = accessToken,
                        )
                    }
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)
        return result
    }

    private fun deleteSession(accessToken: String, sessionId: String) {
        runCatching {
            val connection = (URL("https://photospicker.googleapis.com/v1/sessions/${sessionId.urlEncode()}").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Authorization", "Bearer $accessToken")
            }
            connection.inputStream.use { it.readBytes() }
        }
    }

    private fun JSONObject.toPickerSession(): PickerSession {
        val polling = optJSONObject("pollingConfig")
        return PickerSession(
            id = getString("id"),
            pickerUri = getString("pickerUri"),
            mediaItemsSet = optBoolean("mediaItemsSet", false),
            pollIntervalSeconds = polling?.optString("pollInterval")?.durationSeconds() ?: 5L,
            timeoutSeconds = polling?.optString("timeoutIn")?.durationSeconds() ?: 900L,
        )
    }

    private fun postForm(url: String, params: Map<String, String>): JSONObject {
        val json = postFormResult(url, params)
        json.optString("error").takeIf { it.isNotBlank() }?.let { error(it) }
        return json
    }

    private fun postFormResult(url: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { "${it.key.urlEncode()}=${it.value.urlEncode()}" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return connection.readJson()
    }

    private fun postJson(url: String, accessToken: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        return connection.readJson()
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return connection.readJson()
    }

    private fun HttpURLConnection.readJson(): JSONObject {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (responseCode !in 200..299) {
            val error = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
            error(error ?: "HTTP $responseCode")
        }
        return JSONObject(text)
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.durationSeconds(): Long =
    removeSuffix("s").toDoubleOrNull()?.toLong() ?: 5L
