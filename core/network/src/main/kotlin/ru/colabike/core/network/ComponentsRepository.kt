package ru.colabike.core.network

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.ComponentsApi
import ru.colabike.api.models.ComponentFilters as ComponentFiltersDto
import ru.colabike.api.models.ComponentModel as ComponentModelDto
import ru.colabike.api.models.ComponentPhoto as ComponentPhotoDto
import ru.colabike.api.models.ComponentPhotoSource as ComponentPhotoSourceDto
import ru.colabike.core.model.ComponentFilters
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentModel
import ru.colabike.core.model.ComponentPhoto
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.ComponentSort
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.PhotoSource

/**
 * The public component catalog: nothing here needs a session. Ids that cannot be a UUID are a
 * missing model without a request (a link can carry anything).
 */
class NetworkComponentsRepository(
    private val api: ComponentsApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ComponentsRepository {
    override suspend fun page(
        query: ComponentQuery,
        cursor: String?,
        limit: Int,
    ): Page<ComponentModel> {
        val page =
            apiCall(dispatcher) {
                api.listComponentModels(
                    q = query.text.trim().take(MAX_TEXT).takeIf { it.isNotEmpty() },
                    category = query.category?.take(MAX_CATEGORY)?.takeIf { it.isNotBlank() },
                    brand = query.brand?.take(MAX_BRAND)?.takeIf { it.isNotBlank() },
                    sort =
                        when (query.sort) {
                            ComponentSort.New -> ComponentsApi.SortListComponentModels.new
                            ComponentSort.Popular -> ComponentsApi.SortListComponentModels.popular
                        },
                    limit = limit,
                    cursor = cursor,
                )
            }
        return Page(page.items.map { it.toModel(media) }, page.nextCursor)
    }

    override suspend fun filters(): ComponentFilters =
        apiCall(dispatcher) { api.listComponentFilters() }.toModel()

    override suspend fun model(id: ComponentId): ComponentModel {
        val uuid = uuidOrNotFound(id.value)
        return apiCall(dispatcher) { api.getComponentModel(uuid) }.toModel(media)
    }

    override suspend fun photos(id: ComponentId): List<ComponentPhoto> {
        val uuid = uuidOrNotFound(id.value)
        val list = apiCall(dispatcher) { api.listComponentPhotos(uuid) }
        // A photo whose address is not one the app can load is left out, not shown as a blank.
        return list.items.mapNotNull { it.toModel(media) }
    }

    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()

    private companion object {
        const val MAX_TEXT = 150
        const val MAX_CATEGORY = 60
        const val MAX_BRAND = 100
    }
}

internal fun ComponentModelDto.toModel(media: MediaUrls) =
    ComponentModel(
        id = ComponentId(id.toString()),
        category = category,
        brand = brand,
        name = name,
        description = description,
        path = path,
        builds = builds.coerceAtLeast(0),
        firstPublicAt = Instant.from(firstPublicAt),
        coverUrl = media.resolve(coverUrl),
        archived = archived,
    )

internal fun ComponentFiltersDto.toModel() =
    ComponentFilters(
        categories = categories.filter { it.isNotBlank() }.distinct(),
        brands = brands.filter { it.isNotBlank() }.distinct(),
    )

internal fun ComponentPhotoDto.toModel(media: MediaUrls): ComponentPhoto? {
    val resolved = media.resolve(url) ?: return null
    return ComponentPhoto(
        id = id.toString(),
        url = resolved,
        width = width,
        height = height,
        caption = caption,
        source = source?.toModel(),
        author = author.toModel(media),
        isCover = isCover,
    )
}

/** Both addresses of a source are shown only if they are plain `https` ones. */
internal fun ComponentPhotoSourceDto.toModel() =
    PhotoSource(
        provider = provider,
        url = httpsOrNull(url),
        title = title,
        creator = creator,
        credit = credit,
        license = license,
        licenseUrl = httpsOrNull(licenseUrl),
    )
