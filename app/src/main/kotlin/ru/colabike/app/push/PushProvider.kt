package ru.colabike.app.push

/** Whether the phone can get push at all, as the provider's SDK says it. */
sealed interface PushAvailability {
    data object Available : PushAvailability

    /** The provider's application (RuStore, a supported VK app) is not installed. */
    data object NoDistributor : PushAvailability

    /** This build has no provider or no project: push is not offered. */
    data object NotConfigured : PushAvailability

    /** The SDK reported a fault. */
    data object Failed : PushAvailability
}

/**
 * The push provider behind one small interface, so that the rest of the app knows
 * `PushAvailability` and a token and never a vendor's types. A build without a provider uses
 * [NoPushProvider]: the app and its inbox work the same, and push is simply not offered.
 */
interface PushProvider {
    suspend fun availability(): PushAvailability

    /** The phone's token at the provider, or null when there is none. Never logged. */
    suspend fun token(): String?

    /** Forgets the token at the provider (a sign-out, a withdrawn consent). */
    suspend fun deleteToken()
}

/** The build without a push provider. */
object NoPushProvider : PushProvider {
    override suspend fun availability() = PushAvailability.NotConfigured

    override suspend fun token(): String? = null

    override suspend fun deleteToken() = Unit
}
