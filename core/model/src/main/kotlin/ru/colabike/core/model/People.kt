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

/**
 * What the viewer is to a person. For a guest the API gives none ([PersonSummary.relationship] is
 * null).
 */
data class Relationship(
    val isSelf: Boolean,
    /** The viewer follows the person. */
    val following: Boolean,
    /** The person follows the viewer. */
    val followedBy: Boolean,
    /** Both follow each other. */
    val friends: Boolean,
    /**
     * The viewer blocked the person. Who blocked the viewer is never told: the API has no field for
     * it, and a refusal reads as "unavailable".
     */
    val blockedByMe: Boolean = false,
)

/** A person in a list (a follower, a search result), with what the viewer is to them. */
data class PersonSummary(val person: Person, val relationship: Relationship?)

data class ProfileCounts(val bikes: Int, val followers: Int, val following: Int)

/**
 * A person's public page. The key is [id]; the username is a name that can change. Nothing private
 * is here: the API never sends the e-mail, the settings or the role.
 */
data class Profile(
    val person: Person,
    val bio: String,
    val location: String,
    val joined: java.time.Instant,
    val counts: ProfileCounts,
    /** Null for a guest. */
    val relationship: Relationship?,
)

/** The state of a subscription after `PUT` or `DELETE`: what the server says. */
data class FollowState(val relationship: Relationship, val followers: Int)

/** A subscription that changed, for every screen showing the same person to agree. */
data class FollowChange(val id: UserId, val state: FollowState)
