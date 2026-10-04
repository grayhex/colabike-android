package ru.colabike.core.network

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.colabike.api.models.AccountSession as AccountSessionDto
import ru.colabike.api.models.Author as AuthorDto
import ru.colabike.api.models.Bike as BikeDto
import ru.colabike.api.models.BikeClassification as ClassificationDto
import ru.colabike.api.models.BikeComponent as BikeComponentDto
import ru.colabike.api.models.BikePage as BikePageDto
import ru.colabike.api.models.BikePhoto as BikePhotoDto
import ru.colabike.api.models.BikeRef as BikeRefDto
import ru.colabike.api.models.BikeSummary as BikeSummaryDto
import ru.colabike.api.models.Comment as CommentDto
import ru.colabike.api.models.CommentPage as CommentPageDto
import ru.colabike.api.models.FeedItem as FeedItemDto
import ru.colabike.api.models.FeedPage as FeedPageDto
import ru.colabike.api.models.FollowResult as FollowResultDto
import ru.colabike.api.models.JournalComponent as JournalComponentDto
import ru.colabike.api.models.JournalEntry as JournalEntryDto
import ru.colabike.api.models.JournalPage as JournalPageDto
import ru.colabike.api.models.JournalSummary as JournalSummaryDto
import ru.colabike.api.models.MarketListing as MarketListingDto
import ru.colabike.api.models.Me as MeDto
import ru.colabike.api.models.Profile as ProfileDto
import ru.colabike.api.models.Relationship as RelationshipDto
import ru.colabike.api.models.ReplyPage as ReplyPageDto
import ru.colabike.api.models.UserPage as UserPageDto
import ru.colabike.api.models.UserSummary as UserSummaryDto
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.BikeClassification
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentThread
import ru.colabike.core.model.CommentThreads
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.FollowState
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.ListingBrief
import ru.colabike.core.model.Page
import ru.colabike.core.model.Person
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Photo
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ProfileCounts
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.SessionKind
import ru.colabike.core.model.SessionPlatform
import ru.colabike.core.model.UserId

// DTO -> app model. Screens never see generated classes (docs/architecture.md, "Data flow").

internal fun BikePageDto.toModel(media: MediaUrls): Page<BikeSummary> =
    Page(items.map { it.toModel(media) }, nextCursor)

internal fun BikeSummaryDto.toModel(media: MediaUrls): BikeSummary =
    BikeSummary(
        id = BikeId(id.toString()),
        name = name,
        brand = brand,
        model = model,
        year = year.takeIf { it > 0 },
        category = category,
        classification = classification.toModel(),
        cover = coverPhoto?.toModel(media),
        photoCount = photoCount,
        author = author?.toModel(media),
        likes = likes,
        liked = liked,
        comments = comments,
        isOwner = isOwner,
        isPublic = isPublic,
        isFormer = isFormer,
    )

internal fun ClassificationDto.toModel(): BikeClassification =
    BikeClassification(
        category = category,
        subtype = subtype,
        suspension = suspension,
        construction = construction,
        electric = electric,
        fatbike = fatbike,
    )

internal fun BikeDto.toModel(media: MediaUrls): BikeDetail =
    BikeDetail(
        summary =
            BikeSummary(
                id = BikeId(id.toString()),
                name = name,
                brand = brand,
                model = model,
                year = year.takeIf { it > 0 },
                category = category,
                classification = classification.toModel(),
                cover = coverPhoto?.toModel(media),
                photoCount = photoCount,
                author = author?.toModel(media),
                likes = likes,
                liked = liked,
                comments = comments,
                isOwner = isOwner,
                isPublic = isPublic,
                isFormer = isFormer,
            ),
        trim = trim,
        description = description,
        color = color,
        size = propertySize,
        weightKg = weight,
        mileageKm = mileage,
        manufacturerUrl = httpsOrNull(manufacturerUrl),
        purposes = purposes.filter { it.isNotBlank() },
        priceRub = price,
        groupOrder = groupOrder,
        photos = photos.mapNotNull { it.toModel(media) },
        components = components.map { it.toModel() },
    )

internal fun BikePhotoDto.toModel(media: MediaUrls): Photo? =
    media.resolve(url)?.let { Photo(id = id.toString(), url = it) }

