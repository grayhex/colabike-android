package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
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
import ru.colabike.app.catalog.CatalogState
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

    private fun start(
        bikes: FakeBikes,
        signedIn: Boolean = true,
        catalog: FakeCatalog = FakeCatalog(),
    ): FakeDependencies {
        val dependencies =
            FakeDependencies(
                bikes = bikes,
                catalog = catalog,
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

    /** A value of a list: the field is opened by its tag, the value is tapped in the list. */
    private fun pick(tag: String, name: String) {
        compose.onNodeWithTag("bike-editor:$tag").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
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

    // --- the dictionaries of the site
    // -------------------------------------------------------------

    /**
     * The form of a new bike is the last step of the wizard: here it is reached by hand (no search,
     * no parts), so that the fields are the ones of the editor.
     */
    private fun openNewForm(
        catalog: FakeCatalog = FakeCatalog()
    ): Pair<FakeBikeWizard, FakeDependencies> {
        val bikes = FakeBikes(mapOf(null to Page(emptyList(), null)))
        val dependencies = start(bikes, catalog = catalog)
        compose.onNodeWithTag("bikes:add").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("wizard:manual").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("wizard:next").performScrollTo().performClick()
        compose.waitForIdle()
        return dependencies.wizard to dependencies
    }

    private fun save() {
        compose.onNodeWithTag("wizard:save").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the form asks for the dictionaries when it opens`() {
        val catalog = FakeCatalog()

        openNewForm(catalog)

        assertThat(catalog.loads).isAtLeast(1)
    }

    @Test
    fun `the type is two lists, the rest of it is one tap away`() {
        openNewForm()

        compose.onNodeWithTag("bike-editor:category").assertIsDisplayed()
        compose.onNodeWithTag("bike-editor:subtype").assertIsDisplayed()
        // Nothing of the features takes room until they are asked for.
        compose.onNodeWithTag("bike-editor:suspension:rigid").assertDoesNotExistCompat()
        compose.onNodeWithTag("bike-editor:electric").assertDoesNotExistCompat()

        click("features")

        compose.onNodeWithTag("bike-editor:suspension:rigid").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bike-editor:electric").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a bike that has features shows them from the start`() {
        val bikes = ownBikes()
        val withFeatures =
            own.fromDraft(
                BikeId("b-own"),
                own.toDraft()
                    .copy(
                        classification =
                            own.toDraft()
                                .classification
                                .copy(suspension = "hardtail", electric = true)
                    ),
                "\"v1\"",
            )
        bikes.details = mapOf("b-own" to withFeatures)
        start(bikes)
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:edit").performClick()
        compose.waitForIdle()

        compose
            .onNodeWithTag("bike-editor:suspension:hardtail")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("bike-editor:electric").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a subtype of another category is dropped when the category changes`() {
        val (wizard, _) = openNewForm()
        type("name", "Гравел")
        type("year", "2024")
        pick("category", "Шоссе / гравел")
        pick("subtype", "Gravel")
        pick("category", "MTB")
        save()

        val draft = wizard.made.single().draft
        assertThat(draft.classification.category).isEqualTo("mtb")
        assertThat(draft.classification.subtype).isNull()
    }

    @Test
    fun `a subtype is taken back by choosing none`() {
        val (wizard, _) = openNewForm()
        type("name", "Без подтипа")
        type("year", "2024")
        pick("category", "MTB")
        pick("subtype", "Trail")
        pick("subtype", "Не выбрано")
        save()

        assertThat(wizard.made.single().draft.classification.subtype).isNull()
    }

    @Test
    fun `the words and the subtypes are the site's, not the app's`() {
        val catalog = FakeCatalog().apply { site(SiteCatalogFixtures.renamed()) }
        val (wizard, _) = openNewForm(catalog)
        type("name", "Фэт")
        type("year", "2024")

        pick("category", "Горные")
        pick("subtype", "Фэтбайк")
        save()

        val draft = wizard.made.single().draft
        assertThat(draft.classification.category).isEqualTo("mtb")
        assertThat(draft.classification.subtype).isEqualTo("fat")
    }

    @Test
    fun `a brand is completed from the site's list and a brand of one's own is as good`() {
        val catalog = FakeCatalog().apply { site() }
        val (wizard, _) = openNewForm(catalog)
        type("name", "Мой")
        type("year", "2024")
        pick("category", "MTB")

        type("brand", "can")
        compose.waitForIdle()
        compose.onNode(hasText("Canyon") and hasAnyAncestor(isPopup())).assertIsDisplayed()
        compose.onNode(hasText("Cannondale") and hasAnyAncestor(isPopup())).assertIsDisplayed()
        compose.onNode(hasText("Giant") and hasAnyAncestor(isPopup())).assertDoesNotExistCompat()
        compose.onNode(hasText("Canyon") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike-editor:brand").assertTextContains("Canyon")

        // The model is offered from this brand's list only.
        type("model", "gr")
        compose.waitForIdle()
        compose.onNode(hasText("Grizl") and hasAnyAncestor(isPopup())).assertIsDisplayed()
        compose.onNode(hasText("Neuron") and hasAnyAncestor(isPopup())).assertDoesNotExistCompat()
        compose.onNode(hasText("Grizl") and hasAnyAncestor(isPopup())).performClick()
        save()

        val draft = wizard.made.single().draft
        assertThat(draft.brand).isEqualTo("Canyon")
        assertThat(draft.model).isEqualTo("Grizl")
    }

    @Test
    fun `what is typed is the value, whether the lists know it or not`() {
        val catalog = FakeCatalog().apply { site() }
        val (wizard, _) = openNewForm(catalog)
        type("name", "Самоделка")
        type("year", "2024")
        pick("category", "Специальный")

        type("brand", "Мастерская Иванова")
        type("model", "Первая")
        type("size", "54 см")
        save()

        val draft = wizard.made.single().draft
        assertThat(draft.brand).isEqualTo("Мастерская Иванова")
        assertThat(draft.model).isEqualTo("Первая")
        assertThat(draft.size).isEqualTo("54 см")
    }

    @Test
    fun `a frame size is chosen from the site's sizes`() {
        val catalog = FakeCatalog().apply { site() }
        val (wizard, _) = openNewForm(catalog)
        type("name", "Размерный")
        type("year", "2024")
        pick("category", "MTB")

        compose.onNodeWithTag("bike-editor:size").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("L") and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
        save()

        assertThat(wizard.made.single().draft.size).isEqualTo("L")
    }

    @Test
    fun `without the site's lists the form still works with the ones the app has`() {
        val (wizard, _) =
            openNewForm(FakeCatalog(CatalogState(loaded = true, refreshFailed = true)))
        type("name", "Без сети")
        type("year", "2024")

        pick("category", "Город / туризм")
        pick("subtype", "Touring")
        type("brand", "Любой")
        save()

        val draft = wizard.made.single().draft
        assertThat(draft.classification.subtype).isEqualTo("touring")
        assertThat(draft.brand).isEqualTo("Любой")
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() =
    assertDoesNotExist()
