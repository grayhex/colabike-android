package ru.colabike.core.model

import java.time.Instant
import java.time.LocalDate

@JvmInline value class JournalId(val value: String)

/** The bike an entry is about: enough to name it and to open it. */
data class BikeRef(val id: BikeId, val name: String)

enum class JournalStatus {
    Draft,
    Published,
}

/**
 * An entry of a bike's journal as a list shows it. [kind] is the API's key (`build`, `service`,
 * `review`, `question`, `story`); a kind this version does not know arrives as [KIND_OTHER] and is
 * shown without a label.
 */
data class JournalSummary(
    val id: JournalId,
    val kind: String,
    val title: String,
    /** A draft is the owner's alone. */
    val status: JournalStatus,
    val isPublic: Boolean,
    /** When the thing happened (a service, a build step); not when it was written. */
    val eventDate: LocalDate?,
    val mileageKm: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val bike: BikeRef,
    val author: Person,
    val likes: Int,
    val comments: Int,
    val liked: Boolean,
    /** The start of the text without markup, up to 240 characters; empty in a full entry. */
    val excerpt: String,
) {
    companion object {
        const val KIND_OTHER = "other"
    }
}

/**
 * The whole entry: the Markdown [body], the components as they were when it was written, and its
 * photos. The snapshot is the entry's, not the bike's today.
 */
data class JournalEntry(
    val summary: JournalSummary,
    val body: String,
    val components: List<BikeComponent>,
    val photos: List<Photo>,
)

/** The state of "saved" after a `PUT` or `DELETE`, announced to every screen showing the entry. */
data class SavedChange(val id: JournalId, val saved: Boolean)
