package ru.colabike.app.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.getstream.chat.android.compose.ui.channels.ChannelsScreen
import io.getstream.chat.android.compose.ui.messages.ChannelScreen
import io.getstream.chat.android.compose.viewmodel.channels.ChannelListViewModelFactory
import io.getstream.chat.android.compose.viewmodel.messages.ChannelViewModelFactory
import io.getstream.chat.android.models.Filters
import io.getstream.chat.android.models.querysort.QuerySortByField
import ru.colabike.app.R
import ru.colabike.core.model.ChannelCid

/**
 * The chat provider's screens, as the app places them. A seam like the maps: the app gets the SDK's
 * (themed in [ColaChatTheme]), and a test, where the SDK cannot connect, a drawing in its place.
 */
interface ChatScreens {
    /** The person's conversations. [onNew] starts a new one, [onOpen] opens one. */
    @Composable
    fun Conversations(
        link: ChatLink,
        onOpen: (ChannelCid) -> Unit,
        onNew: () -> Unit,
        modifier: Modifier,
    )

    /** One conversation: its messages and the box to write in. */
    @Composable
    fun Conversation(link: ChatLink, cid: ChannelCid, onBack: () -> Unit, modifier: Modifier)
}

/**
 * The SDK's list and message screens. Only ColaBike's own channels are listed (their type and the
 * person as a member); a new channel is made by the bridge, never by the SDK, because who may write
 * to whom is the server's decision, so the SDK's own "start a chat" is not offered. Links are not
 * fetched for previews (the provider would open them), and nothing is asked of the push services.
 */
object StreamChatScreens : ChatScreens {
    @Composable
    override fun Conversations(
        link: ChatLink,
        onOpen: (ChannelCid) -> Unit,
        onNew: () -> Unit,
        modifier: Modifier,
    ) {
        val filter =
            remember(link) {
                Filters.and(
                    Filters.eq("type", link.channelType),
                    Filters.`in`("members", listOf(link.userId)),
                )
            }
        val factory =
            remember(filter) {
                ChannelListViewModelFactory(
                    querySort = QuerySortByField.descByName("last_updated"),
                    filters = filter,
                )
            }
        ColaChatTheme {
            Box(modifier) {
                ChannelsScreen(
                    viewModelFactory = factory,
                    title = stringResource(R.string.chat_title),
                    isShowingHeader = true,
                    onHeaderActionClick = onNew,
                    onStartChatClick = null,
                    onChannelClick = { onOpen(ChannelCid(it.cid)) },
                    isBackPressEnabled = false,
                )
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
        val context = LocalContext.current
        val factory =
            remember(cid) {
                ChannelViewModelFactory(
                    context = context.applicationContext,
                    channelId = cid.value,
                    autoTranslationEnabled = false,
                )
            }
        ColaChatTheme {
            Box(modifier) {
                ChannelScreen(
                    viewModelFactory = factory,
                    onBackPressed = onBack,
                    skipPushNotification = true,
                    skipEnrichUrl = true,
                )
            }
        }
    }
}
