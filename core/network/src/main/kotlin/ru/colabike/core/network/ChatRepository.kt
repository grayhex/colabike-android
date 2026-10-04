package ru.colabike.core.network

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import ru.colabike.api.apis.ChatApi
import ru.colabike.api.models.CreateChatChannelRequest
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatChannelKind
import ru.colabike.core.model.ChatCredentials
import ru.colabike.core.model.ChatPeople
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.ChatUser
import ru.colabike.core.model.DataError
import ru.colabike.core.model.UserId

/**
 * The bridge to the chat provider. [api] is the client with the Bearer interceptor; [keyed] gives
 * the same API with an `Idempotency-Key` on every request (the contract describes the header but
 * does not declare it as a parameter, so the generated methods cannot send it).
 */
class NetworkChatRepository(
    private val api: ChatApi,
    private val keyed: (key: String) -> ChatApi,
    private val media: MediaUrls,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatRepository {
    override suspend fun credentials(): ChatCredentials {
        val token = apiCall(dispatcher) { api.createChatToken() }
        return ChatCredentials(
            apiKey = token.apiKey,
            user =
                ChatUser(
                    id = token.user.id,
                    name = token.user.name,
                    imageUrl =
                        media.resolve(token.user.image)?.takeIf { it.startsWith("https://") },
                ),
            token = token.token,
            expiresAt = Instant.from(token.expiresAt),
            channelType = token.channelType,
        )
    }

    override suspend fun open(
        kind: ChatChannelKind,
        members: List<UserId>,
        name: String?,
        key: String?,
    ): ChannelCid {
        val ids = members.map { uuidOrNotFound(it.value) }
        // The same limits as the server's, checked before a request that would be refused.
        require(ids.distinct().size == ids.size) { "Members must be distinct" }
        val title = name?.trim()?.takeIf { it.isNotEmpty() }
        val request =
            when (kind) {
                ChatChannelKind.Dm -> {
                    require(ids.size == 1) { "A dialogue has exactly one other person" }
                    CreateChatChannelRequest(CreateChatChannelRequest.Kind.dm, ids)
                }
                ChatChannelKind.Group -> {
                    require(ids.size in GROUP_MEMBERS) { "A group has two to seven other people" }
                    require(title != null && title.length <= MAX_NAME) {
                        "A group has a name of up to $MAX_NAME characters"
                    }
                    CreateChatChannelRequest(CreateChatChannelRequest.Kind.group, ids, title)
                }
            }
        val client = key?.let(keyed) ?: api
        return ChannelCid(apiCall(dispatcher) { client.createChatChannel(request) }.cid)
    }

    override suspend fun people(query: String?): ChatPeople {
        // From two characters it is a search; a single character asks for nothing the API answers.
        val text = query?.trim()?.take(MAX_QUERY)?.takeIf { it.length >= MIN_QUERY }
        val answer = apiCall(dispatcher) { api.listChatPeople(text) }
        return ChatPeople(
            people = answer.people.map { it.toModel(media) },
            searching = answer.mode.value == "search",
        )
    }

    override suspend fun unread(): Int =
        apiCall(dispatcher) { api.getChatUnread() }.unread.coerceAtLeast(0)

    private fun uuidOrNotFound(value: String): UUID =
        runCatching { UUID.fromString(value) }.getOrNull() ?: throw DataError.NotFound()

    private companion object {
        val GROUP_MEMBERS = 2..7
        const val MAX_NAME = 80
        const val MIN_QUERY = 2
        const val MAX_QUERY = 80
    }
}
