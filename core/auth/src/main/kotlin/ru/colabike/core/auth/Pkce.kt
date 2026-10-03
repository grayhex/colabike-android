package ru.colabike.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** RFC 7636 S256: the app keeps [verifier]; the server sees only [challenge] at the start. */
class Pkce private constructor(val verifier: String) {
    val challenge: String = challengeOf(verifier)

    override fun toString() = "Pkce(challenge=$challenge)"

    companion object {
        private val random = SecureRandom()
        private val base64 = Base64.getUrlEncoder().withoutPadding()

        /** 32 random bytes: a 43-character verifier from the unreserved alphabet. */
        fun create(): Pkce = Pkce(base64.encodeToString(ByteArray(32).also(random::nextBytes)))

        fun challengeOf(verifier: String): String =
            base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
            )
    }
}
