package ru.colabike.core.model

import java.time.Instant

/**
 * A place where the account is signed in: a browser or a device with the app. Tokens and their
 * hashes never reach the client, only what a person needs to recognise the place.
 */
data class AccountSession(
    /** The public id from the list, the only thing needed to end the session. */
    val id: String,
    val kind: SessionKind,
    /** What the app reported when it signed in; null for a browser. */
    val deviceName: String?,
    val platform: SessionPlatform,
    val appVersion: String?,
    /** The browser's own description; the only name a browser session has. */
    val userAgent: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    /** The session this very request was made with. */
    val isCurrent: Boolean,
)

enum class SessionKind {
    Browser,
    Device,
    /** A kind this version of the app does not know yet. */
    Unknown,
}

enum class SessionPlatform {
    Android,
    Ios,
    Other,
    /** A browser, or a platform this version of the app does not know yet. */
    Unknown,
}

/** The signed-in person's own sessions. Implementations throw [DataError] on failure. */
interface AccountSessionsRepository {
    /** The current session first, then the rest by last activity (the server's order). */
    suspend fun sessions(): List<AccountSession>

    /**
     * Ends one session by [AccountSession.id]. A session that is already gone is
     * [DataError.NotFound].
     */
    suspend fun revoke(id: String)
}
