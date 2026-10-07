package ru.colabike.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BuildResolution
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.ResolutionStatus
import ru.colabike.core.model.ResolveRequest

/**
 * Adding a bike as a person does (docs/adr/0028): one line to search by, the variants the server
 * finds, the parts to check, the details, and one save that is the same save when it is repeated.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikeWizardFlowTest {
    @get:Rule val compose = createComposeRule()

    private val line = "Giant Contend AR 1 2024"

    private fun start(catalog: FakeCatalog = FakeCatalog().apply { site() }): FakeDependencies {
        val dependencies =
            FakeDependencies(
                bikes = FakeBikes(mapOf(null to Page(emptyList(), null))),
                catalog = catalog,
                auth = FakeAuth(AuthState.SignedIn(account)),
                settings = FakeSettings(guest = false),
            )
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
        compose.onNodeWithTag("bikes:add").performClick()
        compose.waitForIdle()
        return dependencies
    }

    private fun click(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performScrollTo().performTextInput(text)
        compose.waitForIdle()
    }

    private fun search(text: String = line) {
        type("wizard:line", text)
        click("wizard:search")
    }

    private fun pick(tag: String, name: String) {
        click(tag)
        compose.onNode(hasText(name) and hasAnyAncestor(isPopup())).performClick()
        compose.waitForIdle()
    }

    private fun answer(
        dependencies: FakeDependencies,
        resolution: suspend (ResolveRequest) -> BuildResolution,
    ) {
        dependencies.wizard.answers += resolution
    }

    @Test
    fun `the add button opens the wizard on its first step`() {
        start()

        compose.onNodeWithText("Шаг 1 из 3 · Поиск").assertIsDisplayed()
        compose.onNodeWithTag("wizard:line").assertIsDisplayed()
        compose.onNodeWithTag("wizard:search").assertIsDisplayed()
        compose.onNodeWithTag("wizard:manual").assertIsDisplayed()
    }

    @Test
    fun `one build found is checked part by part and made with its source`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()

        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
        compose.onNodeWithText("Найдена комплектация", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("ALUXX aluminium").assertIsDisplayed()
        compose.onNodeWithText("Giant Contact").assertIsDisplayed()
        click("wizard:next")

        compose.onNodeWithText("Шаг 3 из 3 · Детали").assertIsDisplayed()
        pick("bike-editor:category", "MTB")
        click("wizard:save")

        val made = dependencies.wizard.made.single()
        assertThat(made.draft.brand).isEqualTo("Giant")
        // The model is the site's own spelling, the rest of the line is the version.
        assertThat(made.draft.model).isEqualTo("Contend")
        assertThat(made.draft.trim).isEqualTo("AR 1")
        assertThat(made.draft.year).isEqualTo(2024)
        assertThat(made.draft.isPublic).isFalse()
        assertThat(made.previewId).isEqualTo("pv-1")
        assertThat(made.identityConfirmed).isFalse()
        assertThat(made.parts.map { it.name }).containsExactly("ALUXX aluminium", "Giant Contact")
        assertThat(made.key).isNotEmpty()
        // The new bike's own page, not the wizard.
        compose.onNodeWithTag("wizard").assertDoesNotExistCompat()
    }

    @Test
    fun `several variants are offered and one is chosen by its own identifier`() {
        val dependencies = start()
        answer(dependencies) {
            FakeBikeWizard.offered(
                it.query,
                FakeBikeWizard.candidate("c-1", "Giant Contend AR 1 2024", year = 2024),
                FakeBikeWizard.candidate("c-2", "Giant Contend AR 1 2023", year = 2023),
            )
        }
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()

        compose.onNodeWithTag("wizard:offer").assertIsDisplayed()
        compose.onNodeWithTag("wizard:candidate:0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("wizard:candidate:1").performScrollTo().assertIsDisplayed()
        // A search that looked at fewer sources than it knows of does not call itself complete.
        compose.onNodeWithTag("wizard:offer-limited").assertIsDisplayed()

        click("wizard:choose:1")

        val asked = dependencies.wizard.requests
        assertThat(asked).hasSize(2)
        assertThat((asked[1] as ResolveRequest.Variant).candidateId).isEqualTo("c-2")
        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
    }

    @Test
    fun `a variant that cannot be chosen is not a button that works`() {
        val dependencies = start()
        answer(dependencies) {
            FakeBikeWizard.offered(it.query, FakeBikeWizard.candidate(null, "Страница без выбора"))
        }

        search()

        compose.onNodeWithTag("wizard:choose:0").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `a page of another model is used only when the person says so`() {
        val dependencies = start()
        answer(dependencies) {
            FakeBikeWizard.resolved(
                it.query,
                FakeBikeWizard.build(
                    it.query,
                    name = "Giant Contend AR 2",
                    identityMismatch = true,
                ),
            )
        }

        search()

        compose.onNodeWithText("Другая модель или год").assertIsDisplayed()
        compose.onNodeWithTag("wizard:question:yes").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
        compose
            .onNodeWithText("Отличие модели или года источника подтверждено вами.")
            .assertIsDisplayed()
        click("wizard:next")
        pick("bike-editor:category", "MTB")
        click("wizard:save")
        assertThat(dependencies.wizard.made.single().identityConfirmed).isTrue()
    }

    @Test
    fun `a page of another model that is declined takes nothing`() {
        val dependencies = start()
        answer(dependencies) {
            FakeBikeWizard.resolved(it.query, FakeBikeWizard.build(it.query, yearMismatch = true))
        }

        search()
        compose.onNodeWithTag("wizard:question:no").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Шаг 1 из 3 · Поиск").assertIsDisplayed()
        compose.onNodeWithText("Импорт отменён", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("wizard:line").assertIsDisplayed()
    }

    @Test
    fun `nothing found says so and the person goes on by hand`() {
        val dependencies = start()

        search()

        compose
            .onNodeWithText("Подходящей комплектации не нашли", substring = true)
            .assertIsDisplayed()
        click("wizard:manual")
        compose.onNodeWithTag("wizard:by-hand").assertIsDisplayed()
        click("wizard:add-part")
        click("wizard:next")
        // An added row with nothing in it is dropped, not sent.
        compose.onNodeWithText("Шаг 3 из 3 · Детали").assertIsDisplayed()
        assertThat(dependencies.wizard.made).isEmpty()
    }

    @Test
    fun `a line without a model is not searched`() {
        val dependencies = start()

        search("Giant")

        compose.onNodeWithText("Введите марку и модель", substring = true).assertIsDisplayed()
        assertThat(dependencies.wizard.requests).isEmpty()
    }

    @Test
    fun `a source that does not answer may be asked again`() {
        val dependencies = start()
        answer(dependencies) {
            FakeBikeWizard.failed(
                it.query,
                ResolutionStatus.Unavailable,
                "timeout",
                retryable = true,
            )
        }
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        compose.onNodeWithText("Сайт не ответил вовремя", substring = true).assertIsDisplayed()
        click("wizard:retry")

        assertThat(dependencies.wizard.requests).hasSize(2)
        assertThat(dependencies.wizard.requests[1]).isEqualTo(dependencies.wizard.requests[0])
        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
    }

    @Test
    fun `a search that is stopped shows nothing it would have said`() {
        val dependencies = start()
        answer(dependencies) { awaitCancellation() }

        search()
        compose.onNodeWithTag("wizard:resolving").assertIsDisplayed()
        click("wizard:stop")

        compose.onNodeWithTag("wizard:resolving").assertDoesNotExistCompat()
        compose.onNodeWithTag("wizard:search").assertIsEnabled()
    }

    @Test
    fun `a page of a shop is read by its address`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        type("wizard:line", line)
        click("wizard:page-toggle")
        type("wizard:page-url", "https://shop.example/giant-contend")
        click("wizard:page-read")

        val request = dependencies.wizard.requests.single() as ResolveRequest.Page
        assertThat(request.url).isEqualTo("https://shop.example/giant-contend")
        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
    }

    @Test
    fun `a part is changed, removed and added, and what is sent is what is shown`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        click("wizard:part:1:remove")
        click("wizard:add-part")
        type("wizard:part:3:category", "Звонок")
        type("wizard:part:3:name", "Рингтон")
        type("wizard:part:3:price", "350")
        click("wizard:next")
        pick("bike-editor:category", "MTB")
        click("wizard:save")

        val made = dependencies.wizard.made.single()
        assertThat(made.parts.map { it.name }).containsExactly("Giant Contact", "Рингтон")
        assertThat(made.parts.last().category).isEqualTo("Звонок")
        assertThat(made.parts.last().priceRub).isEqualTo(350.0)
    }

    @Test
    fun `a price that is not a number stops the way and is named`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        type("wizard:part:1:price", "много")
        click("wizard:next")

        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
        compose.onNodeWithTag("wizard:part:1:problem").assertIsDisplayed()
        assertThat(dependencies.wizard.made).isEmpty()
    }

    @Test
    fun `what the page says about the bike is offered and never filled in unasked`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        click("wizard:next")

        compose.onNodeWithTag("wizard:suggest-weight").performScrollTo().assertIsDisplayed()
        click("wizard:suggest-weight")
        compose.onNodeWithTag("bike-editor:weight").assertTextContains("9.8")
        compose.onNodeWithTag("wizard:suggest-weight").assertDoesNotExistCompat()
    }

    @Test
    fun `a form that is not good says what is missing and sends nothing`() {
        val dependencies = start()

        click("wizard:manual")
        click("wizard:next")
        click("wizard:save")

        assertThat(dependencies.wizard.made).isEmpty()
        compose.onNodeWithText("Укажите год выпуска.").assertIsDisplayed()
        // Said where the type is chosen, and with the other findings next to the button.
        compose.onAllNodesWithText("Выберите категорию.").assertCountEquals(2)
    }

    @Test
    fun `a lost connection keeps everything and the repeat is the same bike`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }
        dependencies.wizard.outcomes += { throw DataError.Offline(IOException("no network")) }

        search()
        click("wizard:next")
        pick("bike-editor:category", "MTB")
        click("wizard:save")

        compose.onNodeWithTag("wizard:refused").assertIsDisplayed()
        compose.onNodeWithTag("wizard").assertIsDisplayed()
        click("wizard:save")

        val (first, second) = dependencies.wizard.made
        assertThat(second.key).isEqualTo(first.key)
        assertThat(second.draft).isEqualTo(first.draft)
        compose.onNodeWithTag("wizard").assertDoesNotExistCompat()
    }

    @Test
    fun `a source that is gone is saved without or searched again`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }
        dependencies.wizard.outcomes += {
            throw DataError.Rejected(409, "preview_not_found", "", field = "previewId")
        }

        search()
        click("wizard:next")
        pick("bike-editor:category", "MTB")
        click("wizard:save")

        compose.onNodeWithTag("wizard:preview-gone").assertIsDisplayed()
        click("wizard:save-without-source")

        val made = dependencies.wizard.made
        assertThat(made).hasSize(2)
        assertThat(made[1].previewId).isNull()
        assertThat(made[1].parts.map { it.name })
            .containsExactly("ALUXX aluminium", "Giant Contact")
    }

    @Test
    fun `publishing without a confirmed address says so and keeps the form`() {
        val dependencies = start()
        dependencies.wizard.outcomes += {
            throw DataError.Rejected(403, "email_verification_required", "")
        }

        click("wizard:manual")
        click("wizard:next")
        type("bike-editor:name", "Публичный")
        type("bike-editor:year", "2022")
        pick("bike-editor:category", "MTB")
        click("bike-editor:audience:public")
        click("wizard:save")

        compose
            .onNodeWithText("Чтобы показать велосипед всем", substring = true)
            .assertIsDisplayed()
        compose.onNodeWithTag("wizard").assertIsDisplayed()
    }

    @Test
    fun `back goes a step at a time and asks before the wizard is left with something typed`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        click("wizard:next")
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Шаг 1 из 3 · Поиск").assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Выйти из мастера?").assertIsDisplayed()
        compose.onNodeWithTag("wizard:question:no").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("wizard").assertIsDisplayed()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("wizard:question:yes").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("wizard").assertDoesNotExistCompat()
        assertThat(dependencies.wizard.made).isEmpty()
    }

    @Test
    fun `an empty wizard is left without a question`() {
        start()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("wizard").assertDoesNotExistCompat()
    }

    @Test
    fun `a new search asks before it replaces the parts that were changed`() {
        val dependencies = start()
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }
        answer(dependencies) { FakeBikeWizard.resolved(it.query) }

        search()
        type("wizard:part:1:name", " исправлено")
        Espresso.pressBack()
        compose.waitForIdle()
        click("wizard:search")

        compose.onNodeWithText("Повторный поиск").assertIsDisplayed()
        compose.onNodeWithTag("wizard:question:no").performClick()
        compose.waitForIdle()
        assertThat(dependencies.wizard.requests).hasSize(1)
    }

    @Test
    fun `without the site's lists the wizard still works`() {
        val dependencies = start(FakeCatalog().apply { failed() })

        compose.onNodeWithTag("wizard:catalog-failed").assertIsDisplayed()
        click("wizard:catalog-retry")
        assertThat((dependencies.catalog as FakeCatalog).forced).isEqualTo(1)
        search()

        compose
            .onNodeWithText("Подходящей комплектации не нашли", substring = true)
            .assertIsDisplayed()
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() =
    assertDoesNotExist()