internal fun AuthorDto.toModel(media: MediaUrls): Person =
    Person(UserId(id.toString()), username, name, media.resolve(avatarUrl))

internal fun BikeComponentDto.toModel(): BikeComponent =
    BikeComponent(
        id = id.toString(),
        // A section added after this release is shown with the "other" ones, not dropped.
        section =
            if (section == BikeComponentDto.Section.unknown_default_open_api) "other"
            else section.value,
        category = category,
        name = name,
        notes = notes,
        url = httpsOrNull(url),
        groupId = groupId,
        sortOrder = sortOrder,
        priceRub = price,
        modelId = modelId?.toString(),
    )

/**
 * A link from the server is shown only if it is a plain `https` address, never `javascript:` etc.
 */
internal fun httpsOrNull(value: String?): String? =
    value
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.toHttpUrlOrNull()
        ?.takeIf { it.scheme == "https" }
        ?.toString()

/** Public for core:auth: a token grant carries the same `Me`. */
fun MeDto.toAccount(media: MediaUrls): Account =
    Account(
        id = UserId(id.toString()),
        username = username,
        name = name,
        avatarUrl = media.resolve(avatarUrl),
        bio = bio,
        location = location,
        emailVerified = emailVerifiedAt != null,
    )

internal fun AccountSessionDto.toModel(): AccountSession =
    AccountSession(
        id = id.toString(),
        kind =
            when (kind) {
                AccountSessionDto.Kind.browser -> SessionKind.Browser
                AccountSessionDto.Kind.device -> SessionKind.Device
                AccountSessionDto.Kind.unknown_default_open_api -> SessionKind.Unknown
            },
        deviceName = deviceName?.takeIf { it.isNotBlank() },
        platform =
            when (platform) {
                AccountSessionDto.Platform.android -> SessionPlatform.Android
                AccountSessionDto.Platform.ios -> SessionPlatform.Ios
                AccountSessionDto.Platform.other -> SessionPlatform.Other
                AccountSessionDto.Platform.unknown_default_open_api,
                null -> SessionPlatform.Unknown
            },
        appVersion = appVersion?.takeIf { it.isNotBlank() },
        userAgent = userAgent,
        createdAt = createdAt.toInstant(),
        lastSeenAt = lastSeenAt.toInstant(),
        isCurrent = current,
    )

internal fun RelationshipDto.toModel(): Relationship =
    Relationship(
        isSelf = isSelf,
        following = following,
        followedBy = followedBy,
        friends = friends,
    )

internal fun UserSummaryDto.toModel(media: MediaUrls): PersonSummary =
    PersonSummary(
        person = Person(UserId(id.toString()), username, name, media.resolve(avatarUrl)),
        relationship = relationship?.toModel(),
    )

internal fun UserPageDto.toModel(media: MediaUrls): Page<PersonSummary> =
    Page(items.map { it.toModel(media) }, nextCursor)

internal fun ProfileDto.toModel(media: MediaUrls): Profile =
    Profile(
        person = Person(UserId(id.toString()), username, name, media.resolve(avatarUrl)),
        bio = bio,
        location = location,
        joined = createdAt.toInstant(),
        counts = ProfileCounts(counts.bikes, counts.followers, counts.following),
        relationship = relationship?.toModel(),
    )

internal fun FollowResultDto.toModel(): FollowState =
    FollowState(relationship = relationship.toModel(), followers = followers)

// --- journal and feed ----------------------------------------------------------------------

internal fun BikeRefDto.toModel(): BikeRef = BikeRef(BikeId(id.toString()), name)

internal fun JournalPageDto.toModel(media: MediaUrls): Page<JournalSummary> =
    Page(items.map { it.toModel(media) }, nextCursor)

internal fun JournalSummaryDto.toModel(media: MediaUrls): JournalSummary =
    JournalSummary(
        id = JournalId(id.toString()),
        // A kind added after this release is shown without a label, not dropped.
        kind =
            if (kind == JournalSummaryDto.Kind.unknown_default_open_api) JournalSummary.KIND_OTHER
            else kind.value,
        title = title,
        status = status.toModel(),
        isPublic = isPublic,
        eventDate = eventDate,
        mileageKm = mileage?.takeIf { it > 0 },
        createdAt = createdAt.toInstant(),
        updatedAt = updatedAt.toInstant(),
        bike = bike.toModel(),
        author = author.toModel(media),
        likes = likes,
        comments = comments,
        liked = liked,
        excerpt = excerpt,
    )

