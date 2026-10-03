package ru.colabike.app

import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
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
import ru.colabike.core.model.FeedFilter
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.Page

/** The feed, a bike's journal and the saved entries as a person uses them, on a phone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class FeedFlowTest {
    @get:Rule val compose = createComposeRule()

    private val published =
        listOf(
            feedBike(0),
            feedJournal(1),
            FeedItem.Ride(PreviewData.ride, PreviewData.ride.time!!),
            FeedItem.Listing(PreviewData.listing, PreviewData.ride.time!!.minusSeconds(60)),
        )

    private fun dependencies(
        signedIn: Boolean = true,
        feed: FakeFeed = FakeFeed(mapOf(null to Page(published, null))),
        journal: FakeJournal = FakeJournal(),
    ) =
        FakeDependencies(
            bikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
            feed = feed,
            journal = journal,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun section(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun openFeed() {
        section("Лента").performClick()
        compose.waitForIdle()
    }

    // --- the feed ---------------------------------------------------------------------------

    @Test
    fun `the feed shows every kind of publication, and a ride or a listing only tells`() {
        start(dependencies())

        openFeed()

        compose.onNodeWithContentDescription("Велосипед 0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Запись 1", substring = true).assertIsDisplayed()
        compose
            .onNodeWithTag("feed:grid")
            .performScrollToNode(hasContentDescription("Вечерняя по набережной", substring = true))
        compose
            .onNodeWithContentDescription("Вечерняя по набережной", substring = true)
            .assertHasNoClickAction()
        compose
            .onNodeWithTag("feed:grid")
            .performScrollToNode(hasContentDescription("Втулка Shimano Deore", substring = true))
        compose
            .onNodeWithContentDescription("Втулка Shimano Deore", substring = true)
            .assertHasNoClickAction()
    }

    @Test
    fun `the filters are the server's and each asks for its own list`() {
        val dependencies = dependencies()
        start(dependencies)
        openFeed()

        section("Записи").performClick()
        compose.waitForIdle()
        assertThat(dependencies.feed.calls.last()).isEqualTo(FeedFilter.Journal to null)

        section("Покатушки").performClick()
        compose.waitForIdle()
        assertThat(dependencies.feed.calls.last()).isEqualTo(FeedFilter.Rides to null)

        section("Всё").performClick()
        compose.waitForIdle()
        assertThat(dependencies.feed.calls.last()).isEqualTo(FeedFilter.All to null)
    }

    @Test
    fun `a bike in the feed opens the bike and Back returns to the feed`() {
        start(dependencies())
        openFeed()

        compose.onNodeWithContentDescription("Велосипед 0", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запись 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `nobody followed says what to do, and finding people opens search on the people tab`() {
        start(dependencies(feed = FakeFeed(mapOf(null to Page(emptyList(), null)))))
        openFeed()

        compose.onNodeWithText("В ленте пока пусто").assertIsDisplayed()
        compose.onNodeWithText("Найти людей").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Имя или username").assertIsDisplayed()
    }

    @Test
    fun `an empty filter says so instead of the invitation to follow`() {
        val feed = FakeFeed()
        feed.answer = { filter, _ ->
            if (filter == FeedFilter.All) Page(published, null) else Page(emptyList(), null)
        }
        start(dependencies(feed = feed))
        openFeed()

        section("Покатушки").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Покатушек пока нет").assertIsDisplayed()
        compose.onNodeWithText("Найти людей").assertDoesNotExist()
    }

    @Test
    fun `a failed feed offers a retry and the retry shows it`() {
        val feed = FakeFeed(mapOf(null to Page(published, null)))
        feed.nextError = DataError.Offline(java.io.IOException())
        start(dependencies(feed = feed))
        openFeed()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Запись 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a guest has no feed, only an explanation and a way in, and the server is not asked`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)

        openFeed()

        compose.onNodeWithText("Лента для своих").assertIsDisplayed()
        assertThat(dependencies.feed.calls).isEmpty()

        compose.onNodeWithText("Смотреть велосипеды").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).assertIsDisplayed()

        openFeed()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.feed.calls).isEmpty()
    }

    // --- an entry ---------------------------------------------------------------------------

    private fun openEntryFromFeed() {
        openFeed()
        compose.onNodeWithContentDescription("Запись 1", substring = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `an entry shows its text with structure, the snapshot of components and the bike`() {
        start(dependencies())

        openEntryFromFeed()

        compose.onNodeWithText("Запись 1").assertIsDisplayed()
        compose.onNodeWithText("Что сделал").assertIsDisplayed()
        compose.onNodeWithText("цепь KMC").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("Комплектация на момент записи")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("KMC X10").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Городской Трэвел").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `saving an entry shows it, the server is asked once, and tapping again removes it`() {
        val dependencies = dependencies()
        start(dependencies)
        openEntryFromFeed()

        compose.onNodeWithContentDescription("Сохранить запись").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(dependencies.journal.saves).containsExactly(JournalId("j1") to true)
        compose.onNodeWithContentDescription("Убрать запись из сохранённых").assertIsDisplayed()
        compose.onNodeWithText("Сохранено").assertIsDisplayed()

        compose.onNodeWithContentDescription("Убрать запись из сохранённых").performClick()
        compose.waitForIdle()
        assertThat(dependencies.journal.saves.last()).isEqualTo(JournalId("j1") to false)
        compose.onNodeWithContentDescription("Сохранить запись").assertIsDisplayed()
    }

    @Test
    fun `a refused save puts the bookmark back and says why`() {
        val dependencies = dependencies()
        dependencies.journal.saveError = DataError.Server(502, "req-9")
        start(dependencies)
        openEntryFromFeed()

        compose.onNodeWithContentDescription("Сохранить запись").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Сохранить запись").assertIsDisplayed()
        compose.onNode(hasText("req-9", substring = true)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a guest who taps save is asked to sign in and nothing is saved`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)
        openBikeJournal()
        compose.onNodeWithContentDescription("Запись 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Сохранить запись").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.journal.saves).isEmpty()
    }

    @Test
    fun `an entry that is closed answers a guest not found and offers to sign in`() {
        val journal = FakeJournal(entries = emptyMap())
        start(dependencies(signedIn = false, journal = journal))
        openBikeJournal()

        compose.onNodeWithContentDescription("Запись 1", substring = true).performClick()
        compose.waitForIdle()

        compose
            .onNodeWithText("Если это закрытая запись, войдите в свой аккаунт.")
            .assertIsDisplayed()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
    }

    @Test
    fun `the author and the bike of an entry are one tap away`() {
        val dependencies = dependencies()
        start(dependencies)
        openEntryFromFeed()

        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(dependencies.people.profileCalls).containsExactly(PreviewData.rider.id.value)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Городской Трэвел").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
    }

    // --- the journal of a bike and the saved ones
    // --------------------------------------------------

    private fun openBikeJournal() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Журнал велосипеда").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a bike page leads to its journal, entries open, Back walks the way back`() {
        val dependencies = dependencies()
        start(dependencies)

        openBikeJournal()
        assertThat(dependencies.journal.bikeCalls.map { it.first }).containsExactly(BikeId("b1"))
        compose.onNodeWithText("Велосипед 1").assertIsDisplayed() // the bike under the title
        compose.onNodeWithContentDescription("Запись 0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Запись 2", substring = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("Запись 2", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Запись 2").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запись 0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Журнал велосипеда").assertExists()
    }

    @Test
    fun `a bike without entries says so`() {
        start(dependencies(journal = FakeJournal(pages = mapOf(null to Page(emptyList(), null)))))

        openBikeJournal()

        compose.onNodeWithText("В журнале пока нет записей").assertIsDisplayed()
    }

    @Test
    fun `a failed journal offers a retry`() {
        val journal = FakeJournal()
        start(dependencies(journal = journal))
        journal.nextError = DataError.Offline(java.io.IOException())

        openBikeJournal()
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Запись 0", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the saved entries are in the profile, and an entry un-saved leaves the list`() {
        val journal = FakeJournal(savedPages = mapOf(null to Page(journals(0, 2), null)))
        val dependencies = dependencies(journal = journal)
        start(dependencies)

        section("Профиль").performClick()
        compose.onNodeWithText("Сохранённое").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Запись 0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Запись 1", substring = true).assertIsDisplayed()

        compose.onNodeWithContentDescription("Запись 1", substring = true).performClick()
        compose.waitForIdle()
        // It was learned to be saved from the list itself: the bookmark shows it.
        compose
            .onNodeWithContentDescription("Убрать запись из сохранённых")
            .performScrollTo()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Запись 0", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Запись 1", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing saved says so`() {
        start(dependencies())

        section("Профиль").performClick()
        compose.onNodeWithText("Сохранённое").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ничего не сохранено").assertIsDisplayed()
    }
}
