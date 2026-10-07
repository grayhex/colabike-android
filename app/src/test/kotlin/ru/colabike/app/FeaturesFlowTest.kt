package ru.colabike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.config.AppConfigState
import ru.colabike.app.links.LinkOpener
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.navigation.Destination
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.Page
import ru.colabike.core.model.ServiceLinks

/** What the server's flags and links do to the app a person is using, on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class FeaturesFlowTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()

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

    private fun dependencies(
        appConfig: FakeAppConfig = FakeAppConfig(),
        pending: FakePending = FakePending(),
        feed: FakeFeed = FakeFeed(mapOf(null to Page(emptyList(), null))),
    ) =
        FakeDependencies(
            bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
            appConfig = appConfig,
            pending = pending,
            feed = feed,
        )

    private fun hasSection(name: String) =
        compose.onAllNodes(sectionTab(name)).fetchSemanticsNodes().isNotEmpty()

    // --- the tabs ------------------------------------------------------------------------------

    @Test
    fun `all the tabs are there when the server switches nothing off`() {
        start(dependencies())

        for (name in listOf("Лента", "Велосипеды", "Покатушки", "Чаты", "Профиль")) {
            assertThat(hasSection(name)).isTrue()
        }
    }

    @Test
    fun `a function that is off has no tab`() {
        val config =
            FakeAppConfig().apply { set(features = featuresOff(Feature.Rides, Feature.Chat)) }
        start(dependencies(config))

        assertThat(hasSection("Покатушки")).isFalse()
        assertThat(hasSection("Чаты")).isFalse()
        for (name in listOf("Лента", "Велосипеды", "Профиль")) {
            assertThat(hasSection(name)).isTrue()
        }
    }

    @Test
    fun `a change of flags while the app is open leaves the tabs, and hides the entry points`() {
        val config = FakeAppConfig()
        start(dependencies(config))
        compose.onNodeWithContentDescription("Объявления").assertIsDisplayed()

        config.set(features = featuresOff(Feature.Rides, Feature.Market))
        compose.waitForIdle()

        // The tab a person may be standing in does not vanish under them; the next start drops it.
        assertThat(hasSection("Покатушки")).isTrue()
        compose.onNodeWithContentDescription("Объявления").assertDoesNotExist()
        compose.onNodeWithContentDescription("Каталог компонентов").assertIsDisplayed()
    }

    @Test
    fun `the app waits for what the device kept, and not for anything else`() {
        val config = FakeAppConfig(AppConfigState(loaded = false))
        start(dependencies(config))
        assertThat(hasSection("Велосипеды")).isFalse()

        config.state.value = AppConfigState(loaded = true)
        compose.waitForIdle()

        assertThat(hasSection("Велосипеды")).isTrue()
    }

    // --- the entry points ----------------------------------------------------------------------

    @Test
    fun `the catalog and the market have their icons only while their functions are on`() {
        val config =
            FakeAppConfig().apply {
                set(features = featuresOff(Feature.ComponentCatalog, Feature.Market))
            }
        start(dependencies(config))

        compose.onNodeWithContentDescription("Каталог компонентов").assertDoesNotExist()
        compose.onNodeWithContentDescription("Объявления").assertDoesNotExist()
        // Search and the other tools are not about those functions.
        compose.onNodeWithContentDescription("Поиск").assertIsDisplayed()
    }

    @Test
    fun `the saved listings have a row in the profile only while the market is on`() {
        val config = FakeAppConfig().apply { set(features = featuresOff(Feature.Market)) }
        start(dependencies(config))
        compose.section("Профиль").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Записи журнала").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Объявления").assertDoesNotExist()

        config.set()
        compose.waitForIdle()
        compose.onNodeWithText("Объявления").performScrollTo().assertIsDisplayed()
    }

    // --- links and rows into what is off ---------------------------------------------------------

    @Test
    fun `a link to a listing is told it is off, and the person stays where they were`() {
        val config = FakeAppConfig().apply { set(features = featuresOff(Feature.Market)) }
        val pending = FakePending()
        start(dependencies(config, pending))

        pending.offer(Destination.Listing("l1"))
        compose.waitForIdle()

        compose.onNodeWithText("Пока недоступно").assertIsDisplayed()
        compose
            .onNodeWithText("Эта возможность сейчас выключена. Попробуйте позже.")
            .assertIsDisplayed()
        assertThat(pending.destination.value).isNull()
        compose.onNodeWithText("Понятно").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Пока недоступно").assertDoesNotExist()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a link to something that is on opens it, from wherever the person is`() {
        val dependencies = dependencies()
        start(dependencies)
        compose.section("Профиль").performClick()
        compose.waitForIdle()

        dependencies.pending.offer(Destination.Journal("j1"))
        compose.waitForIdle()

        // The journal entry of the link is open (the fake has it), over the Bikes section.
        assertThat(dependencies.journal.entryCalls).isEqualTo(1)
        compose.onNodeWithText("Пока недоступно").assertDoesNotExist()
    }

    @Test
    fun `a listing in the feed of a market that is off tells so instead of opening`() {
        val config = FakeAppConfig().apply { set(features = featuresOff(Feature.Market)) }
        val feed =
            FakeFeed(
                mapOf(
                    null to
                        Page(
                            listOf(FeedItem.Listing(PreviewData.listing, PreviewData.ride.time!!)),
                            null,
                        )
                )
            )
        val dependencies = dependencies(config, feed = feed)
        start(dependencies)
        compose.section("Лента").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("listing:${PreviewData.listing.id}").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Пока недоступно").assertIsDisplayed()
        assertThat(dependencies.market.listingCalls).isEmpty()
    }

    // --- the service links
    // -------------------------------------------------------------------------

    private fun openAbout() {
        compose.section("Профиль").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("О приложении").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `the service pages are the site's own when the server names none, and no support row`() {
        start(dependencies())
        openAbout()

        compose.onNodeWithText("Поддержка").assertDoesNotExist()
        compose.onNodeWithText("Справка").performClick()
        compose.onNodeWithText("О проекте на сайте").performClick()
        compose.onNodeWithText("Условия использования").performClick()
        compose.onNodeWithText("Политика конфиденциальности").performClick()

        assertThat(opened)
            .containsExactly(
                "https://colabike.test/about#guide",
                "https://colabike.test/about",
                "https://colabike.test/legal/terms",
                "https://colabike.test/legal/privacy",
            )
            .inOrder()
    }

    @Test
    fun `the pages the server names are the ones opened, with a support row of their own`() {
        val config =
            FakeAppConfig().apply {
                set(
                    links =
                        ServiceLinks(
                            help = "https://help.example.ru/guide",
                            privacy = "https://example.ru/privacy",
                            terms = null,
                            about = "https://example.ru/about",
                            support = "https://help.example.ru/support",
                        )
                )
            }
        start(dependencies(config))
        openAbout()

        compose.onNodeWithText("Справка").performClick()
        compose.onNodeWithText("Поддержка").performClick()
        compose.onNodeWithText("О проекте на сайте").performClick()
        compose.onNodeWithText("Политика конфиденциальности").performClick()
        // The one page the server leaves out is the site's.
        compose.onNodeWithText("Условия использования").performClick()

        assertThat(opened)
            .containsExactly(
                "https://help.example.ru/guide",
                "https://help.example.ru/support",
                "https://example.ru/about",
                "https://example.ru/privacy",
                "https://colabike.test/legal/terms",
            )
            .inOrder()
    }

    @Test
    fun `asking the server again happens when the app comes back`() {
        val config = FakeAppConfig()
        start(dependencies(config))

        assertThat(config.refreshes).isAtLeast(1)
    }
}
