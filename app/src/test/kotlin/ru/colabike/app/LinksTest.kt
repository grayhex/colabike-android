package ru.colabike.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.links.AppLink
import ru.colabike.app.links.AppLink.NeedsResolver.Kind
import ru.colabike.app.links.AppLinkParser
import ru.colabike.app.links.LinkHandler
import ru.colabike.app.links.LinkTarget
import ru.colabike.app.links.PreferencesPendingNavigation
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.links.target
import ru.colabike.app.navigation.Destination

class AppLinkParserTest {
    private val parser = AppLinkParser("https://colabike.ru")
    private val uuid = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"

    @Test
    fun `the site's own addresses by UUID are typed ids`() {
        assertThat(parser.parse("https://colabike.ru/b/$uuid")).isEqualTo(AppLink.Bike(uuid))
        assertThat(parser.parse("https://colabike.ru/j/$uuid")).isEqualTo(AppLink.Journal(uuid))
        assertThat(parser.parse("https://colabike.ru/r/$uuid")).isEqualTo(AppLink.Ride(uuid))
        assertThat(parser.parse("https://colabike.ru/market/$uuid")).isEqualTo(AppLink.Market(uuid))
    }

    @Test
    fun `an id is lower-cased, the query and the fragment are never read`() {
        assertThat(parser.parse("https://COLABIKE.ru/b/${uuid.uppercase()}?x=1&redirect=evil#top"))
            .isEqualTo(AppLink.Bike(uuid))
    }

    @Test
    fun `profiles by username in both address forms`() {
        assertThat(parser.parse("https://colabike.ru/@test-rider"))
            .isEqualTo(AppLink.Person("test-rider"))
        assertThat(parser.parse("https://colabike.ru/u/test.rider_2"))
            .isEqualTo(AppLink.Person("test.rider_2"))
        assertThat(parser.parse("https://colabike.ru/@")).isEqualTo(AppLink.NotForTheApp)
        assertThat(parser.parse("https://colabike.ru/@-bad")).isEqualTo(AppLink.NotForTheApp)
        // The API takes a username of 3 to 30 characters.
        assertThat(parser.parse("https://colabike.ru/@ab")).isEqualTo(AppLink.NotForTheApp)
        assertThat(parser.parse("https://colabike.ru/@" + "a".repeat(31)))
            .isEqualTo(AppLink.NotForTheApp)
        assertThat(parser.parse("https://colabike.ru/@a/b")).isEqualTo(AppLink.NotForTheApp)
    }

    @Test
    fun `an address with a public id needs a resolver the API does not have`() {
        val link = parser.parse("https://colabike.ru/b/gorodskoy-cube-4k7m9p2x")

        assertThat(link)
            .isEqualTo(AppLink.NeedsResolver(Kind.Bike, "4k7m9p2x", "/b/gorodskoy-cube-4k7m9p2x"))
        // Cyrillic slugs come percent-encoded and stay so.
        val cyrillic = parser.parse("https://colabike.ru/r/покатушка-4k7m9p2x")
        assertThat((cyrillic as AppLink.NeedsResolver).publicId).isEqualTo("4k7m9p2x")
        assertThat(cyrillic.encodedPath).startsWith("/r/%D0%BF")
    }

    @Test
    fun `an id outside the site's alphabet, or too short, is not an object address`() {
        // i, l, o and u are never in a public id; seven characters are too few.
        listOf(
                "https://colabike.ru/b/slug-4k7m9p2i",
                "https://colabike.ru/b/slug-4k7m9p2l",
                "https://colabike.ru/b/slug-4k7m9p2o",
                "https://colabike.ru/b/slug-4k7m9p2u",
                "https://colabike.ru/b/slug-4k7m9p2",
                "https://colabike.ru/b/4k7m9p2x",
                "https://colabike.ru/b/-4k7m9p2x",
                "https://colabike.ru/b/",
            )
            .forEach { assertThat(parser.parse(it)).isEqualTo(AppLink.NotForTheApp) }
    }

