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
    /** A path on the site, relative to its address; kept for what the app has no screen for. */
    val path: String,
    /** The comment or reply the notification is about; the app takes it from here, not the path. */
    val commentId: String? = null,
    /**
     * The date of the ride the notification is about (an invitation, a change, a cancellation, an
     * answer, a reminder). Null for the rest and for invitations made before dates were counted.
     */
    val occurrenceAt: Instant? = null,
    /** The version of the ride's agreements the notification belongs to. */
    val agreementRevision: Int? = null,
    val expiresAt: Instant? = null,
    val state: ListingState? = null,
)

/**
 * What a person switches in the settings and what the inbox filters by. The set is open: a key the
 * app does not know is [Other], listed with the rest and offered no filter of its own.
 */
enum class NotificationCategory(val key: String) {
    /** Rides and invitations. */
    Rides("rides"),

    /** Comments and replies. */
    Discussions("discussions"),

    /** New plans of the people the person has chosen to hear about. */
    Plans("plans"),

    /** Intents to ride of those people. */
    Intents("intents"),

    /** The end of a listing's term. */
    Market("market"),

    /**
     * A message of a conversation. It is pushed and never recorded in the inbox (the messenger
     * keeps what is unread), so it can be switched but not filtered by.
     */
    Chat("chat"),

    /** Follows and likes. */
    Reactions("reactions"),

    /** From ColaBike itself: account safety, the bike of the week. */
    Site("site"),

    /** A category this version of the app does not know yet. */
    Other("");

    companion object {
        fun of(key: String): NotificationCategory =
            entries.firstOrNull { it != Other && it.key == key } ?: Other

        /** The categories that can be asked for: the inbox has no line for a chat message. */
        val Filterable: List<NotificationCategory> = entries.filter { it != Other && it != Chat }
    }
}

/**
 * A notification for the signed-in person. [kind] is an open set (`follow`, `like`, `comment`,
 * `reply`, `ride_like`, `journal_comment`, `market_expiring`, `session_reuse`, and others): the
 * server shows only what the person may see now, and a withdrawn action leaves none.
 */
data class AppNotification(
    val id: String,
    val kind: String,
    val category: NotificationCategory,
    val createdAt: Instant,
    val read: Boolean,
    /** Who did it; null for the site's own notifications. */
    val actor: Person?,
    val target: NotificationTarget,
)

/**
 * How many are unread. The server counts up to 100: with [capped] there are at least that many, so
 * [unread] must not be shown as an exact number. [watermark] (only where the server gives one) is
 * the mark for "read all"; it is the server's and is handed back as it came.
 */
data class NotificationCount(val unread: Int, val capped: Boolean, val watermark: String? = null)

/** What the inbox is narrowed to; both can be on at once. */
data class NotificationFilter(
    val unreadOnly: Boolean = false,
    val category: NotificationCategory? = null,
) {
    val isNarrowed: Boolean
        get() = unreadOnly || category != null
}

/**
 * One page of the inbox. [watermark] marks the newest notification the person could see when the
 * server answered; "read all" with it leaves anything that arrived later unread.
 */
data class NotificationPage(
    val items: List<AppNotification>,
    val nextCursor: String?,
    val watermark: String?,
)

/**
 * What a "mark as read" came to: how many were marked now (a repeat marks none) and how many are
 * still unread, counted as [NotificationCount].
 */
data class NotificationReadResult(val marked: Int, val unread: NotificationCount)
