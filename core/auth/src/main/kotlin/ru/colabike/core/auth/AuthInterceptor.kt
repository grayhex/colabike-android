package ru.colabike.core.auth

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import ru.colabike.api.infrastructure.Serializer
import ru.colabike.api.models.Error

/**
 * Adds the Bearer token to requests for the API host only (never to images or links elsewhere),
 * refreshes once on 401 `token_expired` and retries the request once; `invalid_token` ends the
 * session and the 401 reaches the caller, which shows sign-in.
 */
class AuthInterceptor(private val session: DeviceSession, private val apiHost: String) :
    Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (
            !request.url.host.equals(apiHost, ignoreCase = true) ||
                request.header(AUTHORIZATION) != null
        ) {
            return chain.proceed(request)
        }
        val token = session.accessTokenForRequest() ?: return chain.proceed(request)
        val response = chain.proceed(request.withBearer(token))
        if (response.code != 401) return response
        return when (response.errorCode()) {
            "token_expired" -> {
                val fresh =
                    try {
                        session.refreshAfter(token)
                    } catch (e: java.io.IOException) {
                        response.close()
                        throw e
                    } ?: return response
                response.close()
                chain.proceed(request.withBearer(fresh))
            }
            "invalid_token" -> {
                session.invalidate(token)
                response
            }
            else -> response
        }
    }

    private fun Request.withBearer(token: String) =
        newBuilder().header(AUTHORIZATION, "Bearer $token").build()

    private fun Response.errorCode(): String? = runCatching {
        Serializer.kotlinxSerializationJson
            .decodeFromString(Error.serializer(), peekBody(MAX_ERROR_BYTES).string())
            .error
            .code
    }
        .getOrNull()

    private companion object {
        const val AUTHORIZATION = "Authorization"
        const val MAX_ERROR_BYTES = 16_384L
    }
}
