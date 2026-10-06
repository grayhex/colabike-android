package ru.colabike.core.network

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.BlockChange
import ru.colabike.core.model.DataError
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportReason
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.UserId

class SafetyRepositoryTest {
    private val site = TestServer()
    private val safety =
        NetworkSafetyRepository(site.api.safety, site.media, Dispatchers.Unconfined)
    private val id = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"

    @After fun close() = site.close()

    @Test
    fun `a report goes with its kind, its object and its reason, and says whether it is new`() =
        runTest {
            site.json(200, """{"created":true}""")
            site.json(200, """{"created":false}""")

            val first = safety.report(ReportTarget(ReportKind.RideComment, id), ReportReason.Abuse)
            val again = safety.report(ReportTarget(ReportKind.RideComment, id), ReportReason.Abuse)

            assertThat(first).isTrue()
            assertThat(again).isFalse()
            val request = site.server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/reports")
            val body = request.body!!.utf8()
            assertThat(body).contains("\"entityType\":\"ride_comment\"")
            assertThat(body).contains("\"targetId\":\"$id\"")
            assertThat(body).contains("\"reason\":\"abuse\"")
        }

    @Test
    fun `every kind and reason has its own word in the contract`() = runTest {
        for (kind in ReportKind.entries) {
            site.json(200, """{"created":true}""")
            safety.report(ReportTarget(kind, id), ReportReason.Other)
        }
        val kinds = ReportKind.entries.map { site.server.takeRequest().body!!.utf8() }
        assertThat(kinds.map { Regex("\"entityType\":\"([a-z_]+)\"").find(it)!!.groupValues[1] })
            .containsExactly(
                "profile",
                "bike",
                "comment",
                "ride",
                "ride_comment",
                "journal",
                "journal_comment",
                "component_comment",
                "component_photo",
            )
            .inOrder()
    }

    @Test
    fun `a malformed object id is not found without a request`() = runTest {
        val result = runCatching {
            safety.report(ReportTarget(ReportKind.Bike, "not-a-uuid"), ReportReason.Spam)
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `blocking is PUT, unblocking DELETE, and each is announced to the screens`() = runTest {
        site.json(200, """{"blocked":true}""")
        site.json(200, """{"blocked":false}""")

        safety.blockChanges.test {
            assertThat(safety.setBlocked(UserId(id), true)).isTrue()
            assertThat(awaitItem()).isEqualTo(BlockChange(UserId(id), true))
            assertThat(safety.setBlocked(UserId(id), false)).isFalse()
            assertThat(awaitItem()).isEqualTo(BlockChange(UserId(id), false))
        }

        val put = site.server.takeRequest()
        assertThat(put.method).isEqualTo("PUT")
        assertThat(put.url.encodedPath).isEqualTo("/api/v1/users/$id/block")
        assertThat(site.server.takeRequest().method).isEqualTo("DELETE")
    }

    @Test
    fun `a refused block is announced to nobody`() = runTest {
        site.json(404, error("not_found"))

        safety.blockChanges.test {
            val result = runCatching { safety.setBlocked(UserId(id), true) }

            assertThat(result.exceptionOrNull()).isInstanceOf(DataError.NotFound::class.java)
            expectNoEvents()
        }
    }

    @Test
    fun `the blocked people come as a page with the cursor and say they are blocked`() = runTest {
        site.json(
            200,
            """{"items":[{"id":"$id","username":"rider","name":"Райдер","avatarUrl":null,
                "relationship":{"isSelf":false,"following":false,"followedBy":false,
                "friends":false,"blockedByMe":true}}],"nextCursor":"c2"}""",
        )

        val page = safety.blocked(cursor = "c1", limit = 10)

        assertThat(page.nextCursor).isEqualTo("c2")
        assertThat(page.items.single().person.id).isEqualTo(UserId(id))
        assertThat(page.items.single().relationship?.blockedByMe).isTrue()
        val request = site.server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/blocked")
        assertThat(request.url.queryParameter("cursor")).isEqualTo("c1")
        assertThat(request.url.queryParameter("limit")).isEqualTo("10")
    }
}
