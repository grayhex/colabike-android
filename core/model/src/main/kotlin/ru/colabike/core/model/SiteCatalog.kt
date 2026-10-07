package ru.colabike.core.model

import java.time.Instant

/** A value of a dictionary: the key the API takes, and its name for people. */
data class CatalogOption(val key: String, val name: String)

/** A kind of bike and the subtypes that belong to it (a subtype of another kind is refused). */
data class CatalogCategory(
    val key: String,
    val name: String,
    val subtypes: List<CatalogOption>,
)

/** The facets of a bike's type, by the keys of the API (cola `lib/bike-classification.ts`). */
data class ClassificationCatalog(
    val categories: List<CatalogCategory>,
    val suspensions: List<CatalogOption>,
    val constructions: List<CatalogOption>,
    val uses: List<CatalogOption>,
    val maxUses: Int,
) {
    fun category(key: String?): CatalogCategory? = categories.firstOrNull { it.key == key }

    fun subtypesOf(category: String?): List<CatalogOption> = category(category)?.subtypes.orEmpty()
}

/** A brand of bikes and its models, as the site's lists have them. */
data class CatalogBrand(val name: String, val models: List<String>)

/** A purpose of a bike the site offers ([BikeDraft] does not send these: type has `uses`). */
data class CatalogPurpose(val id: String, val name: String)

/**
 * The site's dictionaries of parts: its groups and the categories in each, the names a category is
 * known by. They suggest and never lock: a category or a name of the person's own is as good, and a
 * part the site has no group for is grouped by its category.
 */
data class ComponentDictionary(
    val groups: List<Group>,
    val buildCategories: List<String>,
    val accessoryCategories: List<String>,
    val names: Map<String, List<String>>,
) {
    data class Group(val id: String, val name: String, val categories: List<String>)

    private val every: List<String> by lazy {
        (groups.flatMap { it.categories } + buildCategories + accessoryCategories).distinct()
    }

    private val accessories: Set<String> by lazy { accessoryCategories.toSet() }

    /**
     * The site's spelling of a category the person typed in any case ("звонок" is "Звонок"); the
     * text as typed, trimmed, for a category of the person's own.
     */
    fun canonical(category: String): String {
        val text = category.trim()
        return every.firstOrNull { it.equals(text, ignoreCase = true) } ?: text
    }

    /** The group a category belongs to, in any case; empty for a category of the person's own. */
    fun groupIdOf(category: String): String {
        val known = canonical(category)
        return groups.firstOrNull { known in it.categories }?.id.orEmpty()
    }

    /** `accessories` for the site's equipment categories, `build` for the rest. */
    fun sectionOf(category: String): String =
        if (canonical(category) in accessories) SECTION_ACCESSORIES else SECTION_BUILD

    /** The name of a group by its key, or null for a key (or a category) that is not the site's. */
    fun groupName(key: String): String? = groups.firstOrNull { it.id == key }?.name

    /** Known categories that contain what was typed, the ones that start with it first. */
    fun suggestions(typed: String, limit: Int = MAX_SUGGESTIONS): List<String> =
        matching(every, typed, limit)

    /** The names the site knows in [category] that contain what was typed. */
    fun nameSuggestions(
        category: String,
        typed: String,
        limit: Int = MAX_SUGGESTIONS,
    ): List<String> = matching(namesOf(category), typed, limit)

    /** Every name of [category], in any case, for a list that opens with nothing typed. */
    fun namesOf(category: String): List<String> {
        val known = canonical(category)
        return names.entries
            .firstOrNull { it.key.equals(known, ignoreCase = true) }
            ?.value
            .orEmpty()
    }

    private fun matching(from: List<String>, typed: String, limit: Int): List<String> {
        val text = typed.trim()
        if (text.isEmpty()) return emptyList()
        val (starts, contains) =
            from
                .filter { it.contains(text, ignoreCase = true) && !it.equals(text, true) }
                .partition { it.startsWith(text, ignoreCase = true) }
        return (starts + contains).take(limit)
    }

    companion object {
        const val SECTION_BUILD = "build"
        const val SECTION_ACCESSORIES = "accessories"
        const val MAX_SUGGESTIONS = 6
    }
}

