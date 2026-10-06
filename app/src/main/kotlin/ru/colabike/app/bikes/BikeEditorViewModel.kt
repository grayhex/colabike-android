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
import ru.colabike.core.model.BikeCatalog
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeDraft
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeProblem
import ru.colabike.core.model.BikeRules
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ClassificationDraft
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PriceVisibility
import ru.colabike.core.model.diff
import ru.colabike.core.model.toDraft

/**
 * What the form holds, as typed: numbers are text until the person saves, so that "12," or an empty
 * field is never turned into a value behind their back. [toDraft] reads it the way the server will.
 */
@Immutable
data class BikeForm(
    val name: String = "",
    val brand: String = "",
    val model: String = "",
    val trim: String = "",
    val year: String = "",
    val classification: ClassificationDraft = ClassificationDraft(category = ""),
    val description: String = "",
    val color: String = "",
    val size: String = "",
    val weight: String = "",
    val mileage: String = "",
    val manufacturerUrl: String = "",
    val price: String = "",
    val priceVisibility: PriceVisibility = PriceVisibility(),
    val isFormer: Boolean = false,
    // Private first: showing a bike to everyone is a choice of its own, never what saving does.
    val isPublic: Boolean = false,
) {
    /** The draft this text means, and what is wrong with the numbers that are not numbers. */
    fun toDraft(): Pair<BikeDraft, List<BikeProblem>> {
        val problems = mutableListOf<BikeProblem>()
        val yearText = year.trim()
        val parsedYear =
            when {
                yearText.isEmpty() -> null
                YEAR.matches(yearText) -> yearText.toInt()
                else -> {
                    problems += BikeProblem.YearOutOfRange
                    null
                }
            }
        val weightText = weight.trim()
        val parsedWeight =
            when {
                weightText.isEmpty() -> null
                else ->
                    decimal(weightText)
                        ?: run {
                            problems += BikeProblem.WeightInvalid
                            null
                        }
            }
        val mileageText = mileage.trim()
        val parsedMileage =
            when {
                mileageText.isEmpty() -> 0
                DIGITS.matches(mileageText) -> mileageText.toInt()
                else -> {
                    problems += BikeProblem.MileageInvalid
                    0
                }
            }
        val priceText = price.trim()
        val parsedPrice =
            when {
                priceText.isEmpty() -> null
                else ->
                    decimal(priceText)
                        ?: run {
                            problems += BikeProblem.PriceInvalid
                            null
                        }
            }
        val draft =
            BikeDraft(
                name = name,
                brand = brand,
                model = model,
                trim = trim,
                year = parsedYear,
                classification = classification,
                description = description,
                color = color,
                size = size,
                weightKg = parsedWeight,
                mileageKm = parsedMileage,
                manufacturerUrl = manufacturerUrl,
                priceRub = parsedPrice,
                priceVisibility = priceVisibility,
                isFormer = isFormer,
                isPublic = isPublic,
            )
        return draft to problems
    }

    companion object {
        private val YEAR = Regex("[0-9]{1,4}")
        private val DIGITS = Regex("[0-9]{1,8}")
        private val DECIMAL = Regex("[0-9]{1,9}([.,][0-9]{1,3})?")

        private fun decimal(text: String): Double? =
            text.takeIf { DECIMAL.matches(it) }?.replace(',', '.')?.toDoubleOrNull()

        /** A number as a person writes it: no trailing zeros and no exponent. */
        private fun plain(value: Double): String =
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

        fun of(bike: BikeDetail): BikeForm {
            val draft = bike.toDraft()
            return BikeForm(
                name = draft.name,
                brand = draft.brand,
                model = draft.model,
                trim = draft.trim,
                year = draft.year?.toString().orEmpty(),
                classification = draft.classification,
                description = draft.description,
                color = draft.color,
                size = draft.size,
                weight = draft.weightKg?.let(::plain).orEmpty(),
                mileage = if (draft.mileageKm == 0) "" else draft.mileageKm.toString(),
                manufacturerUrl = draft.manufacturerUrl,
                price = draft.priceRub?.let(::plain).orEmpty(),
                priceVisibility = draft.priceVisibility,
                isFormer = draft.isFormer,
                isPublic = draft.isPublic,
            )
        }
    }
}

@Immutable
sealed interface BikeEditorUiState {
    data object Loading : BikeEditorUiState

    data class Failed(val message: UiText) : BikeEditorUiState

    /** Not there or not the person's: there is nothing to change. */
    data object Unavailable : BikeEditorUiState