    @Test
    fun `the sign-in return is recognised and nothing like it is`() {
        assertThat(parser.parse("https://colabike.ru/app/auth?code=x"))
            .isEqualTo(AppLink.NativeAuth)
        assertThat(parser.parse("https://colabike.ru/app/auth/")).isEqualTo(AppLink.NativeAuth)
        assertThat(parser.parse("https://colabike.ru/app/auth/extra"))
            .isEqualTo(AppLink.NotForTheApp)
        assertThat(parser.parse("https://colabike.ru/app")).isEqualTo(AppLink.NotForTheApp)
    }

    @Test
    fun `other hosts, schemes, ports and credentials are not for the app`() {
        listOf(
                "http://colabike.ru/b/$uuid",
                "https://evil.example/b/$uuid",
                "https://colabike.ru.evil.example/b/$uuid",
                "https://evil.colabike.ru/b/$uuid",
                "https://colabike.ru@evil.example/b/$uuid",
                "https://user:pass@colabike.ru/b/$uuid",
                "https://colabike.ru:8443/b/$uuid",
                "intent://colabike.ru/b/$uuid#Intent;scheme=https;end",
                "javascript:alert(1)",
                "file:///data/data/ru.colabike.app/files/session/refresh.bin",
                "content://ru.colabike.app/anything",
                "colabike://b/$uuid",
                "https://colabike.ru/b/../api/v1/me",
                "https://colabike.ru/",
                "https://colabike.ru/bikes",
                "",
                "   ",
                "not a url",
            )
            .forEach { assertThat(parser.parse(it)).isEqualTo(AppLink.NotForTheApp) }
        assertThat(parser.parse(null)).isEqualTo(AppLink.NotForTheApp)
    }

    @Test
    fun `an id with path tricks is not a UUID`() {
        listOf(
                "https://colabike.ru/b/$uuid/extra",
                "https://colabike.ru/b/$uuid%00",
                "https://colabike.ru/b/${uuid.dropLast(1)}",
                "https://colabike.ru/b/${uuid}0",
                "https://colabike.ru/b/%2e%2e",
            )
            .forEach { assertThat(parser.parse(it)).isEqualTo(AppLink.NotForTheApp) }
    }
}

class LinkTargetTest {
    private val site = SiteLinks("https://colabike.ru")
    private val uuid = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"

    @Test
    fun `a bike has a screen`() {
        assertThat(AppLink.Bike(uuid).target(site))
            .isEqualTo(LinkTarget.InApp(Destination.Bike(uuid)))
    }

    @Test
    fun `a journal entry and a ride have screens`() {
        assertThat(AppLink.Journal(uuid).target(site))
            .isEqualTo(LinkTarget.InApp(Destination.Journal(uuid)))
        assertThat(AppLink.Ride(uuid).target(site))
            .isEqualTo(LinkTarget.InApp(Destination.Ride(uuid)))
    }

    @Test
    fun `a listing and a profile have screens`() {
        assertThat(AppLink.Market(uuid).target(site))
            .isEqualTo(LinkTarget.InApp(Destination.Listing(uuid)))
        assertThat(AppLink.Person("test-rider").target(site))
            .isEqualTo(LinkTarget.InApp(Destination.Person("test-rider")))
    }

    @Test
    fun `a path the server sent becomes a page of this site and nothing else`() {
        assertThat(site.pageFromPath("/market/$uuid")).isEqualTo("https://colabike.ru/market/$uuid")
        assertThat(site.pageFromPath("/a/with-anchor#comment-$uuid"))
            .isEqualTo("https://colabike.ru/a/with-anchor#comment-$uuid")
        listOf(
                "",
                "market/1",
                "//evil.example/x",
                "https://evil.example/x",
                "/\\evil.example",
                "/a b",
                "/a\nb",
                "@evil.example",
            )
            .forEach { assertThat(site.pageFromPath(it)).isNull() }
    }

