package ru.colabike.core.model

import java.time.Instant
import kotlinx.coroutines.flow.SharedFlow

/** A market listing; [value] is its UUID (the site's short id is another key and is not used). */
@JvmInline value class ListingId(val value: String)

enum class ListingCategory {
    Bikes,
    Components,
    Accessories,
}

/** What the author wants: to sell, to buy, to swap or to give away. */
enum class ListingType {
    Sale,
    Wanted,
    Exchange,
    Free,
}

enum class ListingCondition {
    New,
    Used,
}

enum class ListingSort {
    /** Newest publication first. */
    New,

    /** Cheaper first; listings without a price come last in both price orders. */
    PriceAsc,

    /** Dearer first. */
    PriceDesc,
}

/**
 * What the market list is asked for. Every field is the server's own filter; a change starts a new
 * list, because a cursor of one order (or one set of filters) is not valid for another.
 */
data class MarketQuery(
    val text: String = "",
    val category: ListingCategory? = null,
    val type: ListingType? = null,
    val condition: ListingCondition? = null,
    /** Whole roubles, both ends included; a listing without a price is not in a priced query. */
    val priceMin: Long? = null,
    val priceMax: Long? = null,
    val city: String = "",
    /** A seller's `username`: the listings of one person. */
    val seller: String? = null,
    val sort: ListingSort = ListingSort.New,
) {
    /** The filters the person can lift; the seller and the order are not among them. */
    val hasFilters: Boolean
        get() =
            text.isNotEmpty() ||
                category != null ||
                type != null ||
                condition != null ||
                priceMin != null ||
                priceMax != null ||
                city.isNotEmpty()

    val isDefault: Boolean
        get() = !hasFilters && seller == null && sort == ListingSort.New
}

/** Where a listing stands. A draft is seen by its owner only. */
enum class ListingStatus {
    Draft,
    Active,
    Sold,

    /** A status of a later server version. */
    Unknown,
}

/**
 * The catalog model a listing is tied to. The name and the path follow the catalog, and a merged
 * model is the canonical one.
 */
data class ListingCatalogLink(
    val id: String,
    val name: String,
    /** The model's page on the site, relative to the site's address. */
    val path: String,
    val archived: Boolean,
)

/** The seller's public bike the listing is tied to. A private bike is never here. */
data class ListingBikeLink(val id: BikeId, val name: String, val path: String)

/**
 * A listing's page. The contact is not here: it comes by [MarketRepository.contact] after an
 * explicit action and is kept by nothing but the screen that asked.
 */
data class Listing(
    val brief: ListingBrief,
    val description: String,
    /** `new` or `used`; null for a value of a later server version. */
    val condition: ListingCondition?,
    val status: ListingStatus,
    /** The term is over: the page opens with a note and no contact, like a sold one. */
    val expired: Boolean,
    /** The author gave a contact. Whether it is shown is decided by the server. */
    val hasContact: Boolean,
    val publishedAt: Instant?,
    /** The listing's page on the site, relative to the site's address. */
    val path: String,
    val photos: List<Photo>,
    val isOwner: Boolean,
    val componentModel: ListingCatalogLink?,
    val bikeModel: ListingCatalogLink?,
    val linkedBike: ListingBikeLink?,
    /** Saved by the viewer; a guest has false. */
    val saved: Boolean,
) {
    /** Listed in the market now. Only such a listing can be saved or asked for a contact. */
    val onMarket: Boolean
        get() = status == ListingStatus.Active && !expired
}

/** The seller's other listings that are on the market now: at most four, and how many there are. */
data class SellerListings(val items: List<ListingBrief>, val total: Int)

/**
 * The state of "saved" after a `PUT` or `DELETE`, announced to every screen showing the listing.
 */
data class SavedListingChange(val id: ListingId, val saved: Boolean)

/** The market. Implementations throw [DataError]. */
interface MarketRepository {
    /** Listings that are on the market now, by the server's own filters, sort and cursor. */
    suspend fun page(
        query: MarketQuery,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<ListingBrief>

    /**
     * A listing by id. A sold or expired one is read with its note; a draft only by its owner. What
     * is hidden, blocked or unknown is [DataError.NotFound] alike.
     */
    suspend fun listing(id: ListingId): Listing

    suspend fun sellerOthers(id: ListingId): SellerListings

    /** The signed-in person's saved listings that are on the market now, newest saves first. */
    suspend fun saved(cursor: String? = null, limit: Int = 24): Page<ListingBrief>

    /** Idempotent; the answer is the state to show. Only a listing on the market can be saved. */
    suspend fun setSaved(id: ListingId, saved: Boolean): Boolean

    /** Every change made by [setSaved], for screens that show the same listing. */
    val savedChanges: SharedFlow<SavedListingChange>

    /**
     * The seller's contact. Needs a signed-in person with a confirmed e-mail ([DataError.Rejected]
     * `email_verification_required` otherwise), is rate limited ([DataError.RateLimited]) and is
     * not given for a listing that is off the market. Never cached, never logged.
     */
    suspend fun contact(id: ListingId): String
}
