package ru.colabike.core.network

import java.io.IOException
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.colabike.api.apis.AppApi
import ru.colabike.api.infrastructure.ClientError
import ru.colabike.api.infrastructure.ClientException
import ru.colabike.api.infrastructure.Serializer
import ru.colabike.api.infrastructure.ServerError
import ru.colabike.api.infrastructure.ServerException
import ru.colabike.api.infrastructure.Success
import ru.colabike.api.models.AppCompatibility as CompatibilityDto
import ru.colabike.api.models.AppConfig as AppConfigDto
import ru.colabike.api.models.AppLaunch as LaunchDto
import ru.colabike.api.models.AppNotice as NoticeDto
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.AppConfigRefresh
import ru.colabike.core.model.AppConfigRepository
import ru.colabike.core.model.AppNotice
import ru.colabike.core.model.Compatibility
import ru.colabike.core.model.ConfigAssets
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeatureAvailability
import ru.colabike.core.model.LaunchConfig
import ru.colabike.core.model.LaunchFill
import ru.colabike.core.model.NoticeAction
import ru.colabike.core.model.NoticeKind
import ru.colabike.core.model.OnboardingConfig
import ru.colabike.core.model.OnboardingItem
import ru.colabike.core.model.ServiceLinks
import ru.colabike.core.model.StoredAppConfig
import ru.colabike.core.model.UpdateMode

/**
 * The server-managed app config (`GET /app-config`), kept on the device. It is never in the way: a
 * failure of any kind leaves the old config in use. A new config is taken as a whole or not at all
 * — read, checked, its pictures downloaded, and only then written over the old one — so the texts
 * of one revision are never shown with the pictures of another.
 */
class NetworkAppConfigRepository(
    private val api: AppApi,
    private val media: MediaUrls,
    private val cache: DocumentCache,
    private val assets: ConfigAssets,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppConfigRepository {
    override suspend fun cached(): StoredAppConfig? =
        withContext(dispatcher) {
            val entry = cache.read() ?: return@withContext null
            val config =
                try {
                    decode(entry.body).toModel(media)
                } catch (e: Exception) {
                    // A body this build cannot read (a format change, a damaged file) is no cache.
                    cache.clear()
                    return@withContext null
                }
            StoredAppConfig(config, Instant.ofEpochMilli(entry.validatedAtMillis))
        }

    override suspend fun refresh(): AppConfigRefresh =
        try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataError) {
            AppConfigRefresh.Failed(e)
        } catch (e: Exception) {
            AppConfigRefresh.Failed(DataError.Unexpected(e))
        }

    private suspend fun fetch(): AppConfigRefresh {
        val kept = withContext(dispatcher) { cache.read() }
        val answer = apiCall(dispatcher) { ask(kept?.etag) }
        val now = Instant.now(clock)
        val fresh =
            when (answer) {
                Answer.NotModified -> {
                    // The kept config is confirmed as of now.
                    if (kept == null) return AppConfigRefresh.Failed(DataError.Unexpected(null))
                    withContext(dispatcher) {
                        cache.write(DocumentCache.Entry(kept.body, kept.etag, now.toEpochMilli()))
                    }
                    return AppConfigRefresh.NotModified(now)
                }
                is Answer.Fresh -> answer
            }
        val config = fresh.dto.toModel(media)
        // The pictures first: a revision whose pictures are not on the device does not replace one
        // that is whole. The next start asks again (the kept validator is still the old one's).
        if (!assets.prefetch(config.assetUrls())) {
            return AppConfigRefresh.Failed(DataError.Offline(IOException("pictures of the config")))
        }
        withContext(dispatcher) {
            cache.write(DocumentCache.Entry(encode(fresh.dto), fresh.etag, now.toEpochMilli()))
        }
        // The new config is the kept one now; the pictures of the old one can go.
        assets.retainOnly(config.assetUrls())
        return AppConfigRefresh.Updated(StoredAppConfig(config, now))
    }

    private sealed interface Answer {
        data object NotModified : Answer

        class Fresh(val dto: AppConfigDto, val etag: String?) : Answer
    }

    /** One blocking request. The generated client files a 304 under "server error". */
    private fun ask(validator: String?): Answer =
        when (val response = api.getAppConfigWithHttpInfo(validator)) {
            is Success -> {
                val dto = response.data ?: throw IllegalArgumentException("empty config")
                val etag =
                    response.headers.entries
                        .firstOrNull { it.key.equals("ETag", ignoreCase = true) }
                        ?.value
                        ?.firstOrNull()
                Answer.Fresh(dto, etag)
            }
            is ClientError ->
                throw ClientException(
                    "Client error : ${response.statusCode} ${response.message.orEmpty()}",
                    response.statusCode,
                    response,
                )
            is ServerError ->
                if (response.statusCode == NOT_MODIFIED) {
                    Answer.NotModified
                } else {
                    throw ServerException(
                        "Server error : ${response.statusCode} ${response.message.orEmpty()}",
                        response.statusCode,
                        response,
                    )
                }
            else -> throw UnsupportedOperationException("unexpected answer")
        }

    private fun decode(body: String): AppConfigDto =
        Serializer.kotlinxSerializationJson.decodeFromString(AppConfigDto.serializer(), body)

    private fun encode(dto: AppConfigDto): String =
        Serializer.kotlinxSerializationJson.encodeToString(AppConfigDto.serializer(), dto)

    private companion object {
        const val NOT_MODIFIED = 304
    }
}

