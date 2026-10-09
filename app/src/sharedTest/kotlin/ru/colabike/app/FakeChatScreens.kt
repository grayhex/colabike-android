package ru.colabike.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.getstream.chat.android.compose.state.channels.list.ItemState
import io.getstream.chat.android.compose.ui.channels.header.ChannelListHeader
import io.getstream.chat.android.compose.ui.channels.list.ChannelItem
import io.getstream.chat.android.compose.ui.messages.composer.MessageComposer
import io.getstream.chat.android.compose.ui.messages.list.MessageContainer
import io.getstream.chat.android.models.Channel
import io.getstream.chat.android.models.ChannelCapabilities
import io.getstream.chat.android.models.ChannelUserRead
import io.getstream.chat.android.models.ConnectionState
import io.getstream.chat.android.models.Member
import io.getstream.chat.android.models.Message
import io.getstream.chat.android.models.User
import io.getstream.chat.android.ui.common.state.messages.composer.MessageComposerState
import io.getstream.chat.android.ui.common.state.messages.list.MessageItemState
import io.getstream.chat.android.ui.common.state.messages.list.MessagePosition
import java.util.Date
import ru.colabike.app.messages.ChatLink
import ru.colabike.app.messages.ChatScreens
import ru.colabike.app.messages.ColaChatTheme
import ru.colabike.core.model.ChannelCid

private val me = User(id = "u1", name = "Тестовый Райдер")
private val anna = User(id = "u2", name = "Анна Шоссейная")
private val pavel = User(id = "u3", name = "Павел Гравийный")

private fun at(minutesAgo: Int) = Date(1_759_521_600_000L - minutesAgo * 60_000L)

private fun message(id: String, text: String, user: User, minutesAgo: Int) =
    Message(
        id = id,
        cid = "colabike:dm-1",
        text = text,
        type = "regular",
        user = user,
        createdAt = at(minutesAgo),
    )

private val capabilities = setOf(ChannelCapabilities.SEND_MESSAGE, ChannelCapabilities.READ_EVENTS)

private val conversations =
    listOf(
        Channel(
            id = "dm-1",
            type = "colabike",
            name = "Анна Шоссейная",
            members = listOf(Member(me), Member(anna)),
            messages = listOf(message("m1", "Привет! Едем в субботу на Воробьёвы?", anna, 3)),
            cachedLatestMessages =
                listOf(message("m1", "Привет! Едем в субботу на Воробьёвы?", anna, 3)),
            read = listOf(ChannelUserRead(me, at(3), 2, at(60), null)),
            lastMessageAt = at(3),
        ),
        Channel(
            id = "group-1",
            type = "colabike",
            name = "Субботний заезд",
            members = listOf(Member(me), Member(anna), Member(pavel)),
            messages = listOf(message("m2", "Беру запасную камеру, кому нужна?", pavel, 95)),
            cachedLatestMessages =
                listOf(message("m2", "Беру запасную камеру, кому нужна?", pavel, 95)),
            lastMessageAt = at(95),
        ),
    )

private fun bubble(message: Message, mine: Boolean, position: MessagePosition) =
    MessageItemState(
        message = message,
        isMine = mine,
        currentUser = me,
        groupPosition = position,
        showMessageFooter = position == MessagePosition.BOTTOM || position == MessagePosition.NONE,
        ownCapabilities = capabilities,
    )

/**
 * The provider's screens as the SDK's own components, fed by hand: no client, no connection, but
 * the same rows, bubbles and box to write in a person would see, in the app's theme. The shell and
 * the navigation around them are the real ones; a test finds a row by its tag.
 */
class FakeChatScreens : ChatScreens {
    val shown = mutableListOf<String>()

    @Composable
    override fun Conversations(
        link: ChatLink,
        onOpen: (ChannelCid) -> Unit,
        onNew: () -> Unit,
        modifier: Modifier,
    ) {
        ColaChatTheme {
            Column(modifier.testTag("chat:list")) {
                ChannelListHeader(
                    title = "Сообщения",
                    currentUser = me,
                    connectionState = ConnectionState.Connected,
                    onHeaderActionClick = onNew,
                )
                conversations.forEach { channel ->
                    ChannelItem(
                        channelItem = ItemState.ChannelItemState(channel),
                        currentUser = me,
                        onChannelClick = { onOpen(ChannelCid(channel.cid)) },
                        onChannelLongClick = {},
                        modifier = Modifier.testTag("chat:open:${channel.id}"),
                    )
                }
            }
        }
    }

    @Composable
    override fun Conversation(
        link: ChatLink,
        cid: ChannelCid,
        onBack: () -> Unit,
        modifier: Modifier,
    ) {
        shown += cid.value
        ColaChatTheme {
            Column(modifier.testTag("chat:conversation")) {
                Text(
                    "Диалог ${cid.value}",
                    Modifier.fillMaxWidth()
                        .padding(16.dp)
                        .clickable(onClick = onBack)
                        .testTag("chat:back"),
                )
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    MessageContainer(
                        messageItem =
                            bubble(
                                message("a", "Привет! Едем в субботу на Воробьёвы?", anna, 8),
                                mine = false,
                                position = MessagePosition.NONE,
                            ),
                        onLongItemClick = {},
                    )
                    MessageContainer(
                        messageItem =
                            bubble(
                                message("b", "Да, выезжаю в девять от метро.", me, 6),
                                mine = true,
                                position = MessagePosition.NONE,
                            ),
                        onLongItemClick = {},
                    )
                }
                MessageComposer(
                    messageComposerState =
                        MessageComposerState(
                            inputValue = "",
                            ownCapabilities = capabilities,
                            currentUser = me,
                        ),
                    onSendMessage = { _, _ -> },
                )
            }
        }
    }
}
