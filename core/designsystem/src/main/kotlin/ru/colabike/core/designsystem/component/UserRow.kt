package ru.colabike.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Person

/**
 * A person in a list (the reference "list row"): picture, name, @username, and a chevron when it
 * opens something. The whole row is the touch target, a hairline card at least 48 dp tall.
 */
@Composable
fun UserRow(
    person: Person,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        modifier =
            modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                // The card is a button already; this gives its action a spoken label.
                if (onClick != null) {
                    onClick(label = openLabel) {
                        onClick()
                        true
                    }
                }
            },
        shape = MaterialTheme.shapes.medium,
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Avatar(person.displayName, person.avatarUrl)
            Column(Modifier.weight(1f)) {
                // A name is never cut for good: at a big system font it takes a second line.
                Text(
                    person.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    supporting ?: stringResource(R.string.cola_username, person.username),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                trailing != null -> trailing()
                onClick != null ->
                    Icon(
                        painterResource(ColaIcons.ChevronRight),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
            }
        }
    }
}

/** An author attached to content, with no surrounding card or truncated name. */
@Composable
fun PersonByline(
    person: Person,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val open = stringResource(R.string.cola_open)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .then(
                if (onClick == null) Modifier
                else
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = open,
                        onClick = onClick,
                    )
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Avatar(person.displayName, person.avatarUrl, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(person.displayName, style = MaterialTheme.typography.titleSmall)
            supporting?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
