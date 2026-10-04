package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
 * [onClick] the row only tells (a notification the app cannot open).
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
) {
    val newLabel = stringResource(R.string.cola_notification_new)
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
            },
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.l),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (actor != null) Avatar(actor.displayName, actor.avatarUrl)
            else IconHalo(ColaIcons.Notifications, tone = HaloTone.Secondary)
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
            if (unread) {
                Box(
                    Modifier.padding(top = Spacing.xs)
                        .size(UnreadDot)
                        .background(MaterialTheme.colorScheme.primary, PillShape)
                )
            }
        }
    }
}

private val UnreadDot = 10.dp
