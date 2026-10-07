package ru.colabike.app

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
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.Relationship
import ru.colabike.core.model.UserId

/** From a bike to its author, follow, the lists behind the numbers, and finding people. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class PeopleFlowTest {
    @get:Rule val compose = createComposeRule()

    /** The author of every bike of the fake catalogue ([PreviewData.rider], `u1`). */
    private val author = PreviewData.rider

    private val stranger =
        Relationship(isSelf = false, following = false, followedBy = false, friends = false)

    private fun peopleWith(relationship: Relationship = stranger) =
        FakePeople(
            profiles =
                mapOf(
                    author.id.value to profileOf(author, relationship),
                    "u0" to profileOf(people(0, 1).single().person, stranger),
                )
        )

    private fun dependencies(
        signedIn: Boolean = true,
        people: FakePeople = peopleWith(),
        bikes: FakeBikes = FakeBikes(mapOf(null to Page(bikes(0, 3), null))),
    ) =
        FakeDependencies(
            bikes = bikes,
            people = people,
            auth = FakeAuth(if (signedIn) AuthState.SignedIn(account) else AuthState.SignedOut),
            settings = FakeSettings(guest = !signedIn),
        )

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private fun openAuthorFromBike() {
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Тестовый Райдер").performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun waitForSearch() {
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        compose.waitForIdle()
    }

    // --- the person's page ----------------------------------------------------------------

    @Test
    fun `the author on a bike page opens their page with the bio, numbers and bikes`() {
        val dependencies = dependencies()
        start(dependencies)

        openAuthorFromBike()

        assertThat(dependencies.people.profileCalls).containsExactly("u1")
        compose.onNodeWithText("@test-rider").assertIsDisplayed()
        compose.onNodeWithText("Катаюсь круглый год.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Подписчики, 12").assertIsDisplayed()
        compose.onNodeWithContentDescription("Подписки, 7").assertIsDisplayed()
        compose
            .onNodeWithTag("person:grid")
            .performScrollToNode(hasContentDescription("Велосипед 0", substring = true))
        compose.onNodeWithContentDescription("Велосипед 0", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a member follows at once, the count goes up, and tapping again unfollows`() {
        val dependencies = dependencies()
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").performClick()
        compose.waitForIdle()

        assertThat(dependencies.people.follows).containsExactly(UserId("u1") to true)
        compose.onNodeWithContentDescription("Отписаться от Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithContentDescription("Подписчики, 13").assertIsDisplayed()

        compose.onNodeWithContentDescription("Отписаться от Тестовый Райдер").performClick()
        compose.waitForIdle()

        assertThat(dependencies.people.follows.last()).isEqualTo(UserId("u1") to false)
        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").assertIsDisplayed()
    }

    @Test
    fun `a refused subscription puts the button back and says why`() {
        val dependencies = dependencies()
        dependencies.people.followError = DataError.Server(502, "req-7")
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").assertIsDisplayed()
        compose.onNodeWithContentDescription("Подписчики, 12").assertIsDisplayed()
        compose.onNode(hasText("req-7", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `a guest who taps follow is asked to sign in and nothing is sent`() {
        val dependencies = dependencies(signedIn = false)
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Вход в ColaBike").assertIsDisplayed()
        assertThat(dependencies.people.follows).isEmpty()
    }

    @Test
    fun `my own page says it is me and leads to the account, with no follow button`() {
        val dependencies =
            dependencies(people = peopleWith(Relationship(isSelf = true, false, false, false)))
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithText("Это вы").assertIsDisplayed()
        compose.onNodeWithContentDescription("Подписаться на Тестовый Райдер").assertDoesNotExist()

        compose.onNodeWithText("Мой аккаунт").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Мой публичный профиль").assertIsDisplayed()
    }

    @Test
    fun `a page that failed to load can be asked for again`() {
        val dependencies = dependencies()
        dependencies.people.nextError = DataError.Offline(java.io.IOException())
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Катаюсь круглый год.").assertIsDisplayed()
    }

    @Test
    fun `my public profile opens from the profile hub`() {
        val dependencies =
            dependencies(people = peopleWith(Relationship(true, false, false, false)))
        start(dependencies)

        compose.section("Профиль").performClick()
        compose.onNodeWithText("Мой публичный профиль").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(dependencies.people.profileCalls).containsExactly("u1")
        compose.onNodeWithText("Это вы").assertIsDisplayed()
    }

    // --- the lists behind the numbers -----------------------------------------------------

    @Test
    fun `followers open as a list and a row opens that person`() {
        val dependencies = dependencies()
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithContentDescription("Подписчики, 12").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Райдер 0").assertIsDisplayed()
        compose.onNodeWithText("Райдер 2").assertIsDisplayed()

        compose.onNodeWithText("Райдер 0").performClick()
        compose.waitForIdle()
        assertThat(dependencies.people.profileCalls.last()).isEqualTo("u0")
        compose.onNodeWithContentDescription("Подписаться на Райдер 0").assertIsDisplayed()
    }

    @Test
    fun `following opens its own list and Back returns to the page`() {
        val dependencies = dependencies()
        start(dependencies)
        openAuthorFromBike()

        compose.onNodeWithContentDescription("Подписки, 7").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Райдер 10").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Подписки, 7").assertIsDisplayed()
    }

    @Test
    fun `a bike on a person's page opens the bike`() {
        val dependencies = dependencies()
        start(dependencies)
        openAuthorFromBike()

        compose
            .onNodeWithTag("person:grid")
            .performScrollToNode(hasContentDescription("Велосипед 0", substring = true))
        compose.onNodeWithContentDescription("Велосипед 0", substring = true).performClick()
        compose.waitForIdle()

        // Bike 1 was the one the author was found from; this is another page.
        assertThat(dependencies.bikes.detailCalls).isEqualTo(2)
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
    }

    // --- search ---------------------------------------------------------------------------

    private fun openSearch() {
        compose.onNodeWithContentDescription("Поиск").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `search starts with an explanation, not a list`() {
        start(dependencies())

        openSearch()

        compose.onNodeWithText("Что ищем?").assertIsDisplayed()
        compose.onNodeWithText("Название, бренд, компонент").assertIsDisplayed()
    }

    @Test
    fun `typing finds builds and a card opens the bike`() {
        val dependencies = dependencies()
        dependencies.bikes.searchAnswer = { search, _ ->
            Page(bikes(0, 3).filter { it.name.contains(search.text) }, null)
        }
        start(dependencies)
        openSearch()

        compose.onNodeWithText("Название, бренд, компонент").performTextInput("Велосипед 2")
        waitForSearch()

        assertThat(dependencies.bikes.searches.last().first.text).isEqualTo("Велосипед 2")
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Велосипед 2", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Нравится", substring = true).assertExists()
    }

    @Test
    fun `facets are sent to the server and a search without hits says so`() {
        val dependencies = dependencies()
        dependencies.bikes.searchAnswer = { _, _ -> Page(emptyList(), null) }
        start(dependencies)
        openSearch()

        compose.onNode(hasText("MTB") and hasClickAction()).performClick()
        // The chips are a row that goes past the screen: the later ones are composed on demand.
        compose
            .onNodeWithTag("search:facets")
            .performScrollToNode(hasText("Электро") and hasClickAction())
        compose.onNode(hasText("Электро") and hasClickAction()).performClick()
        waitForSearch()

        val sent = dependencies.bikes.searches.last().first
        assertThat(sent.category).isEqualTo("mtb")
        assertThat(sent.electric).isTrue()
        compose.onNodeWithText("Ничего не нашли").assertIsDisplayed()
    }

    @Test
    fun `the people tab searches people by name and a row opens the person`() {
        val dependencies = dependencies()
        dependencies.people.searchAnswer = { _, _ -> Page(people(0, 2), null) }
        start(dependencies)
        openSearch()

        compose.onNode(hasText("Люди") and hasClickAction()).performClick()
        compose.onNodeWithText("Что ищем?").assertIsDisplayed()
        compose.onNodeWithText("Имя или username").performTextInput("райдер")
        waitForSearch()

        assertThat(dependencies.people.searches.last().first).isEqualTo("райдер")
        compose.onNodeWithText("Райдер 1").assertIsDisplayed()
        compose.onNodeWithText("Райдер 0").performClick()
        compose.waitForIdle()
        assertThat(dependencies.people.profileCalls.last()).isEqualTo("u0")
    }

    @Test
    fun `a failed search shows the reason and Retry asks again`() {
        val dependencies = dependencies()
        dependencies.people.searchAnswer = { _, _ -> Page(people(0, 1), null) }
        start(dependencies)
        openSearch()
        compose.onNode(hasText("Люди") and hasClickAction()).performClick()
        dependencies.people.nextError = DataError.Offline(java.io.IOException())

        compose.onNodeWithText("Имя или username").performTextInput("райдер")
        waitForSearch()
        compose.onNodeWithText("Повторить").performClick()
        waitForSearch()

        compose.onNodeWithText("Райдер 0").assertIsDisplayed()
    }

    @Test
    fun `Back from search returns to the list`() {
        start(dependencies())
        openSearch()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).assertIsDisplayed()
    }
}
