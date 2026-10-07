package ru.colabike.core.network

import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.Call
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.models.BikeRequestPriceVisibility
import ru.colabike.api.models.BikeResolution as ResolutionDto
import ru.colabike.api.models.BikeResolutionBuild as BuildDto
import ru.colabike.api.models.BikeResolutionCandidate as CandidateDto
import ru.colabike.api.models.BikeResolutionCandidateQuality as QualityDto
import ru.colabike.api.models.BikeResolutionRequest
import ru.colabike.api.models.BikeWizardRequest
import ru.colabike.api.models.BikeWizardRequestBike
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BikeWizardRepository
import ru.colabike.core.model.BuildCandidate
import ru.colabike.core.model.BuildPart
import ru.colabike.core.model.BuildQuality
import ru.colabike.core.model.BuildQuery
import ru.colabike.core.model.BuildResolution
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.ResolutionStatus
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.ResolvedBuild
import ru.colabike.core.model.SourceKind
import ru.colabike.core.model.SourcesChecked
import ru.colabike.core.model.SuggestedDetails
import ru.colabike.core.model.UnrecognizedField

/**
 * The search for a build and the making of a bike with it, over the server's own search and rules.
 * Nothing here reads a page: the server's answer is mapped, and what the person checked is sent.
 * [resolving] is the bikes API for a search, which may take over a minute and has to stop when the
 * coroutine does; [created] hears of the bike that was made, for the lists that show bikes.
 */