    data class Editing(
        val form: BikeForm,
        /** The bike being changed, as the server last gave it; null for a new one. */
        val editing: BikeDetail? = null,
        val saving: Boolean = false,
        /** What the form's own rules found, shown once the person tried to save. */
        val problems: List<BikeProblem> = emptyList(),
        /** The server's refusal or a failure, in words; null when there is none. */
        val problem: UiText? = null,
        /** The server holds a newer version: reading it again is possible (and loses the edit). */
        val canReload: Boolean = false,
        /** Saved; the screen closes and shows this bike. */
        val saved: BikeDetail? = null,
        /** The person asked to delete and has not answered the question yet. */
        val confirmingDelete: Boolean = false,
        val deleting: Boolean = false,
        /** Deleted; the screen closes and the bike's page with it. */
        val deleted: Boolean = false,
    ) : BikeEditorUiState
}

/**
 * The form of a bike: a new one, or a change of the person's own. A new bike is sent with a key
 * that is kept for as long as the form is the same, so a repeat after a lost answer is the same
 * bike and the server never makes two of it. A change names the version that was read and sends
 * only the fields that differ from it.
 */
class BikeEditorViewModel(
    private val repository: BikesRepository,
    private val id: BikeId?,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val mutable = MutableStateFlow<BikeEditorUiState>(BikeEditorUiState.Loading)
    val state: StateFlow<BikeEditorUiState> = mutable.asStateFlow()

    /** The last create that was sent and the key it carried. */
    private var attempt: Pair<BikeDraft, String>? = null

    init {
        load()
    }

    fun load() {
        if (id == null) {
            mutable.value = BikeEditorUiState.Editing(BikeForm())
            return
        }
        mutable.value = BikeEditorUiState.Loading
        viewModelScope.launch {
            mutable.value =
                try {
                    val bike = repository.bike(id)
                    // Only the owner's bike has a version to change; anyone else's is not editable.
                    if (bike.summary.isOwner && bike.version != null) {
                        BikeEditorUiState.Editing(BikeForm.of(bike), editing = bike)
                    } else {
                        BikeEditorUiState.Unavailable
                    }
                } catch (_: DataError.NotFound) {
                    BikeEditorUiState.Unavailable
                } catch (e: DataError) {
                    BikeEditorUiState.Failed(e.toUiText())
                }
        }
    }

    // --- the form --------------------------------------------------------------------------------

    private fun edit(transform: (BikeForm) -> BikeForm) {
        mutable.update {
            if (it is BikeEditorUiState.Editing && !it.saving && !it.deleting) {
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

    fun setName(value: String) = edit { it.copy(name = value.take(BikeRules.MAX_NAME + 1)) }

    fun setBrand(value: String) = edit { it.copy(brand = value.take(BikeRules.MAX_BRAND + 1)) }

    fun setModel(value: String) = edit { it.copy(model = value.take(BikeRules.MAX_MODEL + 1)) }

    fun setTrim(value: String) = edit { it.copy(trim = value.take(BikeRules.MAX_TRIM + 1)) }

    fun setYear(value: String) = edit { it.copy(year = value.take(MAX_NUMBER_TEXT)) }

    fun setDescription(value: String) = edit {
        it.copy(description = value.take(BikeRules.MAX_DESCRIPTION + 1))
    }

    fun setColor(value: String) = edit { it.copy(color = value.take(BikeRules.MAX_COLOR + 1)) }

    fun setSize(value: String) = edit { it.copy(size = value.take(BikeRules.MAX_SIZE + 1)) }

    fun setWeight(value: String) = edit { it.copy(weight = value.take(MAX_NUMBER_TEXT)) }

    fun setMileage(value: String) = edit { it.copy(mileage = value.take(MAX_NUMBER_TEXT)) }

    fun setManufacturerUrl(value: String) = edit {
        it.copy(manufacturerUrl = value.take(BikeRules.MAX_LINK + 1))
    }

    fun setPrice(value: String) = edit { it.copy(price = value.take(MAX_NUMBER_TEXT)) }

    fun setPriceVisibility(value: PriceVisibility) = edit { it.copy(priceVisibility = value) }

    fun setFormer(value: Boolean) = edit { it.copy(isFormer = value) }

    fun setPublic(value: Boolean) = edit { it.copy(isPublic = value) }

    /** A new category takes its own subtypes: one of another category is not kept. */
    fun setCategory(key: String) = edit {
        val current = it.classification
        if (key == current.category || key !in BikeCatalog.categories) it
        else
            it.copy(
                classification =
                    current.copy(
                        category = key,
                        subtype =
                            current.subtype?.takeIf { s ->
                                s in BikeCatalog.subtypes[key].orEmpty()
                            },
                    )
            )
    }

    /** Choosing the same subtype again takes it back: none is a choice too. */
    fun setSubtype(key: String) = edit {
        val current = it.classification
        if (key !in BikeCatalog.subtypes[current.category].orEmpty()) it
        else
            it.copy(
                classification = current.copy(subtype = key.takeIf { k -> k != current.subtype })
            )
    }

    fun setSuspension(key: String) = edit {
        val current = it.classification
        if (key !in BikeCatalog.suspensions) it
        else
            it.copy(
                classification =
                    current.copy(suspension = key.takeIf { k -> k != current.suspension })
            )
    }

    fun setConstruction(key: String) = edit {
        val current = it.classification
        if (key !in BikeCatalog.constructions) it
        else
            it.copy(
                classification =
                    current.copy(construction = key.takeIf { k -> k != current.construction })
            )
    }

    /** Up to three uses; a fourth is not added, and a chosen one is taken back by choosing it. */
    fun toggleUse(key: String) = edit {
        val current = it.classification
        when {
            key !in BikeCatalog.uses -> it
            key in current.uses -> it.copy(classification = current.copy(uses = current.uses - key))
            current.uses.size >= BikeCatalog.MAX_USES -> it
            else -> it.copy(classification = current.copy(uses = current.uses + key))
        }
    }

    fun setElectric(value: Boolean) = edit {
        it.copy(classification = it.classification.copy(electric = value))
    }

    fun setFatbike(value: Boolean) = edit {
        it.copy(classification = it.classification.copy(fatbike = value))
    }

    // --- saving ----------------------------------------------------------------------------------

    fun save() {
        val current = mutable.value as? BikeEditorUiState.Editing ?: return
        if (current.saving || current.deleting) return
        val (draft, unreadable) = current.form.toDraft()
        val problems = (unreadable + BikeRules.check(draft, creating = id == null)).distinct()
        if (problems.isNotEmpty()) {
            mutable.value = current.copy(problems = problems, problem = null, canReload = false)
            return
        }
        val original = current.editing
        if (original != null && draft.diff(original.toDraft()).isEmpty) {
            // Nothing differs from what the server holds: nothing to send, the page stays as it is.
            mutable.value = current.copy(saved = original, problems = emptyList(), problem = null)
            return
        }
        mutable.value = current.copy(saving = true, problems = emptyList(), problem = null)
        viewModelScope.launch {
            try {
                val saved =
                    if (original == null) {
                        // The same form after a lost answer is the same bike; a changed form is a
                        // new one.
                        val key = attempt?.takeIf { it.first == draft }?.second ?: newKey()
                        attempt = draft to key
                        repository.create(draft, key)
                    } else {
                        repository.update(
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
                mutable.value = BikeEditorUiState.Unavailable
            } catch (e: DataError) {
                mutable.value = current.copy(saving = false, problem = e.toUiText())
            }
        }
    }

    private fun refusal(error: DataError.Rejected): UiText =
        when {
            error.code == EMAIL_NOT_VERIFIED -> UiText.Res(R.string.bike_needs_email)
            error.status == 412 || error.status == 428 ->
                UiText.Res(R.string.bike_changed_elsewhere)
            error.status == 409 && error.userMessage.isNotBlank() -> UiText.Plain(error.userMessage)
            else -> error.toUiText()
        }

    // --- deleting --------------------------------------------------------------------------------

    fun askDelete() {
        mutable.update {
            if (it is BikeEditorUiState.Editing && it.editing != null && !it.saving)
                it.copy(confirmingDelete = true, problem = null)
            else it
        }
    }

    fun cancelDelete() {
        mutable.update {
            if (it is BikeEditorUiState.Editing) it.copy(confirmingDelete = false) else it
        }
    }

    fun confirmDelete() {
        val current = mutable.value as? BikeEditorUiState.Editing ?: return
        val bike = current.editing ?: return
        if (current.deleting || current.saving) return
        mutable.value = current.copy(confirmingDelete = false, deleting = true, problem = null)
        viewModelScope.launch {
            try {
                repository.delete(bike.summary.id)
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, deleted = true)
            } catch (_: DataError.NotFound) {
                // Already gone elsewhere: what the person wanted has happened.
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, deleted = true)
            } catch (e: DataError.Rejected) {
                mutable.value =
                    current.copy(
                        confirmingDelete = false,
                        deleting = false,
                        problem =
                            if (e.status == 409 && e.userMessage.isBlank())
                                UiText.Res(R.string.bike_delete_has_rides)
                            else refusal(e),
                    )
            } catch (e: DataError) {
                mutable.value =
                    current.copy(confirmingDelete = false, deleting = false, problem = e.toUiText())
            }
        }
    }

    private companion object {
        /** The server's code for "publishing needs a confirmed address". */
        const val EMAIL_NOT_VERIFIED = "email_verification_required"

        /** A number field never needs more characters than the biggest value it can hold. */
        const val MAX_NUMBER_TEXT = 14
    }
}
