package ru.colabike.core.model

import java.time.Instant

/** A model in the component catalog. The id of a merged model is the canonical one. */
@JvmInline value class ComponentId(val value: String)

/**
 * A model from the public catalog: what the model's page on the site shows. No installation, owner
 * or private date is here, because the API does not send them.
 */
data class ComponentModel(
    val id: ComponentId,
    val category: String,
    val brand: String,
    val name: String,
    val description: String,
    /** The model's page on the site, relative to the site's address. */
    val path: String,
    /** Public bikes (without blocked owners) that have this model in their build. */
    val builds: Int,
    val firstPublicAt: Instant,
    val coverUrl: String?,
    /** An archived model is read by its id and is not listed in the catalog. */
    val archived: Boolean,
)

/** The values for the catalog's `category` and `brand` filters, as the server lists them. */
data class ComponentFilters(val categories: List<String>, val brands: List<String>)

enum class ComponentSort {
    /** By when the model appeared in the catalog, newest first. */
    New,

    /** By the number of public bikes with it; the order can move between two pages. */
    Popular,
}

data class ComponentQuery(
    val text: String = "",
    val category: String? = null,
    val brand: String? = null,
    val sort: ComponentSort = ComponentSort.New,
) {
    val isDefault: Boolean
        get() = text.isEmpty() && category == null && brand == null && sort == ComponentSort.New
}

/**
 * Where a photo from an outside source comes from and under what terms. It is shown with the photo.
 */
data class PhotoSource(
    val provider: String,
    /** The page of the photo at the provider, only if it is an `https` address. */
    val url: String?,
    val title: String,
    val creator: String,
    val credit: String,
    val license: String,
    /** The text of the licence, only if it is an `https` address. */
    val licenseUrl: String?,
)

/** A public photo of a model. Uploaded ones have an [author]; outside ones a [source]. */
data class ComponentPhoto(
    val id: String,
    val url: String,
    val width: Int,
    val height: Int,
    val caption: String,
    val source: PhotoSource?,
    val author: Person?,
    val isCover: Boolean,
)

/** The component catalog. Implementations throw [DataError]. */
interface ComponentsRepository {
    /** Published models that are not archived and not merged into others. */
    suspend fun page(
        query: ComponentQuery,
        cursor: String? = null,
        limit: Int = 24,
    ): Page<ComponentModel>

    suspend fun filters(): ComponentFilters

    /**
     * One model. A merged model answers with the canonical one (its [ComponentModel.id] differs
     * from [id]); an archived one is readable; an unpublished or unknown one is
     * [DataError.NotFound].
     */
    suspend fun model(id: ComponentId): ComponentModel

    /** The gallery, cover first, at most 60. */
    suspend fun photos(id: ComponentId): List<ComponentPhoto>
}