    @Test
    fun `an address the API cannot resolve falls back to the site, without a guess`() {
        val link = AppLink.NeedsResolver(Kind.Bike, "4k7m9p2x", "/b/slug-4k7m9p2x")

        assertThat(link.target(site))
            .isEqualTo(LinkTarget.OnSite("https://colabike.ru/b/slug-4k7m9p2x"))
    }

    @Test
    fun `sign-in and strangers lead nowhere`() {
        assertThat(AppLink.NativeAuth.target(site)).isEqualTo(LinkTarget.None)
        assertThat(AppLink.NotForTheApp.target(site)).isEqualTo(LinkTarget.None)
    }
}

class LinkHandlerTest {
    private val uuid = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"
    private val pending = FakePending()
    private val authLinks = mutableListOf<String>()
    private val site = mutableListOf<String>()
    private val handler =
        LinkHandler(
            parser = AppLinkParser("https://colabike.ru"),
            site = SiteLinks("https://colabike.ru"),
            pending = pending,
            onAuthLink = { authLinks += it },
            onSite = { site += it },
        )

    @Test
    fun `the sign-in return goes to sign-in and only there`() {
        handler.handle("https://colabike.ru/app/auth?code=abc")

        assertThat(authLinks).containsExactly("https://colabike.ru/app/auth?code=abc")
        assertThat(pending.destination.value).isNull()
        assertThat(site).isEmpty()
    }

    @Test
    fun `a bike address waits for the shell`() {
        handler.handle("https://colabike.ru/b/$uuid")

        assertThat(pending.destination.value).isEqualTo(Destination.Bike(uuid))
        assertThat(authLinks).isEmpty()
    }

    @Test
    fun `an address without a screen opens on the site`() {
        // The site's own short id cannot be turned into the API's UUID: the site shows the page.
        handler.handle("https://colabike.ru/market/rama-cube-5kq3f7ab")

        assertThat(site).containsExactly("https://colabike.ru/market/rama-cube-5kq3f7ab")
        assertThat(pending.destination.value).isNull()
    }

    @Test
    fun `a listing waits for the shell like a bike does`() {
        handler.handle("https://colabike.ru/market/$uuid")

        assertThat(pending.destination.value).isEqualTo(Destination.Listing(uuid))
        assertThat(site).isEmpty()
    }

    @Test
    fun `a journal entry and a ride wait for the shell like a bike does`() {
        handler.handle("https://colabike.ru/j/$uuid")
        assertThat(pending.destination.value).isEqualTo(Destination.Journal(uuid))

        handler.handle("https://colabike.ru/r/$uuid")
        assertThat(pending.destination.value).isEqualTo(Destination.Ride(uuid))
        assertThat(site).isEmpty()
    }

    @Test
    fun `a profile address waits for the shell like a bike does`() {
        handler.handle("https://colabike.ru/@test-rider")

        assertThat(pending.destination.value).isEqualTo(Destination.Person("test-rider"))
        assertThat(site).isEmpty()
    }

    @Test
    fun `strangers are dropped without a trace`() {
        listOf("https://evil.example/b/$uuid", "javascript:alert(1)", null, "").forEach {
            handler.handle(it)
        }

        assertThat(authLinks).isEmpty()
        assertThat(site).isEmpty()
        assertThat(pending.destination.value).isNull()
    }
}

