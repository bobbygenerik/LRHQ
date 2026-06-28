package com.livingroomhq.core.data.net

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object LrhqHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}
