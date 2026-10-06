package ru.colabike.core.network

import ru.colabike.api.models.BikeClassificationRequest
import ru.colabike.api.models.BikeComponentPatchRequest
import ru.colabike.api.models.BikeComponentRequest
import ru.colabike.api.models.BikePatchRequest
import ru.colabike.api.models.BikePatchRequestPriceVisibility
import ru.colabike.api.models.BikeRequest
import ru.colabike.api.models.BikeRequestPriceVisibility
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BikePatch
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.ComponentPatch
import ru.colabike.core.model.PriceVisibility

/**
 * The whole classification, as the server takes it: it is replaced as a unit. A key this build does
 * not list is a bug of the form (it only offers listed ones), not something to send.
 */
internal fun ClassificationDraft.toRequest(): BikeClassificationRequest =
    BikeClassificationRequest(
        category = known(BikeClassificationRequest.Category.entries, category) { it.value },
        subtype =
            subtype?.let { known(BikeClassificationRequest.Subtype.entries, it) { e -> e.value } },
        suspension =
            suspension?.let {
                known(BikeClassificationRequest.Suspension.entries, it) { e -> e.value }
            },
        construction =
            construction?.let {
                known(BikeClassificationRequest.Construction.entries, it) { e -> e.value }
            },
        uses = uses.map { known(BikeClassificationRequest.Uses.entries, it) { e -> e.value } },
        electric = electric,
        fatbike = fatbike,
    )

private fun <E : Enum<E>> known(entries: List<E>, key: String, value: (E) -> String): E =
    entries.firstOrNull { value(it) == key && it.name != UNKNOWN }
        ?: throw IllegalArgumentException("Unknown classification key")

private const val UNKNOWN = "unknown_default_open_api"

/** A new bike: everything the form holds, the audience named outright. */
internal fun BikeDraft.toRequest(): BikeRequest =
    BikeRequest(
        name = name.trim(),
        brand = brand.trim(),
        model = model.trim(),
        // A new bike has its year (BikeRules.check); the form never gets here without it.
        year = requireNotNull(year) { "A new bike needs a year" },
        classification = classification.toRequest(),
        isPublic = isPublic,
        trim = trim.trim(),
        description = description.trim(),
        color = color.trim(),
        propertySize = size.trim(),
        weight = weightKg,
        mileage = mileageKm,
        manufacturerUrl = manufacturerUrl.trim(),
        price = priceRub,
        priceVisibility = priceVisibility.toCreate(),
        isFormer = isFormer,
    )

/** Only what changed. A weight or price taken away is added as `null` by the client itself. */
internal fun BikePatch.toRequest(): BikePatchRequest =
    BikePatchRequest(
        name = name?.trim(),
        brand = brand?.trim(),
        model = model?.trim(),
        trim = trim?.trim(),
        year = year,
        classification = classification?.toRequest(),
        description = description?.trim(),
        color = color?.trim(),
        propertySize = size?.trim(),
        weight = weightKg,
        mileage = mileageKm,
        manufacturerUrl = manufacturerUrl?.trim(),
        price = priceRub,
        priceVisibility = priceVisibility?.toPatch(),
        isFormer = isFormer,
        isPublic = isPublic,
    )

private fun PriceVisibility.toCreate() =
    BikeRequestPriceVisibility(bike = bike, components = components, accessories = accessories)

private fun PriceVisibility.toPatch() =
    BikePatchRequestPriceVisibility(bike = bike, components = components, accessories = accessories)

private fun sectionOf(key: String): BikeComponentRequest.Section =
    BikeComponentRequest.Section.entries.firstOrNull {
        it.value == key && it != BikeComponentRequest.Section.unknown_default_open_api
    } ?: throw IllegalArgumentException("Unknown section")

private fun patchSectionOf(key: String): BikeComponentPatchRequest.Section =
    BikeComponentPatchRequest.Section.entries.firstOrNull {
        it.value == key && it != BikeComponentPatchRequest.Section.unknown_default_open_api
    } ?: throw IllegalArgumentException("Unknown section")

/** A new part; the group is given as the form reckoned it, empty meaning "by category". */
internal fun ComponentDraft.toRequest(): BikeComponentRequest =
    BikeComponentRequest(
        section = sectionOf(section),
        category = category.trim(),
        name = name.trim(),
        notes = notes.trim(),
        price = priceRub,
        url = url.trim(),
        groupId = groupId,
    )

/** Only what changed. A price taken away is added as `null` by the client itself. */
internal fun ComponentPatch.toRequest(): BikeComponentPatchRequest =
    BikeComponentPatchRequest(
        section = section?.let(::patchSectionOf),
        category = category?.trim(),
        name = name?.trim(),
        notes = notes?.trim(),
        price = priceRub,
        url = url?.trim(),
        groupId = groupId,
    )
