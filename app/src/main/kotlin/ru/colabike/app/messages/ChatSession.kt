package ru.colabike.app.messages

import androidx.compose.runtime.Immutable
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.ChatCredentials
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.DataError

/**
 * The provider's SDK as the session sees it. The SDK is a singleton with a database of its own, so
 * everything that touches it stays behind this: tests give the session a fake, and nothing else of
 * the app knows the SDK is there.
 */
interface ChatGateway {
    /**
     * Connects [credentials]'s person. Whenever the SDK needs a new token (they live five minutes)
     * it asks [refresh]; the first token is the one in [credentials].
     */
    suspend fun connect(credentials: ChatCredentials, refresh: suspend () -> ChatCredentials)

    /** Ends the connection. With [forget] also deletes what the SDK kept of this person. */
    suspend fun disconnect(forget: Boolean)
}

/** The SDK could not connect for a reason of its own (not a ColaBike [DataError]). */
class ChatConnectException(message: String) : Exception(message)

/** Why the chat is not up, in the terms a screen can act on. */
enum class ChatFailure {
    /** The e-mail is not confirmed: the chat is closed until it is, on the site. */
    EmailUnconfirmed,

    /** The chat is switched off or its provider is down; the site itself works. */
    Unavailable,

    /** No network: worth a retry. */
    Offline,

    /** Anything else. */
    Other,
}

/** The non-secret part of a connected session: who is in the chat and the type of its channels. */
@Immutable data class ChatLink(val userId: String, val channelType: String)

@Immutable
sealed interface ChatConnection {
    /** Not asked for yet, or ended (signed out). */
    data object Off : ChatConnection

    data object Connecting : ChatConnection

    data class Connected(val link: ChatLink) : ChatConnection

    data class Failed(val reason: ChatFailure, val message: UiText) : ChatConnection
}

/**
 * The person's connection to the chat for as long as the app is open. It starts when a chat screen
 * first needs it (never at launch, and never for a guest), asks the bridge for a token, hands it to
 * the SDK, and ends with the account: [end] disconnects and makes the SDK forget the person, so the
 * next one finds nothing of this one's conversations. A lost network is the SDK's business once it
 * is connected; a failed start is shown with a retry.
 */
class ChatSession(
    private val repository: ChatRepository,
    private val gateway: ChatGateway,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow<ChatConnection>(ChatConnection.Off)
    val connection: StateFlow<ChatConnection> = mutable.asStateFlow()

    private val lock = Mutex()
    private var starting: Job? = null

    /** Brings the chat up unless it is up or on its way; after a failure it tries again. */
    fun connect() {
        when (mutable.value) {
            is ChatConnection.Connected,
            ChatConnection.Connecting -> return
            else -> Unit
        }
        mutable.value = ChatConnection.Connecting
        starting?.cancel()
        starting = scope.launch {
            lock.withLock {
                mutable.value =
                    try {
                        val credentials = repository.credentials()
                        gateway.connect(credentials) { repository.credentials() }
                        ChatConnection.Connected(
                            ChatLink(credentials.user.id, credentials.channelType)
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: DataError) {
                        ChatConnection.Failed(e.reason(), e.chatText())
                    } catch (e: ChatConnectException) {
                        ChatConnection.Failed(
                            ChatFailure.Other,
                            UiText.Res(R.string.chat_connect_failed),
                        )
                    }
            }
        }
    }

    /**
     * The account is gone (signed out, or another one signed in): drops the connection and the
     * SDK's copy of this person's conversations. Safe when nothing was ever connected.
     */
    fun end() {
        starting?.cancel()
        starting = null
        val wasUp = mutable.value
        mutable.value = ChatConnection.Off
        if (wasUp == ChatConnection.Off) return
        scope.launch { lock.withLock { runCatching { gateway.disconnect(forget = true) } } }
    }
}

private fun DataError.reason(): ChatFailure =
    when {
        this is DataError.Rejected && code == "email_verification_required" ->
            ChatFailure.EmailUnconfirmed
        this is DataError.Server && status == 503 -> ChatFailure.Unavailable
        this is DataError.Offline -> ChatFailure.Offline
        else -> ChatFailure.Other
    }

/** What the chat says about a failure: its own words for the two the chat itself causes. */
internal fun DataError.chatText(): UiText =
    when (reason()) {
        ChatFailure.EmailUnconfirmed -> UiText.Res(R.string.chat_email_unconfirmed)
        ChatFailure.Unavailable -> UiText.Res(R.string.chat_unavailable)
        else -> toUiText()
    }
