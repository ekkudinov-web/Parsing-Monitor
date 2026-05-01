package com.minenergo.monitor.network

import com.minenergo.monitor.Config
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object HttpClientFactory {

    @Volatile
    private var client: OkHttpClient? = null

    fun get(): OkHttpClient {
        client?.let { return it }
        return synchronized(this) {
            client ?: OkHttpClient.Builder()
                .connectTimeout(Config.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Config.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Config.REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
                .also { client = it }
        }
    }
}
