package ru.colabike.app.messages

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaIcons

/**
 * What the shell offers a top bar for the chats: the way into the list of conversations. Absent
 * where the server has switched the chat off and in previews. The chats are not a section of the
 * bottom bar: they open over the screen the person is on, and Back returns to it.
 */
@Immutable class ChatsEntry(val onOpen: () -> Unit)

val LocalChatsEntry = staticCompositionLocalOf<ChatsEntry?> { null }

/** The chats button of a top-level top bar. It draws nothing where the shell offers no chat. */
@Composable
fun ChatsButton() {
    val entry = LocalChatsEntry.current ?: return
    IconButton(onClick = entry.onOpen) {
        Icon(
            painterResource(ColaIcons.Chat),
            contentDescription = stringResource(R.string.chats_open),
        )
    }
}
