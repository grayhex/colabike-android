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
    /** The project at the provider this build belongs to; empty for a build that has none. */
    val projectId: String
        get() = ""

    /**
     * Whether the provider can deliver here. Before [start] this is what the phone itself can tell
     * (is the provider's application there); after it, what the provider's library says.
     */
    suspend fun availability(): PushAvailability

    /**
     * Starts the provider's library. Called only once the person has said yes to push (or the phone
     * is already registered, so that a message can arrive): a library that is not started does
     * nothing, and so a person who never turns push on has none of its work. Safe to repeat.
     */
    fun start() {}

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
