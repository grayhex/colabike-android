package ru.colabike.app.links

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.colabike.app.navigation.Destination

/** What an address of the site is, once it has passed the allowlist. Never holds the raw string. */
sealed interface AppLink {
    /** The ids below are the API's UUIDs, lower-cased and checked. */
    data class Bike(val id: String) : AppLink

    data class Journal(val id: String) : AppLink

    data class Ride(val id: String) : AppLink

    data class Market(val id: String) : AppLink

    /** A profile by `username` (the API takes it as `{ref}`). */
    data class Person(val username: String) : AppLink

    /**
     * An address with the site's public id (`/b/<slug>-<id>`). API v1 has no way to turn it into
     * the UUID it works with, and the app does not guess one: a backend gap, listed in docs.
     */
    data class NeedsResolver(val kind: Kind, val publicId: String, val encodedPath: String) :
        AppLink {
        enum class Kind {
            Bike,
            Journal,
            Ride,
            Market,
        }
    }

    /** The return of the native Yandex ID flow; the sign-in code handles it, not the router. */
    data object NativeAuth : AppLink

    /** Another host or scheme, a port, an unknown path: not an address the app acts on. */
    data object NotForTheApp : AppLink
}

/**
 * Reads incoming addresses (an Intent's data is untrusted input) against an allowlist: `https`, the
 * site's own host and default port, no credentials, and only the address shapes the site has (cola
 * docs/modules/public-urls.md). The query and the fragment are never read.
 */
class AppLinkParser(siteUrl: String) {
    private val site: HttpUrl = siteUrl.toHttpUrl()

    fun parse(raw: String?): AppLink {
        val url = raw?.toHttpUrlOrNull() ?: return AppLink.NotForTheApp
        if (
            url.scheme != "https" ||
                !url.host.equals(site.host, ignoreCase = true) ||
                url.port != site.port ||
                url.username.isNotEmpty() ||
                url.password.isNotEmpty()
        ) {
            return AppLink.NotForTheApp
        }
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size == 2 && segments[0] == "app" && segments[1] == "auth") {
            return AppLink.NativeAuth
        }
        return when {
            segments.size == 1 && segments[0].startsWith("@") ->
                person(segments[0].removePrefix("@"))
            segments.size == 2 && segments[0] == "u" -> person(segments[1])
            segments.size == 2 -> object_(segments[0], segments[1], url.encodedPath)
            else -> AppLink.NotForTheApp
        }
    }

    private fun person(username: String): AppLink =
        if (USERNAME.matches(username)) AppLink.Person(username) else AppLink.NotForTheApp

    private fun object_(prefix: String, rest: String, encodedPath: String): AppLink {
        val kind =
            when (prefix) {
                "b" -> AppLink.NeedsResolver.Kind.Bike
                "j" -> AppLink.NeedsResolver.Kind.Journal
                "r" -> AppLink.NeedsResolver.Kind.Ride
                "market" -> AppLink.NeedsResolver.Kind.Market
                else -> return AppLink.NotForTheApp
            }
        if (UUID.matches(rest)) {
            val id = rest.lowercase()
            return when (kind) {
                AppLink.NeedsResolver.Kind.Bike -> AppLink.Bike(id)
                AppLink.NeedsResolver.Kind.Journal -> AppLink.Journal(id)
                AppLink.NeedsResolver.Kind.Ride -> AppLink.Ride(id)
                AppLink.NeedsResolver.Kind.Market -> AppLink.Market(id)
            }
        }
        // `<slug>-<id>`: the id is the last eight characters after the last dash.
        val publicId = rest.substringAfterLast('-', missingDelimiterValue = "")
        return if (PUBLIC_ID.matches(publicId) && rest.length > publicId.length + 1) {
            AppLink.NeedsResolver(kind, publicId, encodedPath)
        } else {
            AppLink.NotForTheApp
        }
    }

    private companion object {
        val UUID =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Digits and Latin letters without i, l, o, u (the site's public id alphabet). */
        val PUBLIC_ID = Regex("^[0-9a-hjkmnp-tv-z]{8}$")

        /** A username is 3 to 30 characters (cola api-v1, `{ref}`). */
        val USERNAME = Regex("^[A-Za-z0-9][A-Za-z0-9_.-]{2,29}$")
    }
}

/** Where a link leads. */
sealed interface LinkTarget {
    /** A screen the app has. */
    data class InApp(val destination: Destination) : LinkTarget

    /** The app has no screen for it (yet) or cannot resolve it: the site shows it. */
    data class OnSite(val url: String) : LinkTarget

    /** Nothing to do: not an address for the app, or one that sign-in handles. */
    data object None : LinkTarget
}

/**
 * Where [this] link goes. A slice that adds a screen adds its branch here and nothing else: the
 * parser, the pending transition through sign-in and the navigator are already there.
 */
fun AppLink.target(site: SiteLinks): LinkTarget =
    when (this) {
        is AppLink.Bike -> LinkTarget.InApp(Destination.Bike(id))
        // A profile has a screen; the API takes the username as the person's `{ref}`.
        is AppLink.Person -> LinkTarget.InApp(Destination.Person(username))
        is AppLink.Journal -> LinkTarget.InApp(Destination.Journal(id))
        is AppLink.Ride -> LinkTarget.InApp(Destination.Ride(id))
        // No screen yet (#9): the site, by the same address.
        is AppLink.Market -> LinkTarget.OnSite(site.page("market", id))
        // The site can resolve its own address, the app cannot: hand the page over as it is.
        is AppLink.NeedsResolver -> LinkTarget.OnSite(site.pageAt(encodedPath))
        AppLink.NativeAuth,
        AppLink.NotForTheApp -> LinkTarget.None
    }
