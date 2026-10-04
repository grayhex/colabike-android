package ru.colabike.core.model

/**
 * Why data could not be read or written, in terms a screen can act on. Every repository throws only
 * these (plus cancellation); the network layer maps HTTP and transport failures onto them.
 */
sealed class DataError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** No connection, timeout or a broken stream: worth a retry. */
    class Offline(cause: Throwable) : DataError("No connection", cause)

    /** The session is gone (revoked, expired for good, account blocked): show sign-in. */
    class SignedOut : DataError("Signed out")

    class NotFound : DataError("Not found")

    /** Too many requests; [retryAfterSeconds] comes from `Retry-After` when the server sent it. */
    class RateLimited(val retryAfterSeconds: Long?) : DataError("Rate limited")

    /**
     * The server refused the request; [code] is the API error code, [userMessage] its text and
     * [requestId] (`X-Request-ID`) what support needs when the text says nothing.
     */
    class Rejected(
        val status: Int,
        val code: String,
        val userMessage: String,
        val requestId: String? = null,
    ) : DataError("Rejected: $status $code")

    /** A server fault; [requestId] (`X-Request-ID`) is what support needs. */
    class Server(val status: Int, val requestId: String?) : DataError("Server error $status")

    /** Anything else: an unreadable answer, a contract mismatch. */
    class Unexpected(cause: Throwable?) : DataError("Unexpected failure", cause)
}
