package ru.colabike.app.notifications

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.theme.Spacing

/**
 * What the shell offers a top bar for the notifications: the count to show and the way into the
 * inbox. Absent for a guest (the inbox is the signed-in person's) and in previews.
 */
@Immutable class NotificationsEntry(val badge: BellBadge, val onOpen: () -> Unit)

val LocalNotificationsEntry = staticCompositionLocalOf<NotificationsEntry?> { null }

/** The bell of a top-level top bar. It draws nothing where the shell offers no inbox. */
@Composable
fun NotificationsBell() {
    val entry = LocalNotificationsEntry.current ?: return
    val description =
        when (val badge = entry.badge) {
            BellBadge.None -> stringResource(R.string.notifications_open)
            is BellBadge.Exact ->
                pluralStringResource(
                    R.plurals.notifications_open_unread,
                    badge.unread,
                    badge.unread,
                )
            BellBadge.Many -> stringResource(R.string.notifications_open_many)
        }
    IconButton(
        onClick = entry.onOpen,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        BadgedBox(
            badge = {
                when (val badge = entry.badge) {
                    BellBadge.None -> Unit
                    is BellBadge.Exact -> CountBadge(badge.unread.toString())
                    BellBadge.Many -> CountBadge(stringResource(R.string.notifications_many_label))
                }
            }
        ) {
            Icon(painterResource(ColaIcons.Notifications), contentDescription = null)
        }
    }
}

@Composable
private fun CountBadge(text: String) {
    Badge(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.heightIn(min = Spacing.l).widthIn(min = Spacing.l),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
