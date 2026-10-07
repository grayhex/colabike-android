package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.catalog.BuiltinCatalog
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.BikeRules
import ru.colabike.core.model.BikeWizardRepository
import ru.colabike.core.model.BuildCandidate
import ru.colabike.core.model.BuildQuery
import ru.colabike.core.model.BuildQueryParser
import ru.colabike.core.model.BuildResolution
import ru.colabike.core.model.ComponentDictionary
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.ComponentProblem
import ru.colabike.core.model.ComponentRules
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PriceVisibility
import ru.colabike.core.model.ResolutionStatus
import ru.colabike.core.model.ResolveRequest
import ru.colabike.core.model.ResolvedBuild
import ru.colabike.core.model.SiteCatalog
import ru.colabike.core.model.SourcesChecked

/** The three steps of the wizard, as the site's: search, the parts, the details. */
enum class WizardStep {
    Search,
    Build,
    Details,
}

/** A part of the build as the person is editing it; [key] only tells the rows apart. */
@Immutable
data class WizardPart(
    val key: Long,
    val section: String,
    val category: String,
    val name: String,
    val notes: String,
    val groupId: String,
    val price: String = "",
    /** Added by the person, not drafted from the specification: shown apart, in the order added. */
    val added: Boolean = false,
)

/** The variants a search offered, and what was asked: the person chooses one of them. */
@Immutable
data class WizardOffer(
    val query: BuildQuery,
    val candidates: List<BuildCandidate>,
    val sources: SourcesChecked?,
)

/** The build that is being checked: where it comes from and what is still to be said of it. */
@Immutable
data class WizardBuild(
    val build: ResolvedBuild,
    /** The stored preview that keeps the source with the bike; null by hand, or once it expired. */
    val previewId: String?,
    /** The person agreed that the page's model or year differs from the bike's. */
    val confirmed: Boolean,
)

/** Why a search gave no build to check. */
sealed interface SearchFailure {
    /** The line says too little: a brand and a model are needed. */
    data object NoQuery : SearchFailure

    data object NotFound : SearchFailure

    data object UnsupportedBrand : SearchFailure

    /**
     * The service or the page did not answer: [reason] says why, [retryable] if it may be tried
     * again.
     */
    data class Unavailable(val reason: String?, val retryable: Boolean) : SearchFailure

    /** A page was read that is not the specification of a bike. */
    data class Unreadable(val reason: String?) : SearchFailure

    /** The variants offered earlier are gone from the server: the search starts again. */
    data object Expired : SearchFailure

    /** The page was of another model or year, and the person did not accept it. */
    data object Declined : SearchFailure

    /** The request itself failed (no network, too many searches, a fault). */
    data class Failed(val message: UiText) : SearchFailure
}

/** What the search is doing now. */
sealed interface SearchPhase {
    data object Idle : SearchPhase

    data class Resolving(val request: ResolveRequest) : SearchPhase

    data class Failed(val failure: SearchFailure) : SearchPhase
}

/** A question the wizard asks before it goes on, and what it does after "yes". */
sealed interface WizardQuestion {
    /** A new search would replace the parts the person has edited. */
    data class ReplaceParts(val request: ResolveRequest) : WizardQuestion

    /**
     * The page describes another model or year than the bike's. [onSave] is true when the server
     * asked on saving; false when the search found it.
     */
    data class Identity(val build: ResolvedBuild, val asked: Int?, val onSave: Boolean) :
        WizardQuestion

    /** The wizard is left with something typed in it. */
    data object Discard : WizardQuestion
}

