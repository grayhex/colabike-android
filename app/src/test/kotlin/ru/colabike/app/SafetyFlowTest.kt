package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page
import ru.colabike.core.model.PersonSummary
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.UserId

/**
 * Reporting and blocking as a person meets them (docs/adr/0021): from a bike and from a person's
 * page, from a comment, and the list of the blocked, which is where they are taken back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class SafetyFlowTest {
    @get:Rule val compose = createComposeRule()

    private val author = PreviewData.rider

    /** Somebody else: the author of a bike has no report to make of their own bike. */
    private val viewer = account.copy(id = UserId("viewer-1"), username = "viewer")
    private val stranger =
        Relationship(isSelf = false, following = false, followedBy = false, friends = false)
    private val blocked = stranger.copy(blockedByMe = true)

    private fun dependencies(
        signedIn: Boolean = true,
        relationship: Relationship = stranger,
        safety: FakeSafety = FakeSafety(),
    ) =
        FakeDependencies(
            bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
            people =
                FakePeople(profiles = mapOf(author.id.value to profileOf(author, relationship))),
            safety = safety,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(viewer) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openBike() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
    }

    private fun openAuthor() {
        openBike()
        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun more() {
        compose.onNodeWithContentDescription("Ещё").performClick()
        compose.waitForIdle()
    }

    // --- report ----------------------------------------------------------------------------

    @Test
    fun `a bike of someone else can be reported, with a reason, once`() {
        val dependencies = dependencies()
        start(dependencies)
        openBike()

        more()
        compose.onNodeWithText("Пожаловаться").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Отправить").assertIsNotEnabled()
        compose.onNodeWithText("Спам или реклама").performClick()
        compose.onNodeWithText("Отправить").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Спасибо. Жалоба отправлена модераторам.").assertIsDisplayed()
        val (target, reason) = dependencies.safety.reports.single()
        assertThat(target.kind).isEqualTo(ReportKind.Bike)
        assertThat(reason).isEqualTo(ReportReason.Spam)
        compose.onNodeWithText("Готово").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Спасибо. Жалоба отправлена модераторам.").assertDoesNotExist()
    }

    @Test
    fun `a guest who asks to report is asked to sign in, and nothing is sent`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)
        openBike()

        more()
        compose.onNodeWithText("Пожаловаться").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.safety.reports).isEmpty()
    }

    @Test
    fun `a failed report keeps the dialog and says why`() {
        val safety = FakeSafety()
        start(dependencies(safety = safety))
        openBike()
        more()
        compose.onNodeWithText("Пожаловаться").performClick()
        compose.onNodeWithText("Другое").performClick()
        safety.nextError = ru.colabike.core.model.DataError.Offline(java.io.IOException("down"))

        compose.onNodeWithText("Отправить").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Нет соединения", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Отправить").assertIsDisplayed()
    }

    // --- block -----------------------------------------------------------------------------

    @Test
    fun `blocking a person asks first, then the page says so and offers the way back`() {
        val dependencies = dependencies()
        start(dependencies)
        openAuthor()

        more()
        compose.onNodeWithText("Заблокировать").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Заблокировать «Тестовый Райдер»?").assertIsDisplayed()
        assertThat(dependencies.safety.blocks).isEmpty()
        // The server cuts the follows and says the person is blocked: the page reads it again.
        dependencies.people.profiles = mapOf(author.id.value to profileOf(author, blocked))
        // Two words "Заблокировать" are on screen now (the menu's is gone): the dialog's button.
        compose
            .onNode(hasText("Заблокировать") and hasClickAction() and hasAnyAncestor(isDialog()))
            .performClick()
        compose.waitForIdle()

        assertThat(dependencies.safety.blocks).containsExactly(author.id to true)
        compose.onNodeWithText("Вы заблокировали этого человека").assertIsDisplayed()
        compose.onNodeWithText("Подписаться").assertDoesNotExist()
    }

    @Test
    fun `an unblock from the banner is one tap`() {
        val dependencies = dependencies(relationship = blocked)
        start(dependencies)
        openAuthor()
        compose.onNodeWithText("Вы заблокировали этого человека").assertIsDisplayed()
        dependencies.people.profiles = mapOf(author.id.value to profileOf(author, stranger))

        compose.onNodeWithText("Разблокировать").performClick()
        compose.waitForIdle()

        assertThat(dependencies.safety.blocks).containsExactly(author.id to false)
        compose.onNodeWithText("Вы заблокировали этого человека").assertDoesNotExist()
    }

    // --- the list --------------------------------------------------------------------------

    @Test
    fun `the blocked people are in the profile, each with its own unblock`() {
        val person = PersonSummary(PreviewData.rider, blocked)
        val dependencies = dependencies(safety = FakeSafety(listOf(person)))
        start(dependencies)

        compose.section("Профиль").performClick()
        compose.onNodeWithText("Заблокированные").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithContentDescription("Разблокировать: Тестовый Райдер").performClick()
        compose.waitForIdle()

        assertThat(dependencies.safety.blocks).containsExactly(PreviewData.rider.id to false)
        compose.onNodeWithText("Никого не блокировали").assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class OwnContentTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the author of a bike is not offered a report of their own bike`() {
        val dependencies =
            FakeDependencies(
                bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
                // The viewer is the author of every bike of the fake catalogue.
                auth = FakeAuth(AuthState.SignedIn(account.copy(id = PreviewData.rider.id))),
            )
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Ещё").assertDoesNotExist()
    }
}
