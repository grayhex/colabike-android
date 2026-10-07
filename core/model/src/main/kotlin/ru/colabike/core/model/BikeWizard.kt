package ru.colabike.core.model

import java.time.Instant

/**
 * What the one search line of the wizard means: the brand, the model, the version and the year, as
 * the server's search takes them. The year and the version are the person's to give or not: a
 * missing one is `null`, and nothing is made up for it.
 */
data class BuildQuery(
    val brand: String,
    val model: String,
    val trim: String? = null,
    val year: Int? = null,
) {
    /** "Brand Model Version Year" as the person would write it, for the line and for names. */
    val line: String
        get() = listOfNotNull(brand, model, trim, year?.toString()).joinToString(" ")
}

/**
 * Takes the text of the search line apart the way the site's wizard does (cola
 * `lib/bike-search-input.ts`): a year of four digits is the year, the longest brand of the site's
 * list that the text begins with is the brand (else the first word), the longest model of that
 * brand that follows is the model (else all the rest), and what is left after the model is the
 * version. It only splits the input; it never says what a bike is: facts come from the server.
 */
object BuildQueryParser {
    private val YEAR = Regex("\\b(?:19|20|21)\\d{2}\\b")
    private const val MAX_TEXT = 240
    private const val MAX_BRAND = 60
    private const val MAX_MODEL = 100

    fun parse(text: String, brands: List<CatalogBrand> = emptyList()): BuildQuery? {
        var value = text.replace(Regex("\\s+"), " ").trim()
        if (value.isEmpty() || value.length > MAX_TEXT) return null
        val years = YEAR.findAll(value).toList()
        // Two years are not a year: the text says two things at once.
        if (years.size > 1) return null
        val year = years.firstOrNull()?.value?.toInt()
        if (year != null) value = value.replace(YEAR, "").replace(Regex("\\s+"), " ").trim()
        val known =
            brands
                .map { it.name }
                .sortedByDescending { it.length }
                .firstOrNull { value.startsWith("$it ", ignoreCase = true) }
        val brand = known ?: value.substringBefore(' ')
        val rest = value.drop(brand.length).trim()
        if (brand.isEmpty() || rest.isEmpty() || brand.length > MAX_BRAND) return null
        val models =
            brands.firstOrNull { it.name.equals(brand, ignoreCase = true) }?.models.orEmpty()
        // The site's own spelling of the model when the text names one of its models.
        val named =
            models
                .sortedByDescending { it.length }
                .firstOrNull {
                    rest.equals(it, ignoreCase = true) || rest.startsWith("$it ", ignoreCase = true)
                }
        val model = named ?: rest
        val trim = if (named == null) null else rest.drop(named.length).trim().ifEmpty { null }
        if (model.length > MAX_MODEL || (trim?.length ?: 0) > MAX_MODEL) return null
        return BuildQuery(brand = brand, model = model, trim = trim, year = year)
    }
}

/** How a search is asked: for a list to choose from, a page the person named, or one variant. */
sealed interface ResolveRequest {
    val query: BuildQuery

    /**
     * The service searches the manufacturer and the stores, and offers variants when it finds
     * several.
     */
    data class Search(override val query: BuildQuery) : ResolveRequest

    /** The variant the server offered earlier, by its own identifier: never a new search. */
    data class Variant(override val query: BuildQuery, val candidateId: String) : ResolveRequest

    /** The page of a store or a manufacturer that the person pasted. */
    data class Page(override val query: BuildQuery, val url: String) : ResolveRequest
}

/** What a search came to. */
enum class ResolutionStatus {
    /** One build was found: [BuildResolution.build] has it. */
    Resolved,

    /** Several variants were found: the person chooses, [BuildResolution.candidates] has them. */
    Ambiguous,
    NotFound,
    UnsupportedBrand,

    /** The service or the source did not answer; [BuildResolution.retryable] says if it may. */
    Unavailable,

    /** A page was read but is not a specification. */
    ParseError,
}

/** Where a page of a specification comes from. */
enum class SourceKind {
    Manufacturer,
    Distributor,
    Archive,
    Store,
    Web,
    Manual,
}

