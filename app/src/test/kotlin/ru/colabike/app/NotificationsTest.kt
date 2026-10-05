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
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.NotificationFilter
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

    private fun withComment(kind: String, type: String, commentId: String?, path: String) =
        notification(1, kind = kind, type = type, path = path).let {
            it.copy(target = it.target.copy(commentId = commentId))
        }

    @Test
    fun `the comment comes from the target's field, not from the path`() {
        // The path names another comment (or none): the typed field wins.
        val other = "c3000000-0000-4000-8000-000000000003"
        val route =
            withComment("comment", "bike", comment, "/b/x?comment=$other#discussion").route(site)

        assertThat(route)
            .isEqualTo(
                NotificationRoute.InApp(
                    Destination.Comments("bike", id, "Городской Трэвел", comment.lowercase())
                )
            )
        // With the field there is no need for an anchor at all.
        assertThat(withComment("ride_reply", "ride", comment, "/r/x").route(site))
            .isEqualTo(
                NotificationRoute.InApp(
                    Destination.Comments("ride", id, "Городской Трэвел", comment.lowercase())
                )
            )
        // A field that is no UUID is not followed into the discussion.
        assertThat(withComment("comment", "bike", "../x", "/b/x").route(site))
            .isEqualTo(NotificationRoute.InApp(Destination.Bike(id)))
    }

    @Test
    fun `a friend's intention opens its page`() {
        val intent =
            notification(1, kind = "intent_published", type = "intent", path = "/ride-intents")
                .copy(target = notification(1).target.copy(type = "intent", name = "Намерение"))

        assertThat(intent.route(site)).isEqualTo(NotificationRoute.InApp(Destination.Intent(id)))
    }

    @Test
    fun `a ride's invitation, change, cancellation, answer, reminder and offer open the ride`() {
        listOf(
                "ride_invite",
                "ride_changed",
                "ride_cancelled",
                "ride_response",
                "ride_reminder",
                "plan_published",
                "plan_nearby",
            )
            .forEach { kind ->
                val ride =
                    notification(1, kind = kind, type = "ride", path = "/r/x", actor = null).let {
                        it.copy(
                            target =
                                it.target.copy(
                                    occurrenceAt = java.time.Instant.parse("2026-10-10T07:00:00Z"),
                                    agreementRevision = 3,
                                )
                        )
                    }

                assertThat(ride.route(site))
                    .isEqualTo(NotificationRoute.InApp(Destination.Ride(id)))
            }
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
    fun `reading the inbox marks nothing and does not touch the count`() = runTest {
        val repository = FakeNotifications()
        val vm = NotificationsViewModel(repository)

        assertThat(vm.state.value.items.map { it.read })
            .containsExactly(false, true, false)
            .inOrder()
        assertThat(repository.marks).isEmpty()
        assertThat(repository.countCalls).isEqualTo(0)
    }

    private fun mixed() =
        FakeNotifications(
            mapOf(
                null to
                    Page(
                        listOf(
                            notification(1, kind = "ride_invite", type = "ride"),
                            notification(2, kind = "comment"),
                            notification(3, kind = "follow", type = "profile", read = true),
                            notification(4, kind = "like"),
                        ),
                        null,
                    )
            ),
            unread = NotificationCount(3, capped = false),
        )

    @Test
    fun `unread and a category narrow the list together, and a change starts it over`() = runTest {
        val repository = mixed()
        val vm = NotificationsViewModel(repository)
        assertThat(vm.state.value.items).hasSize(4)

        vm.toggleUnreadOnly()
        assertThat(vm.state.value.items.map { it.id }).containsExactly("n1", "n2", "n4")

        vm.selectCategory(NotificationCategory.Discussions)
        assertThat(vm.ui.value.filter)
            .isEqualTo(NotificationFilter(true, NotificationCategory.Discussions))
        assertThat(vm.state.value.items.map { it.id }).containsExactly("n2")
        assertThat(repository.filters.last())
            .isEqualTo(NotificationFilter(true, NotificationCategory.Discussions))

        // The chosen category again is "all"; "unread" off lets the read ones back.
        vm.selectCategory(NotificationCategory.Discussions)
        vm.toggleUnreadOnly()
        assertThat(vm.ui.value.filter).isEqualTo(NotificationFilter())
        assertThat(vm.state.value.items).hasSize(4)
    }

    @Test
    fun `opening a notification marks it, and it shows as read once the server has said so`() =
        runTest {
            val repository = mixed()
            val counts = mutableListOf<NotificationCount>()
            val vm = NotificationsViewModel(repository, counts::add)
            val first = vm.state.value.items.first()

            vm.markRead(first)

            assertThat(repository.marks).containsExactly("one" to listOf("n1"))
            assertThat(vm.state.value.items.first().read).isTrue()
            // The bell takes the server's number; it is not counted down here.
            assertThat(counts.map { it.unread }).containsExactly(2)

            // A notification that is already read is not asked about again.
            vm.markRead(vm.state.value.items.first())
            vm.markRead(vm.state.value.items[2])
            assertThat(repository.marks).hasSize(1)
        }

    @Test
    fun `a mark that failed leaves the notification unread, and tells only if it was asked for`() =
        runTest {
            val repository = mixed()
            val vm = NotificationsViewModel(repository)
            val first = vm.state.value.items.first()

            // Opening: no word, the person has gone to the object; still unread here.
            repository.nextError = DataError.Offline(java.io.IOException())
            vm.markRead(first)
            assertThat(vm.state.value.items.first().read).isFalse()
            assertThat(vm.ui.value.message).isNull()

            // The button: the failure is told, and can be dismissed.
            repository.nextError = DataError.Offline(java.io.IOException())
            vm.markRead(first, asked = true)
            assertThat(vm.state.value.items.first().read).isFalse()
            assertThat(vm.ui.value.message).isNotNull()
            vm.dismissMessage()
            assertThat(vm.ui.value.message).isNull()

            // The next try goes through.
            vm.markRead(first, asked = true)
            assertThat(vm.state.value.items.first().read).isTrue()
        }

    @Test
    fun `read all goes up to the mark of the list, in the category chosen, and reloads the list`() =
        runTest {
            val repository = mixed()
            repository.watermark = "mark-A"
            val counts = mutableListOf<NotificationCount>()
            val vm = NotificationsViewModel(repository, counts::add)
            assertThat(vm.ui.value.watermark).isEqualTo("mark-A")

            vm.selectCategory(NotificationCategory.Rides)
            vm.readAll()

            assertThat(repository.marks).containsExactly("all" to listOf("mark-A", "rides"))
            assertThat(counts.map { it.unread }).containsExactly(2)
            assertThat(vm.ui.value.readingAll).isFalse()
            // The list was asked for again: the one ride notification is read now.
            assertThat(vm.state.value.items.map { it.read }).containsExactly(true)

            vm.selectCategory(null)
            vm.readAll()
            assertThat(repository.marks.last()).isEqualTo("all" to listOf("mark-A"))
            assertThat(vm.state.value.items.all { it.read }).isTrue()
        }

    @Test
    fun `read all is not offered before the list has a mark, and a later page does not move it`() =
        runTest {
            val repository =
                FakeNotifications(
                    mapOf(
                        null to Page(notifications(0, 2), "c1"),
                        "c1" to Page(notifications(2, 2), null),
                    )
                )
            repository.watermark = null
            val vm = NotificationsViewModel(repository)
            vm.readAll()
            assertThat(repository.marks).isEmpty()

            repository.watermark = "first"
            vm.refresh()
            // A notification that arrives while the person reads on: the second page carries a
            // newer mark, but "read all" is for what they saw at the top.
            repository.watermark = "newer"
            vm.loadMore()

            assertThat(vm.ui.value.watermark).isEqualTo("first")
        }

    @Test
    fun `a mark the server no longer knows takes a fresh list and asks to press again`() = runTest {
        val repository = mixed()
        repository.watermark = "mark-old"
        val vm = NotificationsViewModel(repository)
        repository.watermark = "mark-new"
        repository.nextError = DataError.Rejected(400, "invalid_request", "Отметка не распознана")

        vm.readAll()

        // The list was asked for again, so there is a new mark; the person is told, once.
        assertThat(vm.ui.value.watermark).isEqualTo("mark-new")
        assertThat(vm.ui.value.message).isEqualTo(UiText.Res(R.string.notifications_read_all_stale))
        assertThat(vm.ui.value.readingAll).isFalse()
        assertThat(repository.pageCalls).hasSize(2)

        vm.readAll()
        assertThat(repository.marks.last()).isEqualTo("all" to listOf("mark-new"))
    }

    @Test
    fun `a failed read all says so and changes nothing`() = runTest {
        val repository = mixed()
        val counts = mutableListOf<NotificationCount>()
        val vm = NotificationsViewModel(repository, counts::add)
        repository.nextError = DataError.Offline(java.io.IOException())

        vm.readAll()

        assertThat(vm.ui.value.message).isNotNull()
        assertThat(vm.ui.value.readingAll).isFalse()
        assertThat(counts).isEmpty()
        assertThat(vm.state.value.items.map { it.read })
            .containsExactly(false, false, true, false)
            .inOrder()
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
    fun `the server's answer to a mark is the new count, and it is not asked for again at once`() =
        runTest {
            val repository = FakeNotifications(unread = NotificationCount(5, false))
            val vm = vm(repository)
            vm.refresh()

            vm.update(NotificationCount(2, false))
            clock += 10_000
            vm.refresh()

            assertThat(vm.count.value).isEqualTo(NotificationCount(2, false))
            assertThat(repository.countCalls).isEqualTo(1)
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
