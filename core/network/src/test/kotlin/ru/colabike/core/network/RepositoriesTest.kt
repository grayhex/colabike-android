package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.DataError
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform

class RepositoriesTest {
    private val site = TestServer()
    private val bikes = NetworkBikesRepository(site.api.bikes, site.media, Dispatchers.Unconfined)
    private val account =
        NetworkAccountRepository(site.api.account, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `a bike page maps to app models with absolute photos and the cursor`() = runTest {
        site.json(200, site.fixture("bike-page.json"))

        val page = bikes.bikes(BikeScope.Mine, cursor = "c1", limit = 2)

        val request = site.server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes")
        assertThat(request.url.queryParameter("scope")).isEqualTo("mine")
        assertThat(request.url.queryParameter("cursor")).isEqualTo("c1")
        assertThat(request.url.queryParameter("limit")).isEqualTo("2")
        assertThat(request.headers["User-Agent"]).isEqualTo("ColaBike-Android/0.1.0-test")
        assertThat(request.headers["Authorization"]).isNull()

        assertThat(page.nextCursor).isEqualTo("eyJjIjoiMjAyNi0xMC0wMyJ9")
        val (first, second) = page.items
        assertThat(first.cover?.url)
            .isEqualTo(site.config.siteUrl + "/api/photos/0b7e6a52?width=640")
        assertThat(first.author?.displayName).isEqualTo("Тестовый Райдер")
        assertThat(first.year).isEqualTo(2020)
        // An unknown year (0) is no year; a bike without photos has no cover.
        assertThat(second.year).isNull()
        assertThat(second.cover).isNull()
        assertThat(second.isFormer).isTrue()
    }

    @Test
    fun `the bike detail keeps unknown sections and drops unsafe photo urls`() = runTest {
        site.json(200, site.fixture("bike.json"))

        val bike = bikes.bike(BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"))

        assertThat(site.server.takeRequest().url.encodedPath)
            .isEqualTo("/api/v1/bikes/6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
        assertThat(bike.summary.author?.displayName).isEqualTo("test-rider")
        assertThat(bike.summary.author?.avatarUrl)
            .isEqualTo(site.config.siteUrl + "/api/avatars/8d9e0f1a")
        assertThat(bike.weightKg).isEqualTo(14.2)
        assertThat(bike.size).isEqualTo("L")
        assertThat(bike.components.map { it.section }).containsExactly("build", "other").inOrder()
        assertThat(bike.photos).hasSize(1)
    }

    @Test
    fun `a malformed bike id is not found without a request`() = runTest {
        val failure = runCatching { bikes.bike(BikeId("not-a-uuid")) }.exceptionOrNull()
        assertThat(failure).isInstanceOf(ru.colabike.core.model.DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `me keeps the e-mail out of the app model`() = runTest {
        site.json(200, site.fixture("me.json"))

        val me = account.me()

        assertThat(me.displayName).isEqualTo("Тестовый Райдер")
        assertThat(me.emailVerified).isFalse()
        assertThat(me.toString()).doesNotContain("rider@example.test")
    }
}

class AccountSessionsRepositoryTest {
    private val site = TestServer()
    private val sessions =
        NetworkAccountSessionsRepository(site.api.sessions, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `the list keeps the server order and maps devices, browsers and unknowns`() = runTest {
        site.json(200, site.fixture("sessions.json"))

        val list = sessions.sessions()

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/auth/sessions")
        assertThat(list.map { it.isCurrent }).containsExactly(true, false, false).inOrder()

        val (here, browser, odd) = list
        assertThat(here.id).isEqualTo("7c1d2e3f-4a5b-4c6d-8e7f-a1b2c3d4e5f6")
        assertThat(here.kind).isEqualTo(SessionKind.Device)
        assertThat(here.platform).isEqualTo(SessionPlatform.Android)
        assertThat(here.deviceName).isEqualTo("Google Pixel 9")
        assertThat(here.appVersion).isEqualTo("0.2.0")
        assertThat(here.lastSeenAt).isEqualTo(Instant.parse("2026-10-03T19:10:00Z"))

        // A browser has no device name and no platform.
        assertThat(browser.kind).isEqualTo(SessionKind.Browser)
        assertThat(browser.deviceName).isNull()
        assertThat(browser.platform).isEqualTo(SessionPlatform.Unknown)
        assertThat(browser.userAgent).contains("Chrome/141")

        // Blank text is no text, and a platform from the future is Unknown, not a crash.
        assertThat(odd.deviceName).isNull()
        assertThat(odd.appVersion).isNull()
        assertThat(odd.platform).isEqualTo(SessionPlatform.Unknown)
    }

    @Test
    fun `revoking sends DELETE for that session`() = runTest {
        site.server.enqueue(mockwebserver3.MockResponse.Builder().code(204).build())

        sessions.revoke("0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d")

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/auth/sessions/0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d")
    }

    @Test
    fun `a session that is already gone is NotFound`() = runTest {
        site.json(404, error("not_found"))

        val failure = runCatching { sessions.revoke("0a9b8c7d-6e5f-4a3b-9c2d-1e0f9a8b7c6d") }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
    }

    @Test
    fun `a malformed id is NotFound without a request`() = runTest {
        val failure = runCatching { sessions.revoke("../current") }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a dead session is SignedOut`() = runTest {
        site.json(401, error("invalid_token"))

        val failure = runCatching { sessions.sessions() }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.SignedOut::class.java)
    }
}
