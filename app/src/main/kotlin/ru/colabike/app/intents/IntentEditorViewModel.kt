package ru.colabike.app.intents

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.IntentDraft
import ru.colabike.core.model.IntentFold
import ru.colabike.core.model.IntentProblem
import ru.colabike.core.model.IntentReadiness
import ru.colabike.core.model.IntentRules
import ru.colabike.core.model.IntentVisibility
import ru.colabike.core.model.IntentWindowDraft
import ru.colabike.core.model.IntentsRepository
import ru.colabike.core.model.RideIntent
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.toDraft

/**
 * What the form holds. The windows come first and so does the area; everything else is folded away
 * on the screen. [base] is the passport as it was (all that this form has no field for), so that a
 * change never takes away what the site showed.
 */
@Immutable
data class IntentForm(
    val readiness: IntentReadiness,
    val timeZone: ZoneId,
    val windows: List<IntentWindowDraft>,
    val areaLabel: String,
    val purpose: String,
    val pace: String?,
    val surface: String?,
    val meetNewPeople: Boolean,
    val visibility: IntentVisibility,
    val allowSuggestions: Boolean,
    val base: RidePassport = RidePassport(),
) {
    fun toDraft(): IntentDraft =
        IntentDraft(
            readiness = readiness,
            timeZone = timeZone,
            windows = windows,
            passport =
                base.copy(
                    areaLabel = areaLabel.trim(),
                    purpose = purpose,
                    pace = pace,
                    surface = surface,
                ),
            meetNewPeople = meetNewPeople,
            visibility = visibility,
            allowSuggestions = allowSuggestions,
        )
}

@Immutable
sealed interface IntentEditorUiState {
    data object Loading : IntentEditorUiState

    data class Failed(val message: UiText) : IntentEditorUiState

    /** Not there, not the person's, or already cancelled: there is nothing to change. */
    data object Unavailable : IntentEditorUiState

    data class Editing(
        val form: IntentForm,
        /** The intention being changed; null for a new one. */
        val editing: RideIntent? = null,
        val saving: Boolean = false,
        /** What the form's own rules found, shown once the person tried to save. */
        val problems: List<IntentProblem> = emptyList(),
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
        /** A read of the server's current version is possible: another device changed it first. */
        val canReload: Boolean = false,
        /** Saved; the screen closes and shows this intention. */
        val saved: RideIntent? = null,
    ) : IntentEditorUiState
}

/**
 * The form of an intention: a new one, or a change of the person's own. The window and the area
 * come first, prefilled with the coming Saturday so that a quick "I want to ride" is two taps. A
 * new intention is sent with a key that is kept for as long as the form is the same: a repeat after
 * a lost answer is the same intention, and the server never makes two of it.
 */
