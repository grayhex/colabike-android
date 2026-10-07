package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.navigation.Destination
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.AgreementChange
import ru.colabike.core.model.ParticipationResponse
import ru.colabike.core.model.ParticipationState
import ru.colabike.core.model.RequestedDateStatus
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.ViewerRole

/** A notification about a ride's date, opened: the person's part in exactly that date. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class ParticipationFlowTest {
    @get:Rule val compose = createComposeRule()

    private val ride = "b2000000-0000-4000-8000-0000000000b2"
    private val date = "2026-10-10T07:00:00Z"
    private val participation = FakeParticipation()

    private fun start(participation: FakeParticipation = this.participation) {
        compose.setContent {
            ColaBikeTheme {
                ColaBikeApp(
                    FakeDependencies(
                        participation = participation,
                        pending =
                            FakePending().apply { offer(Destination.Participation(ride, date)) },
                    )
                )
            }
        }
        compose.waitForIdle()
    }

    private fun scrolled(tag: String) =
        compose.onNodeWithTag("participation:$tag").performScrollTo()

    @Test
    fun `a notification about a date opens that date, with the terms and the answers on offer`() {
        start()

        assertThat(participation.asked).containsExactly(Instant.parse(date))
        compose.onNodeWithTag("participation").assertIsDisplayed()
        compose
            .onNodeWithTag("participation:title")
            .assertTextContains("Воскресный выезд", substring = true)
        compose
            .onNodeWithTag("participation:when")
            .assertTextContains("Europe/Moscow", substring = true)
        scrolled("meeting")
        compose
            .onNodeWithTag("participation:meeting")
            .assertTextContains("У входа в парк", substring = true)
        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").assertIsNotSelected()
        compose.onNodeWithTag("participation:maybe").assertIsDisplayed()
        compose.onNodeWithTag("participation:declined").assertIsDisplayed()
    }

    @Test
    fun `an answer is a tap, goes for the date and the terms on the screen, and is shown as taken`() {
        start()

        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").performClick()
        compose.waitForIdle()

        assertThat(participation.answers)
            .containsExactly(Triple(ParticipationResponse.Accepted, Instant.parse(date), 3))
        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").assertIsSelected()
        compose.onNodeWithTag("participation:notice").assertExists()
        compose.onNodeWithTag("participation:state").assertTextContains("Вы едете")
    }

    @Test
    fun `nothing is answered by opening the page`() {
        start()

        assertThat(participation.answers).isEmpty()
    }

    @Test
    fun `terms that changed after an answer ask for a new one, naming what changed`() {
        participation.current =
            sampleParticipation(
                state = ParticipationState.Reconfirm,
                previous = ParticipationResponse.Accepted,
                changed = true,
                revision = 4,
                changes = setOf(AgreementChange.Start, AgreementChange.Place),
            )
        start()

        compose.onNodeWithTag("participation:changed").assertIsDisplayed()
        compose.onNodeWithText("Условия изменились — подтвердите участие").assertIsDisplayed()
        compose
            .onNodeWithText("Вы отвечали на прежние условия", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithText("время начала, место встречи", substring = true).assertIsDisplayed()
        scrolled("state")
        compose
            .onNodeWithTag("participation:state")
            .assertTextContains("подтвердить", substring = true)
        // The earlier answer is not the new one.
        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").assertIsNotSelected()

        compose.onNodeWithTag("participation:accepted").performClick()
        compose.waitForIdle()

        assertThat(participation.answers.single().third).isEqualTo(4)
    }

    @Test
    fun `terms that changed between showing and answering are not confirmed`() {
        start()
        participation.conflictWith =
            sampleParticipation(
                state = ParticipationState.Reconfirm,
                previous = ParticipationResponse.Accepted,
                changed = true,
                revision = 5,
                changes = setOf(AgreementChange.Route),
            )

        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("participation:meanwhile").assertIsDisplayed()
        compose.onNodeWithTag("participation:changed").assertIsDisplayed()
        scrolled("accepted")
        compose.onNodeWithTag("participation:accepted").assertIsNotSelected()
    }

    @Test
    fun `a cancelled date has a plain explanation, no place and no buttons`() {
        participation.current =
            sampleParticipation(
                requested = RequestedDateStatus.Cancelled,
                allowed = emptySet(),
                scheduledAt = null,
                meetingHidden = true,
                state = ParticipationState.None,
            )
        start()

        compose
            .onNodeWithTag("participation:date-notice")
            .assertTextContains("Эта дата отменена", substring = true)
        compose.onNodeWithTag("participation:accepted").assertDoesNotExist()
        scrolled("no-answer")
        compose.onNodeWithTag("participation:no-answer").assertIsDisplayed()
        compose.onNodeWithTag("participation:meeting").assertDoesNotExist()
    }

    @Test
    fun `a cancelled date of a series that goes on shows the cancelled date and the next one`() {
        participation.current =
            sampleParticipation(
                requested = RequestedDateStatus.Cancelled,
                allowed = emptySet(),
                scheduledAt = Instant.parse("2026-10-17T07:00:00Z"),
            )
        start()

        compose
            .onNodeWithTag("participation:date-notice")
            .assertTextContains("серия продолжается", substring = true)
        // The page is about the date that was cancelled; the next one is said apart.
        compose
            .onNodeWithTag("participation:when")
            .assertTextContains("10 октября", substring = true)
        compose
            .onNodeWithTag("participation:next")
            .assertTextContains("17 октября", substring = true)
        compose.onNodeWithTag("participation:accepted").assertDoesNotExist()
    }

    @Test
    fun `a cancelled plan is told apart from a cancelled date`() {
        participation.current =
            sampleParticipation(
                status = RideStatus.Cancelled,
                allowed = emptySet(),
                scheduledAt = null,
                requested = RequestedDateStatus.Cancelled,
            )
        start()

        compose
            .onNodeWithTag("participation:date-notice")
            .assertTextContains("отменён целиком", substring = true)
    }

    @Test
    fun `the organizer sees their own plan and no answers`() {
        participation.current =
            sampleParticipation(
                role = ViewerRole.Organizer,
                state = ParticipationState.Organizer,
                allowed = emptySet(),
            )
        start()

        compose
            .onNodeWithTag("participation:state")
            .assertTextContains("организатор", substring = true)
        compose.onNodeWithTag("participation:accepted").assertDoesNotExist()
        compose.onNodeWithTag("participation:reminders").assertDoesNotExist()
    }

    @Test
    fun `a closed enrolment is said, and leaving stays possible`() {
        participation.current =
            sampleParticipation(
                closed = true,
                allowed = setOf(ParticipationResponse.Declined),
                state = ParticipationState.Accepted,
                response = ParticipationResponse.Accepted,
            )
        start()

        compose.onNodeWithTag("participation:closed").assertIsDisplayed()
        scrolled("declined")
        compose.onNodeWithTag("participation:declined").assertIsDisplayed()
        compose.onNodeWithTag("participation:accepted").assertDoesNotExist()
    }

    @Test
    fun `a plan that is gone is unavailable and leads to the rides`() {
        participation.current = null
        start()

        compose.onNodeWithTag("participation:unavailable").assertIsDisplayed()
        compose.onNodeWithText("К покатушкам").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("participation:unavailable").assertDoesNotExist()
    }

    @Test
    fun `a plan's page leads to the terms and the answer, for a member only`() {
        compose.setContent {
            ColaBikeTheme { ColaBikeApp(FakeDependencies(participation = participation)) }
        }
        compose.waitForIdle()
        compose.section("Покатушки").performClick()
        compose.choose("rides:segment", "Ближайшие")
        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("ride:participation").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("participation").assertIsDisplayed()
        // No date was named: the nearest one is asked for.
        assertThat(participation.asked).containsExactly(null)
    }

    @Test
    fun `a guest is not offered the answer`() {
        compose.setContent {
            ColaBikeTheme {
                ColaBikeApp(
                    FakeDependencies(
                        participation = participation,
                        auth = FakeAuth(AuthState.SignedOut),
                        settings = FakeSettings(guest = true),
                    )
                )
            }
        }
        compose.waitForIdle()
        compose.section("Покатушки").performClick()
        compose.choose("rides:segment", "Ближайшие")
        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("ride:participation").assertDoesNotExist()
    }

    @Test
    fun `back leaves the page`() {
        start()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("participation").assertDoesNotExist()
    }
}
