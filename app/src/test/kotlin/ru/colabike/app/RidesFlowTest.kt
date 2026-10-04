package ru.colabike.app

import androidx.compose.ui.test.assertContentDescriptionContains
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
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.OwnRide
import ru.colabike.core.model.Page
import ru.colabike.core.model.RideRole
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.UpcomingRide

/** The Rides section as a person uses it: the lists, one's own, a ride's page, its discussion. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class RidesFlowTest {
    @get:Rule val compose = createComposeRule()

    private fun dependencies(
        signedIn: Boolean = true,
        rides: FakeRides = FakeRides(),
        feed: FakeFeed = FakeFeed(),
    ) =
        FakeDependencies(
            bikes = FakeBikes(),
            rides = rides,
            feed = feed,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun chip(name: String) = compose.onNode(hasText(name) and hasClickAction())

    private fun openRides() {
        compose.section("Покатушки").performClick()
        compose.waitForIdle()
    }

    private fun waitForSearch() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    // --- the lists -----------------------------------------------------------------------------

    @Test
    fun `the section opens on the plans ahead, said with their hour and how many answered`() {
        start(dependencies())

        openRides()

        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .assertIsDisplayed()
        compose
            .onNode(hasContentDescription("Едут: 4 · возможно: 2", substring = true))
            .assertIsDisplayed()
        compose.onNode(hasContentDescription("07:00", substring = true)).assertExists()
    }

    @Test
    fun `the completed rides are another list and a search narrows it after a pause`() {
        val rides = FakeRides()
        rides.answer = { query, _ ->
            Page(rides(0, 3).filter { query == null || it.title.contains(query) }, null)
        }
        start(dependencies(rides = rides))
        openRides()

        chip("Состоявшиеся").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Покатушка 1", substring = true).assertIsDisplayed()

        compose.onNodeWithText("Название, автор, велосипед").performTextInput("Покатушка 2")
        waitForSearch()

        assertThat(rides.completedCalls.last().first).isEqualTo("Покатушка 2")
        compose.onNodeWithContentDescription("Покатушка 2", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Покатушка 1", substring = true).assertDoesNotExist()
    }

    @Test
    fun `nothing found says so and offers to reset`() {
        val rides = FakeRides()
        rides.answer = { query, _ -> Page(if (query == null) rides(0, 2) else emptyList(), null) }
        start(dependencies(rides = rides))
        openRides()
        chip("Состоявшиеся").performClick()

        compose.onNodeWithText("Название, автор, велосипед").performTextInput("бромптон")
        waitForSearch()
        compose.onNodeWithText("Ничего не нашли").assertIsDisplayed()
        compose.onNodeWithText("Сбросить").performClick()
        waitForSearch()

        compose.onNodeWithContentDescription("Покатушка 1", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a guest has the two public lists and no personal ones`() {
        start(dependencies(signedIn = false))

        openRides()

        chip("Ближайшие").assertIsDisplayed()
        chip("Состоявшиеся").assertIsDisplayed()
        chip("Мои планы").assertDoesNotExist()
        chip("Мои поездки").assertDoesNotExist()
    }

    @Test
    fun `my plans show my part in each, what changed, and a cancelled plan is not opened`() {
        val rides = FakeRides()
        rides.myPlans =
            listOf(
                plan(1, RideRole.Organizer),
                plan(2, RideRole.Accepted, changed = true),
                UpcomingRide(
                    ride = plan(3).ride.copy(status = RideStatus.Cancelled),
                    role = RideRole.Cancelled,
                    occurrenceCancelled = false,
                    changedAfterAnswer = false,
                    meetingPoint = null,
                    meetingHidden = false,
                ),
                plan(4, RideRole.Invited).copy(meetingHidden = true),
            )
        start(dependencies(rides = rides))
        openRides()

        chip("Мои планы").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("План 1", substring = true).assertIsDisplayed()
        compose.onNode(hasContentDescription("Вы организатор", substring = true)).assertExists()
        compose
            .onNode(hasContentDescription("Условия плана изменились", substring = true))
            .assertExists()
        compose
            .onNodeWithTag("rides:list")
            .performScrollToNode(hasContentDescription("План 3", substring = true))
        compose.onNodeWithContentDescription("План 3", substring = true).assertHasNoClickAction()
        compose
            .onNodeWithTag("rides:list")
            .performScrollToNode(hasContentDescription("Точка встречи откроется", substring = true))
        assertThat(rides.myUpcomingCalls).isEqualTo(1)
    }

    @Test
    fun `my rides tell a private one apart, which has no page`() {
        val rides = FakeRides()
        rides.minePages =
            mapOf(
                null to
                    Page(
                        listOf(
                            OwnRide(rideSummary(1), true, null, 100),
                            OwnRide(rideSummary(2), false, 300, 100),
                        ),
                        null,
                    )
            )
        start(dependencies(rides = rides))
        openRides()

        chip("Мои поездки").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Покатушка 1", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Приватная", substring = true).assertHasNoClickAction()
        compose.onNodeWithText("Название, автор, велосипед").assertDoesNotExist()
    }

    @Test
    fun `an empty list says what it is for`() {
        val rides = FakeRides(upcomingPages = mapOf(null to Page(emptyList(), null)))
        start(dependencies(rides = rides))
        openRides()

        compose.onNodeWithText("Ближайших планов нет").assertIsDisplayed()
        chip("Мои планы").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Планов нет").assertIsDisplayed()
    }

    @Test
    fun `a failed list offers a retry`() {
        val rides = FakeRides()
        rides.nextError = ru.colabike.core.model.DataError.Offline(java.io.IOException())
        start(dependencies(rides = rides))

        openRides()
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .assertIsDisplayed()
    }

    // --- a ride
    // ------------------------------------------------------------------------------------

    private fun openFirstPlan() {
        openRides()
        compose
            .onNodeWithContentDescription("Воскресный выезд за город", substring = true)
            .performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a plan page says when in the device's zone, the passport, the participants and a hidden meeting`() {
        start(dependencies())

        openFirstPlan()

        compose.onNodeWithText("Воскресный выезд за город").assertIsDisplayed()
        compose.onNode(hasText("Начало:", substring = true)).assertIsDisplayed()
        compose
            .onNode(hasText("часовому поясу вашего устройства", substring = true))
            .assertIsDisplayed()
        compose
            .onNode(hasText("повторяется каждую неделю", ignoreCase = true, substring = true))
            .assertExists()
        compose.onNodeWithText("Паспорт плана").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Измайловский парк").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("25–40 км")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Да").performScrollTo().assertIsDisplayed()
        compose
            .onNode(hasText("Точка встречи видна организатору", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("Едут: 4", substring = true)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a completed ride page shows its numbers, the sensor numbers and its route`() {
        start(dependencies())
        openRides()
        chip("Состоявшиеся").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Покатушка 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("ДИСТАНЦИЯ").assertIsDisplayed()
        compose.onNode(hasText("32,5", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Показатели датчиков").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("avgHeartRate").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Схема маршрута", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a private or cancelled ride is not found, a guest is offered to sign in`() {
        val rides = FakeRides(details = emptyMap())
        start(dependencies(signedIn = false, rides = rides))
        openFirstPlan()

        compose
            .onNodeWithText("Если это закрытая покатушка, войдите в свой аккаунт.")
            .assertIsDisplayed()
        compose.onNodeWithText("Войти").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
    }

    @Test
    fun `the bike and the author of a ride are one tap away`() {
        val dependencies = dependencies()
        start(dependencies)
        openFirstPlan()

        compose.onNodeWithText("Городской Трэвел").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(dependencies.people.profileCalls).containsExactly(PreviewData.rider.id.value)
    }

    @Test
    fun `the discussion of a ride is the shared one, and its count follows on the ride's page`() {
        val dependencies = dependencies()
        dependencies.comments.threads = sampleDiscussion().threads
        start(dependencies)
        openFirstPlan()

        compose.onNodeWithText("Комментарии").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(dependencies.comments.threadCalls.last().first.kind).isEqualTo(CommentKind.Ride)
        compose.onNodeWithText("Красивая рама").assertIsDisplayed()
        compose.onNodeWithTag("comments:field").performTextInput("Еду!")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("3 комментария").performScrollTo().assertIsDisplayed()
    }

    // --- from other places -------------------------------------------------------------------

    @Test
    fun `a bike leads to its rides and a ride opens from there`() {
        val rides = FakeRides()
        start(dependencies(rides = rides))
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Покатушки велосипеда").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(rides.bikeCalls.map { it.first.value }).containsExactly("b1")
        compose.onNodeWithContentDescription("Покатушка 10", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Покатушка 10").assertIsDisplayed()
    }

    @Test
    fun `a ride in the feed opens the ride`() {
        val feed =
            FakeFeed(
                mapOf(
                    null to
                        Page(listOf(FeedItem.Ride(PreviewData.ride, PreviewData.ride.time!!)), null)
                )
            )
        start(dependencies(feed = feed))

        compose.section("Лента").performClick()
        compose.waitForIdle()
        compose
            .onNodeWithContentDescription("Вечерняя по набережной", substring = true)
            .performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вечерняя по набережной").assertIsDisplayed()
        compose.onNodeWithText("Показатели датчиков").performScrollTo().assertIsDisplayed()
    }

    // --- the route and its charts ----------------------------------------------------------

    private fun openCompleted(rides: FakeRides = FakeRides()) {
        start(dependencies(rides = rides))
        openRides()
        chip("Состоявшиеся").performClick()
        compose.onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a ride with a route shows its drawing and the way to the map`() {
        openCompleted()

        compose
            .onNodeWithContentDescription("Схема маршрута", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Маршрут разорван", substring = true).assertExists()
        compose.onNodeWithText("Открыть карту").assertExists()
    }

    @Test
    fun `the map opens on its own screen, says it is only a route, and Back returns to the page`() {
        openCompleted()

        compose.onNodeWithText("Открыть карту").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Маршрут").assertIsDisplayed()
        compose.onNodeWithText("Подложка карты не подключена", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Открыть карту").assertDoesNotExist()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Покатушка 0").assertIsDisplayed()
        compose.onNodeWithText("Открыть карту").assertExists()
    }

    @Test
    fun `the charts come in a request of their own, with a summary instead of the picture`() {
        val rides = FakeRides()
        openCompleted(rides)

        assertThat(rides.analysisCalls).containsExactly("ride-0")
        compose.onNodeWithText("Разбор маршрута").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Высота: от", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Скорость: от", substring = true).assertExists()
        compose.onNodeWithContentDescription("Уклон: от", substring = true).assertExists()
        // Heart rate has a gap, and the summary says so.
        compose
            .onNodeWithContentDescription("Пульс: от", substring = true)
            .assertContentDescriptionContains("есть разрывы", substring = true)
        compose.onNodeWithContentDescription("Мощность", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a ride the server has no analysis for shows its route and no charts`() {
        val rides = FakeRides(analyses = emptyMap())
        openCompleted(rides)

        compose.onNodeWithContentDescription("Схема маршрута", substring = true).assertExists()
        compose.onNodeWithText("Разбор маршрута").assertDoesNotExist()
    }

    @Test
    fun `a failed series request offers a retry and the page is still there`() {
        val rides = FakeRides()
        rides.nextError = null
        start(dependencies(rides = rides))
        openRides()
        chip("Состоявшиеся").performClick()
        compose.waitForIdle()
        // The page loads, then the series fail once.
        rides.failAnalysisOnce = true
        compose.onNodeWithContentDescription("Покатушка 0", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Покатушка 0").assertIsDisplayed()
        compose.onNodeWithText("Не удалось загрузить разбор маршрута.").performScrollTo()
        compose.onNodeWithText("Повторить").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Высота: от", substring = true).assertExists()
        assertThat(rides.analysisCalls).containsExactly("ride-0", "ride-0")
    }

    @Test
    fun `a plan has no route section and asks for no series`() {
        val rides = FakeRides()
        start(dependencies(rides = rides))
        openFirstPlan()

        compose.onNodeWithText("Открыть карту").assertDoesNotExist()
        compose.onNodeWithText("Разбор маршрута").assertDoesNotExist()
        assertThat(rides.analysisCalls).isEmpty()
    }
}
