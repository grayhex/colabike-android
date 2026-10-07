package ru.colabike.core.network

import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.colabike.api.apis.BikesApi
import ru.colabike.api.infrastructure.ClientError
import ru.colabike.api.infrastructure.ClientException
import ru.colabike.api.infrastructure.Serializer
import ru.colabike.api.infrastructure.ServerError
import ru.colabike.api.infrastructure.ServerException
import ru.colabike.api.infrastructure.Success
import ru.colabike.api.models.BikeClassificationRequest
import ru.colabike.api.models.SiteCatalog as CatalogDto
import ru.colabike.core.model.CatalogBrand
import ru.colabike.core.model.CatalogCategory
import ru.colabike.core.model.CatalogOption
import ru.colabike.core.model.CatalogPurpose
import ru.colabike.core.model.CatalogRefresh
import ru.colabike.core.model.ClassificationCatalog
import ru.colabike.core.model.ComponentDictionary
import ru.colabike.core.model.DataError
import ru.colabike.core.model.SiteCatalog
import ru.colabike.core.model.SiteCatalogRepository
import ru.colabike.core.model.StoredCatalog

/**
 * The dictionaries of the site (`GET /catalog`), kept on the device. Like the app config it is
 * never in the way: a failure of any kind leaves the old copy in use, and a new copy is taken as a
 * whole or not at all, with the `ETag` of the one that is kept so that nothing is read twice.
 */
class NetworkSiteCatalogRepository(
    private val api: BikesApi,
    private val cache: DocumentCache,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SiteCatalogRepository {
    override suspend fun cached(): StoredCatalog? =
        withContext(dispatcher) {
            val entry = cache.read() ?: return@withContext null
            try {
                StoredCatalog(
                    decode(entry.body).toModel(),
                    Instant.ofEpochMilli(entry.validatedAtMillis),
                )
            } catch (e: Exception) {
                // A body this build cannot read (a format change, a damaged file) is no cache.
                cache.clear()
                null
            }
        }

    override suspend fun refresh(): CatalogRefresh =
        try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: DataError) {
            CatalogRefresh.Failed(e)
        } catch (e: Exception) {
            CatalogRefresh.Failed(DataError.Unexpected(e))
        }

    private suspend fun fetch(): CatalogRefresh {
        val kept = withContext(dispatcher) { cache.read() }
        val answer = apiCall(dispatcher) { ask(kept?.etag) }
        val now = Instant.now(clock)
        return when (answer) {
            Answer.NotModified -> {
                // The kept copy is confirmed as of now.
                if (kept == null) return CatalogRefresh.Failed(DataError.Unexpected(null))
                withContext(dispatcher) {
                    cache.write(DocumentCache.Entry(kept.body, kept.etag, now.toEpochMilli()))
                }
                CatalogRefresh.NotModified(now)
            }
            is Answer.Fresh -> {
                val catalog = answer.dto.toModel()
                withContext(dispatcher) {
                    cache.write(
                        DocumentCache.Entry(encode(answer.dto), answer.etag, now.toEpochMilli())
                    )
                }
                CatalogRefresh.Updated(StoredCatalog(catalog, now))
            }
        }
    }

    private sealed interface Answer {
        data object NotModified : Answer

        class Fresh(val dto: CatalogDto, val etag: String?) : Answer
    }

    /** One blocking request. The generated client files a 304 under "server error". */
    private fun ask(validator: String?): Answer =
        when (val response = api.getSiteCatalogWithHttpInfo(validator)) {
            is Success -> {
                val dto = response.data ?: throw IllegalArgumentException("empty catalog")
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

    private fun decode(body: String): CatalogDto =
        Serializer.kotlinxSerializationJson.decodeFromString(CatalogDto.serializer(), body)

    private fun encode(dto: CatalogDto): String =
        Serializer.kotlinxSerializationJson.encodeToString(CatalogDto.serializer(), dto)

    private companion object {
        const val NOT_MODIFIED = 304
    }
}

/**
 * The keys of the type of a bike that this build can send: the contract lists them as values, and a
 * key of a later server version is not offered (a request with it could not be made). The words and
 * the order are the site's.
 */
private object KnownKeys {
    private fun <E : Enum<E>> keys(entries: List<E>, value: (E) -> String) =
        entries.filter { it.name != "unknown_default_open_api" }.map(value).toSet()

    val categories = keys(BikeClassificationRequest.Category.entries) { it.value }
    val subtypes = keys(BikeClassificationRequest.Subtype.entries) { it.value }
    val suspensions = keys(BikeClassificationRequest.Suspension.entries) { it.value }
    val constructions = keys(BikeClassificationRequest.Construction.entries) { it.value }
    val uses = keys(BikeClassificationRequest.Uses.entries) { it.value }
}

/**
 * The dictionaries as the app uses them. Values with no key or no name are left out, repeats are
 * taken once, and the order is the site's: what the app cannot use is not guessed at.
 */
internal fun CatalogDto.toModel(): SiteCatalog {
    fun options(list: List<Pair<String, String>>, known: Set<String>) =
        list
            .filter { (key, name) -> key in known && name.isNotBlank() }
            .distinctBy { it.first }
            .map { (key, name) -> CatalogOption(key, name.trim()) }
    fun strings(list: List<String>) = list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return SiteCatalog(
        version = version.coerceAtLeast(0),
        classification =
            ClassificationCatalog(
                categories =
                    classification.categories
                        .filter { it.key in KnownKeys.categories && it.name.isNotBlank() }
                        .distinctBy { it.key }
                        .map { category ->
                            CatalogCategory(
                                key = category.key,
                                name = category.name.trim(),
                                subtypes =
                                    options(
                                        category.subtypes.map { it.key to it.name },
                                        KnownKeys.subtypes,
                                    ),
                            )
                        },
                suspensions =
                    options(
                        classification.suspensions.map { it.key to it.name },
                        KnownKeys.suspensions,
                    ),
                constructions =
                    options(
                        classification.constructions.map { it.key to it.name },
                        KnownKeys.constructions,
                    ),
                uses = options(classification.uses.map { it.key to it.name }, KnownKeys.uses),
                maxUses = classification.maxUses.coerceAtLeast(1),
            ),
        purposes =
            purposes
                .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                .map { CatalogPurpose(it.id, it.name.trim()) },
        brands =
            brands
                .filter { it.name.isNotBlank() }
                .map { CatalogBrand(it.name.trim(), strings(it.models)) },
        manufacturers = strings(manufacturers),
        sizes = strings(sizes),
        components =
            ComponentDictionary(
                groups =
                    components.groups
                        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
                        .map {
                            ComponentDictionary.Group(it.id, it.name.trim(), strings(it.categories))
                        },
                buildCategories = strings(components.buildCategories),
                accessoryCategories = strings(components.accessoryCategories),
                names =
                    components.names
                        .filter { it.category.isNotBlank() }
                        .associate { it.category.trim() to strings(it.names) },
            ),
    )
}
