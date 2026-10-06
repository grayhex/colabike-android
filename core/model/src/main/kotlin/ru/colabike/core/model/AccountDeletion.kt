package ru.colabike.core.model

/**
 * How the account confirms its own deletion and whether it may be deleted at all
 * (cola #354). [method] is what the account has to give; [reason] says why [allowed] is false.
 */
data class AccountDeletion(val method: Method?, val allowed: Boolean, val reason: Reason?) {
    enum class Method {
        /** The current password. */
        Password,

        /** An account made through Yandex ID has no password: it signs in again instead. */
        Yandex,
    }

    enum class Reason {
        /** An administrator hands the rights over first. */
        Admin,

        /** Neither a password nor Yandex: a password is set on the site ("Forgot password?"). */
        NoMethod,

        /** A reason this version does not know; the deletion is not offered. */
        Other,
    }
}

/**
 * What proves who is deleting the account, beyond the access token. A secret: it is held in memory
 * for one request, never stored, and its text form hides it.
 */
sealed interface DeletionProof {
    class Password(val value: String) : DeletionProof {
        override fun toString() = "Password(…)"
    }

    /** A fresh sign-in with the provider: the one-time ColaBike code and the PKCE verifier. */
    class Provider(val code: String, val verifier: String) : DeletionProof {
        override fun toString() = "Provider(…)"
    }
}
