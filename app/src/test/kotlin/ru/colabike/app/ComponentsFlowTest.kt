package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentQuery
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page

/** From the Bikes section to the catalog and a model, and from a bike's build to a model. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class ComponentsFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

    private fun start(
        components: FakeComponents = FakeComponents(),
        comments: FakeComments = FakeComments(),
        bikes: FakeBikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
    ): FakeDependencies {
        val dependencies =
            FakeDependencies(bikes = bikes, components = components, comments = comments)
        compose.setContent {
            ColaBikeTheme {
                CompositionLocalProvider(LocalLinkOpener provides LinkOpener { opened += it }) {
                    ColaBikeApp(dependencies)
                }
            }
        }
        compose.waitForIdle()
        return dependencies
    }

    private fun openCatalog() {
        compose.onNodeWithContentDescription("Каталог компонентов").performClick()
        compose.waitForIdle()
    }

    private fun settle() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    @Test
    fun `the bikes screen opens the catalog, which lists the models with brand, category and builds`() {
        start()

        openCatalog()

        compose.onNodeWithText("Каталог компонентов").assertIsDisplayed()
        compose.onNodeWithTag("component:c1").assertIsDisplayed()
        compose.onNodeWithText("Кассета 1").assertIsDisplayed()
        // The card reads as one phrase, with the count in words.
        compose.onNodeWithText("На 3 велосипедах").assertExists()
    }

    @Test
    fun `a model opens its page with the description, the credits and a way to the discussion`() {
        start()
        openCatalog()

        compose.onNodeWithTag("component:c1").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Кассета 1").assertIsDisplayed()
        compose.onNodeWithText("Кассета 1 для горного велосипеда.").assertIsDisplayed()
        compose.onNodeWithTag("component:page").performScrollToNode(hasText("Фотографии"))
        compose.onNodeWithText("Иван Фотограф / Wikimedia Commons").assertExists()
        compose.onNodeWithText("Источник: Wikimedia Commons, лицензия CC BY-SA 4.0").assertExists()
    }

    @Test
    fun `the links of a source open in the browser, only the https ones`() {
        start()
        openCatalog()
        compose.onNodeWithTag("component:c1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("component:page").performScrollToNode(hasText("Фотографии"))

        compose.onNodeWithText("Лицензия: CC BY-SA 4.0").performScrollTo().performClick()
        compose
            .onNodeWithText("Источник фотографии: Wikimedia Commons")
            .performScrollTo()
            .performClick()

        assertThat(opened)
            .containsExactly(
                "https://creativecommons.org/licenses/by-sa/4.0/",
                "https://commons.wikimedia.org/wiki/File:Deore.jpg",
            )
            .inOrder()
    }

    @Test
    fun `the discussion of a model is the shared comments screen`() {
        val comments = sampleDiscussion()
        start(comments = comments)
        openCatalog()
        compose.onNodeWithTag("component:c1").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("component:comments").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(comments.threadCalls.first().first.kind.name).isEqualTo("Component")
        assertThat(comments.threadCalls.first().first.id).isEqualTo("c1")
    }

    @Test
    fun `a model merged into another opens as the canonical one`() {
        val components = FakeComponents(aliases = mapOf("old" to "c2"))
        start(components)

        // A bike whose build still carries the old id: the page asks for it and shows the new.
        components.pages = mapOf(null to Page(componentModels(1, 2), null))
        openCatalog()
        compose.onNodeWithTag("component:c2").performClick()
        compose.waitForIdle()

        assertThat(components.modelCalls).containsExactly("c2")
        assertThat(components.photoCalls).containsExactly("c2")
    }

    @Test
    fun `an archived model says that it is out of the catalog`() {
        val archived = componentModel(1, archived = true)
        start(
            FakeComponents(
                pages = mapOf(null to Page(listOf(archived), null)),
                models = mapOf("c1" to archived),
            )
        )
        openCatalog()

        // The badge is written in capitals by the design system.
        compose.onNodeWithText("Архивная модель", ignoreCase = true).assertExists()
        compose.onNodeWithTag("component:c1").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Архивная модель", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("Её уже нет в каталоге", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a model that is not there is told so, not shown as an error with a retry`() {
        val components = FakeComponents(models = emptyMap())
        start(components)
        openCatalog()

        compose.onNodeWithTag("component:c1").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Модель не найдена").assertIsDisplayed()
        compose.onNodeWithText("Повторить").assertDoesNotExist()
    }

    @Test
    fun `search, category and brand go to the server, and nothing found offers a reset`() {
        val components = FakeComponents()
        start(components)
        openCatalog()

        compose.onNodeWithTag("components:search").performTextInput("sram")
        settle()
        assertThat(components.queries.last().first).isEqualTo(ComponentQuery(text = "sram"))

        components.pages = mapOf(null to Page(emptyList(), null))
        compose.onNodeWithTag("components:category").performClick()
        compose.onNodeWithText("Тормоза").performClick()
        settle()
        assertThat(components.queries.last().first)
            .isEqualTo(ComponentQuery(text = "sram", category = "Тормоза"))
        compose.onNodeWithText("Ничего не нашли").assertIsDisplayed()

        components.pages = mapOf(null to Page(componentModels(1, 2), null))
        compose.onNodeWithText("Сбросить").performClick()
        settle()
        assertThat(components.queries.last().first).isEqualTo(ComponentQuery())
        compose.onNodeWithTag("component:c1").assertIsDisplayed()
    }

    @Test
    fun `a failure of the catalog is an error with a retry`() {
        val components = FakeComponents()
        components.nextError = DataError.Offline(java.io.IOException("x"))
        start(components)
        openCatalog()
        compose.onNodeWithText("Повторить").assertIsDisplayed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("component:c1").assertIsDisplayed()
    }

    @Test
    fun `a component of a bike's build that is in the catalog opens its model`() {
        val components = FakeComponents()
        start(components)

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        // Only a row with a model is a button; the others have no page to open.
        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Shimano Deore 10-speed").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(components.modelCalls)
            .containsExactly(PreviewData.bikeDetail.components[1].modelId)
        compose.onNodeWithText("Кассета 1 для горного велосипеда.").assertIsDisplayed()
        assertThat(ComponentId("c1").value).isEqualTo("c1")
    }
}
