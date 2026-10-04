package ru.colabike.app.notifications

import ru.colabike.app.links.SiteLinks
import ru.colabike.app.navigation.Destination
import ru.colabike.core.model.AppNotification

/** Where a notification leads. */
sealed interface NotificationRoute {
    /** A screen the app has. */
    data class InApp(val destination: Destination) : NotificationRoute

    /** The app has no screen for it: the site shows it, by an address on its own host. */
    data class OnSite(val url: String) : NotificationRoute

    /** Nothing to open: the server's path is not a path on this site. */
    data object Nowhere : NotificationRoute
}

private val UUID =
    Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

/** The kinds that are about a comment: the target's anchor then names it. */
private fun String.isAboutAComment(): Boolean =
    this == "comment" || this == "reply" || endsWith("_comment") || endsWith("_reply")

/**
 * The comment a notification's path points at. The contract says only that the path carries "the
 * anchor of the comment"; the first UUID of the fragment is taken, whatever word comes before it
 * (`#comment-…`, `#c-…`, the bare id), and lower-cased. No fragment or no UUID in it: null.
 */
internal fun commentAnchor(path: String): String? {
    val fragment = path.substringAfter('#', missingDelimiterValue = "")
    return UUID.find(fragment)?.value?.lowercase()
}

/**
 * Where [this] notification goes. The object comes from the typed [AppNotification.target] (its
 * type and id), never from parsing the path; the path is used only for the comment's anchor and as
 * the address on the site for an object the app has no screen for. A comment's notification opens
 * the discussion on that comment (the server returns the branch and the chain to it), anything else
 * the object itself. A deleted or closed object opens to the usual "not found" and shows nothing of
 * what it held.
 */
fun AppNotification.route(site: SiteLinks): NotificationRoute {
    val id = target.id.lowercase()
    if (!UUID.matches(id)) return NotificationRoute.Nowhere
    val comment = if (kind.isAboutAComment()) commentAnchor(target.path) else null
    val destination: Destination? =
        when (target.type) {
            "bike",
            "ride",
            "journal" ->
                if (comment != null) Destination.Comments(target.type, id, target.name, comment)
                else
                    when (target.type) {
                        "bike" -> Destination.Bike(id)
                        "ride" -> Destination.Ride(id)
                        else -> Destination.Journal(id)
                    }
            "market" -> Destination.Listing(id)
            "component" ->
                if (comment != null) Destination.Comments("component", id, target.name, comment)
                else Destination.Component(id)
            "profile" -> Destination.Person(id)
            // The one account notification the app answers itself: a sign-in used again.
            "account" -> if (kind == "session_reuse") Destination.Devices else null
            else -> null
        }
    if (destination != null) return NotificationRoute.InApp(destination)
    val url = site.pageFromPath(target.path) ?: return NotificationRoute.Nowhere
    return NotificationRoute.OnSite(url)
}
