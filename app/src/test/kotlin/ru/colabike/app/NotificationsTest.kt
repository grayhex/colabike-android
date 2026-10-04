package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.navigation.Destination
import ru.colabike.app.notifications.BellBadge
import ru.colabike.app.notifications.NotificationBadgeViewModel
import ru.colabike.app.notifications.NotificationRoute
import ru.colabike.app.notifications.NotificationsViewModel
import ru.colabike.app.notifications.badge
import ru.colabike.app.notifications.route
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.Page

class NotificationRouteTest {
    private val site = SiteLinks("https://colabike.ru")
    private val id = "6e7f8091-a2b3-4c4d-9e5f-60718293a4b5"
    private val comment = "B2000000-0000-4000-8000-000000000002"

    @Test
    fun `an object opens its own screen`() {
        assertThat(notification(1, kind = "like", type = "bike").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Bike(id)))
        assertThat(notification(1, kind = "ride_like", type = "ride").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Ride(id)))
        assertThat(notification(1, kind = "journal_like", type = "journal").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Journal(id)))
        assertThat(notification(1, kind = "follow", type = "profile").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Person(id)))
    }

    @Test
    fun `a comment opens the discussion at that comment, whatever the anchor is called`() {
        listOf("#comment-$comment", "#c-$comment", "#$comment").forEach { anchor ->
            val route = notification(1, kind = "comment", path = "/b/x-4k7m9p2x$anchor").route(site)

            assertThat(route)
                .isEqualTo(
                    NotificationRoute.InApp(
                        Destination.Comments("bike", id, "Городской Трэвел", comment.lowercase())
                    )
                )
        }
        // Rides and journal entries use the same discussion.
        assertThat(
                notification(1, kind = "ride_reply", type = "ride", path = "/r/x#comment-$comment")
                    .route(site)
            )
            .isEqualTo(
                NotificationRoute.InApp(
                    Destination.Comments("ride", id, "Городской Трэвел", comment.lowercase())
                )
            )
    }

    @Test
    fun `a comment without a readable anchor opens the object, and a like never reads one`() {
        assertThat(notification(1, kind = "comment", path = "/b/x").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Bike(id)))
        assertThat(notification(1, kind = "comment", path = "/b/x#top").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Bike(id)))
        assertThat(notification(1, kind = "like", path = "/b/x#comment-$comment").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Bike(id)))
    }

    @Test
    fun `a listing and a catalog model have screens, a comment under a model opens the discussion`() {
        assertThat(
                notification(1, kind = "market_expiring", type = "market", path = "/market/$id")
                    .route(site)
            )
            .isEqualTo(NotificationRoute.InApp(Destination.Listing(id)))
        assertThat(
                notification(1, kind = "component_like", type = "component", path = "/components/x")
                    .route(site)
            )
            .isEqualTo(NotificationRoute.InApp(Destination.Component(id)))
        assertThat(
                notification(
                        1,
                        kind = "component_reply",
                        type = "component",
                        path = "/components/x#comment-$comment",
                    )
                    .route(site)
            )
            .isEqualTo(
                NotificationRoute.InApp(
                    Destination.Comments("component", id, "Городской Трэвел", comment.lowercase())
                )
            )
    }

    @Test
    fun `a reused sign-in opens the devices, other account events and unknown objects open the site`() {
        assertThat(
                notification(1, kind = "session_reuse", type = "account", path = "/account")
                    .route(site)
            )
            .isEqualTo(NotificationRoute.InApp(Destination.Devices))
        assertThat(
                notification(1, kind = "other", type = "account", path = "/account?tab=a")
                    .route(site)
            )
            .isEqualTo(NotificationRoute.OnSite("https://colabike.ru/account?tab=a"))
        assertThat(
                notification(1, kind = "article_like", type = "article", path = "/a/x").route(site)
            )
            .isEqualTo(NotificationRoute.OnSite("https://colabike.ru/a/x"))
        assertThat(notification(1, kind = "new", type = "galaxy", path = "/galaxy/1").route(site))
            .isEqualTo(NotificationRoute.OnSite("https://colabike.ru/galaxy/1"))
    }

    @Test
    fun `a path that is not on this site, or an id that is no UUID, leads nowhere`() {
        assertThat(
                notification(1, kind = "x", type = "galaxy", path = "https://evil.example/")
                    .route(site)
            )
            .isEqualTo(NotificationRoute.Nowhere)
        assertThat(
                notification(1, kind = "x", type = "galaxy", path = "//evil.example/").route(site)
            )
            .isEqualTo(NotificationRoute.Nowhere)
        val bad = notification(1, kind = "like", type = "bike")
        val badId = bad.copy(target = bad.target.copy(id = "../me"))
        assertThat(badId.route(site)).isEqualTo(NotificationRoute.Nowhere)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `the inbox loads its first page and the next by cursor`() = runTest {
        val repository =
            FakeNotifications(
                mapOf(
                    null to Page(notifications(0, 2), "c1"),
                    "c1" to Page(notifications(2, 2), null),
                )
            )
        val vm = NotificationsViewModel(repository)

        assertThat(vm.state.value.items.map { it.id }).containsExactly("n0", "n1").inOrder()

        vm.loadMore()

        assertThat(vm.state.value.items.map { it.id }).containsExactly("n0", "n1", "n2", "n3")
        assertThat(repository.pageCalls).containsExactly(null, "c1").inOrder()
    }

    @Test
    fun `opening the inbox changes nothing on the server's side and nothing in the count`() =
        runTest {
            val repository = FakeNotifications()
            val vm = NotificationsViewModel(repository)

            // There is no operation to mark as read, so the inbox asks for pages and nothing else.
            assertThat(vm.state.value.items.map { it.read })
                .containsExactly(false, true, false)
                .inOrder()
            assertThat(repository.countCalls).isEqualTo(0)
        }

    @Test
    fun `a failed inbox offers a retry and keeps the list on a failed refresh`() = runTest {
        val repository = FakeNotifications()
        repository.nextError = DataError.Offline(java.io.IOException())
        val vm = NotificationsViewModel(repository)
        assertThat(vm.state.value.error).isNotNull()

        vm.retry()
        assertThat(vm.state.value.items).hasSize(3)

        repository.nextError = DataError.Offline(java.io.IOException())
        vm.refresh()
        assertThat(vm.state.value.items).hasSize(3)
        assertThat(vm.state.value.refreshError).isNotNull()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationBadgeTest {
    @get:Rule val main = MainDispatcherRule()

    private var clock = 1_000_000L

    private fun vm(repository: FakeNotifications) =
        NotificationBadgeViewModel(repository, now = { clock }, minIntervalMs = 30_000)

    @Test
    fun `the count is the server's, and unknown before it is asked`() = runTest {
        val repository = FakeNotifications(unread = NotificationCount(7, capped = false))
        val vm = vm(repository)

        assertThat(vm.count.value).isNull()
        vm.refresh()

        assertThat(vm.count.value).isEqualTo(NotificationCount(7, false))
    }

    @Test
    fun `asking again within half a minute is skipped unless forced`() = runTest {
        val repository = FakeNotifications()
        val vm = vm(repository)

        vm.refresh()
        clock += 10_000
        vm.refresh()
        assertThat(repository.countCalls).isEqualTo(1)

        vm.refresh(force = true)
        assertThat(repository.countCalls).isEqualTo(2)

        clock += 31_000
        vm.refresh()
        assertThat(repository.countCalls).isEqualTo(3)
    }

    @Test
    fun `a failing request keeps the last number and is asked for again at once`() = runTest {
        val repository = FakeNotifications(unread = NotificationCount(3, false))
        val vm = vm(repository)
        vm.refresh()

        clock += 40_000
        repository.nextError = DataError.Offline(java.io.IOException())
        repository.unread = NotificationCount(9, false)
        vm.refresh()
        assertThat(vm.count.value).isEqualTo(NotificationCount(3, false))

        // A failure does not start the half minute: the next try asks.
        vm.refresh()
        assertThat(vm.count.value).isEqualTo(NotificationCount(9, false))
    }

    @Test
    fun `the bell never shows a floor as an exact number`() {
        assertThat((null as NotificationCount?).badge()).isEqualTo(BellBadge.None)
        assertThat(NotificationCount(0, false).badge()).isEqualTo(BellBadge.None)
        assertThat(NotificationCount(5, false).badge()).isEqualTo(BellBadge.Exact(5))
        assertThat(NotificationCount(99, false).badge()).isEqualTo(BellBadge.Exact(99))
        assertThat(NotificationCount(100, false).badge()).isEqualTo(BellBadge.Many)
        // The server counts to 100: with `capped` there are at least that many.
        assertThat(NotificationCount(100, true).badge()).isEqualTo(BellBadge.Many)
        assertThat(NotificationCount(42, true).badge()).isEqualTo(BellBadge.Many)
    }
}
