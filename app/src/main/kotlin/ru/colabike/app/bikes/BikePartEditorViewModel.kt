package ru.colabike.app.bikes

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ComponentCatalog
import ru.colabike.core.model.ComponentDictionary
import ru.colabike.core.model.ComponentDraft
import ru.colabike.core.model.ComponentProblem
import ru.colabike.core.model.ComponentRules
import ru.colabike.core.model.DataError
import ru.colabike.core.model.diff
import ru.colabike.core.model.toDraft

/**
 * What the part form holds, as typed. The section follows the category until the person chooses one
 * ([sectionChosen]): an accessory category is an accessory, but a person's own word is theirs to
 * place.
 */
@Immutable
data class PartForm(
    val section: String = ComponentCatalog.SECTION_BUILD,
    val sectionChosen: Boolean = false,
    val category: String = "",
    val name: String = "",
    val notes: String = "",
    val price: String = "",
    val url: String = "",
) {
    /**
     * The draft this text means. A part keeps the group it had while its category is the same; with
     * another category it gets the site's group for it, or none (then it is grouped by its
     * category).
     */
    fun toDraft(
        original: BikeComponent?,
        dictionary: ComponentDictionary = ComponentCatalog.dictionary,
    ): Pair<ComponentDraft, List<ComponentProblem>> {
        val problems = mutableListOf<ComponentProblem>()
        val priceText = price.trim()
        val parsedPrice =
            if (priceText.isEmpty()) null
            else
                DECIMAL.takeIf { it.matches(priceText) }
                    ?.let { priceText.replace(',', '.').toDouble() }
                    ?: run {
                        problems += ComponentProblem.PriceInvalid
                        null
                    }
        // A category left as it was stays as it was, with its group; one that was changed is the
        // site's spelling when it is a site's category ("звонок" is "Звонок"), else as typed.
        val unchanged = original != null && original.category == category.trim()
        val group = if (unchanged) original!!.groupId else dictionary.groupIdOf(category)
        val draft =
            ComponentDraft(
                section = section,
                category = if (unchanged) original!!.category else dictionary.canonical(category),
                name = name,
                notes = notes,
                priceRub = parsedPrice,
                url = url,
                groupId = group,
            )
        return draft to problems
    }

    companion object {
        private val DECIMAL = Regex("[0-9]{1,9}([.,][0-9]{1,2})?")

        fun of(part: BikeComponent): PartForm =
            PartForm(
                section = part.section,
                sectionChosen = true,
                category = part.category,
                name = part.name,
                notes = part.notes,
                price =
                    part.priceRub
                        ?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }
                        .orEmpty(),
                url = part.url.orEmpty(),
            )
    }
}

@Immutable
sealed interface PartEditorUiState {
    data object Loading : PartEditorUiState

    data class Failed(val message: UiText) : PartEditorUiState

    /** The bike or the part is not there, or not the person's. */
    data object Unavailable : PartEditorUiState

    data class Editing(
        val form: PartForm,
        /** The part being changed, as the server last gave it; null for a new one. */
        val editing: BikeComponent? = null,
        val suggestions: List<String> = emptyList(),
        /** The names the site knows in the category typed, to complete the name of the part. */
        val nameSuggestions: List<String> = emptyList(),
        val saving: Boolean = false,
        val problems: List<ComponentProblem> = emptyList(),
        val problem: UiText? = null,
        /** The server holds a newer version of the part: reading it again is possible. */
        val canReload: Boolean = false,
        val saved: Boolean = false,
        val confirmingDelete: Boolean = false,
        val deleting: Boolean = false,
        val deleted: Boolean = false,
    ) : PartEditorUiState
}

/**
 * The form of a part of a bike's build: a new one ([componentId] null) or a change of one of the
 * person's own. A new part is sent with a key kept for as long as the form is the same; a change
 * sends only what differs from the part as it was read.
 */
