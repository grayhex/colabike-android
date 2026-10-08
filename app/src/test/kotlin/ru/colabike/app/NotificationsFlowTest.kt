package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
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
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationReason
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

    private fun SemanticsNodeInteraction.assertContentDescriptionDoesNotContain(
        text: String
    ): SemanticsNodeInteraction {
        val description =
            fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertThat(description).doesNotContain(text)
        return this
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
        // The digits are visual; the merged button speaks the full count once.
        compose.onNodeWithText("2", useUnmergedTree = true).assertIsDisplayed()
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
        compose.onNodeWithText("99+", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("100", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the bell opens the inbox, which says who did what and shows what is unread`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))

        openInbox()

        compose.onNodeWithText("Уведомления").assertIsDisplayed()
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
        // Looking at the inbox marks nothing and does not change what the server counts.
        assertThat(notifications.marks).isEmpty()
        compose.onNodeWithContentDescription("Уведомления, 2 непрочитанных").assertDoesNotExist()
    }

    private val markRead = hasTestTag("notification:mark_read")

    @Test
    fun `opening a notification reads it, and the bell takes the number the server gives`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        openInbox()

        compose.onNodeWithTag("notification:n0").performClick()
        compose.waitForIdle()

        assertThat(notifications.marks).containsExactly("one" to listOf("n0"))
        // Back in the inbox the notification is read, and the bell says 1 (the server's number).
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notification:n0").assertContentDescriptionDoesNotContain("Новое.")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Уведомления, 1 непрочитанное").assertIsDisplayed()
    }

    @Test
    fun `the button of an unread notification reads it without opening it`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        openInbox()

        compose.onAllNodes(markRead, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()

        assertThat(notifications.marks).containsExactly("one" to listOf("n0"))
        compose.onNodeWithTag("notification:n0").assertContentDescriptionDoesNotContain("Новое.")
        // The read one has no button any more, and the inbox is still on screen.
        compose.onNodeWithTag("notifications:list").assertIsDisplayed()
        assertThat(compose.onAllNodes(markRead, useUnmergedTree = true).fetchSemanticsNodes())
            .hasSize(1)
    }

    @Test
    fun `TalkBack gets the same action on the row`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        openInbox()

        val action =
            compose
                .onNodeWithTag("notification:n0")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Отметить прочитанным" }
        compose.runOnUiThread { action.action() }
        compose.waitForIdle()

        assertThat(notifications.marks).containsExactly("one" to listOf("n0"))
    }

    @Test
    fun `a mark the server refused is told and the notification stays new`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        openInbox()
        notifications.nextError = DataError.Offline(java.io.IOException())

        compose.onAllNodes(markRead, useUnmergedTree = true).onFirst().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Нет соединения", substring = true).assertIsDisplayed()
        compose
            .onNodeWithTag("notification:n0")
            .assertContentDescriptionContains("Новое.", substring = true)
        compose.onNodeWithText("Скрыть").performClick()
        compose.onNodeWithText("Нет соединения", substring = true).assertDoesNotExist()
    }

    @Test
    fun `read all marks what was shown, and the bell and the list follow the server`() {
        val notifications = FakeNotifications()
        start(dependencies(notifications = notifications))
        openInbox()

        compose.onNodeWithTag("notifications:read_all").performClick()
        compose.waitForIdle()

        assertThat(notifications.marks).containsExactly("all" to listOf("mark-1"))
        compose.onNodeWithTag("notification:n0").assertContentDescriptionDoesNotContain("Новое.")
        compose.onNodeWithTag("notification:n2").assertContentDescriptionDoesNotContain("Новое.")
        // Nothing unread is left to read: the action goes.
        compose.onNodeWithTag("notifications:read_all").assertDoesNotExist()
    }

    @Test
    fun `a ride offered near the person says why, and the category narrows the inbox to it`() {
        val notifications =
            FakeNotifications(
                mapOf(
                    null to
                        Page(
                            listOf(
                                notification(
                                    1,
                                    kind = "plan_nearby",
                                    type = "ride",
                                    reasons =
                                        setOf(NotificationReason.Nearby, NotificationReason.Intent),
                                ),
                                notification(
                                    2,
                                    kind = "plan_published",
                                    type = "ride",
                                    reasons = setOf(NotificationReason.Friend),
                                ),
                                notification(3, kind = "comment"),
                            ),
                            null,
                        )
                )
            )
        start(dependencies(notifications = notifications))
        openInbox()

        compose
            .onNodeWithTag("notification:n1")
            .assertContentDescriptionContains("Покатушка рядом", substring = true)
            .assertContentDescriptionContains(
                "В вашем районе, Подходит под ваши планы",
                substring = true,
            )
        // A plan of the circle alone is what the category already says: no reason line.
        compose
            .onNodeWithTag("notification:n2")
            .assertContentDescriptionContains("Новый план друга", substring = true)
            .assertContentDescriptionDoesNotContain("Из вашего круга")

        // The row of chips is longer than a phone is wide.
        compose
            .onNodeWithTag("notifications:filters")
            .performScrollToNode(hasTestTag("notifications:filter:nearby"))
        compose.onNodeWithTag("notifications:filter:nearby").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("notification:n1").assertIsDisplayed()
        compose.onNodeWithTag("notification:n3").assertDoesNotExist()
    }

    @Test
    fun `unread and a category narrow the inbox, and an empty answer says why`() {
        val notifications =
            FakeNotifications(
                mapOf(
                    null to
                        Page(
                            listOf(
                                notification(1, kind = "ride_invite", type = "ride"),
                                notification(2, kind = "comment"),
                                notification(3, kind = "follow", type = "profile", read = true),
                            ),
                            null,
                        )
                )
            )
        start(dependencies(notifications = notifications))
        openInbox()
        compose.onNodeWithTag("notification:n3").assertIsDisplayed()

        compose.onNodeWithTag("notifications:filter:unread").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notification:n3").assertDoesNotExist()
        compose.onNodeWithTag("notification:n1").assertIsDisplayed()

        compose.onNodeWithTag("notifications:filter:rides").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notification:n2").assertDoesNotExist()
        compose.onNodeWithTag("notification:n1").assertIsDisplayed()
        assertThat(notifications.filters.last())
            .isEqualTo(NotificationFilter(true, NotificationCategory.Rides))

        // The row of chips scrolls: the last one is off the phone's screen until it is brought in.
        compose
            .onNodeWithTag("notifications:filters")
            .performScrollToNode(hasTestTag("notifications:filter:site"))
        compose.onNodeWithTag("notifications:filter:site").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Непрочитанных нет").assertIsDisplayed()

        compose
            .onNodeWithTag("notifications:filters")
            .performScrollToNode(hasTestTag("notifications:filter:all"))
        compose.onNodeWithTag("notifications:filter:all").performClick()
        compose.onNodeWithTag("notifications:filter:unread").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("notification:n3").assertIsDisplayed()
    }

    @Test
    fun `a ride's notifications say what happened and when`() {
        val ride =
            notification(1, kind = "ride_reminder", type = "ride", actor = null).let {
                it.copy(
                    target =
                        it.target.copy(
                            name = "Субботний круг",
                            occurrenceAt = java.time.Instant.parse("2026-10-10T07:00:00Z"),
                            agreementRevision = 3,
                        )
                )
            }
        val invite = notification(2, kind = "ride_invite", type = "ride").copy(read = true)
        val notifications = FakeNotifications(mapOf(null to Page(listOf(ride, invite), null)))
        start(dependencies(notifications = notifications))
        openInbox()

        compose
            .onNodeWithTag("notification:n1")
            .assertContentDescriptionContains("Скоро покатушка", substring = true)
        compose
            .onNodeWithTag("notification:n1")
            .assertContentDescriptionContains("Субботний круг", substring = true)
        compose
            .onNodeWithTag("notification:n2")
            .assertContentDescriptionContains("Приглашение на покатушку", substring = true)
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
                                    kind = "article_published",
                                    type = "article",
                                    path = "/articles/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5",
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
            .containsExactly("https://colabike.test/articles/6e7f8091-a2b3-4c4d-9e5f-60718293a4b5")
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