/** Every picture the config shows, to be kept before the config is. */
internal fun AppConfig.assetUrls(): List<String> = buildList {
    if (launch.enabled) launch.imageUrl?.let(::add)
    if (onboarding.enabled) onboarding.items.mapNotNullTo(this) { it.imageUrl }
    notice?.imageUrl?.let(::add)
}
    .distinct()

// --- mapping -------------------------------------------------------------------------------

/** Widths the server serves (`?width=`); a screen-sized picture asks for the larger. */
private const val LAUNCH_WIDTH = 1920
private const val CARD_WIDTH = 1280
private const val MAX_ONBOARDING_ITEMS = 6

/**
 * The config as the app uses it. What the app cannot use is left out, not guessed at: a picture
 * that is not on the site, a link that is not `https`, a flag it has no function for. The server
 * says the same of its own output and this does not rely on it.
 */
internal fun AppConfigDto.toModel(media: MediaUrls): AppConfig {
    val onboardingItems =
        onboarding.items.take(MAX_ONBOARDING_ITEMS).mapNotNull { item ->
            val title = item.title.trim()
            if (title.isEmpty()) null
            else
                OnboardingItem(
                    title = title,
                    body = item.body.cleaned(),
                    imageUrl = media.resolveOnSite(item.imageUrl, CARD_WIDTH),
                )
        }
    return AppConfig(
        revision = revision.coerceAtLeast(0),
        launch = launch.toModel(media),
        onboarding =
            OnboardingConfig(
                enabled = onboarding.enabled && onboardingItems.isNotEmpty(),
                revision = onboarding.revision.coerceAtLeast(0),
                items = onboardingItems,
            ),
        notice = notice?.toModel(media),
        links =
            ServiceLinks(
                help = httpsOrNull(links.help),
                privacy = httpsOrNull(links.privacy),
                terms = httpsOrNull(links.terms),
                about = httpsOrNull(links.about),
                support = httpsOrNull(links.support),
            ),
        features =
            FeatureAvailability(
                Feature.entries
                    .mapNotNull { feature ->
                        features[feature.key]?.let { feature.key to it }
                    }
                    .toMap()
            ),
        compatibility = compatibility.toModel(),
    )
}

private fun LaunchDto.toModel(media: MediaUrls): LaunchConfig {
    val image = media.resolveOnSite(imageUrl, LAUNCH_WIDTH)
    return LaunchConfig(
        // A launch screen without its picture is not one.
        enabled = enabled && image != null,
        imageUrl = image,
        fill =
            when (contentMode) {
                LaunchDto.ContentMode.fit -> LaunchFill.Fit
                // Crop, and any value of a later server version.
                else -> LaunchFill.Crop
            },
        title = title.cleaned(),
    )
}

private fun NoticeDto.toModel(media: MediaUrls): AppNotice? {
    val heading = title.trim()
    if (heading.isEmpty()) return null
    val button = action?.let { a ->
        val url = httpsOrNull(a.url)
        val label = a.label.trim()
        if (url != null && label.isNotEmpty()) NoticeAction(label, url) else null
    }
    return AppNotice(
        revision = revision.coerceAtLeast(0),
        kind =
            when (kind) {
                NoticeDto.Kind.service -> NoticeKind.Service
                NoticeDto.Kind.maintenance -> NoticeKind.Maintenance
                // Promo, and any kind of a later server version.
                else -> NoticeKind.Promo
            },
        title = heading,
        body = body.cleaned(),
        imageUrl = media.resolveOnSite(imageUrl, CARD_WIDTH),
        action = button,
    )
}

private fun CompatibilityDto.toModel(): Compatibility {
    val minimum = minimumSupportedVersionCode?.takeIf { it > 0 }
    val latest = latestVersionCode?.takeIf { it > 0 }
    val url = httpsOrNull(updateUrl)
    // The server softens a block without a way out; the app does not rely on that.
    val hard = updateMode == CompatibilityDto.UpdateMode.hard && minimum != null && url != null
    return Compatibility(
        minimumSupportedVersionCode = minimum,
        latestVersionCode = latest,
        mode = if (hard) UpdateMode.Hard else UpdateMode.Soft,
        updateUrl = url,
        message = updateMessage.cleaned(),
    )
}

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
