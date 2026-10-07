package ru.colabike.app.ui

import androidx.compose.runtime.Composable
import ru.colabike.app.messages.ChatsButton
import ru.colabike.app.notifications.NotificationsBell

/**
 * What every top-level top bar ends with: the chats and the notifications, two buttons side by side
 * with counts of their own. Each draws nothing where the shell offers none (a function the server
 * switched off, a guest's inbox).
 */
@Composable
fun HeaderActions() {
    ChatsButton()
    NotificationsBell()
}
