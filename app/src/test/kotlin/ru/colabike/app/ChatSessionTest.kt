package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.messages.ChatConnectException
import ru.colabike.app.messages.ChatConnection
import ru.colabike.app.messages.ChatFailure
import ru.colabike.app.messages.ChatLink
import ru.colabike.app.messages.ChatSession
import ru.colabike.app.ui.UiText
import ru.colabike.core.model.DataError

/** The connection to the chat provider: when it starts, how it fails, and how it ends. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
    private val chat = FakeChat()
    private val gateway = FakeChatGateway()

    private fun TestScope.session() = ChatSession(chat, gateway, backgroundScope)

    private fun rejected(code: String) = DataError.Rejected(403, code, "")

    @Test
    fun `nothing is connected until a chat screen asks`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()

            assertThat(session.connection.value).isEqualTo(ChatConnection.Off)
            assertThat(chat.credentialCalls).isEqualTo(0)
            assertThat(gateway.connected).isEmpty()
        }

    @Test
    fun `the first request brings the chat up with the token of the bridge`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()

            session.connect()

            assertThat(session.connection.value)
                .isEqualTo(ChatConnection.Connected(ChatLink("u1", "colabike")))
            assertThat(gateway.connected.map { it.token }).containsExactly("token-1")
        }

    @Test
    fun `a token the provider finds expired is asked for again at the bridge`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()

            session.connect()

            // The fake gateway asks for a fresh token right after the first, as the SDK would.
            assertThat(gateway.refreshed?.token).isEqualTo("token-2")
            assertThat(chat.credentialCalls).isEqualTo(2)
        }

    @Test
    fun `asking again while connecting or connected does nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()
            val hold = CompletableDeferred<Unit>().also { gateway.hold = it }

            session.connect()
            session.connect()
            assertThat(session.connection.value).isEqualTo(ChatConnection.Connecting)
            hold.complete(Unit)
            session.connect()

            assertThat(gateway.connected).hasSize(1)
        }

    @Test
    fun `an unconfirmed e-mail is told as such, with the words of the chat`() =
        runTest(UnconfinedTestDispatcher()) {
            chat.credentialsError = rejected("email_verification_required")
            val session = session()

            session.connect()

            val failed = session.connection.value as ChatConnection.Failed
            assertThat(failed.reason).isEqualTo(ChatFailure.EmailUnconfirmed)
            assertThat((failed.message as UiText.Res).id).isEqualTo(R.string.chat_email_unconfirmed)
            assertThat(gateway.connected).isEmpty()
        }

    @Test
    fun `a chat that is off or a provider that is down is not the person's fault`() =
        runTest(UnconfinedTestDispatcher()) {
            chat.credentialsError = DataError.Server(503, "req-1")
            val session = session()

            session.connect()

            val failed = session.connection.value as ChatConnection.Failed
            assertThat(failed.reason).isEqualTo(ChatFailure.Unavailable)
            assertThat((failed.message as UiText.Res).id).isEqualTo(R.string.chat_unavailable)
        }

    @Test
    fun `no network is a failure worth a retry, and the retry connects`() =
        runTest(UnconfinedTestDispatcher()) {
            chat.credentialsError = DataError.Offline(java.io.IOException("no route"))
            val session = session()

            session.connect()
            assertThat((session.connection.value as ChatConnection.Failed).reason)
                .isEqualTo(ChatFailure.Offline)

            session.connect()

            assertThat(session.connection.value).isInstanceOf(ChatConnection.Connected::class.java)
        }

    @Test
    fun `a failure of the SDK itself is shown in words and can be retried`() =
        runTest(UnconfinedTestDispatcher()) {
            gateway.failure = ChatConnectException("The chat did not connect")
            val session = session()

            session.connect()

            val failed = session.connection.value as ChatConnection.Failed
            assertThat(failed.reason).isEqualTo(ChatFailure.Other)
            assertThat((failed.message as UiText.Res).id).isEqualTo(R.string.chat_connect_failed)

            session.connect()
            assertThat(session.connection.value).isInstanceOf(ChatConnection.Connected::class.java)
        }

    @Test
    fun `ending the session disconnects and makes the SDK forget the person`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()
            session.connect()

            session.end()

            assertThat(session.connection.value).isEqualTo(ChatConnection.Off)
            assertThat(gateway.disconnects).containsExactly(true)
        }

    @Test
    fun `ending a session that was never up touches nothing`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()

            session.end()

            assertThat(gateway.disconnects).isEmpty()
        }

    @Test
    fun `a session that failed to start is cleaned too, and the next person starts afresh`() =
        runTest(UnconfinedTestDispatcher()) {
            gateway.failure = ChatConnectException("x")
            val session = session()
            session.connect()

            session.end()
            session.connect()

            assertThat(gateway.disconnects).containsExactly(true)
            assertThat(session.connection.value).isInstanceOf(ChatConnection.Connected::class.java)
        }

    @Test
    fun `ending while the chat is still coming up does not leave it connected`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = session()
            gateway.hold = CompletableDeferred()

            session.connect()
            session.end()

            assertThat(session.connection.value).isEqualTo(ChatConnection.Off)
            assertThat(gateway.connected).isEmpty()
        }
}
