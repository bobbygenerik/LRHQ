package com.livingroomhq.backdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

data class AmbientPhotoCacheStats(
    val photoCount: Int = 0,
    val sizeBytes: Long = 0L,
    val isImporting: Boolean = false,
    val lastMessage: String = "",
) {
    val sizeMegabytes: Long get() = sizeBytes / (1024L * 1024L)
}

data class AmbientPhotoCacheSource(
    val id: String,
    val url: String,
    val bearerToken: String? = null,
)

/**
 * Durable display cache for ambient photos.
 *
 * Google Photos Picker media URLs expire quickly, so the launcher should cache
 * TV-sized display copies and rotate those local files. This repository is the
 * storage layer for that flow; the OAuth/Picker client can feed it selected
 * items once credentials are configured.
 */
class AmbientPhotoCacheRepository(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val cacheDir = File(context.filesDir, "ambient_photo_cache")
    private val maxCacheBytes = 1_000L * 1024L * 1024L
    private val _photos = MutableStateFlow<List<AmbientPhoto>>(emptyList())
    private val _stats = MutableStateFlow(AmbientPhotoCacheStats())

    val photos: StateFlow<List<AmbientPhoto>> = _photos.asStateFlow()
    val stats: StateFlow<AmbientPhotoCacheStats> = _stats.asStateFlow()

    suspend fun restore() = withContext(dispatcher) {
        cacheDir.mkdirs()
        publishState()
    }

    suspend fun importFromText(rawText: String): AmbientPhotoCacheStats = withContext(dispatcher) {
        val urls = extractUrls(rawText)
        if (urls.isEmpty()) {
            val stats = currentStats(isImporting = false, message = "No photo URLs found.")
            _stats.value = stats
            return@withContext stats
        }

        cacheDir.mkdirs()
        _stats.value = currentStats(isImporting = true, message = "Caching ${urls.size} photos...")

        var imported = 0
        var failed = 0
        urls.forEach { rawUrl ->
            runCatching {
                val url = rawUrl.toGoogleDisplayUrl()
                val file = File(cacheDir, "${rawUrl.sha256()}.jpg")
                if (!file.exists()) {
                    downloadResizedJpeg(url, file)
                }
                file.setLastModified(System.currentTimeMillis())
                imported += 1
            }.onFailure {
                failed += 1
            }
        }

        trimToCacheLimit()
        val message = buildString {
            append("Cached $imported photo")
            if (imported != 1) append("s")
            if (failed > 0) append(" · $failed failed")
        }
        publishState(message)
        _stats.value
    }

    suspend fun importPickedPhotos(
        sources: List<AmbientPhotoCacheSource>,
        sync: Boolean = false,
    ): AmbientPhotoCacheStats = withContext(dispatcher) {
        if (sources.isEmpty()) {
            val message = if (sync) "No picked photos found. Existing cache kept." else "No picked photos found."
            val stats = currentStats(isImporting = false, message = message)
            _stats.value = stats
            return@withContext stats
        }

        cacheDir.mkdirs()
        val action = if (sync) "Refreshing" else "Caching"
        _stats.value = currentStats(isImporting = true, message = "$action ${sources.size} Google Photos...")

        if (sync) {
            importPickedPhotosSynced(sources)
        } else {
            importPickedPhotosAdditive(sources)
        }
    }

    private fun importPickedPhotosAdditive(sources: List<AmbientPhotoCacheSource>): AmbientPhotoCacheStats {
        var imported = 0
        var failed = 0
        sources.forEach { source ->
            runCatching {
                cachePhoto(source, cacheDir, skipExisting = true)
                imported += 1
            }.onFailure {
                failed += 1
            }
        }

        trimToCacheLimit()
        val message = buildString {
            append("Cached $imported Google photo")
            if (imported != 1) append("s")
            if (failed > 0) append(" · $failed failed")
        }
        publishState(message)
        return _stats.value
    }

    private fun importPickedPhotosSynced(sources: List<AmbientPhotoCacheSource>): AmbientPhotoCacheStats {
        val stagingDir = File(cacheDir, ".staging").apply {
            mkdirs()
            listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        }

        var imported = 0
        var failed = 0
        sources.forEach { source ->
            runCatching {
                cachePhoto(source, stagingDir)
                imported += 1
            }.onFailure {
                failed += 1
            }
        }

        if (failed > 0 || imported != sources.size) {
            stagingDir.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
            stagingDir.delete()
            val stats = currentStats(
                isImporting = false,
                message = "Refresh failed ($failed failed). Existing cache kept.",
            )
            _stats.value = stats
            return stats
        }

        cachedFiles().forEach { it.delete() }
        stagingDir.listFiles()?.forEach { staged ->
            staged.renameTo(File(cacheDir, staged.name))
        }
        stagingDir.delete()

        trimToCacheLimit()
        publishState("Refreshed album: $imported photos.")
        return _stats.value
    }

    private fun cachePhoto(source: AmbientPhotoCacheSource, targetDir: File, skipExisting: Boolean = false) {
        val file = File(targetDir, "${source.id.sha256()}.jpg")
        if (!skipExisting || !file.exists()) {
            downloadResizedJpeg(source.url.toGoogleDisplayUrl(), file, source.bearerToken)
        }
        file.setLastModified(System.currentTimeMillis())
    }

    suspend fun clear() = withContext(dispatcher) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
        publishState("Google Photos cache cleared.")
    }

    private fun publishState(message: String = _stats.value.lastMessage) {
        val files = cachedFiles()
        _photos.value = files.map { file ->
            AmbientPhoto(
                url = file.toURI().toString(),
                photographer = "Google Photos",
                profileUrl = null,
            )
        }
        _stats.value = AmbientPhotoCacheStats(
            photoCount = files.size,
            sizeBytes = files.sumOf { it.length() },
            isImporting = false,
            lastMessage = message,
        )
    }

    private fun currentStats(isImporting: Boolean, message: String): AmbientPhotoCacheStats {
        val files = cachedFiles()
        return AmbientPhotoCacheStats(
            photoCount = files.size,
            sizeBytes = files.sumOf { it.length() },
            isImporting = isImporting,
            lastMessage = message,
        )
    }

    private fun cachedFiles(): List<File> =
        cacheDir.listFiles { file -> file.isFile && file.extension.equals("jpg", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()

    private fun trimToCacheLimit() {
        var files = cachedFiles()
        var totalBytes = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalBytes <= maxCacheBytes) return
            totalBytes -= file.length()
            file.delete()
        }
    }

    private fun downloadResizedJpeg(url: String, target: File, bearerToken: String? = null) {
        val bytes = httpGetBytes(url, bearerToken)
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight)
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("Unsupported image")

        val resized = bitmap.resizedToFit(1920, 1080)
        target.outputStream().use { out ->
            resized.compress(Bitmap.CompressFormat.JPEG, 84, out)
        }
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
    }

    private fun httpGetBytes(url: String, bearerToken: String? = null): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LRHQ Ambient Cache")
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return connection.inputStream.use { input ->
            ByteArrayOutputStream().use { output ->
                input.copyTo(output)
                output.toByteArray()
            }
        }
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width / sample > 3840 || height / sample > 2160) {
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.resizedToFit(maxWidth: Int, maxHeight: Int): Bitmap {
        val scale = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height, 1f)
        if (scale >= 1f) return this
        return Bitmap.createScaledBitmap(this, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
    }

    private fun extractUrls(rawText: String): List<String> {
        val trimmed = rawText.trim()
        val urls = LinkedHashSet<String>()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { collectJsonUrls(JSONTokenerCompat.parse(trimmed), urls) }
        }
        Regex("""https?://[^\s"',\]}]+""")
            .findAll(rawText)
            .mapTo(urls) { it.value }
        return urls.toList()
    }

    private fun collectJsonUrls(value: Any?, urls: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                value.optString("baseUrl").takeIf { it.startsWith("http") }?.let { urls += it }
                value.keys().forEach { key -> collectJsonUrls(value.opt(key), urls) }
            }
            is JSONArray -> repeat(value.length()) { index -> collectJsonUrls(value.opt(index), urls) }
        }
    }
}

private object JSONTokenerCompat {
    fun parse(raw: String): Any =
        if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONObject(raw)
}

private fun String.toGoogleDisplayUrl(): String {
    if (!contains("googleusercontent.com")) return this
    if (Regex("""[=?-]w\d+""").containsMatchIn(this) || endsWith("=d")) return this
    return "$this=w1920-h1080"
}

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
