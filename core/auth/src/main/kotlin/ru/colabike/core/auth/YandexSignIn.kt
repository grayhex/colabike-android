package ru.colabike.core.auth

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The native Yandex ID flow of cola #304 without Android types, so it is unit-tested: the start
 * address opened in Custom Tabs and the verified App Link the site sends the browser back to. The
 * PKCE verifier waits in [pending] (encrypted), because the app may die while the browser is in
 * front.
 */
class YandexSignIn(
    siteUrl: String,
    private val returnUrl: String,
    private val pending: SecretStore,
) {
    private val site: HttpUrl = siteUrl.toHttpUrl()

    /** Creates and keeps a new verifier; returns the address to open in the system browser. */
    fun startUrl(): String {
        val pkce = Pkce.create()
        pending.write(pkce.verifier)
        return site
            .newBuilder()
            .encodedPath("/api/auth/native/start")
            .addQueryParameter("provider", "yandex")
            .addQueryParameter("code_challenge", pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()
            .toString()
    }

    /** Reads an incoming link. The verifier is handed out once and forgotten. */
    fun handleReturn(link: String): Return {
        val url = link.toHttpUrlOrNull() ?: return Return.NotOurs
        val expected = returnUrl.toHttpUrl()
        if (
            url.scheme != "https" ||
                url.host != expected.host ||
                // An intent filter without a port matches every port: only the site's own counts.
                url.port != expected.port ||
                url.encodedPath != expected.encodedPath
        ) {
            return Return.NotOurs
        }
        val verifier = pending.read().also { pending.clear() }
        url.queryParameter("error")?.let {
            return Return.Failed(Reason.from(it))
        }
        if (verifier == null) return Return.Failed(Reason.NoPendingSignIn)
        val code =
            url.queryParameter("code")?.takeIf(CODE::matches)
                ?: return Return.Failed(Reason.Unknown)
        return Return.Code(code, verifier)
    }

    sealed interface Return {
        /** Exchange with [DeviceSession.signInWithCode]. toString hides both secrets. */
        class Code(val code: String, val verifier: String) : Return {
            override fun toString() = "Code(…)"
        }

        data class Failed(val reason: Reason) : Return

        data object NotOurs : Return
    }

    /** The `?error=` values of the flow, plus the app's own failures. */
    enum class Reason {
        Cancelled,
        Blocked,
        RegistrationClosed,
        EmailExists,
        RateLimited,
        ProviderError,
        NoPendingSignIn,
        Unknown;

        companion object {
            fun from(value: String): Reason =
                when (value) {
                    "cancelled" -> Cancelled
                    "blocked" -> Blocked
                    "registration_closed" -> RegistrationClosed
                    "email_exists" -> EmailExists
                    "rate_limited" -> RateLimited
                    "provider_error" -> ProviderError
                    else -> Unknown
                }
        }
    }

    private companion object {
        val CODE = Regex("^cola_ac_[A-Za-z0-9_-]{43}$")
    }
}
