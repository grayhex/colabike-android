package ru.colabike.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Making, changing and deleting a bike as a person does (docs/adr/0022): from the garage and from
 * the bike's page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikeEditorFlowTest {
    @get:Rule val compose = createComposeRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )

    private fun start(bikes: FakeBikes, signedIn: Boolean = true): FakeDependencies {
        val dependencies =
            FakeDependencies(
                bikes = bikes,
                auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
                settings = FakeSettings(guest = !signedIn),
            )
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
        return dependencies
    }

    private fun ownBikes() =
        FakeBikes(mapOf(null to Page(listOf(own.summary), null))).also {
            it.details = mapOf("b-own" to own)
        }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag("bike-editor:$tag").performScrollTo().performTextInput(text)
    }

    private fun click(tag: String) {
        compose.onNodeWithTag("bike-editor:$tag").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a guest has no way to add a bike`() {
        start(FakeBikes(), signedIn = false)

        compose.onNodeWithTag("bikes:add").assertDoesNotExistCompat()
    }

    @Test
    fun `a bike is added from the garage and its page opens`() {
        val bikes = FakeBikes(mapOf(null to Page(emptyList(), null)))
        start(bikes)

        compose.onNodeWithTag("bikes:add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike-editor").assertIsDisplayed()
        type("name", "Новый гравел")
        type("year", "2024")
        click("category:road_gravel")
        click("subtype:gravel")
        click("save")

        val (draft, key) = bikes.created.single()
        assertThat(draft.name).isEqualTo("Новый гравел")
        assertThat(draft.year).isEqualTo(2024)
        assertThat(draft.classification.category).isEqualTo("road_gravel")
        assertThat(draft.classification.subtype).isEqualTo("gravel")
        assertThat(draft.isPublic).isFalse()
        assertThat(key).isNotEmpty()
        // The new bike's own page, not the form.
        compose.onNodeWithTag("bike-editor").assertDoesNotExistCompat()
        compose.onNodeWithText("Новый гравел").assertIsDisplayed()
    }

    @Test
    fun `a form that is not good says what is missing and sends nothing`() {
        val bikes = FakeBikes()
        start(bikes)

        compose.onNodeWithTag("bikes:add").performClick()
        compose.waitForIdle()
        click("save")

        assertThat(bikes.created).isEmpty()
        compose.onNodeWithText("Назовите велосипед.").assertIsDisplayed()
        compose.onNodeWithText("Укажите год выпуска.").assertIsDisplayed()
        // Said where the type is chosen, and with the other findings next to the button.
        compose.onAllNodesWithText("Выберите категорию.").assertCountEquals(2)
    }

    @Test
    fun `an own bike is changed from its page and the page shows the change`() {
        val bikes = ownBikes()
        start(bikes)

        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:edit").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike-editor:name").assertTextContains("Мой трейл")
        compose.onNodeWithTag("bike-editor:name").performTextClearance()
        type("name", "Переименованный")
        click("save")

        val (_, patch, version) = bikes.updated.single()
        assertThat(patch.name).isEqualTo("Переименованный")
        assertThat(version).isEqualTo("\"v1\"")
        compose.onNodeWithTag("bike-editor").assertDoesNotExistCompat()
        compose.onNodeWithText("Переименованный").assertIsDisplayed()
    }

    @Test
    fun `someone else's bike has no edit`() {
        start(FakeBikes())

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:edit").assertDoesNotExistCompat()
    }

    @Test
    fun `a bike is deleted after the question, and the page goes with it`() {
        val bikes = ownBikes()
        start(bikes)

        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:edit").performClick()
        compose.waitForIdle()
        click("delete")
        assertThat(bikes.deleted).isEmpty()
        compose.onNodeWithText("Удалить велосипед?").assertIsDisplayed()
        compose.onNodeWithTag("bike-editor:delete-confirm").performClick()
        compose.waitForIdle()

        assertThat(bikes.deleted).containsExactly(BikeId("b-own"))
        compose.onNodeWithTag("bike-editor").assertDoesNotExistCompat()
        compose.onNodeWithTag("bike:edit").assertDoesNotExistCompat()
    }

    @Test
    fun `a bike with rides is not deleted and the server says why`() {
        val bikes = ownBikes()
        start(bikes)
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:edit").performClick()
        compose.waitForIdle()
        bikes.writeError = DataError.Rejected(409, "conflict", "У велосипеда есть покатушки.")

        click("delete")
        compose.onNodeWithTag("bike-editor:delete-confirm").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("У велосипеда есть покатушки.").assertIsDisplayed()
        compose.onNodeWithTag("bike-editor").assertIsDisplayed()
    }

    @Test
    fun `publishing without a confirmed address says so and keeps the form`() {
        val bikes = FakeBikes()
        start(bikes)
        compose.onNodeWithTag("bikes:add").performClick()
        compose.waitForIdle()
        type("name", "Публичный")
        type("year", "2022")
        click("category:mtb")
        click("audience:public")
        bikes.writeError = DataError.Rejected(403, "email_verification_required", "")

        click("save")

        compose
            .onNodeWithText("Чтобы показать велосипед всем", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("bike-editor:name").assertTextContains("Публичный")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() =
    assertDoesNotExist()
