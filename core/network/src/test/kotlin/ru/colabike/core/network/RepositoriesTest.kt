package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeQuery
import ru.colabike.core.model.BikeScope
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.DataError
import ru.colabike.core.model.LikeChange
import ru.colabike.core.model.LikeState
import ru.colabike.core.model.ProfileCounts
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform
import ru.colabike.core.model.UserId

class RepositoriesTest {
    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(site.api.bikes, site.api.search, site.media, Dispatchers.Unconfined)
    private val account =
        NetworkAccountRepository(site.api.account, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `a bike page maps to app models with absolute photos and the cursor`() = runTest {
        site.json(200, site.fixture("bike-page.json"))

        val page = bikes.bikes(BikeQuery(BikeScope.Mine), cursor = "c1", limit = 2)

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
        assertThat(bike.components.map { it.section })
            .containsExactly("build", "build", "other")
            .inOrder()
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

class BikesSearchAndLikeTest {
    private val site = TestServer()
    private val bikes =
        NetworkBikesRepository(site.api.bikes, site.api.search, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    @Test
    fun `text and categories go to the server, blank ones do not`() = runTest {
        site.json(200, site.fixture("bike-page.json"))
        site.json(200, site.fixture("bike-page.json"))

        bikes.bikes(
            BikeQuery(BikeScope.Public, text = "  кросс ", categories = setOf("mtb", "bmx"))
        )
        bikes.bikes(BikeQuery(BikeScope.Mine, text = "   "))

        val search = site.server.takeRequest().url
        assertThat(search.queryParameter("q")).isEqualTo("кросс")
        assertThat(search.queryParameter("category")).isEqualTo("bmx,mtb")
        assertThat(search.queryParameter("scope")).isEqualTo("public")
        val all = site.server.takeRequest().url
        assertThat(all.queryParameter("q")).isNull()
        assertThat(all.queryParameter("category")).isNull()
        assertThat(all.queryParameter("scope")).isEqualTo("mine")
    }

    @Test
    fun `a search text is cut to the length the API accepts`() = runTest {
        site.json(200, site.fixture("bike-page.json"))

        bikes.bikes(BikeQuery(text = "я".repeat(400)))

        assertThat(site.server.takeRequest().url.queryParameter("q")).hasLength(150)
    }

    @Test
    fun `a summary carries the classification`() = runTest {
        site.json(200, site.fixture("bike-page.json"))

        val first = bikes.bikes(BikeQuery()).items.first()

        assertThat(first.classification.category).isEqualTo(first.category)
        assertThat(first.classification.electric).isFalse()
    }

    @Test
    fun `the detail keeps prices, groups and https links, and drops other schemes`() = runTest {
        site.json(200, site.fixture("bike.json"))

        val bike = bikes.bike(BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"))

        assertThat(bike.trim).isEqualTo("Pro")
        assertThat(bike.priceRub).isEqualTo(85000.0)
        assertThat(bike.manufacturerUrl).isEqualTo("https://www.cube.eu/travel-sl")
        assertThat(bike.purposes).containsExactly("commuting", "touring").inOrder()
        assertThat(bike.groupOrder).containsExactly("drivetrain", "frame").inOrder()
        val (frame, drivetrain, _) = bike.components
        // A link that is not https never reaches the screen.
        assertThat(frame.url).isNull()
        assertThat(frame.priceRub).isNull()
        assertThat(drivetrain.url).isEqualTo("https://bike-components.example/deore")
        assertThat(drivetrain.priceRub).isEqualTo(12500.0)
        assertThat(drivetrain.groupId).isEqualTo("drivetrain")
    }

    @Test
    fun `liking sends PUT, unliking DELETE, and the answer is the server's`() = runTest {
        val id = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
        site.json(200, """{"liked":true,"likes":5}""")
        site.json(200, """{"liked":false,"likes":4}""")

        val liked = bikes.setLiked(id, true)
        val unliked = bikes.setLiked(id, false)

        assertThat(liked).isEqualTo(LikeState(liked = true, likes = 5))
        assertThat(unliked).isEqualTo(LikeState(liked = false, likes = 4))
        val put = site.server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.url.encodedPath).isEqualTo("/api/v1/bikes/${id.value}/like")
        assertThat(site.server.takeRequest().method).isEqualTo("DELETE")
    }

    @Test
    fun `a like that went through is announced to every screen`() = runTest {
        val id = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
        site.json(200, """{"liked":true,"likes":5}""")

        bikes.likeChanges.test {
            bikes.setLiked(id, true)

            assertThat(awaitItem()).isEqualTo(LikeChange(id, LikeState(true, 5)))
        }
    }

    @Test
    fun `a refused like announces nothing and fails with the reason`() = runTest {
        val id = BikeId("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
        site.json(404, error("not_found"))

        bikes.likeChanges.test {
            val failure = runCatching { bikes.setLiked(id, true) }

            assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
            expectNoEvents()
        }
    }

    @Test
    fun `a malformed id is NotFound without a request`() = runTest {
        val failure = runCatching { bikes.setLiked(BikeId("../me"), true) }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
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

class PeopleRepositoryTest {
    private val site = TestServer()
    private val people =
        NetworkPeopleRepository(site.api.users, site.api.search, site.media, Dispatchers.Unconfined)
    private val bikes =
        NetworkBikesRepository(site.api.bikes, site.api.search, site.media, Dispatchers.Unconfined)
    private val uuid = "8d9e0f1a-2b3c-4d5e-8f70-8192a3b4c5d6"

    @After fun close() = site.close()

    @Test
    fun `a profile maps to the person, counts and what the viewer is to them`() = runTest {
        site.json(200, site.fixture("profile.json"))

        val profile = people.profile("test-rider")

        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/users/test-rider")
        assertThat(profile.person.id.value).isEqualTo(uuid)
        assertThat(profile.person.displayName).isEqualTo("Тестовый Райдер")
        assertThat(profile.person.avatarUrl)
            .isEqualTo(site.config.siteUrl + "/api/avatars/8d9e0f1a")
        assertThat(profile.counts)
            .isEqualTo(ProfileCounts(bikes = 3, followers = 12, following = 7))
        assertThat(profile.relationship?.following).isTrue()
        assertThat(profile.joined).isEqualTo(Instant.parse("2026-09-01T10:00:00Z"))
    }

    @Test
    fun `a ref that is neither a UUID nor a username is not found without a request`() = runTest {
        listOf("", "ab", "../me", "a b c", "x".repeat(31), "name/with/slash").forEach {
            val failure = runCatching { people.profile(it) }
            assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        }
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a blocked or unknown person is NotFound`() = runTest {
        site.json(404, error("not_found"))

        val failure = runCatching { people.profile("nobody-here") }

        assertThat(failure.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
    }

    @Test
    fun `followers come in pages with the viewer's relationship, and an unsafe avatar is dropped`() =
        runTest {
            site.json(200, site.fixture("user-page.json"))

            val page = people.followers(uuid, cursor = "c0", limit = 2)

            val request = site.server.takeRequest().url
            assertThat(request.encodedPath).isEqualTo("/api/v1/users/$uuid/followers")
            assertThat(request.queryParameter("cursor")).isEqualTo("c0")
            assertThat(request.queryParameter("limit")).isEqualTo("2")
            assertThat(page.nextCursor).isEqualTo("eyJyIjoiMjAyNi0wOS0wMSJ9")
            val (first, second) = page.items
            assertThat(first.relationship?.friends).isTrue()
            assertThat(second.relationship).isNull()
            assertThat(second.person.avatarUrl).isNull()
            assertThat(second.person.displayName).isEqualTo("second-rider")
        }

    @Test
    fun `following and the bikes of a person use their own addresses`() = runTest {
        site.json(200, site.fixture("user-page.json"))
        site.json(200, site.fixture("bike-page.json"))

        people.following(uuid)
        val bikesOf = people.bikesOf("test-rider")

        assertThat(site.server.takeRequest().url.encodedPath)
            .isEqualTo("/api/v1/users/$uuid/following")
        assertThat(site.server.takeRequest().url.encodedPath)
            .isEqualTo("/api/v1/users/test-rider/bikes")
        assertThat(bikesOf.items).isNotEmpty()
    }

    @Test
    fun `following sends PUT, unfollowing DELETE, and the answer is announced`() = runTest {
        val id = UserId(uuid)
        site.json(
            200,
            """{"relationship":{"isSelf":false,"following":true,"followedBy":false,"friends":false},"followers":13}""",
        )
        site.json(
            200,
            """{"relationship":{"isSelf":false,"following":false,"followedBy":false,"friends":false},"followers":12}""",
        )

        people.followChanges.test {
            val on = people.setFollowing(id, true)
            val off = people.setFollowing(id, false)

            assertThat(on.followers).isEqualTo(13)
            assertThat(on.relationship.following).isTrue()
            assertThat(off.followers).isEqualTo(12)
            assertThat(awaitItem().state).isEqualTo(on)
            assertThat(awaitItem().state).isEqualTo(off)
        }
        val put = site.server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.url.encodedPath).isEqualTo("/api/v1/users/$uuid/follow")
        assertThat(site.server.takeRequest().method).isEqualTo("DELETE")
    }

    @Test
    fun `one cannot follow oneself, and the refusal announces nothing`() = runTest {
        site.json(400, error("cannot_follow_self", "Нельзя подписаться на себя"))

        people.followChanges.test {
            val failure = runCatching { people.setFollowing(UserId(uuid), true) }

            val rejected = failure.exceptionOrNull() as DataError.Rejected
            assertThat(rejected.code).isEqualTo("cannot_follow_self")
            expectNoEvents()
        }
    }

    @Test
    fun `searching people needs a text, and asks the server only with one`() = runTest {
        site.json(200, site.fixture("user-page.json"))

        val none = people.search("   ")
        assertThat(none.items).isEmpty()
        assertThat(site.server.requestCount).isEqualTo(0)

        people.search("  Райдер ")
        val url = site.server.takeRequest().url
        assertThat(url.encodedPath).isEqualTo("/api/v1/experience/users")
        assertThat(url.queryParameter("q")).isEqualTo("Райдер")
    }

    @Test
    fun `searching bikes sends the text and only the facets that were asked for`() = runTest {
        site.json(200, site.fixture("bike-page.json"))
        site.json(200, site.fixture("bike-page.json"))

        bikes.search(
            BikeSearch(
                text = " dura-ace ",
                category = "mtb",
                suspension = "hardtail",
                electric = true,
                fatbike = false,
            )
        )
        bikes.search(BikeSearch(text = "x", category = "hoverboard", suspension = "airy"))

        val first = site.server.takeRequest().url
        assertThat(first.encodedPath).isEqualTo("/api/v1/experience/bikes")
        assertThat(first.queryParameter("q")).isEqualTo("dura-ace")
        assertThat(first.queryParameter("category")).isEqualTo("mtb")
        assertThat(first.queryParameter("suspension")).isEqualTo("hardtail")
        assertThat(first.queryParameter("electric")).isEqualTo("1")
        assertThat(first.queryParameter("fatbike")).isEqualTo("0")
        // A value the API does not list is not sent (the server would refuse the whole request).
        val second = site.server.takeRequest().url
        assertThat(second.queryParameter("category")).isNull()
        assertThat(second.queryParameter("suspension")).isNull()
        assertThat(second.queryParameter("electric")).isNull()
    }
}
