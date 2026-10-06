package ru.colabike.app.journal

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
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
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.JournalCatalog
import ru.colabike.core.model.JournalDraft
import ru.colabike.core.model.JournalEntry
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalProblem
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalRules
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.diff
import ru.colabike.core.model.toDraft

/**
 * What the form holds, as typed: the date and the mileage are text until the person saves, so that
 * "31.02.2026" or "12k" is told, never turned into something else behind their back.
 */
@Immutable
data class JournalForm(
    val kind: String = JournalCatalog.kinds.last(),
    val title: String = "",
    val body: String = "",
    val status: JournalStatus = JournalStatus.Draft,
    val isPublic: Boolean = false,
    /** `ДД.ММ.ГГГГ`, or empty. */
    val eventDate: String = "",
    val mileage: String = "",
    val installationResult: String? = null,
    val componentIds: Set<String> = emptySet(),
) {
    /** The draft this text means, and what is wrong with the numbers and the date that are not. */
    fun toDraft(): Pair<JournalDraft, List<JournalProblem>> {
        val problems = mutableListOf<JournalProblem>()
        val dateText = eventDate.trim()
        val date =
            when {
                dateText.isEmpty() -> null
                else ->
                    parseDate(dateText)
                        ?: run {
                            problems += JournalProblem.DateInvalid
                            null
                        }
            }
        val mileageText = mileage.trim()
        val km =
            when {
                mileageText.isEmpty() -> null
                DIGITS.matches(mileageText) -> mileageText.toInt()
                else -> {
                    problems += JournalProblem.MileageInvalid
                    null
                }
            }
        val draft =
            JournalDraft(
                kind = kind,
                title = title,
                body = body,
                status = status,
                isPublic = isPublic,
                eventDate = date,
                mileageKm = km,
                // How an installation went is said of a build only; the server drops it for others.
                installationResult = installationResult.takeIf { kind == JournalCatalog.BUILD },
                componentIds = componentIds.toList(),
            )
        return draft to problems
    }

    companion object {
        private val DIGITS = Regex("[0-9]{1,8}")
        private val DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

        fun parseDate(text: String): LocalDate? =
            try {
                LocalDate.parse(text, DATE)
            } catch (_: DateTimeParseException) {
                null
            }

        fun formatDate(date: LocalDate): String = DATE.format(date)

        fun of(entry: JournalEntry): JournalForm {
            val draft = entry.toDraft()
            return JournalForm(
                kind =
                    draft.kind.takeIf { it in JournalCatalog.kinds } ?: JournalCatalog.kinds.last(),
                title = draft.title,
                body = draft.body,
                status = draft.status,
                isPublic = draft.isPublic,
                eventDate = draft.eventDate?.let(::formatDate).orEmpty(),
                mileage = draft.mileageKm?.toString().orEmpty(),
                installationResult = draft.installationResult,
                componentIds = draft.componentIds.toSet(),
            )
        }
    }
}

@Immutable
sealed interface JournalEditorUiState {
    data object Loading : JournalEditorUiState

    data class Failed(val message: UiText) : JournalEditorUiState

    /** Not there or not the person's: there is nothing to change. */
    data object Unavailable : JournalEditorUiState

    data class Editing(
        val form: JournalForm,
        /** The bike the entry is about: its parts to choose from, whether others can see it. */
        val bike: BikeDetail,
        /**
         * The parts to choose from: the bike's, and those an entry kept that the bike no longer
         * has.
         */
        val choices: List<BikeComponent>,
        /** The entry being changed, as the server last gave it; null for a new one. */
        val editing: JournalEntry? = null,
        val saving: Boolean = false,
        /** What the form's own rules found, shown once the person tried to save. */
        val problems: List<JournalProblem> = emptyList(),
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
        /** The server holds a newer version: reading it again is possible (and loses the edit). */
        val canReload: Boolean = false,
        /** Saved; the screen closes and shows this entry. */
        val saved: JournalEntry? = null,
        /** The person asked to delete and has not answered the question yet. */
        val confirmingDelete: Boolean = false,
        val deleting: Boolean = false,
        /** Deleted; the screen closes and the entry's page with it. */
        val deleted: Boolean = false,
    ) : JournalEditorUiState {
        /** Said to the person who publishes for everyone: the bike is private, so is the entry. */
        val hiddenByBike: Boolean
            get() =
                form.status == JournalStatus.Published && form.isPublic && !bike.summary.isPublic
    }
}