@Immutable
data class WizardUiState(
    val step: WizardStep = WizardStep.Search,
    val searchText: String = "",
    /** The name for the garage; empty means the bike is named by its brand, model and year. */
    val garageName: String = "",
    val pageOpen: Boolean = false,
    val pageUrl: String = "",
    val phase: SearchPhase = SearchPhase.Idle,
    val offer: WizardOffer? = null,
    val found: WizardBuild? = null,
    val parts: List<WizardPart> = emptyList(),
    /**
     * The parts the person has changed since the build came: a new search asks before replacing.
     */
    val partsEdited: Boolean = false,
    val form: BikeForm = BikeForm(),
    /** What the search line meant when the build step was opened; null by hand with no line. */
    val query: BuildQuery? = null,
    val problems: List<BikeProblem> = emptyList(),
    val partProblems: Map<Long, List<ComponentProblem>> = emptyMap(),
    val saving: Boolean = false,
    val problem: UiText? = null,
    /** The preview is gone: the bike can be saved without its source, or searched for again. */
    val previewGone: Boolean = false,
    val question: WizardQuestion? = null,
    /** Saved; the screen closes and shows this bike. */
    val created: BikeDetail? = null,
) {
    /** Something is typed in: leaving asks first. */
    val dirty: Boolean
        get() =
            searchText.isNotBlank() ||
                garageName.isNotBlank() ||
                step != WizardStep.Search ||
                offer != null
}

/**
 * The wizard of a new bike, as the site's: search, the parts, the details. The server does the work
 * (the search, the variants, the preview, the rules of making a bike); this holds what the person
 * has typed and chosen, and never loses it: a failed search, a refused save or a lost connection
 * leave every field as it was. A search can be stopped; a variant is chosen by the identifier the
 * server gave, never by a new search; a page of another model or year is used only when the person
 * says so; a new search does not replace edited parts without asking; and saving is one operation
 * under a key that stays while the request is the same, so a repeat after a lost answer is the same
 * bike.
 */
