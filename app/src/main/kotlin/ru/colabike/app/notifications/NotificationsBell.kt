package ru.colabike.app.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.theme.PillShape
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
    // Measure the badge as part of the action, including at 200% font size. BadgedBox inside
    // IconButton places long counts outside the button's fixed bounds and clips them in a top bar.
    Row(
        modifier =
            Modifier.testTag("notifications:button")
                .clip(PillShape)
                .clickable(role = Role.Button, onClick = entry.onOpen)
                .heightIn(min = Spacing.touch)
                .widthIn(min = Spacing.touch)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painterResource(ColaIcons.Notifications),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        when (val badge = entry.badge) {
            BellBadge.None -> Unit
            is BellBadge.Exact -> CountBadge(badge.unread.toString())
            BellBadge.Many -> CountBadge(stringResource(R.string.notifications_many_label))
        }
    }
}

@Composable
private fun CountBadge(text: String) {
    val style = MaterialTheme.typography.labelSmall
    val minimum = with(LocalDensity.current) { style.lineHeight.toDp() }.coerceAtLeast(Spacing.l)
    Badge(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier =
            Modifier.testTag("notifications:count")
                .clearAndSetSemantics {}
                .heightIn(min = Spacing.l)
                .widthIn(min = minimum),
    ) {
        Text(text, style = style)
    }
}