private fun JournalSummaryDto.Status.toModel() =
    if (this == JournalSummaryDto.Status.draft) JournalStatus.Draft else JournalStatus.Published

internal fun JournalEntryDto.toModel(media: MediaUrls): JournalEntry =
    JournalEntry(
        summary =
            JournalSummary(
                id = JournalId(id.toString()),
                kind =
                    if (kind == JournalEntryDto.Kind.unknown_default_open_api)
                        JournalSummary.KIND_OTHER
                    else kind.value,
                title = title,
                status =
                    if (status == JournalEntryDto.Status.draft) JournalStatus.Draft
                    else JournalStatus.Published,
                isPublic = isPublic,
                eventDate = eventDate,
                mileageKm = mileage?.takeIf { it > 0 },
                createdAt = createdAt.toInstant(),
                updatedAt = updatedAt.toInstant(),
                bike = bike.toModel(),
                author = author.toModel(media),
                likes = likes,
                comments = comments,
                liked = liked,
                excerpt = "",
            ),
        body = body,
        components = components.map { it.toModel() },
        photos =
            photos.mapNotNull { photo ->
                media.resolve(photo.url)?.let { Photo(photo.id.toString(), it) }
            },
    )

/** A component of the entry's snapshot is shown like a bike's own: same fields, same rules. */
internal fun JournalComponentDto.toModel(): BikeComponent =
    BikeComponent(
        id = id.toString(),
        section =
            if (section == JournalComponentDto.Section.unknown_default_open_api) "other"
            else section.value,
        category = category,
        name = name,
        notes = notes,
        url = httpsOrNull(url),
        groupId = groupId,
        sortOrder = sortOrder,
        priceRub = price,
        modelId = modelId?.toString(),
    )

/** Items the app cannot show (an unknown type, a missing object) are left out of the page. */
internal fun FeedPageDto.toModel(media: MediaUrls): Page<FeedItem> =
    Page(items.mapNotNull { it.toModel(media) }, nextCursor)

internal fun FeedItemDto.toModel(media: MediaUrls): FeedItem? {
    val at = publishedAt.toInstant()
    return when (type) {
        FeedItemDto.Type.bike -> bike?.let { FeedItem.Bike(it.toModel(media), at) }
        FeedItemDto.Type.journal -> journal?.let { FeedItem.Journal(it.toModel(media), at) }
        FeedItemDto.Type.ride -> ride?.let { FeedItem.Ride(it.toModel(media), at) }
        FeedItemDto.Type.market -> listing?.let { FeedItem.Listing(it.toBrief(media), at) }
        FeedItemDto.Type.unknown_default_open_api -> null
    }
}

internal fun MarketListingDto.toBrief(media: MediaUrls): ListingBrief =
    ListingBrief(
        id = id.toString(),
        title = title,
        price = price,
        currency = currency,
        category = category.value,
        type = listingType.value,
        location = location,
        cover =
            photos.firstNotNullOfOrNull { photo ->
                media.resolve(photo.url)?.let { Photo(photo.id.toString(), it) }
            },
        author = author.toModel(media),
    )

// --- comments ------------------------------------------------------------------------------

internal fun CommentDto.toModel(media: MediaUrls): Comment =
    Comment(
        id = id.toString(),
        parentId = parentId?.toString(),
        // A tombstone has neither author nor text, whatever else the body might carry.
        author = if (deleted) null else author?.toModel(media),
        body = if (deleted) null else body,
        createdAt = createdAt.toInstant(),
        editedAt = editedAt?.toInstant(),
        deleted = deleted,
        replyCount = replyCount,
    )

internal fun CommentPageDto.toModel(media: MediaUrls): CommentThreads =
    CommentThreads(
        items =
            items.map {
                CommentThread(it.comment.toModel(media), it.replies.map { r -> r.toModel(media) })
            },
        nextCursor = nextCursor,
        focusPath = focusPath.map { it.toModel(media) },
    )

internal fun ReplyPageDto.toModel(media: MediaUrls): Page<Comment> =
    Page(items.map { it.toModel(media) }, nextCursor)