/**
 * The dictionaries of the site, as the API gives them (`GET /catalog`). They fill the pickers of
 * the bike form and of the wizard; the values only suggest, because the site takes a brand, a
 * model, a size or a part of the person's own, and what a bike already has stays as it is.
 *
 * [version] grows with every save of the dictionaries on the site; the copy on the device is
 * replaced when the server says it changed. [Builtin] (see the app) stands in until a copy has been
 * read.
 */
data class SiteCatalog(
    val version: Int,
    val classification: ClassificationCatalog,
    val purposes: List<CatalogPurpose>,
    val brands: List<CatalogBrand>,
    val manufacturers: List<String>,
    val sizes: List<String>,
    val components: ComponentDictionary,
) {
    /** The brands that contain what was typed, the ones that start with it first. */
    fun brandSuggestions(typed: String, limit: Int = BRAND_LIMIT): List<String> {
        val text = typed.trim()
        if (text.isEmpty()) return brands.take(limit).map { it.name }
        val (starts, contains) =
            brands
                .map { it.name }
                .filter { it.contains(text, ignoreCase = true) }
                .partition { it.startsWith(text, ignoreCase = true) }
        return (starts + contains).take(limit)
    }

    /** The models of [brand] (in any case) that contain what was typed. */
    fun modelSuggestions(brand: String, typed: String, limit: Int = BRAND_LIMIT): List<String> {
        val models =
            brands.firstOrNull { it.name.equals(brand.trim(), ignoreCase = true) }?.models.orEmpty()
        val text = typed.trim()
        if (text.isEmpty()) return models.take(limit)
        val (starts, contains) =
            models
                .filter { it.contains(text, ignoreCase = true) }
                .partition { it.startsWith(text, ignoreCase = true) }
        return (starts + contains).take(limit)
    }

    /**
     * What a search box of "brand model, year and version" offers while it is typed: whole names of
     * a brand with a model of its own, the ones that begin with the text first.
     */
    fun searchSuggestions(typed: String, limit: Int = BRAND_LIMIT): List<String> {
        val text = typed.trim().replace(Regex("\\s+"), " ")
        if (text.length < 2) return emptyList()
        val whole = brands.flatMap { brand -> brand.models.map { "${brand.name} $it" } }
        val (starts, contains) =
            whole
                .filter { it.contains(text, ignoreCase = true) && !it.equals(text, true) }
                .partition { it.startsWith(text, ignoreCase = true) }
        return (starts + contains).take(limit)
    }

    private companion object {
        const val BRAND_LIMIT = 8
    }
}

/** The dictionaries as they are on the device and when the server last confirmed them. */
data class StoredCatalog(val catalog: SiteCatalog, val validatedAt: Instant)

/** What a request for the dictionaries came to. */
sealed interface CatalogRefresh {
    /** The server says nothing changed; the kept copy is confirmed as of [validatedAt]. */
    data class NotModified(val validatedAt: Instant) : CatalogRefresh

    /** A new copy replaced the old one as a whole. */
    data class Updated(val stored: StoredCatalog) : CatalogRefresh

    /** Nothing changed on the device. The old copy, if any, stays in use. */
    data class Failed(val error: DataError) : CatalogRefresh
}

/**
 * The dictionaries of the site, kept on the device. Implementations throw nothing: a failure is a
 * [CatalogRefresh.Failed], and the app goes on with what it has.
 */
interface SiteCatalogRepository {
    /** The last valid copy kept on the device, or null if there is none or it cannot be read. */
    suspend fun cached(): StoredCatalog?

    /** Asks the server with the validator of the kept copy; a new one replaces it as a whole. */
    suspend fun refresh(): CatalogRefresh
}
