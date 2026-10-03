package ru.colabike.core.network

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * The one OkHttp setup of the app. There is deliberately no logging interceptor: request and
 * response bodies of the `/auth/sessions` endpoints carry tokens, and nothing may put them into
 * Logcat (AGENTS.md).
 */
object HttpClients {
    fun base(config: ApiConfig): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(DefaultHeaders(config.userAgent))
            .build()
}

private class DefaultHeaders(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(
            chain
                .request()
                .newBuilder()
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .build()
        )
}
