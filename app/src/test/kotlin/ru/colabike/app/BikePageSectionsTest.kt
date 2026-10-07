package ru.colabike.app

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
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
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.toDraft

/**
 * The sections of the bike page that are not the discussion (issue #56): the owner's two small
 * actions under the photo, the build that is shut until it is asked for, the latest entry of the
 * journal with its "+ Запись", and the way to the rides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikePageSectionsTest {
    @get:Rule val compose = createComposeRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )

    private fun ownBikes(detail: ru.colabike.core.model.BikeDetail = own) =
        FakeBikes(mapOf(null to Page(listOf(detail.summary), null))).also {
            it.details = mapOf("b-own" to detail)
        }

    private fun ownJournal() =
        FakeJournal(
            pages =
                mapOf(
                    null to
                        Page(
                            listOf(journalSummary(0, BikeRef(BikeId("b-own"), "Мой трейл"))),
                            null,
                        )
                )
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun dependencies(
        bikes: FakeBikes = FakeBikes(),
        journal: FakeJournal = FakeJournal(),
    ) =
        FakeDependencies(
            bikes = bikes,
            journal = journal,
            auth = FakeAuth(AuthState.SignedIn(account)),
        )

    private fun openOwn() {
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
    }

    private fun openOther() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
    }

    // --- the build -------------------------------------------------------------------------

    @Test
    fun `the build is shut when the page opens, and opens and shuts in place`() {
        start(dependencies())
        openOther()

        compose.onNodeWithText("Комплектация").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Shimano Deore 10-speed").assertDoesNotExist()

        compose.onNodeWithText("Комплектация").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Shimano Deore 10-speed").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Shimano Deore 10-speed").assertDoesNotExist()
    }

    @Test
    fun `the arrow is the same switch as the title`() {
        start(dependencies())
        openOther()

        compose.onNodeWithTag("bike:equipment-toggle").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Shimano Deore 10-speed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every component stands under its own category, the accessories after the build`() {
        start(dependencies())
        openOther()
        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()

        // The category is written over the name of each part, as the mockup has it.
        compose.onNodeWithText("РАМА").performScrollTo().assertIsDisplayed()
        assertThat(compose.onAllNodesWithText("ТРАНСМИССИЯ").fetchSemanticsNodes()).hasSize(2)
        compose.onNodeWithText("Аксессуары").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Латунный").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a note that says no more than the name is not said again`() {
        val repeated =
            own.copy(
                components =
                    listOf(
                        BikeComponent(
                            "c1",
                            "build",
                            "Вилка",
                            "SR Suntour Axon 34",
                            "sr suntour axon 34",
                        ),
                        BikeComponent("c2", "build", "Манетка", "Shimano XT M8100", "Правая"),
                    )
            )
        start(dependencies(ownBikes(repeated)))
        openOwn()
        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("SR Suntour Axon 34").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("sr suntour axon 34").assertDoesNotExist()
        compose.onNodeWithText("Правая").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a bike with no build shows no build to someone else, and a place to fill it to its owner`() {
        val bare = own.copy(components = emptyList())
        start(dependencies(ownBikes(bare)))
        openOwn()

        // The owner has the pencil: a build to fill.
        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Комплектация пока не заполнена.").assertIsDisplayed()
        compose.onNodeWithTag("bike:parts").assertExists()
    }

    @Test
    fun `someone else's bike without a build has no build card`() {
        val bare = PreviewData.bikeDetail.copy(components = emptyList())
        val bikes = FakeBikes().also { it.details = mapOf("b1" to bare) }
        start(dependencies(bikes))
        openOther()

        compose.onNodeWithText("Комплектация").assertDoesNotExist()
    }

    @Test
    fun `the pencil is the owner's and goes to the editor without opening the build`() {
        start(dependencies(ownBikes()))
        openOwn()

        compose.onNodeWithTag("bike:parts").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("parts").assertIsDisplayed()
    }

    @Test
    fun `someone else has no pencil`() {
        start(dependencies())
        openOther()

        compose.onNodeWithTag("bike:parts").assertDoesNotExist()
    }

    // --- the journal ---------------------------------------------------------------------

    @Test
    fun `the latest entry is on the page, opens, and all entries open the list`() {
        start(dependencies())
        openOther()

        compose.onNodeWithText("Журнал").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bike:journal-entry").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bike:journal-entry").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Запись 0").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:journal-all").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запись 2", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an empty journal is one quiet line`() {
        start(dependencies(journal = FakeJournal(pages = mapOf(null to Page(emptyList(), null)))))
        openOther()

        compose.onNodeWithText("Записей пока нет").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("bike:journal-all").assertDoesNotExist()
        compose.onNodeWithTag("bike:journal-new").assertDoesNotExist()
    }

    @Test
    fun `a journal that fails says so, can be tried again, and leaves the page alone`() {
        val journal = FakeJournal()
        journal.nextError = DataError.Offline(java.io.IOException())
        start(dependencies(journal = journal))
        openOther()

        compose.onNodeWithText("Не удалось загрузить журнал").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Велосипед 1").assertIsDisplayed()
        compose.onNodeWithTag("bike:rides").performScrollTo().assertIsDisplayed()

        compose
            .onNode(hasText("Повторить") and hasAnyAncestor(hasTestTag("bike:journal")))
            .performScrollTo()
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:journal-entry").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `plus entry is the owner's and opens the form of this very bike`() {
        val journal = ownJournal()
        start(dependencies(ownBikes(), journal))
        openOwn()

        compose.onNodeWithTag("bike:journal-new").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor").assertIsDisplayed()
        compose.onNodeWithTag("journal-editor:title").performTextInput("Первое обслуживание")
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(journal.created.single().first).isEqualTo(BikeId("b-own"))
    }

    @Test
    fun `someone else's bike has no plus entry`() {
        start(dependencies())
        openOther()

        compose.onNodeWithTag("bike:journal-new").assertDoesNotExist()
    }

    @Test
    fun `an entry written on the page is the one the page shows when it comes back`() {
        val journal = ownJournal()
        start(dependencies(ownBikes(), journal))
        openOwn()
        compose.onNodeWithTag("bike:journal-new").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("journal-editor:title").performTextInput("Свежая заметка")
        compose.onNodeWithTag("journal-editor:save").performScrollTo().performClick()
        compose.waitForIdle()

        // The editor goes to the entry; Back from it comes to the page of the bike.
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose
            .onNodeWithContentDescription("Свежая заметка", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- the photo ------------------------------------------------------------------------

    @Test
    fun `plus photo and manage are the owner's, under the photo`() {
        start(dependencies(ownBikes()))
        openOwn()

        compose.onNodeWithTag("bike:photo-add").assertIsDisplayed()
        compose.onNodeWithTag("bike:photos").assertIsDisplayed()
        compose.onNodeWithTag("bike:photos").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:photos").assertDoesNotExist()
    }

    @Test
    fun `plus photo opens the photos of the bike`() {
        start(dependencies(ownBikes()))
        openOwn()

        compose.onNodeWithTag("bike:photo-add").performClick()
        compose.waitForIdle()

        // The picker is the system's; what the test sees is that the bike's photos are the screen.
        compose.onNodeWithTag("bike:photo-add").assertDoesNotExist()
    }

    @Test
    fun `someone else sees no photo actions`() {
        start(dependencies())
        openOther()

        compose.onNodeWithTag("bike:photo-add").assertDoesNotExist()
        compose.onNodeWithTag("bike:photos").assertDoesNotExist()
    }

    // --- the rest of the page --------------------------------------------------------------

    @Test
    fun `the rides of the bike are one row away`() {
        val rides = FakeRides()
        start(
            dependencies().let { FakeDependencies(bikes = it.bikes, rides = rides, auth = it.auth) }
        )
        openOther()

        compose.onNodeWithTag("bike:rides").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(rides.bikeCalls.map { it.first }).contains(BikeId("b1"))
    }

    @Test
    fun `the name is said once, with the kind and the year under it`() {
        start(dependencies())
        openOther()

        assertThat(compose.onAllNodesWithText("Велосипед 1").fetchSemanticsNodes()).hasSize(1)
        compose
            .onNode(
                hasText("Город / туризм", substring = true) and hasText("2020", substring = true)
            )
            .assertIsDisplayed()
    }

    @Test
    fun `the description and the facts stay, short`() {
        start(dependencies())
        openOther()

        compose.onNodeWithText("Надёжный горный велосипед", substring = true).assertIsDisplayed()
        compose.onNode(hasTestTag("bike:passport")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Вес 14,2 кг").assertExists()
        compose.onNodeWithContentDescription("Размер рамы L").assertExists()
    }

    @Test
    fun `the buttons of the page are not smaller than a finger`() {
        start(dependencies(ownBikes(), ownJournal()))
        openOwn()

        // What counts is the area that answers a touch: a small icon may have a larger target. The
        // page is looked at from its top to its end, a part at a time.
        val density =
            ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        val problems = mutableSetOf<String>()

        fun look() {
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                val box = node.touchBoundsInRoot
                if (box.width <= 0f || box.height <= 0f) return@forEach
                if (box.height < 48 * density - 1 || box.width < 48 * density - 1) {
                    val config = node.config
                    val words =
                        config.getOrNull(SemanticsProperties.ContentDescription)
                            ?: config.getOrNull(SemanticsProperties.Text)
                    problems += "$words ${box.width / density} x ${box.height / density} dp"
                }
            }
        }
        look()
        compose.onNodeWithTag("bike:journal-all").performScrollTo()
        look()
        compose.onNodeWithTag("comments:field").performScrollTo()
        look()
        compose.onNodeWithTag("bike:rides").performScrollTo()
        look()
        compose.onNodeWithTag("bike:manufacturer").performScrollTo()
        look()

        assertThat(problems).isEmpty()
    }
}
