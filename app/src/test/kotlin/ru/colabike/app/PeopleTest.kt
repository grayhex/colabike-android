package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.people.PeopleListKind
import ru.colabike.app.people.PeopleListViewModel
import ru.colabike.app.people.PersonViewModel
import ru.colabike.app.search.SearchTab
import ru.colabike.app.search.SearchViewModel
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.BikeSearch
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.Relationship

@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val people = FakePeople()
    private val bikes = FakeBikes()

    private fun vm(ref: String = rider.id.value) = PersonViewModel(people, bikes, ref)

    @Test
    fun `the page loads the profile, then the person's public bikes`() = runTest {
        val vm = vm("test-rider")

        val state = vm.state.value
        assertThat(state.profile?.person?.displayName).isEqualTo("Тестовый Райдер")
        assertThat(state.bikes).hasSize(2)
        assertThat(state.loading).isFalse()
        // Asked by what opened the page (a username from a link).
        assertThat(people.profileCalls).containsExactly("test-rider")
    }

    @Test
    fun `a person who is hidden, blocked or unknown is not found`() = runTest {
        val vm = vm("nobody-here")

        assertThat(vm.state.value.profile).isNull()
        assertThat(vm.state.value.notFound).isTrue()
        assertThat(vm.state.value.error).isEqualTo(UiText.Res(R.string.error_not_found))
    }

    @Test
    fun `the bikes failing leaves the profile and offers its own retry`() = runTest {
        val vm = vm()
        people.nextError = DataError.Offline(java.io.IOException())

        vm.loadMoreBikes() // nothing more: no next page, so no request
        assertThat(people.nextError).isNotNull()
        vm.load()

        assertThat(vm.state.value.profile).isNull() // the profile request took the error
    }

    @Test
    fun `following turns the button at once and the server's count replaces the guess`() = runTest {
        val vm = vm()

        vm.toggleFollow()

        val profile = vm.state.value.profile!!
        assertThat(profile.relationship?.following).isTrue()
        assertThat(profile.counts.followers).isEqualTo(13)
        assertThat(people.follows).containsExactly(rider.id to true)
        assertThat(vm.state.value.following).isFalse()
    }

    @Test
    fun `a refusal puts the button and the count back and says why`() = runTest {
        val vm = vm()
        people.followError = DataError.Offline(java.io.IOException())

        vm.toggleFollow()

        val state = vm.state.value
        assertThat(state.profile?.relationship?.following).isFalse()
        assertThat(state.profile?.counts?.followers).isEqualTo(12)
        assertThat(state.followError).isEqualTo(UiText.Res(R.string.error_offline))
        // The next try starts clean.
        vm.toggleFollow()
        assertThat(vm.state.value.followError).isNull()
    }

    @Test
    fun `one cannot follow oneself, and a guest has nothing to follow with`() = runTest {
        val self =
            profileOf(
                relationship =
                    Relationship(
                        isSelf = true,
                        following = false,
                        followedBy = false,
                        friends = false,
                    )
            )
        val guest = profileOf(relationship = null)

        people.profiles = mapOf("me" to self, "someone" to guest)
        vm("me").toggleFollow()
        vm("someone").toggleFollow()

        assertThat(people.follows).isEmpty()
    }

    @Test
    fun `friends are those who follow each other`() = runTest {
        val follower =
            profileOf(
                relationship =
                    Relationship(
                        isSelf = false,
                        following = false,
                        followedBy = true,
                        friends = false,
                    )
            )
        people.profiles = mapOf("f" to follower)
        val vm = vm("f")

        vm.toggleFollow()

        assertThat(vm.state.value.profile?.relationship?.following).isTrue()
    }

    @Test
    fun `a subscription made elsewhere shows on the open page`() = runTest {
        val vm = vm()

        people.setFollowing(rider.id, true)
        runCurrent()

        assertThat(vm.state.value.profile?.relationship?.following).isTrue()
        assertThat(people.profileCalls).hasSize(1) // not loaded again
    }

    @Test
    fun `a like on a bike's page shows on the card here`() = runTest {
        val vm = vm()
        val card = vm.state.value.bikes.first()

        bikes.pages = mapOf(null to Page(vm.state.value.bikes, null))
        bikes.setLiked(card.id, !card.liked)
        runCurrent()

        assertThat(vm.state.value.bikes.first().liked).isEqualTo(!card.liked)
    }
}

class PeopleListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val people = FakePeople()

    @Test
    fun `followers and following are different lists of the same person`() = runTest {
        val followers = PeopleListViewModel(people, "u1", PeopleListKind.Followers)
        val following = PeopleListViewModel(people, "u1", PeopleListKind.Following)

        assertThat(followers.state.value.people).hasSize(3)
        assertThat(following.state.value.people).hasSize(2)
    }

    @Test
    fun `a failed first page is an error that load() recovers from`() = runTest {
        people.nextError = DataError.Server(502, "req-1")
        val vm = PeopleListViewModel(people, "u1", PeopleListKind.Followers)
        assertThat(vm.state.value.error)
            .isEqualTo(UiText.Res(R.string.error_server, listOf("req-1")))

        vm.load()

        assertThat(vm.state.value.error).isNull()
        assertThat(vm.state.value.people).hasSize(3)
    }

    @Test
    fun `pages add up without repeating a person, and a failed page keeps the list`() = runTest {
        val first = Page(people(0, 3), "c1")
        people.followersPage = first
        val vm = PeopleListViewModel(people, "u1", PeopleListKind.Followers)
        people.followersPage = Page(people(2, 3), null) // overlaps with u2
        vm.loadMore()

        assertThat(vm.state.value.people.map { it.person.id.value })
            .containsExactly("u0", "u1", "u2", "u3", "u4")
            .inOrder()
        assertThat(vm.state.value.nextCursor).isNull()

        // A page that fails keeps what is shown.
        people.followersPage = Page(people(0, 1), "c9")
        val again = PeopleListViewModel(people, "u1", PeopleListKind.Followers)
        people.nextError = DataError.Offline(java.io.IOException())
        again.loadMore()
        assertThat(again.state.value.people).hasSize(1)
        assertThat(again.state.value.moreError).isNotNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val bikes = FakeBikes()
    private val people = FakePeople()

    private fun vm() = SearchViewModel(bikes, people, debounceMs = 400)

    @Test
    fun `nothing is asked before there is something to ask`() = runTest {
        val vm = vm()
        advanceTimeBy(1_000)
        runCurrent()

        assertThat(bikes.searches).isEmpty()
        assertThat(vm.state.value.bikes.asked).isFalse()

        vm.selectTab(SearchTab.People)
        assertThat(people.searches).isEmpty()
    }

    @Test
    fun `typing waits for a pause and asks once with the whole text`() = runTest {
        val vm = vm()

        vm.onText("к")
        advanceTimeBy(200)
        vm.onText("кубе")
        advanceTimeBy(200)
        vm.onText("cube ")
        assertThat(bikes.searches).isEmpty()
        advanceTimeBy(401)
        runCurrent()

        assertThat(bikes.searches).containsExactly(BikeSearch(text = "cube") to null)
    }

    @Test
    fun `a facet alone is a search, and a chosen one tapped again goes`() = runTest {
        val vm = vm()

        vm.selectCategory("mtb")
        runCurrent()
        assertThat(bikes.searches.last().first).isEqualTo(BikeSearch(category = "mtb"))

        vm.toggleElectric()
        vm.selectSuspension("hardtail")
        assertThat(bikes.searches.last().first)
            .isEqualTo(BikeSearch(category = "mtb", suspension = "hardtail", electric = true))

        vm.selectCategory("mtb")
        assertThat(bikes.searches.last().first.category).isNull()
    }

    @Test
    fun `a new condition cancels the one in flight and its page never appears`() = runTest {
        val first = CompletableDeferred<Page<ru.colabike.core.model.BikeSummary>>()
        bikes.searchAnswer = { search, _ ->
            if (search.category == "mtb") first.await() else Page(bikes(30, 2), null)
        }
        val vm = vm()

        vm.selectCategory("mtb")
        vm.selectCategory("bmx") // a different condition while the first is still on its way
        first.complete(Page(bikes(0, 5), "stale"))
        runCurrent()

        assertThat(vm.state.value.bikes.items.map { it.name })
            .containsExactly("Велосипед 30", "Велосипед 31")
            .inOrder()
        assertThat(vm.state.value.bikes.nextCursor).isNull()
    }

    @Test
    fun `people need a text and each tab keeps its own results`() = runTest {
        people.searchAnswer = { _, _ -> Page(people(0, 2), "p2") }
        val vm = vm()

        vm.onText("райдер")
        advanceTimeBy(401)
        runCurrent()
        assertThat(bikes.searches).hasSize(1)

        vm.selectTab(SearchTab.People)
        runCurrent()

        assertThat(people.searches).containsExactly("райдер" to null)
        assertThat(vm.state.value.people.items).hasSize(2)
        // Back on bikes: the same condition is not asked again.
        vm.selectTab(SearchTab.Bikes)
        runCurrent()
        assertThat(bikes.searches).hasSize(1)
    }

    @Test
    fun `a failed search is an error and asking again is allowed`() = runTest {
        val vm = vm()
        bikes.nextError = DataError.Offline(java.io.IOException())

        vm.selectCategory("mtb")
        runCurrent()
        assertThat(vm.state.value.bikes.error).isEqualTo(UiText.Res(R.string.error_offline))

        vm.retry()
        runCurrent()
        assertThat(vm.state.value.bikes.error).isNull()
        assertThat(bikes.searches).hasSize(2)
    }

    @Test
    fun `clearing the text empties the results without asking`() = runTest {
        val vm = vm()
        vm.onText("cube")
        advanceTimeBy(401)
        runCurrent()
        val asked = bikes.searches.size

        vm.clearText()
        runCurrent()

        assertThat(vm.state.value.typed).isEmpty()
        assertThat(vm.state.value.bikes.asked).isFalse()
        assertThat(bikes.searches).hasSize(asked)
    }

    @Test
    fun `more pages add up without repeats, a failed page keeps the list`() = runTest {
        bikes.searchAnswer = { _, cursor ->
            if (cursor == null) Page(bikes(0, 3), "c1") else Page(bikes(2, 3), null)
        }
        val vm = vm()
        vm.selectCategory("mtb")
        runCurrent()

        vm.loadMore()
        runCurrent()

        assertThat(vm.state.value.bikes.items.map { it.id.value })
            .containsExactly("b0", "b1", "b2", "b3", "b4")
            .inOrder()
        assertThat(vm.state.value.bikes.nextCursor).isNull()
    }
}