class BikeWizardViewModel(
    private val repository: BikeWizardRepository,
    /** The site's dictionaries in force now: the lists of brands, kinds and parts. */
    private val catalog: () -> SiteCatalog = { BuiltinCatalog.value },
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = mutable.asStateFlow()

    private var search: Job? = null
    private var saving: Job? = null
    private var lastRequest: ResolveRequest? = null
    private var nextPart = 1L

    /** What was sent last and under which key: the same request keeps its key. */
    private var attempt: Pair<Any, String>? = null

    private val dictionary: ComponentDictionary
        get() = catalog().components

    // --- step 1: search --------------------------------------------------------------------------

    fun setSearchText(value: String) = update { it.copy(searchText = value.take(MAX_LINE)) }

    fun setGarageName(value: String) = update {
        it.copy(garageName = value.take(BikeRules.MAX_NAME + 1), form = it.form.withName(value))
    }

    fun setPageUrl(value: String) = update { it.copy(pageUrl = value.take(BikeRules.MAX_LINK)) }

    fun togglePage() = update { it.copy(pageOpen = !it.pageOpen) }

    /** The search by the line: the manufacturer first, then the stores. */
    fun search() {
        val query = queryOf(state.value) ?: return noQuery()
        run(ResolveRequest.Search(query))
    }

    /** The search by a page of a shop or of the manufacturer that the person pasted. */
    fun searchPage() {
        val current = state.value
        val query = queryOf(current) ?: return noQuery()
        val url = current.pageUrl.trim()
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) {
            update { it.copy(phase = SearchPhase.Failed(SearchFailure.NoQuery)) }
            return
        }
        run(ResolveRequest.Page(query, url))
    }

    /**
     * One of the variants the search offered, by its own identifier; a page without one, by its
     * address.
     */
    fun choose(candidate: BuildCandidate) {
        val offer = state.value.offer ?: return
        run(
            candidate.candidateId?.let { ResolveRequest.Variant(offer.query, it) }
                ?: ResolveRequest.Page(offer.query, candidate.url)
        )
    }

    /** Stops the search on its way: nothing it would have said is shown. */
    fun stopSearch() {
        search?.cancel()
        search = null
        update { if (it.phase is SearchPhase.Resolving) it.copy(phase = SearchPhase.Idle) else it }
    }

    /** On to the parts with none: the search found nothing, or the person knows the bike. */
    fun continueByHand() {
        val current = state.value
        val query = queryOf(current)
        update {
            it.copy(
                step = WizardStep.Build,
                query = query,
                form = formFor(it.form, query, null),
                phase = SearchPhase.Idle,
            )
        }
    }

    private fun noQuery() = update { it.copy(phase = SearchPhase.Failed(SearchFailure.NoQuery)) }

    private fun queryOf(state: WizardUiState): BuildQuery? =
        BuildQueryParser.parse(state.searchText, catalog().brands)

    /** The same request again after a failure that may pass: the line, the page or the variant. */
    fun retry() {
        lastRequest?.let(::run)
    }

    private fun run(request: ResolveRequest) {
        val current = state.value
        if (current.phase is SearchPhase.Resolving) return
        lastRequest = request
        // A build the person has already worked on is not thrown away by a new search unasked.
        if (current.found != null && current.partsEdited && current.question == null) {
            update { it.copy(question = WizardQuestion.ReplaceParts(request)) }
            return
        }
        update { it.copy(phase = SearchPhase.Resolving(request), question = null, problem = null) }
        search = viewModelScope.launch {
            try {
                applyResolution(request, repository.resolve(request))
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                update {
                    it.copy(phase = SearchPhase.Failed(SearchFailure.Failed(e.toUiText())))
                }
            }
        }
    }

    private fun applyResolution(request: ResolveRequest, result: BuildResolution) {
        val build = result.build
        when {
            result.status == ResolutionStatus.Resolved && build != null ->
                if (build.needsConfirmation) {
                    // The page may not be the bike: the person decides before anything is taken.
                    pending = Held(request.query, build, result.previewId)
                    update {
                        it.copy(
                            phase = SearchPhase.Idle,
                            question = WizardQuestion.Identity(build, request.query.year, false),
                        )
                    }
                } else take(request.query, build, result.previewId, confirmed = false)
            result.status == ResolutionStatus.Ambiguous && result.candidates.isNotEmpty() ->
                update {
                    it.copy(
                        phase = SearchPhase.Idle,
                        offer =
                            WizardOffer(request.query, result.candidates, result.sourcesChecked),
                    )
                }
            else -> {
                val failure =
                    when (result.status) {
                        ResolutionStatus.UnsupportedBrand -> SearchFailure.UnsupportedBrand
                        ResolutionStatus.Unavailable ->
                            SearchFailure.Unavailable(result.reason, result.retryable)
                        ResolutionStatus.ParseError -> SearchFailure.Unreadable(result.reason)
                        else ->
                            when (result.reason) {
                                "candidate_expired" -> SearchFailure.Expired
                                "not_complete_bike" -> SearchFailure.Unreadable(result.reason)
                                else -> SearchFailure.NotFound
                            }
                    }
                update {
                    it.copy(
                        phase = SearchPhase.Failed(failure),
                        // The variants offered before are gone from the server with an expired id.
                        offer = if (failure == SearchFailure.Expired) null else it.offer,
                    )
                }
            }
        }
    }

    /** The build that waits for the person's answer about its model or year. */
    private var pending: Held? = null

    private class Held(val query: BuildQuery, val build: ResolvedBuild, val previewId: String?)

    private fun take(
        query: BuildQuery,
        build: ResolvedBuild,
        previewId: String?,
        confirmed: Boolean,
    ) {
        val parts =
            build.parts.map { part ->
                WizardPart(
                    key = nextPart++,
                    section = part.section,
                    category = part.category,
                    name = part.name,
                    notes = part.notes,
                    groupId = part.groupId,
                    // A part with no category of its own is named by the person.
                    added = part.category.isBlank(),
                )
            }
        update {
            it.copy(
                step = WizardStep.Build,
                phase = SearchPhase.Idle,
                query = query,
                offer = null,
                found = WizardBuild(build, previewId, confirmed),
                parts = parts,
                partsEdited = false,
                partProblems = emptyMap(),
                previewGone = false,
                form = formFor(it.form, query, build),
            )
        }
    }

    /**
     * The form after a search: what the person asked is the bike; what the page says is offered.
     */
    private fun formFor(form: BikeForm, query: BuildQuery?, build: ResolvedBuild?): BikeForm =
        if (query == null) form
        else
            form.copy(
                brand = query.brand,
                model = query.model,
                trim = query.trim.orEmpty(),
                // The year the person gave; with none, the one the page names.
                year = (query.year ?: build?.sourceYear)?.toString().orEmpty(),
            )

    // --- questions
    // ---------------------------------------------------------------------------------

    /** "Yes" to the question that is open. */
    fun confirm() {
        val question = state.value.question ?: return
        update { it.copy(question = null) }
        when (question) {
            is WizardQuestion.ReplaceParts -> {
                update { it.copy(partsEdited = false) }
                run(question.request)
            }
            is WizardQuestion.Identity ->
                if (question.onSave) {
                    update { it.copy(found = it.found?.copy(confirmed = true)) }
                    save()
                } else {
                    pending?.let { take(it.query, it.build, it.previewId, confirmed = true) }
                    pending = null
                }
            WizardQuestion.Discard -> Unit
        }
    }

    /** "No" to the question that is open: nothing changes, except that the page is not taken. */
    fun decline() {
        val question = state.value.question ?: return
        update {
            it.copy(
                question = null,
                phase =
                    if (question is WizardQuestion.Identity && !question.onSave)
                        SearchPhase.Failed(SearchFailure.Declined)
                    else it.phase,
            )
        }
        if (question is WizardQuestion.Identity && !question.onSave) pending = null
    }

    // --- step 2: the parts
    // ---------------------------------------------------------------------------

    fun addPart() = edited {
        it.copy(
            parts =
                it.parts +
                    WizardPart(
                        key = nextPart++,
                        section = ComponentDictionary.SECTION_BUILD,
                        category = "",
                        name = "",
                        notes = "",
                        groupId = "",
                        added = true,
                    )
        )
    }

    fun removePart(key: Long) = edited {
        it.copy(parts = it.parts.filterNot { part -> part.key == key })
    }

    /**
     * A category of the site's tells the section and the group; one of the person's own keeps them.
     */
    fun setPartCategory(key: Long, value: String) =
        changePart(key) { part ->
            val category = value.take(ComponentRules.MAX_CATEGORY + 1)
            val known = dictionary.groupIdOf(category)
            part.copy(
                category = category,
                groupId = if (known.isNotEmpty()) known else part.groupId,
                section = if (known.isNotEmpty()) dictionary.sectionOf(category) else part.section,
            )
        }

    fun setPartName(key: Long, value: String) =
        changePart(key) {
            it.copy(name = value.take(ComponentRules.MAX_NAME + 1))
        }

    fun setPartPrice(key: Long, value: String) =
        changePart(key) {
            it.copy(price = value.take(MAX_NUMBER_TEXT))
        }

    fun setPartNotes(key: Long, value: String) =
        changePart(key) {
            it.copy(notes = value.take(ComponentRules.MAX_NOTES + 1))
        }

    private fun changePart(key: Long, change: (WizardPart) -> WizardPart) = edited {
        it.copy(parts = it.parts.map { part -> if (part.key == key) change(part) else part })
    }

    private fun edited(change: (WizardUiState) -> WizardUiState) = update {
        change(it).copy(partsEdited = true, partProblems = emptyMap(), problem = null)
    }

    /**
     * On to the details. A part that is empty is dropped; one that is wrong stops the way, named.
     */
    fun next() {
        val current = state.value
        when (current.step) {
            WizardStep.Search -> continueByHand()
            WizardStep.Build -> {
                val kept = current.parts.filterNot { it.category.isBlank() && it.name.isBlank() }
                val wrong =
                    kept
                        .associate { it.key to ComponentRules.check(draftOf(it)).toMutableList() }
                        .mapValues { (key, found) ->
                            if (unreadablePrice(kept.first { it.key == key })) {
                                found += ComponentProblem.PriceInvalid
                            }
                            found.distinct()
                        }
                        .filterValues { it.isNotEmpty() }
                update {
                    if (wrong.isNotEmpty()) it.copy(parts = kept, partProblems = wrong)
                    else it.copy(parts = kept, partProblems = emptyMap(), step = WizardStep.Details)
                }
            }
            WizardStep.Details -> save()
        }
    }

    /** Back one step; false at the first: the wizard is the one to leave. */
    fun back(): Boolean {
        val step = state.value.step
        if (state.value.saving) return true
        return when (step) {
            WizardStep.Details -> {
                update { it.copy(step = WizardStep.Build, problems = emptyList(), problem = null) }
                true
            }
            WizardStep.Build -> {
                update { it.copy(step = WizardStep.Search) }
                true
            }
            WizardStep.Search -> false
        }
    }

    /** Asks before the wizard is left with something typed in it. */
    fun askLeave() = update { it.copy(question = WizardQuestion.Discard) }

    // --- step 3: the details ---------------------------------------------------------------------

    private fun form(change: (BikeForm) -> BikeForm) = update {
        if (it.saving) it
        else
            it.copy(
                form = change(it.form),
                problems = emptyList(),
                problem = null,
                previewGone = false,
            )
    }

    fun setBrand(value: String) = form { it.withBrand(value) }

    fun setModel(value: String) = form { it.withModel(value) }

    fun setTrim(value: String) = form { it.withTrim(value) }

    fun setYear(value: String) = form { it.withYear(value) }

    fun setDescription(value: String) = form { it.withDescription(value) }

    fun setColor(value: String) = form { it.withColor(value) }

    fun setSize(value: String) = form { it.withSize(value) }

    fun setWeight(value: String) = form { it.withWeight(value) }

    fun setMileage(value: String) = form { it.withMileage(value) }

    fun setManufacturerUrl(value: String) = form { it.withManufacturerUrl(value) }

    fun setPrice(value: String) = form { it.withPrice(value) }

    fun setPriceVisibility(value: PriceVisibility) = form { it.withPriceVisibility(value) }

    fun setFormer(value: Boolean) = form { it.withFormer(value) }

    fun setPublic(value: Boolean) = form { it.withPublic(value) }

    fun setElectric(value: Boolean) = form { it.withElectric(value) }

    fun setFatbike(value: Boolean) = form { it.withFatbike(value) }

    fun setCategory(key: String) = form {
        it.copy(classification = it.classification.withCategory(key, catalog().classification))
    }

    fun setSubtype(key: String) = form {
        it.copy(classification = it.classification.withSubtype(key, catalog().classification))
    }

    fun setSuspension(key: String) = form {
        it.copy(classification = it.classification.withSuspension(key, catalog().classification))
    }

    fun setConstruction(key: String) = form {
        it.copy(classification = it.classification.withConstruction(key, catalog().classification))
    }

    fun toggleUse(key: String) = form {
        it.copy(classification = it.classification.withUseToggled(key, catalog().classification))
    }

    /**
     * What the page says the bike weighs or is coloured, taken into a field that is still empty.
     */
    fun useSuggestedWeight() = form {
        val weight = state.value.found?.build?.suggested?.weightKg
        if (weight == null || it.weight.isNotBlank()) it else it.copy(weight = plain(weight))
    }

    fun useSuggestedColor() = form {
        val color = state.value.found?.build?.suggested?.color
        if (color.isNullOrBlank() || it.color.isNotBlank()) it
        else it.copy(color = color.take(BikeRules.MAX_COLOR))
    }

    fun useSuggestedLink() = form {
        val link = state.value.found?.build?.suggested?.manufacturerUrl
        if (link.isNullOrBlank() || it.manufacturerUrl.isNotBlank() || !BikeRules.isLink(link)) it
        else it.copy(manufacturerUrl = link)
    }

    /** The preview of the source is gone: the bike can still be made, without the source. */
    fun saveWithoutSource() {
        update {
            it.copy(
                found = it.found?.copy(previewId = null, confirmed = false),
                previewGone = false,
                problem = null,
            )
        }
        save()
    }

    /** Back to the search to find the build again; the parts typed stay until it is replaced. */
    fun searchAgain() = update {
        it.copy(step = WizardStep.Search, previewGone = false, problem = null)
    }

    // --- saving
    // ------------------------------------------------------------------------------------

    fun save() {
        val current = state.value
        if (current.saving || current.created != null) return
        val (typed, unreadable) = current.form.toDraft()
        // The server names a bike that has no name of its own; the check wants one.
        val named =
            typed.name.trim().ifEmpty { nameOf(typed.brand, typed.model, typed.trim, typed.year) }
        val problems = (unreadable + BikeRules.check(typed.copy(name = named), true)).distinct()
        val parts = current.parts.map(::draftOf)
        val partProblems =
            current.parts
                .associate { it.key to ComponentRules.check(draftOf(it)) }
                .filterValues { it.isNotEmpty() }
        if (problems.isNotEmpty() || partProblems.isNotEmpty()) {
            update { it.copy(problems = problems, partProblems = partProblems, problem = null) }
            if (partProblems.isNotEmpty()) update { it.copy(step = WizardStep.Build) }
            return
        }
        val preview = current.found?.previewId
        val confirmed = current.found?.confirmed == true
        // The same request after a lost answer is the same bike; a changed one is a new one.
        val snapshot = listOf(typed, parts, preview, confirmed)
        val key = attempt?.takeIf { it.first == snapshot }?.second ?: newKey()
        attempt = snapshot to key
        update {
            it.copy(saving = true, problems = emptyList(), problem = null, previewGone = false)
        }
        saving = viewModelScope.launch {
            try {
                val bike = repository.create(typed, parts, preview, confirmed, key)
                update { it.copy(saving = false, created = bike) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError.Rejected) {
                refused(e)
            } catch (e: DataError) {
                update { it.copy(saving = false, problem = e.toUiText()) }
            }
        }
    }

    private fun refused(error: DataError.Rejected) {
        val build = state.value.found?.build
        when {
            error.field == "previewId" ->
                update { it.copy(saving = false, previewGone = true, problem = null) }
            error.field == "identityConfirmed" && build != null ->
                update {
                    it.copy(
                        saving = false,
                        question = WizardQuestion.Identity(build, it.form.year.toIntOrNull(), true),
                    )
                }
            error.code == EMAIL_NOT_VERIFIED ->
                update { it.copy(saving = false, problem = UiText.Res(R.string.bike_needs_email)) }
            error.status == 409 && error.userMessage.isNotBlank() ->
                update { it.copy(saving = false, problem = UiText.Plain(error.userMessage)) }
            else -> update { it.copy(saving = false, problem = error.toUiText()) }
        }
    }

    // --- helpers
    // -----------------------------------------------------------------------------------

    private fun draftOf(part: WizardPart): ComponentDraft {
        val category = dictionary.canonical(part.category)
        return ComponentDraft(
            section = part.section,
            category = category,
            name = part.name,
            notes = part.notes,
            priceRub = priceOf(part.price),
            url = "",
            groupId = part.groupId,
        )
    }

    private fun priceOf(text: String): Double? =
        text.trim().takeIf { it.isNotEmpty() && PRICE.matches(it) }?.replace(',', '.')?.toDouble()

    private fun unreadablePrice(part: WizardPart): Boolean =
        part.price.isNotBlank() && !PRICE.matches(part.price.trim())

    private fun plain(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    private fun nameOf(brand: String, model: String, trim: String, year: Int?): String =
        listOf(brand.trim(), model.trim(), trim.trim(), year?.toString().orEmpty())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .take(BikeRules.MAX_NAME)

    private fun update(change: (WizardUiState) -> WizardUiState) = mutable.update(change)

    private companion object {
        const val MAX_LINE = 240
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
        val PRICE = Regex("[0-9]{1,9}([.,][0-9]{1,2})?")
    }
}
