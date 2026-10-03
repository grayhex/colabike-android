package ru.colabike.core.model

/** Anyone shown next to content: an author, a follower, a participant. */
data class Person(val id: UserId, val username: String, val name: String, val avatarUrl: String?) {
    /** What to call the person in UI and in TalkBack: the name, else the username. */
    val displayName: String
        get() = name.ifBlank { username }
}

/**
 * The signed-in person from `/me`. The e-mail and the role stay out on purpose: no screen of the
 * foundation needs them, and what is not held cannot leak into logs or UI state.
 */
data class Account(
    val id: UserId,
    val username: String,
    val name: String,
    val avatarUrl: String?,
    val bio: String,
    val location: String,
    val emailVerified: Boolean,
) {
    val displayName: String
        get() = name.ifBlank { username }
}
