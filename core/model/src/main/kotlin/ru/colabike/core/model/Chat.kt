package ru.colabike.core.model

import java.time.Instant

/** A channel of the chat provider by its `type:id`; the provider's SDK opens it by this. */
@JvmInline value class ChannelCid(val value: String)

/** A person as the chat knows them: the chat's own id, not the ColaBike one. */
data class ChatUser(val id: String, val name: String, val imageUrl: String?)

/**
 * What the chat SDK needs to connect a person: the public application key, their profile in the
 * chat, and a short-lived token (five minutes). The token is a credential, so it is neither in
 * `toString()` nor in any log.
 */
class ChatCredentials(
    val apiKey: String,
    val user: ChatUser,
    val token: String,
    val expiresAt: Instant,
    /** The type of ColaBike's channels in the chat: with it the client opens and lists them. */
    val channelType: String,
) {
    override fun toString(): String = "ChatCredentials(user=${user.id}, expiresAt=$expiresAt)"
}

enum class ChatChannelKind {
    /** One other person; opening it again gives the same dialogue. */
    Dm,

    /** A named group of two to seven other people; every request makes a new one. */
    Group,
}

/**
 * The people one may write to: those the person follows (no query), or the matches of a query. The
 * right to write is checked again when the channel is made.
 */
data class ChatPeople(val people: List<Person>, val searching: Boolean)

/**
 * ColaBike's side of the chat: the bridge to the provider (API v1, cola#324). The messages
 * themselves travel between the provider's SDK and the provider; nothing is copied here.
 */
interface ChatRepository {
    /**
     * A token for the chat, asked for again whenever the last has expired. Needs a confirmed
     * e-mail; the answer is 403 `email_verification_required` without it, and 503 when the chat is
     * off.
     */
    suspend fun credentials(): ChatCredentials

    /**
     * Makes or opens a channel with [members] (ColaBike ids). [key] makes a repeat of the same
     * request within a day give the same channel instead of a second group.
     */
    suspend fun open(
        kind: ChatChannelKind,
        members: List<UserId>,
        name: String? = null,
        key: String? = null,
    ): ChannelCid

    suspend fun people(query: String? = null): ChatPeople

    /** Unread messages in all the person's channels (0 before they ever connected). */
    suspend fun unread(): Int
}
