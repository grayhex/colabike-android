package ru.colabike.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import ru.colabike.core.designsystem.R
import ru.colabike.core.model.Person

/** A person in a list: picture, name, @username; the whole row is the touch target. */
@Composable
fun UserRow(
    person: Person,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(person.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                supporting ?: stringResource(R.string.cola_username, person.username),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = { Avatar(person.displayName, person.avatarUrl) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(),
        modifier =
            if (onClick != null)
                modifier.clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.cola_open),
                    onClick = onClick,
                )
            else modifier,
    )
}
