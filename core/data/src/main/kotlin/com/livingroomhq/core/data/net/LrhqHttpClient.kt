package com.livingroomhq.core.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Default cap for playlist / XMLTV ingest (64 MiB). */
const val DEFAULT_INGEST_MAX_BYTES = 64L * 1024 * 1024

object LrhqHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
