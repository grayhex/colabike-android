package ru.colabike.core.model

import java.time.Instant

/** What the server filters the feed by (`type`). */
enum class FeedFilter {
    All,
    Rides,
    Journal,
}

/**
 * A listing of the market as the feed shows it: enough for a card, not for a page. The contact and
 * the owner's fields never come here.
 */
data class ListingBrief(
    val id: String,
    val title: String,
    /** In [currency]; null is "by agreement" (or a free item with a price of 0). */
    val price: Double?,
    val currency: String,
    val category: String,
    val type: String,
    val location: String,
    val cover: Photo?,
    val author: Person,
)

/**
 * One publication in the feed. The server fills exactly one object per item; an item whose object
 * is missing, or whose type this version does not know, is dropped by the repository rather than
 * shown empty.
 */
sealed interface FeedItem {
    val publishedAt: Instant

    data class Bike(val bike: BikeSummary, override val publishedAt: Instant) : FeedItem

    data class Journal(val entry: JournalSummary, override val publishedAt: Instant) : FeedItem

    data class Ride(val ride: RideSummary, override val publishedAt: Instant) : FeedItem

    data class Listing(val listing: ListingBrief, override val publishedAt: Instant) : FeedItem

    /** A key that identifies the item among the others in a list. */
    val key: String
        get() =
            when (this) {
                is Bike -> "bike:${bike.id.value}"
                is Journal -> "journal:${entry.id.value}"
                is Ride -> "ride:${ride.id.value}"
                is Listing -> "market:${listing.id}"
            }
}