/**
 * The form of a journal entry: a new one for the person's own bike, or a change of their own entry.
 * A new entry is sent with a key that is kept for as long as the form is the same, so a repeat
 * after a lost answer is the same entry and the server never makes two of it. A change names the
 * version that was read and sends only the fields that differ from it.
 */
class JournalEditorViewModel(
    private val journal: JournalRepository,
    private val bikes: BikesRepository,
    private val bikeId: BikeId,
    private val id: JournalId?,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow<JournalEditorUiState>(JournalEditorUiState.Loading)
    val state: StateFlow<JournalEditorUiState> = mutable.asStateFlow()

    /** The last create that was sent and the key it carried. */
    private var attempt: Pair<JournalDraft, String>? = null

    init {
        load()
    }

    fun load() {
        mutable.value = JournalEditorUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val bike = bikes.bike(bikeId)
                    val entry = id?.let { journal.entry(it) }
                    when {
                        // Only the owner of the bike writes in its journal.
                        !bike.summary.isOwner -> JournalEditorUiState.Unavailable
                        // The author's answer has a version; anyone else's does not, and is not
                        // editable.
                        entry != null &&
                            (entry.version == null || entry.summary.bike.id != bikeId) ->
                            JournalEditorUiState.Unavailable
                        else ->
                            JournalEditorUiState.Editing(
                                form = entry?.let(JournalForm::of) ?: JournalForm(),
                                bike = bike,
                                choices = choicesOf(bike, entry),
                                editing = entry,
                            )
                    }
                } catch (_: DataError.NotFound) {
                    JournalEditorUiState.Unavailable
                } catch (e: DataError) {
                    JournalEditorUiState.Failed(e.toUiText())
                }
        }
    }

    /** The bike's parts, then those the entry kept that the bike has since lost. */
    private fun choicesOf(bike: BikeDetail, entry: JournalEntry?): List<BikeComponent> {
        val current = bike.components
        val known = current.mapTo(HashSet()) { it.id }
        return current + entry?.components.orEmpty().filter { it.id !in known }
    }

    // --- the form --------------------------------------------------------------------------------

    private fun edit(transform: (JournalForm) -> JournalForm) {
        mutable.update {
            if (it is JournalEditorUiState.Editing && !it.saving && !it.deleting) {
                it.copy(
                    form = transform(it.form),
                    // The old findings and refusal were about the old form.
                    problems = emptyList(),
                    problem = null,
                    canReload = false,
                )
            } else {
                it
            }
        }
    }

    fun setKind(key: String) = edit {
        if (key in JournalCatalog.kinds) it.copy(kind = key) else it
    }

    fun setTitle(value: String) = edit { it.copy(title = value.take(JournalRules.MAX_TITLE + 1)) }

    fun setBody(value: String) = edit { it.copy(body = value.take(JournalRules.MAX_BODY + 1)) }

    fun setStatus(value: JournalStatus) = edit { it.copy(status = value) }

    fun setPublic(value: Boolean) = edit { it.copy(isPublic = value) }

    fun setEventDate(value: String) = edit { it.copy(eventDate = value.take(MAX_DATE_TEXT)) }

    /** Today, as the person would type it. */
    fun setEventDateToday() = edit { it.copy(eventDate = JournalForm.formatDate(today())) }

    fun setMileage(value: String) = edit { it.copy(mileage = value.take(MAX_NUMBER_TEXT)) }

    /** Choosing the same result again takes it back: "not said" is a choice too. */
    fun setInstallationResult(key: String) = edit {
        if (key !in JournalCatalog.installationResults) it
        else it.copy(installationResult = key.takeIf { k -> k != it.installationResult })
    }

    /** A chosen part is taken back by choosing it; no more than the server's fifty. */
    fun toggleComponent(partId: String) = edit {
        when {
            partId in it.componentIds -> it.copy(componentIds = it.componentIds - partId)
            it.componentIds.size >= JournalRules.MAX_COMPONENTS -> it
            else -> it.copy(componentIds = it.componentIds + partId)
        }
    }

    // --- saving ----------------------------------------------------------------------------------

    fun save() {
        val current = mutable.value as? JournalEditorUiState.Editing ?: return
        if (current.saving || current.deleting) return
        val (draft, unreadable) = current.form.toDraft()
        val original = current.editing
        val problems = (unreadable + JournalRules.check(draft)).distinct()
        if (problems.isNotEmpty()) {
            mutable.value = current.copy(problems = problems, problem = null, canReload = false)
            return
        }
        if (original != null && draft.diff(original.toDraft()).isEmpty) {
            // Nothing differs from what the server holds: nothing to send.
            mutable.value = current.copy(saved = original, problems = emptyList(), problem = null)
            return
        }
        mutable.value = current.copy(saving = true, problems = emptyList(), problem = null)
        viewModelScope.launch {
            try {
                val saved =
                    if (original == null) {
                        // The same form after a lost answer is the same entry; a changed form is a
                        // new one.
                        val key = attempt?.takeIf { it.first == draft }?.second ?: newKey()
                        attempt = draft to key
                        journal.create(bikeId, draft, key)
                    } else {
                        journal.update(
                            original.summary.id,
                            draft.diff(original.toDraft()),
                            original.version,
                        )
                    }
                mutable.value =
                    current.copy(
                        saving = false,
                        saved = saved,
                        // The version a next change names: the one this answer came with.
                        editing = if (original != null) saved else null,
                    )
            } catch (e: DataError.Rejected) {
                mutable.value =
                    current.copy(
                        saving = false,
                        problem = refusal(e),
                        // Another device changed it first: read it again, then decide.
                        canReload = e.status == 412,
                    )
            } catch (_: DataError.NotFound) {
                mutable.value = JournalEditorUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(saving = false, problem = e.toUiText())
            }
        }
    }

    private fun refusal(error: DataError.Rejected): UiText =
        when {
            error.code == EMAIL_NOT_VERIFIED -> UiText.Res(R.string.journal_needs_email)
            error.status == 412 || error.status == 428 ->
                UiText.Res(R.string.journal_changed_elsewhere)
            error.status == 409 && error.userMessage.isNotBlank() -> UiText.Plain(error.userMessage)
            else -> error.toUiText()
        }

    // --- deleting --------------------------------------------------------------------------------

    fun askDelete() {
        mutable.update {
            if (it is JournalEditorUiState.Editing && it.editing != null && !it.saving)
                it.copy(confirmingDelete = true, problem = null)
            else it
        }
    }

    fun cancelDelete() {
        mutable.update {
            if (it is JournalEditorUiState.Editing) it.copy(confirmingDelete = false) else it
        }
    }

    fun confirmDelete() {
        val current = mutable.value as? JournalEditorUiState.Editing ?: return
        val entry = current.editing ?: return
        if (current.deleting || current.saving) return
        mutable.value = current.copy(confirmingDelete = false, deleting = true, problem = null)
        viewModelScope.launch {
            try {
                // An entry already gone elsewhere is deleted all the same (the repository says so).
                journal.delete(entry.summary.id)
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, deleted = true)
            } catch (e: DataError) {
                mutable.value =
                    current.copy(
                        confirmingDelete = false,
                        deleting = false,
                        problem = (e as? DataError.Rejected)?.let(::refusal) ?: e.toUiText(),
                    )
            }
        }
    }

    private companion object {
        /** The server's code for "publishing needs a confirmed address". */
        const val EMAIL_NOT_VERIFIED = "email_verification_required"

        /** A number field never needs more characters than the biggest value it can hold. */
        const val MAX_NUMBER_TEXT = 14

        /** `ДД.ММ.ГГГГ`, with room for a slip. */
        const val MAX_DATE_TEXT = 12
    }
}
