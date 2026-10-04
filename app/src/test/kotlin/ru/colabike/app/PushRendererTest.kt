package ru.colabike.app

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import ru.colabike.app.push.PushChannel
import ru.colabike.app.push.PushEnvelope
import ru.colabike.app.push.PushRenderer
import ru.colabike.app.push.PushTap
import ru.colabike.app.push.PushTarget

/** The channels and the one notification a push becomes. */
@RunWith(RobolectricTestRunner::class)
class PushRendererTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val manager = app.getSystemService(NotificationManager::class.java)
    private val now = Instant.parse("2026-10-05T00:00:00Z")
    private val renderer = PushRenderer(app, Clock.fixed(now, ZoneOffset.UTC))

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun envelope(
        event: Int = 1,
        group: String = "bike:a",
        category: String = "discussions",
        type: String = "reply",
        title: String = "Ответ на ваш комментарий",
        body: String? = "К велосипеду «Gravel Nuroad»",
    ) =
        PushEnvelope(
            deliveryId = "00000000-0000-4000-8000-%012d".format(event),
            eventId = "00000000-0000-4000-9000-%012d".format(event),
            bindingGeneration = 4,
            category = category,
            type = type,
            createdAt = Instant.parse("2026-10-04T09:00:00Z"),
            expiresAt = Instant.parse("2026-10-11T09:00:00Z"),
            neutral = false,
            title = title,
            body = body,
            group = group,
            target = PushTarget("bike", "00000000-0000-4000-8000-00000000000a", null, null, null),
        )

    private fun active() = manager.activeNotifications.toList()

    @Test
    fun `the six channels exist with the names of the settings, and an unknown category is general`() {
        PushChannel.ensure(app)

        val names = manager.notificationChannels.associate { it.id to it.name.toString() }
        assertThat(names)
            .containsExactly(
                "rides",
                "Мои поездки и приглашения",
                "plans",
                "Планы друзей",
                "nearby",
                "Рядом",
                "chat",
                "Сообщения",
                "discussions",
                "Комментарии и ответы",
                "general",
                "Другое",
            )
        assertThat(PushChannel.of("rides")).isEqualTo(PushChannel.Rides)
        assertThat(PushChannel.of("plans")).isEqualTo(PushChannel.Plans)
        assertThat(PushChannel.of("intents")).isEqualTo(PushChannel.Plans)
        assertThat(PushChannel.of("nearby")).isEqualTo(PushChannel.Nearby)
        assertThat(PushChannel.of("chat")).isEqualTo(PushChannel.Chat)
        assertThat(PushChannel.of("discussions")).isEqualTo(PushChannel.Discussions)
        assertThat(PushChannel.of("reactions")).isEqualTo(PushChannel.General)
        assertThat(PushChannel.of("from_the_future")).isEqualTo(PushChannel.General)
    }

    @Test
    fun `a channel that is there keeps the person's choice when channels are created again`() {
        manager.createNotificationChannel(
            NotificationChannel(
                "rides",
                "Мои поездки и приглашения",
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        PushChannel.ensure(app)
        PushChannel.ensure(app)

        assertThat(manager.getNotificationChannel("rides").importance)
            .isEqualTo(NotificationManager.IMPORTANCE_LOW)
        assertThat(manager.notificationChannels).hasSize(6)
    }

    @Test
    fun `a push becomes one notification in its channel, with its text, and a tap that stays inside`() {
        assertThat(renderer.show(envelope(), "account-1")).isTrue()

        val posted = active().single()
        val notification = posted.notification
        assertThat(posted.tag).isEqualTo("account-1|bike:a")
        assertThat(notification.channelId).isEqualTo("discussions")
        assertThat(notification.extras.getString(Notification.EXTRA_TITLE))
            .isEqualTo("Ответ на ваш комментарий")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("К велосипеду «Gravel Nuroad»")
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        // The tap is an immutable PendingIntent to the activity that is not exported.
        val tap = shadowOf(notification.contentIntent)
        assertThat(tap.isActivity).isTrue()
        val intent = tap.savedIntent
        assertThat(intent.component?.className)
            .isEqualTo("ru.colabike.app.push.NotificationTapActivity")
        assertThat(PushTap.from(intent)?.eventId).isEqualTo(envelope().eventId)
        // The system takes it down by itself when it expires.
        assertThat(notification.timeoutAfter).isEqualTo(6L * 24 * 3600 * 1000 + 9 * 3600 * 1000)
    }

    @Test
    fun `the lock screen shows a general text, not the title and the body`() {
        renderer.show(envelope(title = "Приглашение в закрытый план", body = "Место: гараж"), "a")

        val notification = active().single().notification
        assertThat(notification.visibility).isEqualTo(Notification.VISIBILITY_PRIVATE)
        val publicVersion = notification.publicVersion
        assertThat(publicVersion).isNotNull()
        val shown =
            listOfNotNull(
                publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE),
                publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT),
            )
        assertThat(shown.joinToString()).doesNotContain("закрытый")
        assertThat(shown.joinToString()).doesNotContain("гараж")
    }

    @Test
    fun `news of the same stack replaces the notification without ringing again, other stacks stay apart`() {
        renderer.show(envelope(event = 1, group = "ride:a"), "account-1")
        val first = active().single().notification
        assertThat(first.flags and Notification.FLAG_ONLY_ALERT_ONCE).isEqualTo(0)

        renderer.show(envelope(event = 2, group = "ride:a", title = "Изменено"), "account-1")
        val replaced = active().single().notification
        assertThat(replaced.extras.getString(Notification.EXTRA_TITLE)).isEqualTo("Изменено")
        assertThat(replaced.flags and Notification.FLAG_ONLY_ALERT_ONCE).isNotEqualTo(0)

        renderer.show(envelope(event = 3, group = "ride:b"), "account-1")
        assertThat(active()).hasSize(2)
    }

    @Test
    fun `two accounts never share a stack`() {
        renderer.show(envelope(group = "bike:a"), "account-1")
        renderer.show(envelope(event = 2, group = "bike:a"), "account-2")

        assertThat(active().map { it.tag }).containsExactly("account-1|bike:a", "account-2|bike:a")
    }

    @Test
    fun `a category the app does not know is shown in the general channel`() {
        renderer.show(envelope(category = "from_the_future"), "a")

        assertThat(active().single().notification.channelId).isEqualTo("general")
    }

    @Test
    fun `cancelling takes down everything shown`() {
        renderer.show(envelope(event = 1, group = "a"), "x")
        renderer.show(envelope(event = 2, group = "b"), "x")

        renderer.cancelAll()

        assertThat(active()).isEmpty()
    }

    @Test
    fun `without the permission or with notifications switched off nothing is posted`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(renderer.show(envelope(), "a")).isFalse()
        assertThat(active()).isEmpty()

        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(manager).setNotificationsEnabled(false)
        assertThat(renderer.show(envelope(), "a")).isFalse()
        assertThat(active()).isEmpty()
    }
}