class NetworkBikeWizardRepository(
    private val api: BikesApi,
    private val media: MediaUrls,
    private val resolving: (onCall: (Call) -> Unit) -> BikesApi = { api },
    private val created: (BikeDetail) -> Unit = {},
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BikeWizardRepository {
    override suspend fun resolve(request: ResolveRequest): BuildResolution {
        val body = request.toRequest()
        return cancellableUpload(dispatcher, api = resolving) { it.resolveBike(body) }.toModel()
    }

    override suspend fun create(
        draft: BikeDraft,
        parts: List<ComponentDraft>,
        previewId: String?,
        identityConfirmed: Boolean,
        key: String,
    ): BikeDetail {
        // The key and the preview are UUIDs by contract; a bad one is a bug here, not a request.
        val uuid =
            requireNotNull(runCatching { UUID.fromString(key) }.getOrNull()) {
                "Idempotency-Key must be a UUID"
            }
        val preview = previewId?.let {
            requireNotNull(runCatching { UUID.fromString(it) }.getOrNull()) {
                "previewId must be a UUID"
            }
        }
        val request =
            BikeWizardRequest(
                bike = draft.toWizardBike(),
                components = parts.map { it.toRequest() },
                previewId = preview,
                identityConfirmed = identityConfirmed.takeIf { it },
            )
        return apiCall(dispatcher) {
                api.createBikeWithBuildWithHttpInfo(uuid, request).valueAndTag()
            }
            .let { (dto, tag) -> dto.toModel(media, tag) }
            .also(created)
    }
}

internal fun ResolveRequest.toRequest(): BikeResolutionRequest {
    val asked = query
    return BikeResolutionRequest(
        brand = asked.brand.trim(),
        model = asked.model.trim(),
        trim = asked.trim?.trim()?.takeIf { it.isNotEmpty() },
        year = asked.year,
        sourceUrl = (this as? ResolveRequest.Page)?.let { java.net.URI(it.url.trim()) },
        candidateId = (this as? ResolveRequest.Variant)?.candidateId,
        // The list of variants is asked for with no variant and no page named.
        chooseCandidates = (this as? ResolveRequest.Search)?.let { true },
    )
}

private fun BikeDraft.toWizardBike(): BikeWizardRequestBike =
    BikeWizardRequestBike(
        brand = brand.trim(),
        model = model.trim(),
        // A new bike has its year (BikeRules.check); the form never gets here without it.
        year = requireNotNull(year) { "A new bike needs a year" },
        classification = classification.toRequest(),
        isPublic = isPublic,
        name = name.trim().takeIf { it.isNotEmpty() },
        trim = trim.trim(),
        description = description.trim(),
        color = color.trim(),
        propertySize = size.trim(),
        weight = weightKg,
        mileage = mileageKm,
        manufacturerUrl = manufacturerUrl.trim(),
        price = priceRub,
        priceVisibility =
            BikeRequestPriceVisibility(
                bike = priceVisibility.bike,
                components = priceVisibility.components,
                accessories = priceVisibility.accessories,
            ),
        isFormer = isFormer,
    )

internal fun ResolutionDto.toModel(): BuildResolution =
    BuildResolution(
        status =
            when (status) {
                ResolutionDto.Status.resolved -> ResolutionStatus.Resolved
                ResolutionDto.Status.ambiguous -> ResolutionStatus.Ambiguous
                ResolutionDto.Status.not_found -> ResolutionStatus.NotFound
                ResolutionDto.Status.unsupported_brand -> ResolutionStatus.UnsupportedBrand
                ResolutionDto.Status.parse_error -> ResolutionStatus.ParseError
                // The service not answering, and any state of a later server version.
                else -> ResolutionStatus.Unavailable
            },
        query = BuildQuery(query.brand, query.model, query.trim, query.year),
        cached = cached,
        retryable = retryable,
        reason = reason?.takeIf { it.isNotBlank() },
        previewId = previewId?.toString(),
        previewExpiresAt = previewExpiresAt?.toInstant(),
        candidates = candidates.map { it.toModel() },
        build = build?.toModel(),
        sourcesChecked = sourcesChecked?.let { SourcesChecked(it.asked, it.answered, it.complete) },
    )

private fun sourceKindOf(value: String?): SourceKind? =
    when (value) {
        "manufacturer" -> SourceKind.Manufacturer
        "distributor" -> SourceKind.Distributor
        "archive" -> SourceKind.Archive
        "store" -> SourceKind.Store
        "web" -> SourceKind.Web
        "manual" -> SourceKind.Manual
        else -> null
    }

private fun QualityDto.toModel() =
    BuildQuality(
        complete = level == QualityDto.Level.complete,
        recognizedComponents = recognizedComponents,
        coverage = coverage,
    )

private fun CandidateDto.toModel() =
    BuildCandidate(
        candidateId = candidateId?.takeIf { it.isNotBlank() },
        name = name.trim(),
        brand = brand.trim(),
        year = year,
        url = url,
        sourceHost = sourceHost,
        sourceKind = sourceKindOf(sourceKind?.value),
        sourceName = sourceName?.trim()?.takeIf { it.isNotEmpty() },
        drivetrain = drivetrain?.trim()?.takeIf { it.isNotEmpty() },
        quality = quality?.toModel(),
        warnings = warnings,
        selectable = selectable,
        otherHosts = otherHosts,
    )

private fun BuildDto.toModel(): ResolvedBuild {
    val text = { value: String? -> value?.trim()?.takeIf { it.isNotEmpty() } }
    return ResolvedBuild(
        name = name.trim(),
        brand = brand.trim(),
        model = model.trim(),
        trim = text(trim),
        year = year,
        sourceYear = sourceYear,
        sourceUrl = sourceUrl,
        sourceHost = sourceHost,
        sourceKind = sourceKindOf(sourceKind?.value),
        sourceName = text(sourceName),
        manualSelection = manualSelection,
        identityMismatch = identityMismatch,
        yearMismatch = yearMismatch,
        warnings = warnings,
        quality = quality?.toModel(),
        parts =
            components.map {
                BuildPart(
                    section = it.section.value,
                    category = it.category.trim(),
                    name = it.name.trim(),
                    notes = it.notes.trim(),
                    groupId = it.groupId.trim(),
                )
            },
        unrecognized = unrecognized.map { UnrecognizedField(it.label.trim(), it.value.trim()) },
        suggested =
            SuggestedDetails(
                weightKg = suggested.weightKg,
                color = text(suggested.color),
                sizes = text(suggested.sizes),
                wheelSize = text(suggested.wheelSize),
                manufacturerUrl = text(suggested.manufacturerUrl),
            ),
    )
}
