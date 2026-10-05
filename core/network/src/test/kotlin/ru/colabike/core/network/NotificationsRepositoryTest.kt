package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingState
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationFilter
import ru.colabike.core.model.NotificationReason
import ru.colabike.core.model.NotificationsRepository

class NotificationsRepositoryTest {
    private val site = TestServer()
    private val notifications =
        NetworkNotificationsRepository(
            site.api.personal,
            site.media,
            Dispatchers.Unconfined,
        )

    @After fun close() = site.close()

    @Test
    fun `the inbox pages by cursor, newest first, and reads each notification as it came`() =
        runTest {
            site.json(200, site.fixture("notification-page.json"))

            val page = notifications.page(cursor = "c0", limit = 10)

            val request = site.server.takeRequest().url
            assertThat(request.encodedPath).isEqualTo("/api/v1/me/notifications")
            assertThat(request.queryParameter("cursor")).isEqualTo("c0")
            assertThat(request.queryParameter("limit")).isEqualTo("10")
            assertThat(page.nextCursor).isEqualTo("eyJ0IjoiMSJ9")
            val (comment, follow, expiring, future) = page.items
            assertThat(comment.kind).isEqualTo("comment")
            assertThat(comment.read).isFalse()
            assertThat(comment.createdAt).isEqualTo(Instant.parse("2026-10-03T18:30:00Z"))
            assertThat(comment.actor?.name).isEqualTo("Вторая Райдерша")
            // The comment comes as a field of the target, not out of the path.
            assertThat(comment.category).isEqualTo(NotificationCategory.Discussions)
            assertThat(comment.target.commentId).isEqualTo("b2000000-0000-4000-8000-000000000002")
            assertThat(comment.target.occurrenceAt).isNull()
            // The mark for "read all" is the server's, for the whole inbox.
            assertThat(page.watermark).isEqualTo("eyJ3IjoxfQ")
            assertThat(follow.read).isTrue()
            assertThat(follow.target.type).isEqualTo("profile")
            // The site's own notification has no actor, and a listing says how it stands.
            assertThat(expiring.actor).isNull()
            assertThat(expiring.target.state).isEqualTo(ListingState.Expiring)
            assertThat(expiring.target.expiresAt).isEqualTo(Instant.parse("2026-10-09T09:00:00Z"))
            // A kind and a target type from the future are kept as words, not dropped.
            assertThat(future.kind).isEqualTo("a_kind_from_the_future")
            assertThat(future.target.type).isEqualTo("galaxy")
            // A category from the future is kept as "other", not dropped with its notification.
            assertThat(future.category).isEqualTo(NotificationCategory.Other)
            // A ride offered in the person's area says why; a reason from the future is dropped,
            // and the others stay.
            val offer = page.items[4]
            assertThat(offer.kind).isEqualTo("plan_nearby")
            assertThat(offer.category).isEqualTo(NotificationCategory.Nearby)
            assertThat(offer.reasons)
                .containsExactly(NotificationReason.Nearby, NotificationReason.Intent)
            assertThat(comment.reasons).isEmpty()
        }

    @Test
    fun `the count is the server's number and says when it is only a floor`() = runTest {
        site.json(200, site.fixture("notification-count.json"))
        val exact = notifications.count()
        assertThat(exact.unread).isEqualTo(7)
        assertThat(exact.capped).isFalse()
        assertThat(exact.watermark).isEqualTo("eyJ3IjoxfQ")

        site.json(200, site.fixture("notification-count-capped.json"))
        val capped = notifications.count()
        assertThat(capped.unread).isEqualTo(100)
        assertThat(capped.capped).isTrue()
    }

    @Test
    fun `the filters are asked for by the words of the contract, and only when set`() = runTest {
        site.json(200, site.fixture("notification-page.json"))
        notifications.page(filter = NotificationFilter())
        val plain = site.server.takeRequest().url
        assertThat(plain.queryParameter("unread")).isNull()
        assertThat(plain.queryParameter("category")).isNull()

        site.json(200, site.fixture("notification-page.json"))
        notifications.page(
            cursor = "c1",
            filter = NotificationFilter(unreadOnly = true, category = NotificationCategory.Rides),
        )
        val narrowed = site.server.takeRequest().url
        assertThat(narrowed.queryParameter("unread")).isEqualTo("1")
        assertThat(narrowed.queryParameter("category")).isEqualTo("rides")
        assertThat(narrowed.queryParameter("cursor")).isEqualTo("c1")
    }

