package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ListingState

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
            // The anchor of the comment stays in the path, for the router to read.
            assertThat(comment.target.path)
                .endsWith("#comment-b2000000-0000-4000-8000-000000000002")
            assertThat(follow.read).isTrue()
            assertThat(follow.target.type).isEqualTo("profile")
            // The site's own notification has no actor, and a listing says how it stands.
            assertThat(expiring.actor).isNull()
            assertThat(expiring.target.state).isEqualTo(ListingState.Expiring)
            assertThat(expiring.target.expiresAt).isEqualTo(Instant.parse("2026-10-09T09:00:00Z"))
            // A kind and a target type from the future are kept as words, not dropped.
            assertThat(future.kind).isEqualTo("a_kind_from_the_future")
            assertThat(future.target.type).isEqualTo("galaxy")
        }

    @Test
    fun `the count is the server's number and says when it is only a floor`() = runTest {
        site.json(200, site.fixture("notification-count.json"))
        val exact = notifications.count()
        assertThat(exact.unread).isEqualTo(7)
        assertThat(exact.capped).isFalse()

        site.json(200, site.fixture("notification-count-capped.json"))
        val capped = notifications.count()
        assertThat(capped.unread).isEqualTo(100)
        assertThat(capped.capped).isTrue()
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
