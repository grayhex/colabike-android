package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.DataError

class CommentsRepositoryTest {
    private val site = TestServer()
    private val comments =
        NetworkCommentsRepository(
            api = site.api.comments,
            keyed = site.api::commentsWithKey,
            media = site.media,
            dispatcher = Dispatchers.Unconfined,
        )
    private val id = "6e7f8091-a2b3-4c4d-9e5f-60718293a4b5"
    private val bike = CommentTarget(CommentKind.Bike, id)
    private val root = "aaaaaaa1-0000-4000-8000-000000000001"
    private val reply = "bbbbbbb2-0000-4000-8000-000000000002"
    private val key = "11111111-2222-4333-8444-555555555555"

    @After fun close() = site.close()

    @Test
    fun `roots come with their first replies, a tombstone has neither author nor text`() = runTest {
        site.json(200, site.fixture("comment-page.json"))

        val page = comments.threads(bike, cursor = "c0", limit = 10)

        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/bikes/$id/comments")
        assertThat(request.queryParameter("cursor")).isEqualTo("c0")
        assertThat(request.queryParameter("limit")).isEqualTo("10")
        assertThat(request.queryParameter("focus")).isNull()
        assertThat(page.nextCursor).isEqualTo("eyJjIjoiMiJ9")
        assertThat(page.focusPath).isEmpty()
        val (first, tombstone, last) = page.items
        assertThat(first.root.body).isEqualTo("Отличный велосипед!")
        assertThat(first.root.author?.displayName).isEqualTo("Тестовый Райдер")
        assertThat(first.root.replyCount).isEqualTo(2)
        assertThat(first.replies.map { it.body }).containsExactly("Согласен", "Спасибо!").inOrder()
        assertThat(first.replies[1].editedAt).isEqualTo(Instant.parse("2026-09-20T12:00:00Z"))
        assertThat(first.replies[0].parentId).isEqualTo(root)
        assertThat(tombstone.root.deleted).isTrue()
        assertThat(tombstone.root.body).isNull()
        assertThat(tombstone.root.author).isNull()
        assertThat(tombstone.replies.single().body).isEqualTo("А тут было удалено")
        assertThat(last.replies).isEmpty()
        assertThat(last.root.editedAt).isNull()
    }

    @Test
    fun `every kind of object has its own path`() = runTest {
        val paths =
            mapOf(
                CommentKind.Bike to "bikes",
                CommentKind.Journal to "journal",
                CommentKind.Ride to "rides",
                CommentKind.Component to "component-models",
            )
        paths.forEach { (kind, path) ->
            site.json(200, site.fixture("comment-page.json"))
            site.json(200, site.fixture("reply-page.json"))
            comments.threads(CommentTarget(kind, id))
            comments.replies(CommentTarget(kind, id), root)

            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/$path/$id/comments")
            assertThat(site.server.takeRequest().url.encodedPath)
                .isEqualTo("/api/v1/$path/$id/comments/$root/replies")
        }
    }

    @Test
    fun `a deep link asks for one branch and gets the path to the comment`() = runTest {
        site.json(200, site.fixture("comment-focus.json"))

        val page = comments.threads(bike, focus = reply)

        val request = site.server.takeRequest().url
        assertThat(request.queryParameter("focus")).isEqualTo(reply)
        assertThat(request.queryParameter("cursor")).isNull()
        assertThat(page.items).hasSize(1)
        assertThat(page.nextCursor).isNull()
        assertThat(page.focusPath.map { it.id }).containsExactly(root, reply).inOrder()
    }

    @Test
    fun `replies page with their own cursor`() = runTest {
        site.json(200, site.fixture("reply-page.json"))

        val page = comments.replies(bike, root, cursor = "r0")

        val request = site.server.takeRequest().url
        assertThat(request.queryParameter("cursor")).isEqualTo("r0")
        assertThat(page.items).hasSize(2)
        assertThat(page.nextCursor).isEqualTo("eyJyIjoiMiJ9")
    }

    @Test
    fun `a comment goes out trimmed with its key, and the count is announced`() = runTest {
        site.json(201, site.fixture("comment.json"))
        val announced = mutableListOf<CommentCountChange>()
        val job =
            launch(Dispatchers.Unconfined) { comments.countChanges.collect { announced += it } }

        val created = comments.post(bike, "  Спасибо!  ", parentId = root, key = key)

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/$id/comments")
        assertThat(request.headers["Idempotency-Key"]).isEqualTo(key)
        assertThat(request.headers["Content-Type"]).contains("application/json")
        val body = request.body!!.utf8()
        assertThat(body).contains("\"body\":\"Спасибо!\"")
        assertThat(body).contains("\"parentId\":\"$root\"")
        assertThat(created.id).isEqualTo(reply)
        assertThat(announced).containsExactly(CommentCountChange(bike, +1))
        job.cancel()
    }

