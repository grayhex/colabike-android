package ru.colabike.core.model

import java.time.Instant

/** The condition of a market listing a notification is about (only `market_expiring` has one). */
enum class ListingState {
    Closed,
    Expired,
    Expiring,
    Extended,

    /** A state this version of the app does not know yet. */
    Unknown,
}

/**
 * What a notification is about: an object, its name and its path on the site. The name and the path
 * are worked out when read, so they follow a rename. [type] is an open set (`bike`, `ride`,
 * `journal`, `article`, `component`, `profile`, `market`, `account`, `bike-week`, and others to
 * come): a type the app does not know is shown generally and opened on the site.
 */
data class NotificationTarget(
    val type: String,
    val id: String,
    val name: String,
    /** A path on the site, relative to its address; a comment's notification adds its anchor. */
    val path: String,
    val expiresAt: Instant? = null,
    val state: ListingState? = null,
)

/**
 * A notification for the signed-in person. [kind] is an open set (`follow`, `like`, `comment`,
 * `reply`, `ride_like`, `journal_comment`, `market_expiring`, `session_reuse`, and others): the
 * server shows only what the person may see now, and a withdrawn action leaves none.
 */
data class AppNotification(
    val id: String,
    val kind: String,
    val createdAt: Instant,
    val read: Boolean,
    /** Who did it; null for the site's own notifications. */
    val actor: Person?,
    val target: NotificationTarget,
)

/**
 * How many are unread. The server counts up to 100: with [capped] there are at least that many, so
 * [unread] must not be shown as an exact number.
 */
data class NotificationCount(val unread: Int, val capped: Boolean)
