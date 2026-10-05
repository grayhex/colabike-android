package ru.colabike.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.push.PushChannel
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationSettingsChange

/** Profile → Notifications as a person uses it, on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class NotificationSettingsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val settings = FakeNotificationSettings()
    private val device = FakeDeviceNotifications()

    private fun start(
        settings: FakeNotificationSettings = this.settings,
        device: FakeDeviceNotifications = this.device,
    ) {
        compose.setContent {
            ColaBikeTheme {
                ColaBikeApp(
                    FakeDependencies(notificationSettings = settings, deviceNotifications = device)
                )
            }
        }
        compose.waitForIdle()
        compose.section("Профиль").performClick()
        compose.onNodeWithText("Что, когда и от кого присылать").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun tag(name: String) = compose.onNodeWithTag("notif-settings:$name")

    private fun scrolled(name: String) = tag(name).performScrollTo()

    // --- entry and the two parts -------------------------------------------------------------

    @Test
    fun `the profile leads to the settings, which say what is the account's and what the phone's`() {
        start()

        compose.onNodeWithTag("notif-settings").assertIsDisplayed()
        compose.onNodeWithText("Для аккаунта").assertIsDisplayed()
        compose.onNodeWithText("На этом телефоне").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `back returns to the profile`() {
        start()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithText("Что, когда и от кого присылать").assertIsDisplayed()
    }

    @Test
    fun `a failed load says why and loading again shows the settings`() {
        settings.loadError = DataError.Server(503, null)
        start()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        settings.loadError = null
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("notif-settings").assertIsDisplayed()
    }

    // --- the account's switches --------------------------------------------------------------

    @Test
    fun `mail is switched on, the server is asked, and the switch shows what it holds`() {
        start()
        scrolled("email").assertIsOff()

        tag("email").performClick()
        compose.waitForIdle()

        assertThat(settings.changes)
            .containsExactly(NotificationSettingsChange(emailEnabled = true))
        tag("email").assertIsOn()
        compose.onNodeWithText("Настройки сохранены").assertIsDisplayed()
    }

    @Test
    fun `push cannot be switched on while the server cannot carry it`() {
        start()

        scrolled("push").assertIsNotEnabled()
        compose
            .onNodeWithText("Сервер пока не принимает push", substring = true)
            .assertIsDisplayed()
        assertThat(settings.changes).isEmpty()
    }

    @Test
    fun `push is switched on once the server can carry it`() {
        val ready =
            FakeNotificationSettings(
                defaultNotificationSettings.copy(
                    channels = defaultNotificationSettings.channels.copy(pushAvailable = true)
                )
            )
        start(settings = ready)

        scrolled("push").assertIsEnabled().assertIsOff()
        tag("push").performClick()
        compose.waitForIdle()

        assertThat(ready.changes).containsExactly(NotificationSettingsChange(pushEnabled = true))
        tag("push").assertIsOn()
    }

    @Test
    fun `a category's mail switch names the category`() {
        start()

        scrolled("category:rides:email")
        tag("category:rides:email").performClick()
        compose.waitForIdle()

        assertThat(settings.changes)
            .containsExactly(NotificationSettingsChange(categoryEmail = mapOf("rides" to true)))
    }

    @Test
    fun `a channel a category cannot use is not offered at all`() {
        start()

        // Market has no push to send, plans and intents no mail: no dead switches.
        compose.onAllNodesWithTag("notif-settings:category:market:push").assertCountEquals(0)
        compose.onAllNodesWithTag("notif-settings:category:plans:email").assertCountEquals(0)
        scrolled("category:plans:push").assertIsDisplayed()
    }

    // --- the failure -------------------------------------------------------------------------

    @Test
    fun `a refused change keeps the old value, says so and can be tried again`() {
        start()
        settings.failChange = DataError.Server(503, null)

        scrolled("email")
        tag("email").performClick()
        compose.waitForIdle()

        tag("email").assertIsOff()
        compose.onNodeWithText("Настройки сохранены").assertDoesNotExist()
        compose.onNodeWithTag("notif-settings:problem").assertIsDisplayed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        tag("email").assertIsOn()
        assertThat(settings.changes)
            .containsExactly(
                NotificationSettingsChange(emailEnabled = true),
                NotificationSettingsChange(emailEnabled = true),
            )
    }

    // --- pause, quiet hours ------------------------------------------------------------------

    @Test
    fun `a pause is chosen from the offered lengths and lifted with a button`() {
        start()

        compose.onNodeWithText("На 8 часов").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(settings.changes)
            .containsExactly(
                NotificationSettingsChange(
                    pauseUntil = Instant.parse("2026-10-03T20:00:00Z").plus(Duration.ofHours(8))
                )
            )
        scrolled("paused").assertIsDisplayed()

        compose.onNodeWithText("Снять паузу").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(settings.changes.last()).isEqualTo(NotificationSettingsChange(resume = true))
        compose.onNodeWithText("На 8 часов").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `quiet hours are switched on and the zone of the phone is taken with them`() {
        start()

        scrolled("quiet")
        tag("quiet").performClick()
        compose.waitForIdle()

        val change = settings.changes.single()
        assertThat(change.quietEnabled).isTrue()
        assertThat(change.timeZone).isNotNull()
        tag("quiet").assertIsOn()
        scrolled("quiet-from").assertIsDisplayed()
    }

    // --- the circle --------------------------------------------------------------------------

    @Test
    fun `the circle is chosen, and the chosen people are added by name`() {
        start()

        scrolled("circle:selected")
        tag("circle:selected").performClick()
        compose.waitForIdle()
        assertThat(settings.changes.last())
            .isEqualTo(NotificationSettingsChange(circleMode = CircleMode.Selected))

        scrolled("add-name")
        tag("add-name").performTextInput("@${rider.username}")
        tag("add").performClick()
        compose.waitForIdle()

        assertThat(settings.changes.last())
            .isEqualTo(NotificationSettingsChange(circleAdd = listOf(rider.id.value)))
        scrolled("member:added-${rider.id.value}").assertIsDisplayed()
    }

    @Test
    fun `people are added only to a chosen circle`() {
        start()

        compose.onAllNodesWithTag("notif-settings:add-name").assertCountEquals(0)
    }

    @Test
    fun `a name nobody goes by says so and changes nothing`() {
        start(
            settings = FakeNotificationSettings(busyNotificationSettings.copy(pausedUntil = null))
        )

        scrolled("add-name")
        tag("add-name").performTextInput("nobody-here")
        tag("add").performClick()
        compose.waitForIdle()

        compose
            .onNodeWithText("Не нашли пользователя с таким именем")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a mute is lifted from the list`() {
        val busy = FakeNotificationSettings(busyNotificationSettings)
        start(settings = busy)

        scrolled("mute:ride-1").assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Вернуть уведомления: Воскресный выезд за город")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        assertThat(busy.changes.single().muteRemove.single().id).isEqualTo("ride-1")
    }

    // --- this phone --------------------------------------------------------------------------

    @Test
    fun `a fresh phone is asked after being told why, and the system's answer is not taken for granted`() {
        start()

        scrolled("os-ask").assertIsDisplayed()
        tag("os-ask").performClick()
        compose.waitForIdle()
        compose.onNode(isDialog()).assertIsDisplayed()
        compose.onNodeWithText("Не сейчас").performClick()
        compose.waitForIdle()

        assertThat(device.asked).isEqualTo(0)
        scrolled("os-ask").assertIsDisplayed()
    }

    @Test
    fun `a refusal is told from not having been asked`() {
        start(device = FakeDeviceNotifications(deniedPhone))

        scrolled("os-off").assertIsDisplayed()
        compose
            .onNodeWithText("Нужен RuStore", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a channel switched off in Android is named`() {
        start(
            device =
                FakeDeviceNotifications(readyPhone.copy(channelsOff = listOf(PushChannel.Rides)))
        )

        scrolled("channels-off").assertIsDisplayed()
    }

    @Test
    fun `a phone that shows notifications says so`() {
        start(device = FakeDeviceNotifications(readyPhone))

        scrolled("os-on").assertIsDisplayed()
        compose
            .onNodeWithText("Сервис доставки найден", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the account's switches are the server's, whatever the phone says`() {
        start(device = FakeDeviceNotifications(deniedPhone))

        scrolled("email").assertIsEnabled().assertIsOff()
    }
}
