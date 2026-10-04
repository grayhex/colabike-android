package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Person

/**
 * One notification of the inbox: who did it (their picture, or a tinted bell for the site's own
 * notifications), what happened, to which object, and when. An unread one carries a dot; the dot is
 * a shape, and the spoken text begins with "new", so the state is not told by colour alone. Without
 * [onClick] the row only tells (a notification the app cannot open). An unread row with
 * [onMarkRead] has a button that marks it read without opening it, and the same as an action for
 * TalkBack.
 */
@Composable
fun NotificationRow(
    title: String,
    body: String,
    whenText: String,
    unread: Boolean,
    actor: Person?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onMarkRead: (() -> Unit)? = null,
) {
    val newLabel = stringResource(R.string.cola_notification_new)
    val markReadLabel = stringResource(R.string.cola_notification_mark_read)
    val openLabel = stringResource(R.string.cola_open)
    val description =
        listOfNotNull(newLabel.takeIf { unread }, title, body.takeIf { it.isNotBlank() }, whenText)
            .joinToString(". ")
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = openLabel) {
                        onClick()
                        true
                    }
                }
                // The button on the row is part of it for TalkBack: one more action on the row.
                if (unread && onMarkRead != null) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(markReadLabel) {
                                onMarkRead()
                                true
                            }
                        )
                }
            },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.l),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // The dot of an unread one sits on the picture's corner, ringed in the card's colour: a
            // shape, so the state is not told by colour alone, and no width of the text is lost.
            Box {
                if (actor != null) Avatar(actor.displayName, actor.avatarUrl)
                else IconHalo(ColaIcons.Notifications, tone = HaloTone.Secondary)
                if (unread) {
                    Box(
                        Modifier.align(Alignment.TopEnd)
                            .offset(x = UnreadDot / 3, y = -UnreadDot / 3)
                            .size(UnreadDot + UnreadRing * 2)
                            .background(MaterialTheme.colorScheme.surfaceContainer, PillShape)
                            .padding(UnreadRing)
                            .background(MaterialTheme.colorScheme.primary, PillShape)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (body.isNotBlank()) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    whenText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (unread && onMarkRead != null) {
                IconButton(
                    onClick = onMarkRead,
                    // Beside the text, not under it: the row is padded, the button is not.
                    modifier =
                        Modifier.offset(x = Spacing.s, y = -Spacing.s)
                            .size(Spacing.touch)
                            .testTag("notification:mark_read"),
                ) {
                    Icon(
                        painterResource(ColaIcons.DoneAll),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val UnreadDot = 10.dp
private val UnreadRing = 2.dp