class IntentEditorViewModel(
    private val repository: IntentsRepository,
    private val clock: Clock,
    private val phoneZone: ZoneId,
    private val id: String?,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow<IntentEditorUiState>(IntentEditorUiState.Loading)
    val state: StateFlow<IntentEditorUiState> = mutable.asStateFlow()

    /** The last create that was sent and the key it carried. */
    private var attempt: Pair<IntentDraft, String>? = null

    init {
        load()
    }

    fun load() {
        if (id == null) {
            mutable.value = IntentEditorUiState.Editing(newForm())
            return
        }
        mutable.value = IntentEditorUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val intent = repository.get(id)
                    if (intent.editable) {
                        IntentEditorUiState.Editing(formOf(intent), editing = intent)
                    } else {
                        IntentEditorUiState.Unavailable
                    }
                } catch (_: DataError.NotFound) {
                    IntentEditorUiState.Unavailable
                } catch (e: DataError) {
                    IntentEditorUiState.Failed(e.toUiText())
                }
        }
    }

    // --- the form --------------------------------------------------------------------------------

    private fun edit(transform: (IntentForm) -> IntentForm) {
        mutable.update {
            if (it is IntentEditorUiState.Editing && !it.saving) {
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

    fun setReadiness(value: IntentReadiness) = edit { it.copy(readiness = value) }

    fun setAreaLabel(value: String) = edit {
        it.copy(areaLabel = value.take(IntentDraft.MAX_AREA_LABEL + 1))
    }

    fun setPurpose(value: String) = edit { it.copy(purpose = value) }

    /** Choosing the same pace again takes it back: no pace is a choice too. */
    fun setPace(value: String) = edit { it.copy(pace = if (it.pace == value) null else value) }

    fun setSurface(value: String) = edit {
        it.copy(surface = if (it.surface == value) null else value)
    }

    fun setMeetNewPeople(value: Boolean) = edit { it.copy(meetNewPeople = value) }

    fun setVisibility(value: IntentVisibility) = edit { it.copy(visibility = value) }

    fun setAllowSuggestions(value: Boolean) = edit { it.copy(allowSuggestions = value) }

    fun addWindow() = edit {
        if (it.windows.size >= IntentDraft.MAX_WINDOWS) it
        else {
            // After the last one: the next day at the same hours.
            val last = it.windows.lastOrNull() ?: defaultWindow()
            it.copy(
                windows =
                    it.windows + IntentWindowDraft(last.start.plusDays(1), last.end.plusDays(1))
            )
        }
    }

    fun removeWindow(index: Int) = edit {
        if (it.windows.size <= 1 || index !in it.windows.indices) it
        else it.copy(windows = it.windows.filterIndexed { i, _ -> i != index })
    }

    fun setDate(index: Int, date: LocalDate) =
        changeWindow(index) { w ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(w.start.toLocalDate(), date)
            IntentWindowDraft(w.start.plusDays(days), w.end.plusDays(days))
        }

    fun setStart(index: Int, time: LocalTime) =
        changeWindow(index) { w ->
            val length = java.time.Duration.between(w.start, w.end)
            val start = LocalDateTime.of(w.start.toLocalDate(), time)
            // Moving the start moves the end with it, so a window keeps its length.
            IntentWindowDraft(start, start.plus(length))
        }

    fun setEnd(index: Int, time: LocalTime) =
        changeWindow(index) { w ->
            var end = LocalDateTime.of(w.start.toLocalDate(), time)
            // An end that is not after the start is the next day, as in a calendar.
            if (!end.isAfter(w.start)) end = end.plusDays(1)
            IntentWindowDraft(w.start, end, w.startFold, null)
        }

    /** Which of the two repeated times is meant, when the clocks go back in that hour. */
    fun setFold(index: Int, start: Boolean, fold: IntentFold) =
        changeWindow(index) { w ->
            if (start) w.copy(startFold = fold) else w.copy(endFold = fold)
        }

    private fun changeWindow(index: Int, transform: (IntentWindowDraft) -> IntentWindowDraft) =
        edit {
            if (index !in it.windows.indices) it
            else
                it.copy(
                    windows =
                        it.windows.mapIndexed { i, w ->
                            if (i == index) transform(w).clearUnneededFolds(it.timeZone) else w
                        }
                )
        }

    // --- saving ----------------------------------------------------------------------------------

    fun save() {
        val current = mutable.value as? IntentEditorUiState.Editing ?: return
        if (current.saving) return
        val draft = current.form.toDraft()
        val problems = IntentRules.check(draft, clock.instant())
        if (problems.isNotEmpty()) {
            mutable.value = current.copy(problems = problems, problem = null, canReload = false)
            return
        }
        mutable.value = current.copy(saving = true, problems = emptyList(), problem = null)
        viewModelScope.launch {
            try {
                val saved =
                    if (id == null) {
                        // The same form after a lost answer is the same intention; a changed form
                        // is a new one.
                        val key = attempt?.takeIf { it.first == draft }?.second ?: newKey()
                        attempt = draft to key
                        repository.create(draft, key)
                    } else {
                        repository.replace(id, draft, current.editing?.version)
                    }
                mutable.value = current.copy(saving = false, saved = saved, editing = saved)
            } catch (e: DataError.Rejected) {
                mutable.value =
                    current.copy(
                        saving = false,
                        problem =
                            when (e.status) {
                                // Another device changed it first: read it again, then decide.
                                412 -> UiText.Res(R.string.intent_changed_elsewhere)
                                409 -> conflict(e, id != null)
                                else -> e.toUiText()
                            },
                        canReload = e.status == 412,
                    )
            } catch (_: DataError.NotFound) {
                mutable.value = IntentEditorUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(saving = false, problem = e.toUiText())
            }
        }
    }

    private fun conflict(error: DataError.Rejected, replacing: Boolean): UiText =
        when {
            replacing -> UiText.Res(R.string.intent_cancelled_or_full)
            error.userMessage.isNotBlank() -> UiText.Plain(error.userMessage)
            // The person has the most open intentions the server allows.
            else -> UiText.Res(R.string.intent_too_many)
        }

    // --- the first form
    // ---------------------------------------------------------------------------

    private fun defaultWindow(): IntentWindowDraft {
        val today = LocalDate.now(clock.withZone(phoneZone))
        val saturday = today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
        return IntentWindowDraft(
            LocalDateTime.of(saturday, LocalTime.of(10, 0)),
            LocalDateTime.of(saturday, LocalTime.of(13, 0)),
        )
    }

    private fun newForm() =
        IntentForm(
            readiness = IntentReadiness.Ready,
            timeZone = phoneZone,
            windows = listOf(defaultWindow()),
            areaLabel = "",
            purpose = "leisure",
            pace = null,
            surface = null,
            meetNewPeople = false,
            // Private first: publishing to the community is a choice of its own.
            visibility = IntentVisibility.Private,
            allowSuggestions = false,
        )

    private fun formOf(intent: RideIntent): IntentForm {
        val draft = intent.toDraft()
        return IntentForm(
            readiness = draft.readiness,
            timeZone = draft.timeZone,
            windows = draft.windows,
            areaLabel = draft.passport.areaLabel.orEmpty(),
            purpose = draft.passport.purpose ?: "leisure",
            pace = draft.passport.pace,
            surface = draft.passport.surface,
            meetNewPeople = draft.meetNewPeople ?: false,
            visibility = draft.visibility,
            allowSuggestions = draft.allowSuggestions,
            base = draft.passport,
        )
    }
}

/** A fold only means something in the hour the clocks repeat; elsewhere it is dropped. */
private fun IntentWindowDraft.clearUnneededFolds(zone: ZoneId): IntentWindowDraft =
    copy(
        startFold = startFold.takeIf { zone.rules.getValidOffsets(start).size > 1 },
        endFold = endFold.takeIf { zone.rules.getValidOffsets(end).size > 1 },
    )
