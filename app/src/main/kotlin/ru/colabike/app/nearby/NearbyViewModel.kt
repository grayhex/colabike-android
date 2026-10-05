package ru.colabike.app.nearby

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NearbyChange
import ru.colabike.core.model.NearbyRepository
import ru.colabike.core.model.NearbySettings
import ru.colabike.core.model.NearbySource
import ru.colabike.core.model.radiusChoices
import ru.colabike.core.model.startRadius

/** Which of the three lists of kinds a choice belongs to. */
enum class NearbyGroup {
    Purposes,
    Paces,
    Surfaces,
}

/**
 * The area this phone has just read, shown for the person to confirm. It holds the centre of a grid
 * cell and never the point: the place was rounded before it got here. It is in memory only, is not
 * saved with the screen and prints no coordinates.
 */
@Immutable
class DeviceAreaDraft(val longitude: Double, val latitude: Double, val radiusM: Int) {
    fun withRadius(radiusM: Int) = DeviceAreaDraft(longitude, latitude, radiusM)

    override fun toString() = "DeviceAreaDraft(radiusM=$radiusM)"
}

@Immutable
sealed interface NearbyUiState {
    data object Loading : NearbyUiState

    data class Failed(val message: UiText) : NearbyUiState

    /**
     * [settings] are always what the server holds: a switch moves only after the server took the
     * change. A change that fails leaves the old value and a [problem] to announce.
     */
    data class Loaded(
        val settings: NearbySettings,
        val saving: Boolean = false,
        /** The phone is being asked where it is. */
        val locating: Boolean = false,
        /** The place this phone read, waiting for the person's yes. */
        val draft: DeviceAreaDraft? = null,
        /** An area chosen on the site is in force: replacing it needs a yes of its own. */
        val askReplace: Boolean = false,
        /** The outcome of the last change in words, announced to TalkBack; null when none. */
        val notice: UiText? = null,
        val problem: UiText? = null,
    ) : NearbyUiState {
        /** Whether anything is on its way: a second change waits for the first. */
        val busy: Boolean
            get() = saving || locating
    }
}

/**
 * The private area of "rides near me" (cola#343). Everything is the server's: a switch moves after
 * the server took it, a second phone's change is a conflict to read again (never an overwrite
 * nobody saw), and a phone's place is rounded to a grid cell before anything is sent.
 */
