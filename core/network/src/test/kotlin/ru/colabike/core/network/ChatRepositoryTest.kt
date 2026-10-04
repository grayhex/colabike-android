package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.DataError
import ru.colabike.core.model.UserId

class ChatRepositoryTest {
    private val site = TestServer()
    private val chat =
        NetworkChatRepository(
            site.api.chat,
            site.api::chatWithKey,
            site.media,
            Dispatchers.Unconfined,
        )
    private val first = "10000000-0000-4000-8000-000000000002"
    private val second = "10000000-0000-4000-8000-000000000003"

    @After fun close() = site.close()

    @Test
    fun `the credentials come from a bodiless POST and keep the token out of toString`() = runTest {
        site.json(200, site.fixture("chat-token.json"))

        val credentials = chat.credentials()

        val request = site.server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/chat/token")
        assertThat(credentials.apiKey).isEqualTo("pk_test_public")
        assertThat(credentials.user.id).isEqualTo("cb-1")
        assertThat(credentials.user.imageUrl).isEqualTo("https://colabike.ru/media/u/1.jpg")
        assertThat(credentials.channelType).isEqualTo("colabike")
        assertThat(credentials.expiresAt).isEqualTo(Instant.parse("2026-10-04T12:05:00Z"))
        // A credential is not text: it must not reach a log through a string.
        assertThat(credentials.toString()).doesNotContain("eyJ")
        assertThat(credentials.toString()).doesNotContain("pk_test")
        assertThat(credentials.token).isEqualTo("eyJ.fake.token")
    }

    @Test
    fun `an avatar that is not https is not passed to the chat`() = runTest {
        site.json(
            200,
            site
                .fixture("chat-token.json")
                .replace("https://colabike.ru/media/u/1.jpg", "http://x/1.jpg"),
        )

        assertThat(chat.credentials().user.imageUrl).isNull()
    }

    @Test
    fun `a dialogue is one person, and the same request opens the same channel`() = runTest {
        site.json(201, site.fixture("chat-channel.json"))

        val cid = chat.open(ChatChannelKind.Dm, listOf(UserId(first)))

        val request = site.server.takeRequest()
        assertThat(request.url.encodedPath).isEqualTo("/api/v1/chat/channels")
        val body = request.body!!.utf8()
        assertThat(body).contains("\"kind\":\"dm\"")
        assertThat(body).contains(first)
        assertThat(body).doesNotContain("name")
        assertThat(request.headers["Idempotency-Key"]).isNull()
        assertThat(cid.value).isEqualTo("colabike:dm-1-2")
    }

    @Test
    fun `a group has a name, two or more others, and a key that makes a repeat the same group`() =
        runTest {
            site.json(201, site.fixture("chat-channel.json"))

            chat.open(
                ChatChannelKind.Group,
                listOf(UserId(first), UserId(second)),
                name = "  Воскресный выезд  ",
                key = "6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31",
            )

            val request = site.server.takeRequest()
            val body = request.body!!.utf8()
            assertThat(body).contains("\"kind\":\"group\"")
            assertThat(body).contains("\"name\":\"Воскресный выезд\"")
            assertThat(request.headers["Idempotency-Key"])
                .isEqualTo("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31")
        }

    @Test
    fun `what the server would refuse is refused before the request`() = runTest {
        suspend fun fails(block: suspend () -> Unit) =
            assertThat(runCatching { block() }.exceptionOrNull())
                .isInstanceOf(IllegalArgumentException::class.java)

        fails { chat.open(ChatChannelKind.Dm, listOf(UserId(first), UserId(second))) }
        fails { chat.open(ChatChannelKind.Dm, emptyList()) }
        fails { chat.open(ChatChannelKind.Group, listOf(UserId(first)), "Имя") }
        fails { chat.open(ChatChannelKind.Group, listOf(UserId(first), UserId(first)), "Имя") }
        fails { chat.open(ChatChannelKind.Group, listOf(UserId(first), UserId(second)), "  ") }
        fails {
            chat.open(ChatChannelKind.Group, listOf(UserId(first), UserId(second)), "я".repeat(81))
        }
        fails {
            chat.open(
                ChatChannelKind.Group,
                (1..8).map { UserId("10000000-0000-4000-8000-00000000000$it") },
                "Имя",
            )
        }
        assertThat(
                runCatching { chat.open(ChatChannelKind.Dm, listOf(UserId("../me"))) }
                    .exceptionOrNull()
            )
            .isInstanceOf(DataError.NotFound::class.java)
        assertThat(site.server.requestCount).isEqualTo(0)
    }

    @Test
    fun `people are those the person follows, or from two characters a search`() = runTest {
        site.json(200, site.fixture("chat-people.json"))
        val following = chat.people()
        val request = site.server.takeRequest().url
        assertThat(request.encodedPath).isEqualTo("/api/v1/chat/people")
        assertThat(request.queryParameter("q")).isNull()
        assertThat(following.searching).isFalse()
        assertThat(following.people.map { it.username })
            .containsExactly("rider2", "rider3")
            .inOrder()
        assertThat(following.people[1].displayName).isEqualTo("rider3")
        assertThat(following.people[1].avatarUrl).endsWith("/media/u/3.jpg")

        site.json(200, site.fixture("chat-people.json").replace("following", "search"))
        val found = chat.people("  ри ")
        assertThat(site.server.takeRequest().url.queryParameter("q")).isEqualTo("ри")
        assertThat(found.searching).isTrue()

        // One character is no search: the list the person follows is asked for.
        site.json(200, site.fixture("chat-people.json"))
        chat.people("р")
        assertThat(site.server.takeRequest().url.queryParameter("q")).isNull()
    }

    @Test
    fun `the unread count is the provider's total`() = runTest {
        site.json(200, site.fixture("chat-unread.json"))

        assertThat(chat.unread()).isEqualTo(5)
        assertThat(site.server.takeRequest().url.encodedPath).isEqualTo("/api/v1/chat/unread")
    }

    @Test
    fun `an unconfirmed e-mail, a chat that is off and a person who cannot be written to`() =
        runTest {
            site.json(403, error("email_verification_required"))
            val unconfirmed = runCatching { chat.credentials() }.exceptionOrNull()
            assertThat(unconfirmed).isInstanceOf(DataError.Rejected::class.java)
            assertThat((unconfirmed as DataError.Rejected).code)
                .isEqualTo("email_verification_required")

            site.json(503, error("service_unavailable"))
            val off = runCatching { chat.credentials() }.exceptionOrNull()
            assertThat(off).isInstanceOf(DataError.Server::class.java)
            assertThat((off as DataError.Server).status).isEqualTo(503)

            site.json(404, error("not_found"))
            assertThat(
                    runCatching { chat.open(ChatChannelKind.Dm, listOf(UserId(first))) }
                        .exceptionOrNull()
                )
                .isInstanceOf(DataError.NotFound::class.java)
        }
}