@RunWith(RobolectricTestRunner::class)
class PendingNavigationTest {
    private val preferences =
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("pending-test", Context.MODE_PRIVATE)
    private val uuid = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"
    private var now = Instant.parse("2026-10-03T10:00:00Z")
    private val clock =
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId?) = this

            override fun instant(): Instant = now
        }

    private fun pending() = PreferencesPendingNavigation(preferences, clock)

    @Test
    fun `a place offered is kept until it is taken`() {
        val first = pending()
        first.offer(Destination.Bike(uuid))

        assertThat(first.destination.value).isEqualTo(Destination.Bike(uuid))
        first.clear()
        assertThat(first.destination.value).isNull()
        assertThat(pending().destination.value).isNull()
    }

    @Test
    fun `it survives a restart of the process`() {
        pending().offer(Destination.Bike(uuid))

        assertThat(pending().destination.value).isEqualTo(Destination.Bike(uuid))
    }

    @Test
    fun `an old intention is not carried out out of the blue`() {
        pending().offer(Destination.Bike(uuid))
        now = now.plus(Duration.ofMinutes(31))

        assertThat(pending().destination.value).isNull()
        // And it is gone for good, not only hidden.
        now = now.minus(Duration.ofMinutes(31))
        assertThat(pending().destination.value).isNull()
    }

    @Test
    fun `every place a link or a notification leads to is kept, and survives a restart`() {
        val places =
            listOf(
                Destination.Ride(uuid),
                Destination.Journal(uuid),
                Destination.Listing(uuid),
                Destination.Component(uuid),
                Destination.Comments("bike", uuid, "", uuid),
                Destination.Comments("ride", uuid, "", null),
                Destination.Comments("component", uuid, "", uuid),
                Destination.Devices,
                Destination.Notifications,
            )
        places.forEach { place ->
            pending().clear()
            pending().offer(place)

            assertThat(pending().destination.value).isEqualTo(place)
        }
    }

    @Test
    fun `a discussion is kept without the name of its object`() {
        pending().offer(Destination.Comments("bike", uuid, "Городской Трэвел", uuid))

        assertThat(pending().destination.value)
            .isEqualTo(Destination.Comments("bike", uuid, "", uuid))
    }

    @Test
    fun `a place that is not an id, or a kind that is not one, is not kept`() {
        listOf(
                Destination.Ride("../me"),
                Destination.Journal("x"),
                Destination.Listing("1 OR 1=1"),
                Destination.Comments("galaxy", uuid, "", null),
                Destination.Comments("bike", uuid, "", "../x"),
                Destination.Comments("bike", "nope", "", null),
                Destination.Messages,
            )
            .forEach { place ->
                pending().clear()
                pending().offer(place)

                assertThat(pending().destination.value).isNull()
            }
    }

    @Test
    fun `a stored value that was tampered with is dropped`() {
        listOf("ride:../x", "comments:bike:$uuid", "comments:galaxy:$uuid:", "devices-x", "x:y")
            .forEach { value ->
                preferences
                    .edit()
                    .putString("pending_destination", value)
                    .putLong("pending_at", now.toEpochMilli())
                    .commit()

                assertThat(pending().destination.value).isNull()
            }
    }

    @Test
    fun `a person is kept as the link named them`() {
        pending().offer(Destination.Person("test-rider"))
        assertThat(pending().destination.value).isEqualTo(Destination.Person("test-rider"))

        pending().offer(Destination.Person(uuid))
        assertThat(pending().destination.value).isEqualTo(Destination.Person(uuid))

        // Not a ref the API takes.
        pending().clear()
        pending().offer(Destination.Person("../me"))
        assertThat(pending().destination.value).isNull()
    }

    @Test
    fun `only what a link can open is kept, and only if it is whole`() {
        pending().apply {
            offer(Destination.Profile)
            offer(Destination.Bike("../../etc/passwd"))
            assertThat(destination.value).isNull()
        }

        preferences
            .edit()
            .putString("pending_destination", "bike:not-a-uuid")
            .putLong("pending_at", now.toEpochMilli())
            .apply()
        assertThat(pending().destination.value).isNull()
        preferences
            .edit()
            .putString("pending_destination", "profile:$uuid")
            .putLong("pending_at", now.toEpochMilli())
            .apply()
        assertThat(pending().destination.value).isNull()
    }
}