    @Test
    fun `a root comment sends no parent at all`() = runTest {
        site.json(201, site.fixture("comment.json"))

        comments.post(bike, "Привет", parentId = null, key = key)

        assertThat(site.server.takeRequest().body!!.utf8()).doesNotContain("parentId")
    }

    @Test
    fun `the key belongs to the one request and others carry none`() = runTest {
        site.json(201, site.fixture("comment.json"))
        site.json(200, site.fixture("comment-page.json"))
        site.json(200, site.fixture("comment.json"))

        comments.post(bike, "Раз", null, key)
        comments.threads(bike)
        comments.edit(bike, reply, "Два")

        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isEqualTo(key)
        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isNull()
        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isNull()
    }

    @Test
    fun `sending the same thing twice sends the same key twice`() = runTest {
        site.json(201, site.fixture("comment.json"))
        site.json(201, site.fixture("comment.json"))

        comments.post(bike, "Раз", null, key)
        comments.post(bike, "Раз", null, key)

        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isEqualTo(key)
        assertThat(site.server.takeRequest().headers["Idempotency-Key"]).isEqualTo(key)
    }

    @Test
    fun `an empty, a too long comment and a key that is no UUID are refused without a request`() =
        runTest {
            val tooLong = "я".repeat(1_001)
            listOf("", "   ", tooLong).forEach {
                val failure = runCatching { comments.post(bike, it, null, key) }.exceptionOrNull()
                assertThat(failure).isInstanceOf(DataError.Rejected::class.java)
                assertThat(runCatching { comments.edit(bike, reply, it) }.exceptionOrNull())
                    .isInstanceOf(DataError.Rejected::class.java)
            }
            assertThat(
                    runCatching { comments.post(bike, "ok", null, "not-a-key") }.exceptionOrNull()
                )
                .isInstanceOf(DataError.NotFound::class.java)
            assertThat(site.server.requestCount).isEqualTo(0)
            // The longest allowed one goes.
            site.json(201, site.fixture("comment.json"))
            comments.post(bike, "я".repeat(1_000), null, key)
            assertThat(site.server.requestCount).isEqualTo(1)
        }

    @Test
    fun `unverified mail, limits and a hidden object are told apart`() = runTest {
        site.json(403, error("email_verification_required", "Подтвердите почту"))
        val unverified = runCatching { comments.post(bike, "Раз", null, key) }.exceptionOrNull()
        assertThat(unverified).isInstanceOf(DataError.Rejected::class.java)
        assertThat((unverified as DataError.Rejected).code).isEqualTo("email_verification_required")
        assertThat(unverified.status).isEqualTo(403)

        site.json(429, error("rate_limited"), "Retry-After", "120")
        val limited = runCatching { comments.post(bike, "Раз", null, key) }.exceptionOrNull()
        assertThat((limited as DataError.RateLimited).retryAfterSeconds).isEqualTo(120)

        site.json(404, error("not_found"))
        assertThat(runCatching { comments.post(bike, "Раз", null, key) }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)

        site.json(409, error("conflict", "Другое тело"))
        val conflict = runCatching { comments.post(bike, "Раз", null, key) }.exceptionOrNull()
        assertThat((conflict as DataError.Rejected).status).isEqualTo(409)
    }

    @Test
    fun `an edit is a PATCH with the new text and does not change the count`() = runTest {
        site.json(200, site.fixture("comment.json"))
        val announced = mutableListOf<CommentCountChange>()
        val job =
            launch(Dispatchers.Unconfined) { comments.countChanges.collect { announced += it } }

        comments.edit(bike, reply, " Новое ")

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("PATCH")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/$id/comments/$reply")
        assertThat(request.body!!.utf8()).isEqualTo("{\"body\":\"Новое\"}")
        assertThat(announced).isEmpty()
        job.cancel()
    }

    @Test
    fun `a delete is announced only when it went through`() = runTest {
        val announced = mutableListOf<CommentCountChange>()
        val job =
            launch(Dispatchers.Unconfined) { comments.countChanges.collect { announced += it } }
        site.json(404, error("not_found"))
        site.server.enqueue(MockResponse.Builder().code(204).build())

        val failed = runCatching { comments.delete(bike, reply) }.exceptionOrNull()
        assertThat(failed).isInstanceOf(DataError.NotFound::class.java)
        assertThat(announced).isEmpty()

        comments.delete(bike, reply)
        val request = site.server.takeRequest().let { site.server.takeRequest() }
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/bikes/$id/comments/$reply")
        assertThat(announced).containsExactly(CommentCountChange(bike, -1))
        job.cancel()
    }

    @Test
    fun `a malformed id is not found without a request`() = runTest {
        val broken = CommentTarget(CommentKind.Journal, "../me")
        assertThat(runCatching { comments.threads(broken) }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(runCatching { comments.replies(bike, "nope") }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(runCatching { comments.threads(bike, focus = "nope") }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(runCatching { comments.delete(bike, "nope") }.exceptionOrNull())
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }
}
