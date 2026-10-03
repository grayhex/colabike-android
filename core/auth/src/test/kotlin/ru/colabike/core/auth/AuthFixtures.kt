package ru.colabike.core.auth

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Headers.Companion.headersOf
import ru.colabike.core.network.ApiConfig
import ru.colabike.core.network.ColaBikeApi
import ru.colabike.core.network.HttpClients
import ru.colabike.core.network.MediaUrls

val NOW: Instant = Instant.parse("2026-10-03T10:00:00Z")

class MemoryStore(var value: String? = null) : SecretStore {
    override fun read() = value

    override fun write(token: String) {
        value = token
    }

    override fun clear() {
        value = null
    }
}

fun grant(n: String, expiresAt: Instant = NOW.plusSeconds(900)) =
    """
    {"session":{"id":"0f0e0d0c-0b0a-4908-8706-050403020100","kind":"device","deviceName":"Test","platform":"android",
      "appVersion":"0.1.0","userAgent":"ColaBike-Android/0.1.0","createdAt":"2026-10-03T09:00:00.000Z",
      "lastSeenAt":"2026-10-03T10:00:00.000Z","current":true},
     "accessToken":"cola_at_$n","accessTokenExpiresAt":"$expiresAt","refreshToken":"cola_rt_$n",
     "refreshTokenExpiresAt":"2026-12-02T10:00:00.000Z",
     "user":{"id":"8d9e0f1a-2b3c-4d5e-8f70-8192a3b4c5d6","username":"test-rider","name":"Тестовый Райдер",
      "email":"rider@example.test","role":"user","bio":"","location":"","avatarUrl":null,
      "createdAt":"2026-09-01T10:00:00.000Z","emailVerifiedAt":null}}
    """
        .trimIndent()

const val ME =
    """{"id":"8d9e0f1a-2b3c-4d5e-8f70-8192a3b4c5d6","username":"test-rider","name":"Тестовый Райдер","email":"rider@example.test","role":"user","bio":"","location":"","avatarUrl":null,"createdAt":"2026-09-01T10:00:00.000Z","emailVerifiedAt":null}"""

fun error(code: String) = """{"error":{"code":"$code","message":"…"}}"""

fun json(status: Int, body: String) =
    MockResponse.Builder()
        .code(status)
        .headers(headersOf("Content-Type", "application/json"))
        .body(body)
        .build()

/**
 * A fake API v1: access tokens are valid when listed in [validAccess], refresh tokens in
 * [refreshes] map to the next grant; counts refreshes to prove single flight.
 */
class FakeApi : AutoCloseable {
    val validAccess = mutableSetOf<String>()
    val expiredAccess = mutableSetOf<String>()
    val refreshes = mutableMapOf<String, String>()
    val refreshCount = AtomicInteger()
    @Volatile var refreshDelayMs = 0L
    @Volatile var dropNextRefreshAnswer = false

    val server =
        MockWebServer().apply {
            dispatcher =
                object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = answer(request)
                }
            start()
        }
    val config = ApiConfig(server.url("/").toString().trimEnd('/'), "0.1.0")
    val media = MediaUrls(config.siteUrl)
    val plain = ColaBikeApi(config, HttpClients.base(config))

    fun session(
        store: SecretStore = MemoryStore(),
        clock: Clock = Clock.fixed(NOW, ZoneOffset.UTC),
    ) = DeviceSession(plain.sessions, store, DeviceInfo("Test", "0.1.0"), media, clock)

    fun authed(session: DeviceSession) =
        ColaBikeApi(
            config,
            HttpClients.base(config)
                .newBuilder()
                .addInterceptor(AuthInterceptor(session, server.hostName))
                .build(),
        )

    private fun answer(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        val bearer = request.headers["Authorization"]?.removePrefix("Bearer ")
        return when {
            path == "/api/v1/auth/sessions" && request.method == "POST" -> {
                val body = request.body!!.utf8()
                if (body.contains("\"password\":\"right-password\""))
                    json(201, grant("A")).also {
                        validAccess += "cola_at_A"
                        refreshes["cola_rt_A"] = "B"
                    }
                else json(401, error("invalid_credentials"))
            }
            path == "/api/v1/auth/sessions/refresh" -> {
                refreshCount.incrementAndGet()
                Thread.sleep(refreshDelayMs)
                val token = Regex("cola_rt_[A-Za-z0-9]+").find(request.body!!.utf8())?.value
                val next = refreshes.remove(token) ?: return json(401, error("invalid_token"))
                validAccess += "cola_at_$next"
                refreshes["cola_rt_$next"] = next + "x"
                if (dropNextRefreshAnswer) {
                    dropNextRefreshAnswer = false
                    // The server handed out a pair, the answer got lost: the same token works once
                    // more.
                    refreshes[token!!] = next
                    return MockResponse.Builder()
                        .onResponseStart(mockwebserver3.SocketEffect.CloseSocket())
                        .build()
                }
                json(200, grant(next))
            }
            path == "/api/v1/auth/sessions/current" && request.method == "DELETE" ->
                if (bearer in validAccess)
                    MockResponse.Builder().code(204).build().also { validAccess -= bearer!! }
                else json(401, error("invalid_token"))
            bearer == null ->
                if (path == "/api/v1/me") json(401, error("unauthorized"))
                else json(200, """{"items":[],"nextCursor":null}""")
            bearer in expiredAccess -> json(401, error("token_expired"))
            bearer in validAccess ->
                if (path == "/api/v1/me") json(200, ME)
                else json(200, """{"items":[],"nextCursor":null}""")
            else -> json(401, error("invalid_token"))
        }
    }

    override fun close() = server.close()
}