class BikePartEditorViewModel(
    private val repository: BikesRepository,
    private val bikeId: BikeId,
    private val componentId: String?,
    /** The site's dictionary of parts in force now (the built-in one until it has been read). */
    private val dictionary: () -> ComponentDictionary = { ComponentCatalog.dictionary },
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow<PartEditorUiState>(PartEditorUiState.Loading)
    val state: StateFlow<PartEditorUiState> = mutable.asStateFlow()
    private var attempt: Pair<ComponentDraft, String>? = null

    init {
        load()
    }

    fun load() {
        mutable.value = PartEditorUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val bike = repository.bike(bikeId)
                    val part = componentId?.let { id ->
                        bike.components.firstOrNull { it.id == id }
                    }
                    when {
                        !bike.summary.isOwner -> PartEditorUiState.Unavailable
                        componentId == null -> PartEditorUiState.Editing(PartForm())
                        part == null -> PartEditorUiState.Unavailable
                        else -> PartEditorUiState.Editing(PartForm.of(part), editing = part)
                    }
                } catch (_: DataError.NotFound) {
                    PartEditorUiState.Unavailable
                } catch (e: DataError) {
                    PartEditorUiState.Failed(e.toUiText())
                }
        }
    }

    private fun edit(transform: (PartForm) -> PartForm) {
        mutable.update {
            if (it is PartEditorUiState.Editing && !it.saving && !it.deleting) {
                val form = transform(it.form)
                it.copy(
                    form = form,
                    suggestions = dictionary().suggestions(form.category),
                    nameSuggestions = dictionary().nameSuggestions(form.category, form.name),
                    problems = emptyList(),
                    problem = null,
                    canReload = false,
                )
            } else it
        }
    }

    fun setCategory(value: String) = edit {
        val category = value.take(ComponentRules.MAX_CATEGORY + 1)
        // A category of the site's tells its section, until the person chooses one themselves.
        it.copy(
            category = category,
            section =
                if (it.sectionChosen || dictionary().groupIdOf(category).isEmpty()) it.section
                else dictionary().sectionOf(category),
        )
    }

    fun pickCategory(value: String) = setCategory(value)

    fun setName(value: String) = edit { it.copy(name = value.take(ComponentRules.MAX_NAME + 1)) }

    fun setNotes(value: String) = edit { it.copy(notes = value.take(ComponentRules.MAX_NOTES + 1)) }

    fun setPrice(value: String) = edit { it.copy(price = value.take(MAX_NUMBER_TEXT)) }

    fun setUrl(value: String) = edit { it.copy(url = value.take(BikeLinkMax)) }

    fun setSection(value: String) = edit {
        if (
            value != ComponentCatalog.SECTION_BUILD && value != ComponentCatalog.SECTION_ACCESSORIES
        )
            it
        else it.copy(section = value, sectionChosen = true)
    }

    fun save() {
        val current = mutable.value as? PartEditorUiState.Editing ?: return
        if (current.saving || current.deleting) return
        val original = current.editing
        val (draft, unreadable) = current.form.toDraft(original, dictionary())
        val problems = (unreadable + ComponentRules.check(draft)).distinct()
        if (problems.isNotEmpty()) {
            mutable.value = current.copy(problems = problems, problem = null, canReload = false)
            return
        }
        val patch = original?.let { draft.diff(it.toDraft()) }
        if (original != null && patch!!.isEmpty) {
            // Nothing differs from what the server holds: nothing to send.
            mutable.value = current.copy(saved = true, problems = emptyList(), problem = null)
            return
        }
        mutable.value = current.copy(saving = true, problems = emptyList(), problem = null)
        viewModelScope.launch {
            try {
                val part =
                    if (original == null) {
                        // The same form after a lost answer is the same part.
                        val key = attempt?.takeIf { it.first == draft }?.second ?: newKey()
                        attempt = draft to key
                        repository.addComponent(bikeId, draft, key)
                    } else {
                        repository.updateComponent(bikeId, original.id, patch!!, original.version)
                    }
                mutable.value =
                    current.copy(
                        saving = false,
                        saved = true,
                        editing = if (original != null) part else null,
                    )
            } catch (e: DataError.Rejected) {
                mutable.value =
                    current.copy(saving = false, problem = refusal(e), canReload = e.status == 412)
            } catch (_: DataError.NotFound) {
                mutable.value = PartEditorUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(saving = false, problem = e.toUiText())
            }
        }
    }

    private fun refusal(error: DataError.Rejected): UiText =
        when {
            error.code == EMAIL_NOT_VERIFIED -> UiText.Res(R.string.part_needs_email)
            error.status == 412 -> UiText.Res(R.string.part_changed_elsewhere)
            else -> error.toUiText()
        }

    // --- deleting --------------------------------------------------------------------------------

    fun askDelete() {
        mutable.update {
            if (it is PartEditorUiState.Editing && it.editing != null && !it.saving)
                it.copy(confirmingDelete = true, problem = null)
            else it
        }
    }

    fun cancelDelete() {
        mutable.update {
            if (it is PartEditorUiState.Editing) it.copy(confirmingDelete = false) else it
        }
    }

    fun confirmDelete() {
        val current = mutable.value as? PartEditorUiState.Editing ?: return
        val part = current.editing ?: return
        if (current.deleting || current.saving) return
        mutable.value = current.copy(confirmingDelete = false, deleting = true, problem = null)
        viewModelScope.launch {
            try {
                repository.removeComponent(bikeId, part.id)
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, deleted = true)
            } catch (_: DataError.NotFound) {
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, deleted = true)
            } catch (e: DataError.Rejected) {
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, problem = refusal(e))
            } catch (e: DataError) {
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, problem = e.toUiText())
            }
        }
    }

    private companion object {
        const val EMAIL_NOT_VERIFIED = "email_verification_required"
        const val MAX_NUMBER_TEXT = 14
        const val BikeLinkMax = 2049
    }
}
