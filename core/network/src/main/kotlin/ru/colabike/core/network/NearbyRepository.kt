package ru.colabike.core.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.PlanningApi
import ru.colabike.api.models.Nearby as NearbyDto
import ru.colabike.api.models.NearbyAreaRequest
import ru.colabike.api.models.NearbyOffer as OfferDto
import ru.colabike.api.models.NearbyOffers as OffersDto
import ru.colabike.api.models.NearbySettingsPatch
import ru.colabike.api.models.NearbySettingsPatchFilters
import ru.colabike.core.model.NearbyArea
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbyGrid
import ru.colabike.core.model.NearbyLimits
import ru.colabike.core.model.NearbyOffer
import ru.colabike.core.model.NearbyOffers
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyPreferences
import ru.colabike.core.model.NearbyReason
import ru.colabike.core.model.NearbyRepository
import ru.colabike.core.model.NearbySettings
import ru.colabike.core.model.NearbySource

/**
 * The private area of "rides near me" (cola#343). The version of what was read travels with the
 * state (`ETag`) and comes back as `If-Match`, so a second phone's change is a conflict to read
 * again, never an overwrite nobody saw. A phone's place is never in a message or a log: only the
 * centre of a grid cell is sent, and the generated client prints no body.
 */
class NetworkNearbyRepository(
    private val api: PlanningApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NearbyRepository {
    override suspend fun settings(): NearbySettings =
        apiCall(dispatcher) { answer(api.getNearbyWithHttpInfo()) }

    override suspend fun change(change: NearbyChange, version: String?): NearbySettings {
        if (change.isEmpty) return settings()
        val patch =
            NearbySettingsPatch(
                enabled = change.enabled,
                horizonDays = change.horizonDays,
                filters =
                    if (
                        change.purposes != null || change.paces != null || change.surfaces != null
                    ) {
                        NearbySettingsPatchFilters(
                            purposes = change.purposes?.sorted(),
                            paces = change.paces?.sorted(),
                            surfaces = change.surfaces?.sorted(),
                        )
                    } else {
                        null
                    },
            )
        return apiCall(dispatcher) { answer(api.updateNearbySettingsWithHttpInfo(patch, version)) }
    }

    override suspend fun confirmDeviceArea(
        longitude: Double,
        latitude: Double,
        radiusM: Int,
        replaceManual: Boolean,
        version: String?,
    ): NearbySettings {
        val body =
            NearbyAreaRequest(
                source = NearbyAreaRequest.Source.device,
                center = listOf(longitude, latitude),
                radiusM = radiusM,
                replaceSource = if (replaceManual) true else null,
            )
        return apiCall(dispatcher) {
            answer(api.saveNearbyAreaWithHttpInfo(version.orEmpty(), body))
        }
    }

    override suspend fun removeArea(version: String?): NearbySettings =
        apiCall(dispatcher) { answer(api.removeNearbyAreaWithHttpInfo(version)) }

    override suspend fun forget() {
        apiCall(dispatcher) { api.forgetNearby() }
    }

    override suspend fun offers(limit: Int): NearbyOffers =
        apiCall(dispatcher) { api.listNearbyOffers(limit) }.toModel(media)

    private fun answer(
        response: ru.colabike.api.infrastructure.ApiResponse<NearbyDto?>
    ): NearbySettings {
        val (dto, tag) = response.valueAndTag()
        return dto.toModel(tag)
    }
}

internal fun NearbyDto.toModel(version: String?): NearbySettings =
    NearbySettings(
        available = available,
        enabled = enabled,
        source =
            when (source) {
                NearbyDto.Source.manual -> NearbySource.Manual
                NearbyDto.Source.device -> NearbySource.Device
                else -> null
            },
        area =
            area?.let {
                NearbyArea(
                    label = it.label,
                    // [longitude, latitude], the order of GeoJSON.
                    longitude = it.center.getOrElse(0) { 0.0 },
                    latitude = it.center.getOrElse(1) { 0.0 },
                    radiusM = it.radiusM,
                )
            },
        observedAt = observedAt?.toInstant(),
        expiresAt = expiresAt?.toInstant(),
        expired = expired,
        horizonDays = horizonDays,
        preferences =
            NearbyPreferences(
                purposes = filters.purposes.toSet(),
                paces = filters.paces.toSet(),
                surfaces = filters.surfaces.toSet(),
            ),
        limits =
            NearbyLimits(
                minRadiusM = limits.minRadiusM,
                maxRadiusM = limits.maxRadiusM,
                radiusStepM = limits.radiusStepM,
                deviceTtlHours = limits.deviceTtlHours,
                grid = NearbyGrid(limits.cell.latStep, limits.cell.lngStep),
            ),
        version = version,
    )

internal fun OffersDto.toModel(media: MediaUrls): NearbyOffers =
    NearbyOffers(
        state =
            when (state) {
                OffersDto.State.ready -> NearbyOffersState.Ready
                OffersDto.State.off -> NearbyOffersState.Off
                OffersDto.State.no_area -> NearbyOffersState.NoArea
                OffersDto.State.expired -> NearbyOffersState.Expired
                // An answer the app does not know is treated as "not available now".
                else -> NearbyOffersState.Unavailable
            },
        items = items.map { it.toModel(media) },
    )

private fun OfferDto.toModel(media: MediaUrls) =
    NearbyOffer(
        ride = ride.toModel(media),
        reasons =
            reasons
                .mapNotNull {
                    when (it) {
                        OfferDto.Reasons.nearby -> NearbyReason.Nearby
                        OfferDto.Reasons.intent -> NearbyReason.Intent
                        else -> null
                    }
                }
                .toSet(),
    )
