package ru.colabike.app.messages

import android.content.Context
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import io.getstream.chat.android.client.token.TokenProvider
import io.getstream.chat.android.client.user.CredentialConfig
import io.getstream.chat.android.client.user.storage.UserCredentialStorage
import io.getstream.chat.android.models.User
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import ru.colabike.core.model.ChatCredentials

/**
 * Stream Chat's own SDK behind [ChatGateway]. The client is built once, with the application key
 * the bridge gave (it is a public key), and lives as long as the process. Nothing is logged: the
 * SDK's log level is off, so no token, message or id reaches Logcat.
 */
class StreamChatGateway(context: Context) : ChatGateway {
    private val appContext = context.applicationContext
    private var client: ChatClient? = null

    override suspend fun connect(
        credentials: ChatCredentials,
        refresh: suspend () -> ChatCredentials,
    ) {
        val chat = client ?: build(credentials.apiKey).also { client = it }
        val user =
            User(
                id = credentials.user.id,
                name = credentials.user.name,
                image = credentials.user.imageUrl.orEmpty(),
            )
        val result = chat.connectUser(user, TokensOf(credentials.token, refresh)).await()
        if (result.isFailure) throw ChatConnectException("The chat did not connect")
    }

    override suspend fun disconnect(forget: Boolean) {
        val chat = client ?: return
        // The user may never have been connected (a failed start): that is not an error here.
        chat.disconnect(flushPersistence = forget).await()
    }

    private fun build(apiKey: String): ChatClient =
        ChatClient.Builder(apiKey, appContext)
            .logLevel(ChatLogLevel.NOTHING)
            // The SDK keeps the person's id and token in SharedPreferences to restore a push; there
            // is no push here, so nothing of the kind is written to the disk.
            .credentialStorage(MemoryCredentials())
            .build()
}

/**
 * The first token is the one from the bridge; when the SDK finds it expired (five minutes) it asks
 * for another, and the bridge is asked again. [loadToken] runs on a worker thread of the SDK, which
 * is why it may block.
 */
private class TokensOf(
    first: String,
    private val refresh: suspend () -> ChatCredentials,
) : TokenProvider {
    private val unused = AtomicReference<String?>(first)

    override fun loadToken(): String =
        unused.getAndSet(null)
            ?: runBlocking {
                // The contract of the SDK: a token that cannot be loaded is an empty string.
                runCatching { refresh().token }.getOrDefault("")
            }
}

/** A credential store that never reaches the disk. */
private class MemoryCredentials : UserCredentialStorage {
    @Volatile private var held: CredentialConfig? = null

    override fun put(credentialConfig: CredentialConfig) {
        held = credentialConfig
    }

    override fun get(): CredentialConfig? = held

    override fun clear() {
        held = null
    }
}
