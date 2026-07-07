package com.livingroomhq.core.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

data class HttpSyncMetadata(
    val etag: String? = null,
    val lastModified: String? = null,
)

sealed class ConditionalFetchResult {
    data object NotModified : ConditionalFetchResult()
    data class Modified(
        val stream: InputStream,
        val metadata: HttpSyncMetadata,
    ) : ConditionalFetchResult()
}

private const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

suspend fun conditionalHttpFetch(
    url: String,
    prior: HttpSyncMetadata?,
): ConditionalFetchResult = withContext(Dispatchers.IO) {
    val builder = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
    prior?.etag?.let { builder.header("If-None-Match", it) }
    prior?.lastModified?.let { builder.header("If-Modified-Since", it) }

    val response = LrhqHttpClient.client.newCall(builder.build()).execute()
    if (response.code == 304) {
        response.close()
        return@withContext ConditionalFetchResult.NotModified
    }
    if (!response.isSuccessful) {
        response.close()
        throw IOException("HTTP ${response.code} for $url")
    }
    val body = response.body ?: run {
        response.close()
        throw IOException("Empty response body for $url")
    }
    val metadata = HttpSyncMetadata(
        etag = response.header("ETag"),
        lastModified = response.header("Last-Modified"),
    )
    val raw = LimitedInputStream(body.byteStream(), DEFAULT_INGEST_MAX_BYTES)
    val contentEncoding = response.header("Content-Encoding").orEmpty()
    val stream = if (contentEncoding.equals("gzip", ignoreCase = true) || url.endsWith(".gz", ignoreCase = true)) {
        GZIPInputStream(raw)
    } else {
        raw
    }
    ConditionalFetchResult.Modified(stream, metadata)
}
