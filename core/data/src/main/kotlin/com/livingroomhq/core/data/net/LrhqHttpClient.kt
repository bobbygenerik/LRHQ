package com.livingroomhq.core.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Default cap for playlist / XMLTV ingest (256 MiB). */
const val DEFAULT_INGEST_MAX_BYTES = 256L * 1024 * 1024

object LrhqHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
