package ru.colabike.core.network

import java.util.UUID
import ru.colabike.api.models.JournalPatchRequest
import ru.colabike.api.models.JournalRequest
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.JournalDraft
import ru.colabike.core.model.JournalPatch
import ru.colabike.core.model.JournalStatus

private const val UNKNOWN = "unknown_default_open_api"

/** A key this build does not list is a bug of the form (it only offers listed ones). */
private fun <E : Enum<E>> known(entries: List<E>, key: String, value: (E) -> String): E =
    entries.firstOrNull { value(it) == key && it.name != UNKNOWN }
        ?: throw IllegalArgumentException("Unknown journal key")

private fun JournalStatus.toRequest() =
    if (this == JournalStatus.Draft) JournalRequest.Status.draft
    else JournalRequest.Status.published

private fun JournalStatus.toPatch() =
    if (this == JournalStatus.Draft) JournalPatchRequest.Status.draft
    else JournalPatchRequest.Status.published

/** A new entry: the audience named outright, as the contract requires. */
internal fun JournalDraft.toRequest(bike: BikeId): JournalRequest =
    JournalRequest(
        bikeId = UUID.fromString(bike.value),
        kind = known(JournalRequest.Kind.entries, kind) { it.value },
        title = title.trim(),
        body = body.trim(),
        status = status.toRequest(),
        // A draft is the author's alone; saying "public" with it would only confuse a later edit.
        isPublic = isPublic && status == JournalStatus.Published,
        eventDate = eventDate,
        mileage = mileageKm,
        installationResult =
            installationResult
                ?.takeIf { kind == "build" }
                ?.let { known(JournalRequest.InstallationResult.entries, it) { e -> e.value } },
        componentIds = componentIds.distinct().map(UUID::fromString),
    )

/** Only what changed. A date, a mileage or a result taken away is added as `null` by the client. */
internal fun JournalPatch.toRequest(): JournalPatchRequest =
    JournalPatchRequest(
        kind = kind?.let { known(JournalPatchRequest.Kind.entries, it) { e -> e.value } },
        title = title?.trim(),
        body = body?.trim(),
        status = status?.toPatch(),
        isPublic = isPublic,
        eventDate = eventDate,
        mileage = mileageKm,
        installationResult =
            installationResult?.let {
                known(JournalPatchRequest.InstallationResult.entries, it) { e -> e.value }
            },
        componentIds = componentIds?.distinct()?.map(UUID::fromString),
    )
