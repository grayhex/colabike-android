package ru.colabike.core.network

import ru.colabike.api.models.AccountSession as AccountSessionDto
import ru.colabike.api.models.Author as AuthorDto
import ru.colabike.api.models.Bike as BikeDto
import ru.colabike.api.models.BikeComponent as BikeComponentDto
import ru.colabike.api.models.BikePage as BikePageDto
import ru.colabike.api.models.BikePhoto as BikePhotoDto
import ru.colabike.api.models.BikeSummary as BikeSummaryDto
import ru.colabike.api.models.Me as MeDto
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountSession
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.Page
import ru.colabike.core.model.Person
import ru.colabike.core.model.Photo
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
        description = description,
        color = color,
        size = propertySize,
        weightKg = weight,
        mileageKm = mileage,
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
    )

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
