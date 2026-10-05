package ru.colabike.app.notifications.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.push.PushSync
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.DataError
import ru.colabike.core.model.MuteRef
import ru.colabike.core.model.NotificationMute
import ru.colabike.core.model.NotificationSettings
import ru.colabike.core.model.NotificationSettingsChange
import ru.colabike.core.model.NotificationSettingsRepository
import ru.colabike.core.model.PeopleRepository

/** A change the server did not take, kept so that "try again" sends exactly it. */
@Immutable data class SettingsProblem(val message: UiText, val retry: NotificationSettingsChange?)

@Immutable
sealed interface NotificationSettingsUiState {
    data object Loading : NotificationSettingsUiState

    data class Failed(val message: UiText) : NotificationSettingsUiState

    /**
     * [settings] are always what the server holds: a switch moves only after the server took the
     * change, and until then [saving] says it is on its way. A change that fails leaves the old
     * value and a [problem] to announce, never a "saved" that did not happen.
     */
    data class Loaded(
        val settings: NotificationSettings,
        /** The phone's own side; null until it was read. */
        val device: DeviceNotificationsState? = null,
        val saving: Boolean = false,
        /** The outcome of the last change in words, announced to TalkBack; null when none. */
        val notice: UiText? = null,
        val problem: SettingsProblem? = null,
        /**
         * The time zone of this phone: what quiet hours start from and what is offered to adopt.
         */
        val phoneZone: ZoneId = ZoneId.of("UTC"),
    ) : NotificationSettingsUiState
}

/**
 * The account's notification settings and the state of this phone. The account part is the server's
 * (the same as on the site); the phone part is read from the system. One change at a time.
 */
