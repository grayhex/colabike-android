package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.colabike.api.apis.MarketApi
import ru.colabike.api.apis.PersonalApi
import ru.colabike.api.models.MarketBikeLink as MarketBikeLinkDto
import ru.colabike.api.models.MarketCatalogLink as MarketCatalogLinkDto
import ru.colabike.api.models.MarketListingDetail as MarketListingDetailDto
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Listing
import ru.colabike.core.model.ListingBikeLink
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.ListingCatalogLink
import ru.colabike.core.model.ListingCategory
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingSort
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.ListingType
import ru.colabike.core.model.MarketQuery
import ru.colabike.core.model.MarketRepository
import ru.colabike.core.model.Page
import ru.colabike.core.model.Photo
import ru.colabike.core.model.SavedListingChange
import ru.colabike.core.model.SellerListings

/**
 * The market. Listings are public, but the same client carries the Bearer of a signed-in person:
 * their own draft, the "saved" flag of a page and the contact need it. Ids that cannot be a UUID
 * are a missing listing without a request (a link can carry anything); a seller that cannot be a
 * username is a missing seller.
 */
class NetworkMarketRepository(
    private val api: MarketApi,
    private val personal: PersonalApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MarketRepository {
    private val changes = MutableSharedFlow<SavedListingChange>(extraBufferCapacity = 16)
    override val savedChanges: SharedFlow<SavedListingChange> = changes.asSharedFlow()

    override suspend fun page(query: MarketQuery, cursor: String?, limit: Int): Page<ListingBrief> {
        val seller =
            query.seller?.let { name -> name.takeIf { USERNAME.matches(it) } ?: throw notFound() }
        val page =
            apiCall(dispatcher) {
                api.listMarket(
                    q = query.text.trim().take(MAX_TEXT).takeIf { it.isNotEmpty() },
                    category =
                        query.category?.let {
                            when (it) {
                                ListingCategory.Bikes -> MarketApi.CategoryListMarket.bikes
                                ListingCategory.Components ->
                                    MarketApi.CategoryListMarket.components
                                ListingCategory.Accessories ->
                                    MarketApi.CategoryListMarket.accessories
                            }
                        },
                    type =
                        query.type?.let {
                            when (it) {
                                ListingType.Sale -> MarketApi.TypeListMarket.sale
                                ListingType.Wanted -> MarketApi.TypeListMarket.wanted
                                ListingType.Exchange -> MarketApi.TypeListMarket.exchange
                                ListingType.Free -> MarketApi.TypeListMarket.free
                            }
                        },
                    condition =
                        query.condition?.let {
                            when (it) {
                                ListingCondition.New -> MarketApi.ConditionListMarket.new
                                ListingCondition.Used -> MarketApi.ConditionListMarket.used
                            }
                        },
                    priceMin = query.priceMin?.let(::roubles),
                    priceMax = query.priceMax?.let(::roubles),
                    city = query.city.trim().take(MAX_CITY).takeIf { it.isNotEmpty() },
                    seller = seller,
                    sort =
                        when (query.sort) {
                            ListingSort.New -> MarketApi.SortListMarket.new
                            ListingSort.PriceAsc -> MarketApi.SortListMarket.price_asc
                            ListingSort.PriceDesc -> MarketApi.SortListMarket.price_desc
                        },
                    limit = limit,
                    cursor = cursor,
                )
            }
        return Page(page.items.map { it.toBrief(media) }, page.nextCursor)
    }

    override suspend fun listing(id: ListingId): Listing {
        val uuid = uuidOrNotFound(id.value)
        return apiCall(dispatcher) { api.getMarketListing(uuid) }.toModel(media)
    }

    override suspend fun sellerOthers(id: ListingId): SellerListings {
        val uuid = uuidOrNotFound(id.value)
        val others = apiCall(dispatcher) { api.listMarketSellerOthers(uuid) }
        return SellerListings(others.items.map { it.toBrief(media) }, others.total.coerceAtLeast(0))
    }

    override suspend fun saved(cursor: String?, limit: Int): Page<ListingBrief> {
        val page = apiCall(dispatcher) { personal.listSavedMarket(limit = limit, cursor = cursor) }
        return Page(page.items.map { it.toBrief(media) }, page.nextCursor)
    }

    override suspend fun setSaved(id: ListingId, saved: Boolean): Boolean {
        val uuid = uuidOrNotFound(id.value)
        val answer =
            apiCall(dispatcher) {
                if (saved) api.saveMarketListing(uuid) else api.unsaveMarketListing(uuid)
            }
        changes.tryEmit(SavedListingChange(id, answer.saved))
        return answer.saved
    }

    override suspend fun contact(id: ListingId): String {
        val uuid = uuidOrNotFound(id.value)
        // Nothing is kept: not here, not in an HTTP cache (the client has none), not in a log.
        return apiCall(dispatcher) { api.getMarketContact(uuid) }.contact
    }

    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw notFound()

    private fun notFound() = DataError.NotFound()

    /** The contract takes whole roubles as digits, at most ten of them. */
    private fun roubles(value: Long): String = value.coerceIn(0L, MAX_ROUBLES).toString()

    private companion object {
        const val MAX_TEXT = 150
        const val MAX_CITY = 100
        const val MAX_ROUBLES = 9_999_999_999L
        val USERNAME = Regex("^[A-Za-z0-9._-]{3,30}$")
    }
}

internal fun MarketListingDetailDto.toModel(media: MediaUrls): Listing =
    Listing(
        brief =
            ListingBrief(
                id = id.toString(),
                title = title,
                price = price,
                currency = currency,
                category = category.value,
                type = listingType.value,
                location = location,
                cover = photos(media).firstOrNull(),
                author = author.toModel(media),
            ),
        description = description,
        condition = condition.toCondition(),
        status = status.toStatus(),
        expired = expired,
        hasContact = hasContact,
        publishedAt = publishedAt?.toInstant(),
        path = path,
        photos = photos(media),
        isOwner = isOwner,
        componentModel = componentModel?.toModel(),
        bikeModel = bikeModel?.toModel(),
        linkedBike = linkedBike?.toModel(),
        saved = saved,
    )

// The contact the owner's copy of a listing carries is deliberately not read: the app asks for it
// by `/contact`, as for anybody, so there is one place it enters the app and one place it can
// leave.

private fun MarketListingDetailDto.photos(media: MediaUrls): List<Photo> =
    photos.mapNotNull { photo ->
        media.resolve(photo.url)?.let { Photo(photo.id.toString(), it) }
    }

private fun MarketListingDetailDto.Condition.toCondition(): ListingCondition? =
    when (this) {
        MarketListingDetailDto.Condition.new -> ListingCondition.New
        MarketListingDetailDto.Condition.used -> ListingCondition.Used
        MarketListingDetailDto.Condition.unknown_default_open_api -> null
    }

private fun MarketListingDetailDto.Status.toStatus(): ListingStatus =
    when (this) {
        MarketListingDetailDto.Status.draft -> ListingStatus.Draft
        MarketListingDetailDto.Status.active -> ListingStatus.Active
        MarketListingDetailDto.Status.sold -> ListingStatus.Sold
        MarketListingDetailDto.Status.unknown_default_open_api -> ListingStatus.Unknown
    }

private fun MarketCatalogLinkDto.toModel() =
    ListingCatalogLink(id = id.toString(), name = name, path = path, archived = archived)

private fun MarketBikeLinkDto.toModel() =
    ListingBikeLink(id = BikeId(id.toString()), name = name, path = path)
