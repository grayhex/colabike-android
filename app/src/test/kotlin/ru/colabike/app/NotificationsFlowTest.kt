package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.Page

/** The bell and the inbox as a person uses them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class NotificationsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val comment = "b2000000-0000-4000-8000-000000000002"

    private fun dependencies(
        signedIn: Boolean = true,
        notifications: FakeNotifications = FakeNotifications(),
        comments: FakeComments = sampleDiscussion(),
    ) =
        FakeDependencies(
            bikes = FakeBikes(),
            notifications = notifications,
            comments = comments,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    ColaBikeApp(dependencies)
                }
            }
        }
        compose.waitForIdle()
    }

    private val bell = hasContentDescription("Уведомления", substring = true)

    private fun openInbox() {
        compose.onNode(bell).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a member's bell says how many are unread, and a guest has no bell`() {
        start(dependencies())
        compose.onNodeWithContentDescription("Уведомления, 2 непрочитанных").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun `a guest has no inbox, no bell and no request`() {
        val notifications = FakeNotifications()
        start(dependencies(signedIn = false, notifications = notifications))

        compose.onNode(bell).assertDoesNotExist()
        assertThat(notifications.countCalls).isEqualTo(0)
    }

    @Test
    fun `a count that is only a floor is never an exact number`() {
        start(
            dependencies(
                notifications = FakeNotifications(unread = NotificationCount(100, capped = true))
            )
        )

        compose
            .onNodeWithContentDescription("Уведомления, непрочитанных не меньше ста")
            .assertExists()
        compose.onNodeWithText("99+").assertIsDisplayed()
        compose.onNodeWithText("100").assertDoesNotExist()
    }

    @Test
    fun `the bell opens the inbox, which says who did what, shows what is unread, and says it cannot mark them`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))

        openInbox()

        compose.onNodeWithText("Уведомления").assertIsDisplayed()
        compose
            .onNodeWithText("Прочитанными уведомления отмечаются на сайте", substring = true)
            .assertIsDisplayed()
        // n0 is unread and n1 read: the state is in the words, not only in a dot.
        compose
            .onNodeWithTag("notification:n0")
            .assertContentDescriptionContains("Новое. Новый комментарий", substring = true)
        compose
            .onNodeWithTag("notification:n1")
            .assertContentDescriptionContains("Новый комментарий", substring = true)
        assertThat(
                compose
                    .onNodeWithTag("notification:n1")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.ContentDescription]
                    .single()
            )
            .doesNotContain("Новое.")
        assertThat(notifications.pageCalls).containsExactly(null)
        // Looking at the inbox does not change what the server counts.
        compose.onNodeWithContentDescription("Уведомления, 2 непрочитанных").assertDoesNotExist()
    }

    @Test
    fun `a comment's notification opens the discussion at that comment`() {
        val comments = sampleDiscussion()
        val notifications =
            FakeNotifications(
                mapOf(
                    null to
                        Page(
                            listOf(
                                notification(
                                    1,
                                    kind = "comment",
                                    path = "/b/6e7f8091#comment-$comment",
                                )
                            ),
                            null,
                        )
                )
            )
        start(dependencies(notifications = notifications, comments = comments))
        openInbox()

        compose.onNodeWithTag("notification:n1").performClick()
        compose.waitForIdle()

        val call = comments.threadCalls.last()
        assertThat(call.first.id).isEqualTo("6e7f8091-a2b3-4c4d-9e5f-60718293a4b5")
        assertThat(call.third).isEqualTo(comment)
    }

    @Test
    fun `a like opens the bike, and the bike page is the same as from the list`() {
        val notifications =
            FakeNotifications(
                mapOf(null to Page(listOf(notification(1, kind = "like", type = "bike")), null))
            )
        start(dependencies(notifications = notifications))
        openInbox()

        compose.onNodeWithTag("notification:n1").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Назад").assertExists()
    }

    @Test
    fun `an object the app has no screen for is shown by the site, and a site's own notification has no person`() {
        val notifications =
            FakeNotifications(
                mapOf(
                    null to
                        Page(
                            listOf(
                                notification(
                                    1,
                                    kind = "market_expiring",
                                    type = "market",
                                    path = "/market/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5",
                                    actor = null,
                                )
                            ),
                            null,
                        )
                )
            )
        start(dependencies(notifications = notifications))
        openInbox()

        compose.onNodeWithTag("notification:n1").performClick()

        assertThat(opened)
            .containsExactly("https://colabike.test/market/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5")
    }

    @Test
    fun `a kind from the future has a general look and still opens its object`() {
        val notifications =
            FakeNotifications(
                mapOf(
                    null to
                        Page(listOf(notification(1, kind = "something_new", type = "ride")), null)
                )
            )
        start(dependencies(notifications = notifications))
        openInbox()

        compose
            .onNodeWithTag("notification:n1")
            .assertContentDescriptionContains("Новое событие", substring = true)
        compose.onNodeWithTag("notification:n1").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Назад").assertExists()
    }

    @Test
    fun `an empty inbox says so`() {
        start(
            dependencies(notifications = FakeNotifications(mapOf(null to Page(emptyList(), null))))
        )
        openInbox()

        compose.onNodeWithText("Уведомлений нет").assertIsDisplayed()
    }

    @Test
    fun `a failed inbox offers a retry`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        notifications.nextError = DataError.Offline(java.io.IOException())
        openInbox()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("notifications:list").assertIsDisplayed()
    }

    @Test
    fun `more pages come as the list is read to its end`() {
        val notifications =
            FakeNotifications(
                mapOf(
                    null to Page(notifications(0, 6), "c1"),
                    "c1" to Page(notifications(6, 2), null),
                )
            )
        start(dependencies(notifications = notifications))
        openInbox()

        compose
            .onNodeWithTag("notifications:list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("notification:n7"))
        compose.waitForIdle()

        assertThat(notifications.pageCalls).containsExactly(null, "c1").inOrder()
    }
}
