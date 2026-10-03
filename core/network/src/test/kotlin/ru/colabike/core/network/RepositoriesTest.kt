package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeScope

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