class NearbyViewModel(
    private val repository: NearbyRepository,
    private val location: CoarseLocation,
) : ViewModel() {
    private val mutableState = MutableStateFlow<NearbyUiState>(NearbyUiState.Loading)
    val state: StateFlow<NearbyUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = NearbyUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    NearbyUiState.Loaded(repository.settings())
                } catch (e: DataError) {
                    NearbyUiState.Failed(e.toUiText())
                }
        }
    }

    fun dismissNotice() = updateLoaded { it.copy(notice = null, problem = null) }

    // --- the switch, the horizon and the kinds ---------------------------------------------------

    fun setEnabled(on: Boolean) {
        val loaded = loaded() ?: return
        // An operator who switched it off allows only turning off.
        if (on && !loaded.settings.available) return
        change(NearbyChange(enabled = on))
    }

    fun setHorizon(days: Int) = change(NearbyChange(horizonDays = days))

    /** Adds the kind to the ones wanted, or takes it out; none chosen means any. */
    fun toggle(group: NearbyGroup, key: String) {
        val preferences = loaded()?.settings?.preferences ?: return
        val current =
            when (group) {
                NearbyGroup.Purposes -> preferences.purposes
                NearbyGroup.Paces -> preferences.paces
                NearbyGroup.Surfaces -> preferences.surfaces
            }
        val next = if (key in current) current - key else current + key
        change(
            when (group) {
                NearbyGroup.Purposes -> NearbyChange(purposes = next)
                NearbyGroup.Paces -> NearbyChange(paces = next)
                NearbyGroup.Surfaces -> NearbyChange(surfaces = next)
            }
        )
    }

    private fun change(change: NearbyChange) {
        val loaded = loaded() ?: return
        if (loaded.busy || change.isEmpty) return
        run(R.string.nearby_saved) { repository.change(change, it.settings.version) }
    }

    // --- the area from this phone ----------------------------------------------------------------

    /** Reads the approximate place once (the permission is already given) and offers the area. */
    fun locate() {
        val loaded = loaded() ?: return
        if (loaded.busy || !loaded.settings.available) return
        updateLoaded { it.copy(locating = true, notice = null, problem = null, draft = null) }
        viewModelScope.launch {
            val result = location.current()
            updateLoaded { current ->
                when (result) {
                    is CoarseResult.Located -> {
                        val limits = current.settings.limits
                        val (longitude, latitude) =
                            limits.grid.centerOf(result.fix.longitude, result.fix.latitude)
                        current.copy(
                            locating = false,
                            draft =
                                DeviceAreaDraft(
                                    longitude,
                                    latitude,
                                    limits.startRadius(current.settings.area?.radiusM),
                                ),
                        )
                    }
                    CoarseResult.NoPermission ->
                        current.copy(
                            locating = false,
                            problem = UiText.Res(R.string.nearby_location_denied),
                        )
                    CoarseResult.ServiceOff ->
                        current.copy(
                            locating = false,
                            problem = UiText.Res(R.string.nearby_location_off),
                        )
                    CoarseResult.Unavailable ->
                        current.copy(
                            locating = false,
                            problem = UiText.Res(R.string.nearby_location_unavailable),
                        )
                }
            }
        }
    }

    /** The system's question was answered with a no. */
    fun permissionDenied() = updateLoaded {
        it.copy(problem = UiText.Res(R.string.nearby_location_denied))
    }

    fun setDraftRadius(radiusM: Int) {
        updateLoaded { current ->
            val draft = current.draft ?: return@updateLoaded current
            if (radiusM !in current.settings.limits.radiusChoices()) current
            else current.copy(draft = draft.withRadius(radiusM))
        }
    }

    fun discardDraft() = updateLoaded { it.copy(draft = null, askReplace = false) }

    /**
     * Sends the area. An area chosen on the site is replaced only after a yes of its own
     * ([askReplace]), and the server holds to it too (409), so that two devices never overwrite
     * each other silently.
     */
    fun confirmDraft(replaceManual: Boolean = false) {
        val loaded = loaded() ?: return
        val draft = loaded.draft ?: return
        if (loaded.busy) return
        val replacing = loaded.settings.source == NearbySource.Manual
        if (replacing && !replaceManual) {
            updateLoaded { it.copy(askReplace = true) }
            return
        }
        run(R.string.nearby_area_saved, clearDraft = true) {
            repository.confirmDeviceArea(
                longitude = draft.longitude,
                latitude = draft.latitude,
                radiusM = draft.radiusM,
                replaceManual = replaceManual || replacing,
                version = it.settings.version,
            )
        }
    }

    fun cancelReplace() = updateLoaded { it.copy(askReplace = false) }

    fun removeArea() {
        val loaded = loaded() ?: return
        if (loaded.busy || loaded.settings.area == null) return
        run(R.string.nearby_area_removed) { repository.removeArea(it.settings.version) }
    }

    /** Opts out of all of it: the area, the switch and the kinds. */
    fun forget() {
        val loaded = loaded() ?: return
        if (loaded.busy) return
        updateLoaded { it.copy(saving = true, notice = null, problem = null, draft = null) }
        viewModelScope.launch {
            try {
                repository.forget()
                val fresh = repository.settings()
                updateLoaded {
                    it.copy(
                        settings = fresh,
                        saving = false,
                        notice = UiText.Res(R.string.nearby_forgotten),
                    )
                }
            } catch (e: DataError) {
                updateLoaded { it.copy(saving = false, problem = e.toUiText()) }
            }
        }
    }

    // --- one change at a time -----------------------------------------------------------------

    private fun run(
        notice: Int,
        clearDraft: Boolean = false,
        block: suspend (NearbyUiState.Loaded) -> NearbySettings,
    ) {
        val before = loaded() ?: return
        updateLoaded { it.copy(saving = true, notice = null, problem = null, askReplace = false) }
        viewModelScope.launch {
            try {
                val settings = block(before)
                updateLoaded {
                    it.copy(
                        settings = settings,
                        saving = false,
                        notice = UiText.Res(notice),
                        draft = if (clearDraft) null else it.draft,
                    )
                }
            } catch (e: DataError.Rejected) {
                when (e.status) {
                    // Another phone or the site changed it meanwhile: show what is there now.
                    412 -> reloadAfter(UiText.Res(R.string.nearby_changed_elsewhere))
                    // The operator switched it off since it was read.
                    503 -> reloadAfter(UiText.Res(R.string.nearby_unavailable_now))
                    // An area from the site is in force and the server holds to the person's yes.
                    409 ->
                        updateLoaded {
                            it.copy(saving = false, askReplace = it.draft != null)
                        }
                    else -> updateLoaded { it.copy(saving = false, problem = e.toUiText()) }
                }
            } catch (e: DataError) {
                updateLoaded { it.copy(saving = false, problem = e.toUiText()) }
            }
        }
    }

    private suspend fun reloadAfter(problem: UiText) {
        val fresh =
            try {
                repository.settings()
            } catch (_: DataError) {
                null
            }
        updateLoaded {
            it.copy(
                settings = fresh ?: it.settings,
                saving = false,
                draft = if (fresh != null) null else it.draft,
                problem = problem,
            )
        }
    }

    private fun loaded() = state.value as? NearbyUiState.Loaded

    private fun updateLoaded(transform: (NearbyUiState.Loaded) -> NearbyUiState.Loaded) {
        mutableState.update { if (it is NearbyUiState.Loaded) transform(it) else it }
    }
}
