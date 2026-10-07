package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.LocalSharer
import ru.colabike.app.links.Sharer
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.Page

/** Find a bike, look at it, like it, show it to someone: the screens as a person uses them. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikeFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val shared = mutableListOf<Pair<String, String>>()

    private val catalogue: List<BikeSummary> =
        listOf(
            PreviewData.bike.copy(
                id = BikeId("b-cube"),
                name = "Городской Cube",
                brand = "Cube",
                liked = false,
            ),
            PreviewData.bike.copy(
                id = BikeId("b-trek"),
                name = "Горный Trek",
                brand = "Trek",
                category = "mtb",
                liked = false,
            ),
            PreviewData.bike.copy(
                id = BikeId("b-own"),
                name = "Мой гравел",
                brand = "Canyon",
                isOwner = true,
                liked = false,
            ),
        )

    /** A server that filters the catalogue by text, as `/bikes?q=` does. */
    private fun dependencies(signedIn: Boolean = true): FakeDependencies {
        val bikes = FakeBikes(mapOf(null to Page(catalogue, null)))
        bikes.answer = { query, _ ->
            Page(
                catalogue.filter {
                    query.text.isBlank() ||
                        it.name.contains(query.text, ignoreCase = true) ||
                        it.brand.contains(query.text, ignoreCase = true)
                },
                null,
            )
        }
        return FakeDependencies(
            bikes = bikes,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )
    }

    private fun start(dependencies: FakeDependencies) {
        compose.setContent {
            CompositionLocalProvider(
                LocalLinkOpener provides LinkOpener { opened += it },
                LocalSharer provides Sharer { title, url -> shared += title to url },
            ) {
                ColaBikeTheme { ColaBikeApp(dependencies) }
            }
        }
        compose.waitForIdle()
    }

    /** The list's cards below the first screen are not composed until scrolled to. */
    private fun open(name: String) {
        compose
            .onNodeWithTag("bikes:grid")
            .performScrollToNode(hasContentDescription(name, substring = true))
        compose.onNodeWithContentDescription(name, substring = true).performClick()
        compose.waitForIdle()
    }

    /** The view model's pause is on the main looper's clock, which the test must move. */
    private fun waitForSearch() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    // --- search and filters ---------------------------------------------------------------

    @Test
    fun `typing narrows the list after a pause, and a cleared field brings everything back`() {
        start(dependencies())

        compose.onNodeWithText("Название, бренд, автор").performTextInput("trek")
        waitForSearch()

        compose.onNodeWithContentDescription("Горный Trek", substring = true).assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Городской Cube", substring = true)
            .assertDoesNotExist()

        compose.onNodeWithContentDescription("Очистить поиск").performClick()
        waitForSearch()

        compose.onNodeWithContentDescription("Городской Cube", substring = true).assertIsDisplayed()
    }

    @Test
    fun `nothing found says so and offers to reset`() {
        start(dependencies())

        compose.onNodeWithText("Название, бренд, автор").performTextInput("бромптон")
        waitForSearch()

        compose.onNodeWithText("Ничего не нашли").assertIsDisplayed()
        compose.onNodeWithText("Сбросить").performClick()
        waitForSearch()
        compose.onNodeWithContentDescription("Городской Cube", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a category chip is sent to the server and tapped again it goes`() {
        val dependencies = dependencies()
        start(dependencies)

        compose.onNode(hasText("MTB") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertThat(dependencies.bikes.calls.last().first.categories).containsExactly("mtb")

        compose.onNode(hasText("MTB") and hasClickAction()).performClick()
        compose.waitForIdle()
        assertThat(dependencies.bikes.calls.last().first.categories).isEmpty()
    }

    // --- the bike page --------------------------------------------------------------------

    @Test
    fun `the page shows what the owner gave, the price only because it is given`() {
        start(dependencies())
        open("Городской Cube")

        compose.onNodeWithText("Город / туризм · Touring").assertIsDisplayed()

        compose.onNodeWithText("ЦЕНА").performScrollTo().assertIsDisplayed()
        compose
            .onNode(hasText("85", substring = true) and hasText("₽", substring = true))
            .assertExists()
        compose.onNodeWithText("Shimano Deore 10-speed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `no price block for a bike whose owner does not show one`() {
        val dependencies = dependencies()
        dependencies.bikes.details =
            mapOf(
                "b-cube" to
                    PreviewData.bikeDetail.copy(
                        summary = catalogue[0],
                        priceRub = null,
                        components =
                            PreviewData.bikeDetail.components.map { it.copy(priceRub = null) },
                    )
            )
        start(dependencies)
        open("Городской Cube")

        compose.onNodeWithText("ЦЕНА").assertDoesNotExist()
        compose.onNode(hasText("₽", substring = true)).assertDoesNotExist()
    }

    @Test
    fun `the owner's group order is the page's order`() {
        start(dependencies())
        open("Городской Cube")

        // PreviewData: groupOrder = drivetrain, frame.
        val drivetrain = compose.onNodeWithText("Shimano Deore 10-speed").performScrollTo()
        val frame = compose.onNodeWithText("Cube Aluminium Superlite").performScrollTo()
        assertThat(drivetrain.getUnclippedBoundsInRoot().top)
            .isLessThan(frame.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun `the maker's pages open outside the app`() {
        start(dependencies())
        open("Городской Cube")

        compose.onNodeWithText("Страница производителя").performScrollTo().performClick()
        compose
            .onNodeWithContentDescription(
                "Страница компонента «Shimano Deore 10-speed» на сайте производителя"
            )
            .performScrollTo()
            .performClick()

        assertThat(opened)
            .containsExactly("https://www.cube.eu/travel-sl", "https://example.test/deore")
            .inOrder()
    }

    // --- like and share -------------------------------------------------------------------

    @Test
    fun `a like is given and shown, and the list agrees when the person goes back`() {
        val dependencies = dependencies()
        start(dependencies)
        open("Горный Trek")

        compose.onNodeWithContentDescription("Нравится, 4").performClick()
        compose.waitForIdle()

        assertThat(dependencies.bikes.likes).containsExactly(BikeId("b-trek") to true)
        compose.onNodeWithContentDescription("Нравится, 5").assertIsDisplayed()
    }

    @Test
    fun `a guest who taps the heart is asked to sign in and nothing is given`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)
        open("Горный Trek")

        compose.onNodeWithContentDescription("Нравится, 4").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.bikes.likes).isEmpty()
    }

    @Test
    fun `one's own bike shows its count and no switch`() {
        val dependencies = dependencies()
        start(dependencies)
        open("Мой гравел")

        compose.onNodeWithContentDescription("Нравится, 4").assertHasNoClickAction()
        assertThat(dependencies.bikes.likes).isEmpty()
    }

    @Test
    fun `sharing offers the address the site redirects to the canonical page`() {
        start(dependencies())
        open("Городской Cube")

        compose.onNodeWithText("Поделиться").performClick()

        assertThat(shared).containsExactly("Городской Cube" to "https://colabike.test/b/b-cube")
    }

    // --- the photos -----------------------------------------------------------------------

    @Test
    fun `the gallery counts the photos and opens them full screen`() {
        start(dependencies())
        open("Городской Cube")

        compose.onNodeWithText("1 / 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Фото 1 из 3").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Фото 1 из 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Фото 1 из 3").assertDoesNotExist()
        compose.onNodeWithText("1 / 3").assertIsDisplayed()
    }
}
