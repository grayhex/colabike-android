package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import org.junit.After
import org.junit.Test
import ru.colabike.api.models.CreateSessionRequest
import ru.colabike.api.models.DeviceInput
import ru.colabike.core.model.DataError

class ApiCallTest {
    private val site = TestServer()

    @After fun close() = site.close()

    private suspend fun failure(): Throwable? = runCatching {
        apiCall(Dispatchers.Unconfined) { site.api.account.getMe() }
    }
        .exceptionOrNull()

    @Test
    fun `a dead token means signing in again`() = runTest {
        site.json(401, error("invalid_token"), "WWW-Authenticate", "Bearer error=\"invalid_token\"")
        assertThat(failure()).isInstanceOf(DataError.SignedOut::class.java)
    }

    @Test
    fun `not found, rate limit with Retry-After and server faults with a request id`() = runTest {
        site.json(404, error("not_found"))
        assertThat(failure()).isInstanceOf(DataError.NotFound::class.java)

        site.json(429, error("rate_limited"), "Retry-After", "900")
        assertThat((failure() as DataError.RateLimited).retryAfterSeconds).isEqualTo(900)

        site.json(500, error("internal_error"), "X-Request-ID", "req-123")
        val server = failure() as DataError.Server
        assertThat(server.status).isEqualTo(500)
        assertThat(server.requestId).isEqualTo("req-123")
    }

    @Test
    fun `an unknown error code is kept with its status and message`() = runTest {
        site.json(409, error("brand_new_code", "Новое правило"))
        val rejected = failure() as DataError.Rejected
        assertThat(rejected.status).isEqualTo(409)
        assertThat(rejected.code).isEqualTo("brand_new_code")
        assertThat(rejected.userMessage).isEqualTo("Новое правило")
    }

    @Test
    fun `a body that is not an API error still maps by status`() = runTest {
        site.json(400, "<html>proxy</html>")
        val rejected = failure() as DataError.Rejected
        assertThat(rejected.code).isEqualTo("unknown")
    }

    @Test
    fun `a broken connection is offline, an unreadable answer is unexpected`() = runTest {
        site.server.enqueue(
            MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build()
        )
        assertThat(failure()).isInstanceOf(DataError.Offline::class.java)

        site.json(200, """{"id": 1}""")
        assertThat(failure()).isInstanceOf(DataError.Unexpected::class.java)
    }

    @Test
    fun `optional fields are left out of request bodies, not sent as null`() = runTest {
        site.json(401, error("invalid_credentials"))
        val device =
            DeviceInput(
                name = "Pixel",
                platform = DeviceInput.Platform.android,
                appVersion = "0.1.0",
            )

        runCatching {
            apiCall(Dispatchers.Unconfined) {
                site.api.sessions.createSession(
                    CreateSessionRequest(
                        device = device,
                        email = "a@example.test",
                        password = "secret-1",
                    )
                )
            }
        }

        val body = site.server.takeRequest().body!!.utf8()
        assertThat(body).contains("\"email\"")
        assertThat(body).doesNotContain("\"code\"")
        assertThat(body).doesNotContain("null")
    }
}