/** How much of a page was read. */
data class BuildQuality(val complete: Boolean, val recognizedComponents: Int, val coverage: Double)

/** One variant the search offers: a page of one source. A choice is of this one and no other. */
data class BuildCandidate(
    /** The identifier to choose it by; null: the page [url] itself is chosen. */
    val candidateId: String?,
    val name: String,
    val brand: String,
    /** The year the page names; null: the page names none. */
    val year: Int?,
    val url: String,
    val sourceHost: String?,
    val sourceKind: SourceKind?,
    val sourceName: String?,
    val drivetrain: String?,
    val quality: BuildQuality?,
    val warnings: List<String>,
    val selectable: Boolean,
    val otherHosts: List<String>,
)

/** A part of the build as the server drafts it from the specification. */
data class BuildPart(
    val section: String,
    val category: String,
    val name: String,
    val notes: String,
    val groupId: String,
)

/** A line of the page that was not read as a part. */
data class UnrecognizedField(val label: String, val value: String)

/** What the page says about the bike itself, to offer and never to fill in on its own. */
data class SuggestedDetails(
    val weightKg: Double?,
    val color: String?,
    val sizes: String?,
    val wheelSize: String?,
    val manufacturerUrl: String?,
)

/** The build found: what the page says, how much of it was read, and the parts drafted from it. */
data class ResolvedBuild(
    val name: String,
    val brand: String,
    val model: String,
    val trim: String?,
    val year: Int?,
    /** The year the page names. */
    val sourceYear: Int?,
    val sourceUrl: String,
    val sourceHost: String?,
    val sourceKind: SourceKind?,
    val sourceName: String?,
    /** The person chose the page; the match was not checked. */
    val manualSelection: Boolean,
    /** The page describes another model than the one asked for. */
    val identityMismatch: Boolean,
    /** The page's year is not the year asked for. */
    val yearMismatch: Boolean,
    val warnings: List<String>,
    val quality: BuildQuality?,
    val parts: List<BuildPart>,
    val unrecognized: List<UnrecognizedField>,
    val suggested: SuggestedDetails,
) {
    /** The person must say that this build is the one they want. */
    val needsConfirmation: Boolean
        get() = identityMismatch || yearMismatch
}

/** How many sources were asked and how many answered: a limited search is not called complete. */
data class SourcesChecked(val asked: Int, val answered: Int, val complete: Boolean)

/** The answer of the server to a search, whole. */
data class BuildResolution(
    val status: ResolutionStatus,
    val query: BuildQuery,
    val cached: Boolean,
    val retryable: Boolean,
    /** Why there is no build: `timeout`, `http_403`, `candidate_expired` and the like. */
    val reason: String?,
    /** The stored preview of a found build; creation names it to keep the source. */
    val previewId: String?,
    val previewExpiresAt: Instant?,
    val candidates: List<BuildCandidate>,
    val build: ResolvedBuild?,
    val sourcesChecked: SourcesChecked?,
)

/**
 * The search for a build and the making of a bike with it. The server's own search and rules do the
 * work; nothing here parses a page.
 */
interface BikeWizardRepository {
    /**
     * Asks the server to find a build. A failure of the service is an answer
     * ([ResolutionStatus.Unavailable]), not an error. May take over a minute; the coroutine's
     * cancellation stops the search.
     */
    suspend fun resolve(request: ResolveRequest): BuildResolution

    /**
     * Makes a bike with the parts the person has checked, in one operation. [key] (a UUID) is the
     * identity of the intention: the same key after a lost answer is the same bike. [previewId]
     * keeps the source and the factory specification of a found build; with the model or the year
     * of the page unlike the bike's, [identityConfirmed] says the person agreed. Refusals are
     * [DataError.Rejected]: `previewId` as the [DataError.Rejected.field] for a preview that is
     * gone, `identityConfirmed` for a difference not agreed to.
     */
    suspend fun create(
        draft: BikeDraft,
        parts: List<ComponentDraft>,
        previewId: String?,
        identityConfirmed: Boolean,
        key: String,
    ): BikeDetail
}