class NotificationSettingsViewModel(
    private val repository: NotificationSettingsRepository,
    private val people: PeopleRepository,
    private val device: DeviceNotifications,
    private val clock: Clock,
    private val phoneZone: ZoneId = ZoneId.systemDefault(),
    /** Makes the phone's registration at the server follow what the person just chose. */
    private val pushSync: PushSync = PushSync.None,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow<NotificationSettingsUiState>(NotificationSettingsUiState.Loading)
    val state: StateFlow<NotificationSettingsUiState> = mutableState.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutableState.value = NotificationSettingsUiState.Loading
        viewModelScope.launch {
            val phone = runCatching { device.state() }.getOrNull()
            mutableState.value =
                try {
                    NotificationSettingsUiState.Loaded(
                        settings = repository.settings(),
                        device = phone,
                        phoneZone = phoneZone,
                    )
                } catch (e: DataError) {
                    NotificationSettingsUiState.Failed(e.toUiText())
                }
        }
    }

    /** Reads the phone again: the person may have been to the system settings. */
    fun refreshDevice() {
        viewModelScope.launch {
            val phone = runCatching { device.state() }.getOrNull() ?: return@launch
            updateLoaded { it.copy(device = phone) }
        }
    }

    /** The system's question was put; the answer comes with the next [refreshDevice]. */
    fun permissionAsked() {
        device.markAsked()
        refreshDevice()
        // A yes to the system's question is what lets this phone be registered.
        pushSync.request(true)
    }

    fun dismissNotice() = updateLoaded { it.copy(notice = null, problem = null) }

    fun retry() {
        val retry = (state.value as? NotificationSettingsUiState.Loaded)?.problem?.retry ?: return
        apply(retry)
    }

    // --- the account's switches ---------------------------------------------------------------

    fun setPush(on: Boolean) {
        val loaded = loaded() ?: return
        // A server that cannot carry push yet is not asked to: the switch is off and says why.
        if (on && !loaded.settings.channels.pushAvailable) return
        apply(NotificationSettingsChange(pushEnabled = on))
    }

    fun setEmail(on: Boolean) {
        val channels = loaded()?.settings?.channels ?: return
        if (on && !(channels.emailAvailable && channels.emailVerified)) return
        apply(NotificationSettingsChange(emailEnabled = on))
    }

    fun setCategoryPush(key: String, on: Boolean) =
        apply(NotificationSettingsChange(categoryPush = mapOf(key to on)))

    fun setCategoryEmail(key: String, on: Boolean) =
        apply(NotificationSettingsChange(categoryEmail = mapOf(key to on)))

    fun setReminders(on: Boolean) = apply(NotificationSettingsChange(reminders = on))

    // --- quiet hours and the pause ------------------------------------------------------------

    fun setQuietEnabled(on: Boolean) {
        val loaded = loaded() ?: return
        // Quiet hours are read on the person's own clock, so the zone of this phone is said with
        // the first switch-on; the person can change it later.
        apply(
            NotificationSettingsChange(
                quietEnabled = on,
                timeZone = loaded.phoneZone.takeIf { on && loaded.settings.timeZone == null },
            )
        )
    }

    fun setQuietFrom(time: LocalTime) = apply(NotificationSettingsChange(quietFrom = time))

    fun setQuietTo(time: LocalTime) = apply(NotificationSettingsChange(quietTo = time))

    fun setQuietCancellations(on: Boolean) =
        apply(NotificationSettingsChange(quietAllowCancellations = on))

    fun usePhoneZone() {
        val loaded = loaded() ?: return
        apply(NotificationSettingsChange(timeZone = loaded.phoneZone))
    }

    fun pause(duration: Duration) =
        apply(NotificationSettingsChange(pauseUntil = clock.instant().plus(duration)))

    fun resume() = apply(NotificationSettingsChange(resume = true))

    // --- whose plans, and what is muted -------------------------------------------------------

    fun setCircle(mode: CircleMode) {
        if (mode == CircleMode.Unknown) return
        apply(NotificationSettingsChange(circleMode = mode))
    }

    fun setConsidering(on: Boolean) = apply(NotificationSettingsChange(considering = on))

    fun removeMember(id: String) = apply(NotificationSettingsChange(circleRemove = listOf(id)))

    fun removeMute(mute: NotificationMute) =
        apply(NotificationSettingsChange(muteRemove = listOf(MuteRef(mute.kind, mute.id))))

    /** Adds a person to the circle by the name they go by; the circle holds ids. */
    fun addMember(username: String) {
        val name = username.trim().removePrefix("@")
        if (name.isEmpty() || loaded()?.saving != false) return
        updateLoaded { it.copy(saving = true, notice = null, problem = null) }
        viewModelScope.launch {
            val id =
                try {
                    people.profile(name).person.id.value
                } catch (e: DataError.NotFound) {
                    updateLoaded {
                        it.copy(
                            saving = false,
                            problem =
                                SettingsProblem(
                                    UiText.Res(R.string.notif_settings_no_such_person),
                                    null,
                                ),
                        )
                    }
                    return@launch
                } catch (e: DataError) {
                    updateLoaded {
                        it.copy(saving = false, problem = SettingsProblem(e.toUiText(), null))
                    }
                    return@launch
                }
            updateLoaded { it.copy(saving = false) }
            apply(
                NotificationSettingsChange(circleAdd = listOf(id)),
                R.string.notif_settings_member_added,
            )
        }
    }

    // --- one change at a time -------------------------------------------------------------------

    private fun apply(
        change: NotificationSettingsChange,
        notice: Int = R.string.notif_settings_saved,
    ) {
        val before = loaded() ?: return
        if (before.saving || change.isEmpty) return
        updateLoaded { it.copy(saving = true, notice = null, problem = null) }
        viewModelScope.launch {
            val outcome =
                try {
                    Outcome.Saved(repository.change(change))
                } catch (e: DataError) {
                    Outcome.Failed(e.toUiText())
                }
            // The consent and the categories of push decide whether the phone is registered.
            if (outcome is Outcome.Saved) pushSync.request(true)
            updateLoaded { current ->
                when (outcome) {
                    is Outcome.Saved ->
                        current.copy(
                            settings = outcome.settings,
                            saving = false,
                            notice = UiText.Res(notice),
                        )
                    is Outcome.Failed ->
                        current.copy(
                            saving = false,
                            problem = SettingsProblem(outcome.message, change),
                        )
                }
            }
        }
    }

    private fun loaded() = state.value as? NotificationSettingsUiState.Loaded

    private fun updateLoaded(
        transform: (NotificationSettingsUiState.Loaded) -> NotificationSettingsUiState.Loaded
    ) {
        mutableState.update { if (it is NotificationSettingsUiState.Loaded) transform(it) else it }
    }

    private sealed interface Outcome {
        data class Saved(val settings: NotificationSettings) : Outcome

        data class Failed(val message: UiText) : Outcome
    }
}
