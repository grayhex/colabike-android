package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.toDraft

/**
 * The build of a bike as its owner changes it (docs/adr/0022): from the bike's page to the parts, a
 * part added, changed, deleted, and the order of the groups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikePartsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )

    private fun bikes() =
        FakeBikes(mapOf(null to Page(listOf(own.summary), null))).also {
            it.details = mapOf("b-own" to own)
        }

    private fun start(bikes: FakeBikes) {
        val dependencies =
            FakeDependencies(bikes = bikes, auth = FakeAuth(AuthState.SignedIn(account)))
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openParts() {
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:parts").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag("part-editor:$tag").performScrollTo().performTextInput(text)
    }

    @Test
    fun `someone else's bike has no way to its build editor`() {
        start(FakeBikes())

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:parts").assertDoesNotExist()
    }

    @Test
    fun `the build opens from the bike's page with its parts`() {
        start(bikes())

        openParts()

        compose.onNodeWithTag("parts").assertIsDisplayed()
        compose.onNodeWithText("Shimano Deore 10-speed").assertIsDisplayed()
    }

    @Test
    fun `a part is added and the build shows it`() {
        val bikes = bikes()
        start(bikes)
        openParts()

        compose.onNodeWithTag("parts:add").performClick()
        compose.waitForIdle()
        type("category", "Звонок")
        type("name", "Звонок Timber")
        compose.onNodeWithTag("part-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        val (_, draft, key) = bikes.addedParts.single()
        assertThat(draft.name).isEqualTo("Звонок Timber")
        assertThat(draft.section).isEqualTo("accessories")
        assertThat(key).isNotEmpty()
        compose.onNodeWithTag("part-editor").assertDoesNotExist()
        compose.onNodeWithText("Звонок Timber").assertIsDisplayed()
    }

    @Test
    fun `a form that is not good says what is missing and sends nothing`() {
        val bikes = bikes()
        start(bikes)
        openParts()

        compose.onNodeWithTag("parts:add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("part-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(bikes.addedParts).isEmpty()
        compose.onNodeWithText("Укажите категорию.").assertIsDisplayed()
        compose.onNodeWithText("Назовите деталь.").assertIsDisplayed()
    }

    @Test
    fun `a part is changed from the build and the build shows the change`() {
        val bikes = bikes()
        start(bikes)
        openParts()

        compose.onNodeWithTag("parts:part:c2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("part-editor:name").assertTextContains("Shimano Deore 10-speed")
        compose.onNodeWithTag("part-editor:name").performTextClearance()
        type("name", "Shimano Deore 12-speed")
        compose.onNodeWithTag("part-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(bikes.changedParts.single().second.name).isEqualTo("Shimano Deore 12-speed")
        compose.onNodeWithText("Shimano Deore 12-speed").assertIsDisplayed()
    }

    @Test
    fun `a part is deleted after the question`() {
        val bikes = bikes()
        start(bikes)
        openParts()

        compose.onNodeWithTag("parts:part:c2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("part-editor:delete").performScrollTo().performClick()
        assertThat(bikes.removedParts).isEmpty()
        compose.onNodeWithText("Удалить деталь?").assertIsDisplayed()
        compose.onNodeWithTag("part-editor:delete-confirm").performClick()
        compose.waitForIdle()

        assertThat(bikes.removedParts).containsExactly("c2")
        compose.onNodeWithTag("part-editor").assertDoesNotExist()
        compose.onNodeWithText("Shimano Deore 10-speed").assertDoesNotExist()
    }

    @Test
    fun `a group is moved and the first cannot go up`() {
        val bikes = bikes()
        start(bikes)
        openParts()

        compose.onNodeWithTag("parts:up:drivetrain").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("parts:down:drivetrain").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(bikes.groupOrders.single()).containsExactly("frame", "drivetrain").inOrder()
    }

    @Test
    fun `a refusal on a public bike is explained and the form stays`() {
        val bikes = bikes()
        start(bikes)
        openParts()
        compose.onNodeWithTag("parts:add").performClick()
        compose.waitForIdle()
        type("category", "Цепь")
        type("name", "KMC")
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")

        compose.onNodeWithTag("part-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        compose
            .onNodeWithText("Чтобы менять комплектацию публичного велосипеда", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("part-editor:name").assertTextContains("KMC")
    }
}
