package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.notifications.settings.NotificationSettingsUiState
import ru.colabike.app.notifications.settings.NotificationSettingsViewModel
import ru.colabike.app.notifications.settings.OsPermission
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.DataError
import ru.colabike.core.model.MuteKind
import ru.colabike.core.model.NotificationMute
import ru.colabike.core.model.NotificationSettingsChange

class NotificationSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val now = Instant.parse("2026-10-03T20:00:00Z")
    private val moscow = ZoneId.of("Europe/Moscow")
    private val repository = FakeNotificationSettings()
    private val people = FakePeople()
    private val device = FakeDeviceNotifications()

    private fun viewModel() =
        NotificationSettingsViewModel(
            repository = repository,
            people = people,
            device = device,
            clock = Clock.fixed(now, ZoneOffset.UTC),
            phoneZone = moscow,
        )

    private fun loaded(vm: NotificationSettingsViewModel) =
        vm.state.value as NotificationSettingsUiState.Loaded

    private fun withPush() {
        repository.current =
            repository.current.copy(
                channels = repository.current.channels.copy(pushAvailable = true)
            )
    }

    @Test
    fun `it shows the account's settings next to the state of this phone`() = runTest {
        val vm = viewModel()

        assertThat(loaded(vm).settings).isEqualTo(defaultNotificationSettings)
        assertThat(loaded(vm).device).isEqualTo(freshPhone)
        assertThat(loaded(vm).phoneZone).isEqualTo(moscow)
        assertThat(loaded(vm).saving).isFalse()
    }

    @Test
    fun `a failed load says why and loading again recovers`() = runTest {
        repository.loadError = DataError.Offline(IOException())
        val vm = viewModel()

        assertThat(vm.state.value)
            .isEqualTo(NotificationSettingsUiState.Failed(UiText.Res(R.string.error_offline)))

        repository.loadError = null
        vm.load()

        assertThat(vm.state.value).isInstanceOf(NotificationSettingsUiState.Loaded::class.java)
    }

    @Test
    fun `a saved change shows what the server holds and says so`() = runTest {
        val vm = viewModel()

        vm.setReminders(false)

        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(reminders = false))
        assertThat(loaded(vm).settings.reminders).isFalse()
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.notif_settings_saved))
        assertThat(loaded(vm).problem).isNull()
        assertThat(loaded(vm).saving).isFalse()
    }

    @Test
    fun `a change the server refused keeps the old value and never says saved`() = runTest {
        val vm = viewModel()
        repository.failChange = DataError.Server(503, null)

        vm.setReminders(false)

        assertThat(loaded(vm).settings.reminders).isTrue()
        assertThat(loaded(vm).notice).isNull()
        assertThat(loaded(vm).problem).isNotNull()
        assertThat(loaded(vm).problem!!.retry)
            .isEqualTo(NotificationSettingsChange(reminders = false))
        assertThat(loaded(vm).saving).isFalse()
    }

    @Test
    fun `trying again sends exactly the change that failed`() = runTest {
        val vm = viewModel()
        repository.failChange = DataError.Offline(IOException())
        vm.setConsidering(true)

        vm.retry()

        assertThat(repository.changes)
            .containsExactly(
                NotificationSettingsChange(considering = true),
                NotificationSettingsChange(considering = true),
            )
        assertThat(loaded(vm).settings.considering).isTrue()
        assertThat(loaded(vm).problem).isNull()
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.notif_settings_saved))
    }

    @Test
    fun `a change on its way holds the next one back`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        vm.setReminders(false)
        assertThat(loaded(vm).saving).isTrue()
        vm.setConsidering(true)
        assertThat(repository.changes).hasSize(1)

        gate.complete(Unit)

        assertThat(loaded(vm).saving).isFalse()
        assertThat(loaded(vm).settings.reminders).isFalse()
        assertThat(loaded(vm).settings.considering).isFalse()
    }

    @Test
    fun `push cannot be switched on while the server cannot carry it`() = runTest {
        val vm = viewModel()

        vm.setPush(true)

        assertThat(repository.changes).isEmpty()
        assertThat(loaded(vm).settings.channels.pushEnabled).isFalse()
    }

    @Test
    fun `push is switched on when the server can carry it, and off whenever`() = runTest {
        withPush()
        val vm = viewModel()

        vm.setPush(true)
        assertThat(loaded(vm).settings.channels.pushEnabled).isTrue()
        vm.setPush(false)

        assertThat(repository.changes)
            .containsExactly(
                NotificationSettingsChange(pushEnabled = true),
                NotificationSettingsChange(pushEnabled = false),
            )
            .inOrder()
        assertThat(loaded(vm).settings.channels.pushEnabled).isFalse()
    }

    @Test
    fun `mail needs an address the person has confirmed`() = runTest {
        repository.current =
            repository.current.copy(
                channels = repository.current.channels.copy(emailVerified = false)
            )
        val vm = viewModel()

        vm.setEmail(true)

        assertThat(repository.changes).isEmpty()
    }

    @Test
    fun `a category's switches name the category by its key`() = runTest {
        val vm = viewModel()

        vm.setCategoryEmail("rides", true)
        vm.setCategoryPush("plans", false)

        assertThat(repository.changes)
            .containsExactly(
                NotificationSettingsChange(categoryEmail = mapOf("rides" to true)),
                NotificationSettingsChange(categoryPush = mapOf("plans" to false)),
            )
            .inOrder()
        assertThat(loaded(vm).settings.categories.first { it.key == "rides" }.email.enabled)
            .isTrue()
    }

    @Test
    fun `a pause runs from now for as long as the person chose`() = runTest {
        val vm = viewModel()

        vm.pause(Duration.ofHours(8))

        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(pauseUntil = now.plus(Duration.ofHours(8))))
        assertThat(loaded(vm).settings.pausedUntil).isEqualTo(now.plus(Duration.ofHours(8)))
    }

    @Test
    fun `lifting the pause asks to resume, not to set nothing`() = runTest {
        val vm = viewModel()
        vm.pause(Duration.ofHours(1))

        vm.resume()

        assertThat(repository.changes.last()).isEqualTo(NotificationSettingsChange(resume = true))
        assertThat(loaded(vm).settings.pausedUntil).isNull()
    }

    @Test
    fun `quiet hours start on the clock of this phone when no zone was said`() = runTest {
        val vm = viewModel()

        vm.setQuietEnabled(true)

        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(quietEnabled = true, timeZone = moscow))
        assertThat(loaded(vm).settings.timeZone).isEqualTo(moscow)
        assertThat(loaded(vm).settings.quietHours.enabled).isTrue()
    }

    @Test
    fun `quiet hours keep a zone the person already chose`() = runTest {
        repository.current = repository.current.copy(timeZone = ZoneId.of("Asia/Yekaterinburg"))
        val vm = viewModel()

        vm.setQuietEnabled(true)

        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(quietEnabled = true, timeZone = null))
        assertThat(loaded(vm).settings.timeZone).isEqualTo(ZoneId.of("Asia/Yekaterinburg"))
    }

    @Test
    fun `the window's ends and the cancellation choice are changed one at a time`() = runTest {
        val vm = viewModel()

        vm.setQuietFrom(LocalTime.of(23, 30))
        vm.setQuietTo(LocalTime.of(6, 0))
        vm.setQuietCancellations(true)

        val quiet = loaded(vm).settings.quietHours
        assertThat(quiet.from).isEqualTo(LocalTime.of(23, 30))
        assertThat(quiet.to).isEqualTo(LocalTime.of(6, 0))
        assertThat(quiet.allowCancellations).isTrue()
    }

    @Test
    fun `the phone's zone can be adopted later`() = runTest {
        repository.current = repository.current.copy(timeZone = ZoneId.of("Asia/Yekaterinburg"))
        val vm = viewModel()

        vm.usePhoneZone()

        assertThat(loaded(vm).settings.timeZone).isEqualTo(moscow)
    }

    @Test
    fun `a circle mode the app does not know is neither offered nor sent`() = runTest {
        val vm = viewModel()

        vm.setCircle(CircleMode.Unknown)
        vm.setCircle(CircleMode.Selected)

        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(circleMode = CircleMode.Selected))
        assertThat(loaded(vm).settings.circleMode).isEqualTo(CircleMode.Selected)
    }

    @Test
    fun `a person is added to the circle by the name they go by`() = runTest {
        val vm = viewModel()

        vm.addMember("  @${rider.username} ")

        assertThat(people.profileCalls).containsExactly(rider.username)
        assertThat(repository.changes)
            .containsExactly(NotificationSettingsChange(circleAdd = listOf(rider.id.value)))
        assertThat(loaded(vm).settings.circleMembers.map { it.id }).containsExactly(rider.id)
        assertThat(loaded(vm).notice).isEqualTo(UiText.Res(R.string.notif_settings_member_added))
        assertThat(loaded(vm).saving).isFalse()
    }

    @Test
    fun `a name nobody goes by changes nothing and says so`() = runTest {
        val vm = viewModel()

        vm.addMember("nobody-here")

        assertThat(repository.changes).isEmpty()
        assertThat(loaded(vm).problem!!.message)
            .isEqualTo(UiText.Res(R.string.notif_settings_no_such_person))
        assertThat(loaded(vm).problem!!.retry).isNull()
        assertThat(loaded(vm).saving).isFalse()
    }

    @Test
    fun `an empty name asks for nothing`() = runTest {
        val vm = viewModel()

        vm.addMember("  @ ")

        assertThat(people.profileCalls).isEmpty()
        assertThat(repository.changes).isEmpty()
    }

    @Test
    fun `a person leaves the circle and a mute is lifted by what it is about`() = runTest {
        val mute = NotificationMute(MuteKind.Ride, "ride-1", "Вечерняя покатушка")
        repository.current =
            repository.current.copy(circleMembers = listOf(rider), mutes = listOf(mute))
        val vm = viewModel()

        vm.removeMember(rider.id.value)
        vm.removeMute(mute)

        assertThat(loaded(vm).settings.circleMembers).isEmpty()
        assertThat(loaded(vm).settings.mutes).isEmpty()
        assertThat(repository.changes.map { it.muteRemove.map { ref -> ref.id } }.last())
            .containsExactly("ride-1")
    }

    @Test
    fun `dismissing clears the notice and the problem`() = runTest {
        val vm = viewModel()
        vm.setReminders(false)

        vm.dismissNotice()

        assertThat(loaded(vm).notice).isNull()
        assertThat(loaded(vm).problem).isNull()
    }

    @Test
    fun `asking for the permission is remembered and the phone is read again`() = runTest {
        val vm = viewModel()

        device.state = freshPhone.copy(permission = OsPermission.Granted)
        vm.permissionAsked()

        assertThat(device.asked).isEqualTo(1)
        assertThat(loaded(vm).device!!.permission).isEqualTo(OsPermission.Granted)
    }
}
