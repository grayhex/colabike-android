package ru.colabike.core.model

import java.time.Instant

/**
 * This phone's binding to the account at the server (cola#342). The push address the provider gave
 * is a secret and is never in an answer; the generation is not: the app compares it with every push
 * and shows nothing made for another one.
 */
data class PushDeviceBinding(
    val provider: String,
    val projectId: String,
    val generation: Int,
    val registeredAt: Instant,
    val updatedAt: Instant,
    val lastSeenAt: Instant,
)

/**
 * What the phone tells the server to bind itself: the install, the project and the provider's
 * address. [expectedGeneration] is the generation the app believes is current: the server refuses
 * (409) when it knows another, so a late answer cannot bring the past back.
 *
 * [token] is the address of the phone at the provider: it is not printed, logged or kept.
 */
class PushDeviceRegistration(
    val installationId: String,
    val projectId: String,
    val token: String,
    val expectedGeneration: Int? = null,
) {
    override fun toString() =
        "PushDeviceRegistration(project=$projectId, expectedGeneration=$expectedGeneration)"
}

/** The registry of push addresses at the server, for the one phone the session belongs to. */
interface PushDeviceRepository {
    /** This phone's binding, or null when there is none (never made, revoked, or ended). */
    suspend fun current(): PushDeviceBinding?

    /** Binds the phone, or moves the binding to a new address; the same request repeats safely. */
    suspend fun register(registration: PushDeviceRegistration): PushDeviceBinding

    /** Revokes the binding. Revoking what is not there is fine. */
    suspend fun revoke()
}