    @Test
    fun `opening one notification marks that one, and says how many are left`() = runTest {
        site.json(200, site.fixture("notification-read.json"))

        val result = notifications.markRead("a1000000-0000-4000-8000-000000000001")

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.url.encodedPath)
            .isEqualTo("/api/v1/me/notifications/a1000000-0000-4000-8000-000000000001/read")
        assertThat(result.marked).isEqualTo(1)
        assertThat(result.unread.unread).isEqualTo(6)
        assertThat(result.unread.capped).isFalse()
    }

    @Test
    fun `a notification that is not the person's, or no longer there, is not found`() = runTest {
        site.json(404, error("not_found"))
        assertThat(
                runCatching { notifications.markRead("a1000000-0000-4000-8000-000000000001") }
                    .exceptionOrNull()
            )
            .isInstanceOf(DataError.NotFound::class.java)
        // An id that is not an id is not sent at all.
        val before = site.server.requestCount
        assertThat(runCatching { notifications.markRead("not-an-id") }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(before)
    }

    @Test
    fun `a selection goes in batches of a hundred, and the counts add up`() = runTest {
        val ids = (1..250).map { "a1000000-0000-4000-8000-%012d".format(it) }
        site.json(200, """{"marked": 100, "unread": 150, "capped": true}""")
        site.json(200, """{"marked": 100, "unread": 50, "capped": false}""")
        site.json(200, """{"marked": 40, "unread": 10, "capped": false}""")

        val result = notifications.markRead(ids)

        val sent =
            (1..3).map {
                val request = site.server.takeRequest()
                assertThat(request.method).isEqualTo("POST")
                assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/notifications/read")
                Json.parseToJsonElement(request.body!!.utf8()).jsonObject["ids"]!!.jsonArray.size
            }
        assertThat(sent).containsExactly(100, 100, 50).inOrder()
        assertThat(result.marked).isEqualTo(240)
        // What is left is what the last answer said.
        assertThat(result.unread.unread).isEqualTo(10)
    }

    @Test
    fun `read all hands the servers mark back as it came, with the category if there is one`() =
        runTest {
            site.json(200, """{"marked": 3, "unread": 1, "capped": false}""")
            val all = notifications.markAllRead("opaque-mark-1")
            val first = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
            assertThat(first["watermark"]!!.jsonPrimitive.content).isEqualTo("opaque-mark-1")
            assertThat(first.containsKey("category")).isFalse()
            // A notice that came after the list is above the mark: it stays unread, and no second
            // request is made for it.
            assertThat(all.marked).isEqualTo(3)
            assertThat(all.unread.unread).isEqualTo(1)
            assertThat(site.server.requestCount).isEqualTo(1)

            site.json(200, """{"marked": 1, "unread": 2, "capped": false}""")
            notifications.markAllRead("opaque-mark-2", NotificationCategory.Reactions)
            val second = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
            assertThat(second["category"]!!.jsonPrimitive.content).isEqualTo("reactions")
        }

    @Test
    fun `a full read all asks again, a short one does not, and it never goes on for ever`() =
        runTest {
            val full = NotificationsRepository.READ_ALL_LIMIT
            site.json(200, """{"marked": $full, "unread": 400, "capped": true}""")
            site.json(200, """{"marked": 250, "unread": 0, "capped": false}""")

            val result = notifications.markAllRead("m")

            assertThat(site.server.requestCount).isEqualTo(2)
            assertThat(result.marked).isEqualTo(full + 250)
            assertThat(result.unread.unread).isEqualTo(0)

            // A server that keeps answering "full" is asked a few times, not endlessly.
            repeat(8) { site.json(200, """{"marked": $full, "unread": 400, "capped": true}""") }
            val before = site.server.requestCount
            notifications.markAllRead("m")
            assertThat(site.server.requestCount - before).isEqualTo(5)
        }

    @Test
    fun `a mark the server did not give is refused with the servers own words`() = runTest {
        site.json(
            400,
            """{"error":{"code":"invalid_request","message":"Проверьте поля запроса."}}""",
        )

        val failure = runCatching { notifications.markAllRead("made-up") }.exceptionOrNull()

        assertThat(failure).isInstanceOf(DataError.Rejected::class.java)
        assertThat((failure as DataError.Rejected).code).isEqualTo("invalid_request")
    }

    // --- the backend's shared examples (pinned, see SOURCE.txt) ---------------------------------

    private fun contract(name: String): JsonObject =
        Json.parseToJsonElement(
                requireNotNull(javaClass.getResource("/contracts/notifications/v1/$name")) {
                        "no contract file $name"
                    }
                    .readText()
            )
            .jsonObject

    @Test
    fun `every example of the backend reads as the server meant it`() = runTest {
        val cases = contract("targets.json")["cases"]!!.jsonArray.map { it.jsonObject }
        val page = buildJsonObject {
            put(
                "items",
                buildJsonArray { cases.forEach { add(it["notification"]!!) } },
            )
            put("nextCursor", null as String?)
            put("watermark", "mark")
        }
        site.json(200, page.toString())

        val items = notifications.page(limit = 100).items

        assertThat(items).hasSize(cases.size)
        cases.zip(items).forEach { (case, item) ->
            val wire = case["notification"]!!.jsonObject
            val target = wire["target"]!!.jsonObject
            val label = case["name"]!!.jsonPrimitive.content
            assertThat(item.id).isEqualTo(wire["id"]!!.jsonPrimitive.content)
            assertThat(item.kind).isEqualTo(wire["type"]!!.jsonPrimitive.content)
            assertThat(item.category.key).isEqualTo(wire["category"]!!.jsonPrimitive.content)
            assertThat(item.target.type).isEqualTo(target["type"]!!.jsonPrimitive.content)
            assertThat(item.target.id).isEqualTo(target["id"]!!.jsonPrimitive.content)
            val comment = target["commentId"]!!.jsonPrimitive
            assertThat(item.target.commentId)
                .isEqualTo(comment.content.takeIf { comment.content != "null" })
            val occurrence = target["occurrenceAt"]!!.jsonPrimitive
            if (occurrence.content == "null") {
                assertThat(item.target.occurrenceAt).isNull()
            } else {
                assertThat(item.target.occurrenceAt).isEqualTo(Instant.parse(occurrence.content))
            }
            assertWithMessage(label).that(item.category).isNotEqualTo(NotificationCategory.Other)
        }
        // The mark-up of a ride's agreement and the date arrive for the ride events.
        val reminder = items.first { it.kind == "ride_reminder" }
        assertThat(reminder.target.occurrenceAt).isEqualTo(Instant.parse("2026-10-10T07:00:00Z"))
        assertThat(reminder.target.agreementRevision).isEqualTo(3)
        // An invitation made before dates were counted has none, and keeps its path.
        val old = items.first { it.target.occurrenceAt == null && it.kind == "ride_invite" }
        assertThat(old.target.agreementRevision).isNull()
        assertThat(old.target.path).startsWith("/")
    }

    @Test
    fun `the read examples of the backend are answered the way the repository reads them`() =
        runTest {
            val cases = contract("read.json")["cases"]!!.jsonArray.map { it.jsonObject }

            fun case(name: String) = cases.first { it["name"]!!.jsonPrimitive.content == name }

            fun body(name: String) = case(name)["response"]!!.jsonObject["body"]!!.toString()

            val path = case("mark_one")["request"]!!.jsonObject["path"]!!.jsonPrimitive.content
            val id = path.removePrefix("/me/notifications/").removeSuffix("/read")
            site.json(200, body("mark_one"))
            val result = notifications.markRead(id)
            assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1$path")
            assertThat(result.marked).isEqualTo(1)
            assertThat(result.unread.unread).isEqualTo(2)

            // A repeat marks none and says so.
            site.json(200, body("mark_one_again"))
            assertThat(notifications.markRead(id).marked).isEqualTo(0)
            site.server.takeRequest()

            site.json(200, body("count_with_watermark"))
            val counted = notifications.count()
            site.server.takeRequest()
            assertThat(counted.unread).isEqualTo(3)
            val mark =
                Json.parseToJsonElement(body("count_with_watermark"))
                    .jsonObject["watermark"]!!
                    .jsonPrimitive
                    .content
            assertThat(counted.watermark).isEqualTo(mark)

            // "Read all" with the mark, then in a category: the mark goes back as it came.
            site.json(200, body("read_all"))
            assertThat(notifications.markAllRead(counted.watermark!!).marked).isEqualTo(3)
            val sent = site.server.takeRequest().body!!.utf8()
            assertThat(
                    Json.parseToJsonElement(sent).jsonObject["watermark"]!!.jsonPrimitive.content
                )
                .isEqualTo(mark)

            site.json(200, body("read_all_in_category"))
            val inCategory = notifications.markAllRead(mark, NotificationCategory.Reactions)
            assertThat(inCategory.marked).isEqualTo(1)
            assertThat(inCategory.unread.unread).isEqualTo(2)
        }

    @Test
    fun `a request without a session fails as signed out, a failing server as a server error`() =
        runTest {
            site.json(401, error("unauthorized"))
            assertThat(runCatching { notifications.count() }.exceptionOrNull())
                .isInstanceOf(DataError.SignedOut::class.java)

            site.json(500, error("internal_error"))
            assertThat(runCatching { notifications.page() }.exceptionOrNull())
                .isInstanceOf(DataError.Server::class.java)
        }
}
