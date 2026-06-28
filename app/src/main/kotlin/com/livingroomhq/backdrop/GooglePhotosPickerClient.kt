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
    val grantedScopes: String = "",
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
            val requestId = UUID.randomUUID().toString()
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

            val device = requestDeviceCode(requestId)
            val phoneLink = device.verificationUrlComplete
            _state.value = _state.value.copy(
                isBusy = true,
                userCode = device.userCode,
                verificationUrl = device.verificationUrl,
                verificationUrlComplete = phoneLink,
                status = "Waiting for phone sign-in.",
            )

            val token = pollForToken(device)
            requirePickerScope(token)
            _state.value = _state.value.copy(status = "Linking Google Photos…")

            val session = createPickerSessionWithRetry(token.accessToken, requestId)
            _state.value = _state.value.copy(
                pickerUri = session.pickerUri,
                status = "Select photos on your phone, then tap Done.",
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
            error.printStackTrace()
            _state.value = _state.value.copy(
                isBusy = false,
                status = if (sync) {
                    "Album refresh stopped. Existing cache kept."
                } else {
                    "Google Photos Picker stopped."
                },
                error = humanizePickerError(error),
            )
        }
    }

    private fun humanizePickerError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("client_secret", ignoreCase = true) ||
                message.contains("missing required parameter", ignoreCase = true) ->
                "Google requires a client secret for this OAuth client type. " +
                    "Even for TV clients, Google Cloud Console provides a client secret. " +
                    "Please copy the client secret from Google Cloud Console and add it to googlePhotos.clientSecret in local.properties."
            message.contains("Invalid device flow scope", ignoreCase = true) ||
                message.contains("invalid_scope", ignoreCase = true) ->
                "This TV OAuth client can only request profile during sign-in. " +
                    "Photos access is granted on your phone via the full link below — do not add picker scopes in Cloud Console."
            message.contains("Precondition", ignoreCase = true) ||
                message.contains("FAILED_PRECONDITION", ignoreCase = true) ->
                "Couldn't open Google Photos on your phone. Tap the phone link below (not google.com/device alone), " +
                    "sign in, and stay on the phone until Photos opens."
            message.contains("photospicker.mediaitems.readonly", ignoreCase = true) ->
                message
            else -> message.ifBlank { error.javaClass.simpleName }
        }
    }

    private fun requirePickerScope(token: OAuthToken) {
        if (token.grantedScopes.contains(PICKER_SCOPE)) return
        if (token.grantedScopes.isBlank()) return
        error("Photos access wasn't granted. Open the phone link below on your phone and approve access.")
    }

    private fun requestDeviceCode(requestId: String): DeviceCodeResponse {
        // TV device flow only allows openid/profile/email scopes. Picker access is granted on the
        // phone when the user completes sign-in with the same requestId in state (streamlined flow).
        val state = JSONObject()
            .put("requestId", requestId)
            .put("displayName", "LRHQ")
            .toString()
        val json = postForm(
            url = "https://oauth2.googleapis.com/device/code",
            params = mapOf(
                "client_id" to clientId,
                "scope" to DEVICE_FLOW_SCOPE,
                "state" to state,
            ),
        )
        return DeviceCodeResponse(
            deviceCode = json.getString("device_code"),
            userCode = json.getString("user_code"),
            verificationUrl = json.getString("verification_url"),
            verificationUrlComplete = completeVerificationUrl(
                json.getString("verification_url"),
                json.getString("user_code"),
                json.optString("verification_url_complete"),
            ),
            intervalSeconds = json.optLong("interval", 5L),
            expiresInSeconds = json.optLong("expires_in", 900L),
        )
    }

    private suspend fun createPickerSessionWithRetry(accessToken: String, requestId: String): PickerSession {
        var lastError: Throwable? = null
        repeat(6) { attempt ->
            runCatching {
                return createPickerSession(accessToken, requestId)
            }.onFailure { error ->
                lastError = error
                val message = error.message.orEmpty()
                val retriable = message.contains("Precondition", ignoreCase = true) ||
                    message.contains("FAILED_PRECONDITION", ignoreCase = true)
                if (!retriable || attempt == 5) throw error
                delay(2_000L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Picker session failed.")
    }

    private fun completeVerificationUrl(
        verificationUrl: String,
        userCode: String,
        verificationUrlComplete: String,
    ): String {
        if (verificationUrlComplete.isNotBlank()) return verificationUrlComplete
        val base = verificationUrl.trimEnd('/')
        return "$base?user_code=${userCode.urlEncode()}"
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
            val response = postFormResult("https://oauth2.googleapis.com/token", params, throwOnError = false)
            val error = response.optString("error")
            when {
                response.has("access_token") -> {
                    return OAuthToken(
                        accessToken = response.getString("access_token"),
                        grantedScopes = response.optString("scope"),
                    )
                }
                error == "authorization_pending" -> Unit
                error == "slow_down" -> intervalSeconds += 5L
                error.isNotBlank() -> error(error)
            }
        }
        error("Google sign-in timed out.")
    }

    private fun createPickerSession(accessToken: String, requestId: String): PickerSession {
        val json = postJson(
            url = "https://photospicker.googleapis.com/v1/sessions?requestId=${requestId.urlEncode()}",
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

    private fun postFormResult(url: String, params: Map<String, String>, throwOnError: Boolean = true): JSONObject {
        val body = params.entries.joinToString("&") { "${it.key.urlEncode()}=${it.value.urlEncode()}" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return connection.readJson(throwOnError)
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

    private fun HttpURLConnection.readJson(throwOnError: Boolean = true): JSONObject {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (throwOnError && responseCode !in 200..299) {
            val message = runCatching {
                json.optJSONObject("error")?.optString("message")
                    ?: json.optString("error_description")
                    ?: json.optString("error")
            }.getOrNull()?.takeIf { it.isNotBlank() }
            error(message ?: "HTTP $responseCode")
        }
        return json
    }
}

private fun String.urlEncode(): String =
    URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun String.durationSeconds(): Long =
    removeSuffix("s").toDoubleOrNull()?.toLong() ?: 5L

private const val DEVICE_FLOW_SCOPE = "profile"
private const val PICKER_SCOPE = "photospicker.mediaitems.readonly"
