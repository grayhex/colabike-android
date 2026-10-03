package ru.colabike.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing

/**
 * The like of a bike as a pill: the heart (the one place the like colour is a fill) and the count.
 * A switch for TalkBack ("Нравится, 5, включено"). Without [onToggle] it only shows the count, as
 * for one's own bike, which cannot be liked. 48 dp tall; [busy] holds further taps while the server
 * answers.
 */
@Composable
fun LikeButton(
    liked: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    val label = stringResource(R.string.cola_like)
    val tint = if (liked) ColaTheme.colors.like else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = PillShape
    val interactive =
        if (onToggle != null) {
            Modifier.toggleable(
                value = liked,
                enabled = !busy,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
        } else {
            Modifier
        }
    Row(
        modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .then(interactive)
            .heightIn(min = Spacing.touch)
            .padding(horizontal = Spacing.l)
            .semantics(mergeDescendants = true) { contentDescription = "$label, $count" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Icon(
            painterResource(if (liked) ColaIcons.LikeFilled else ColaIcons.Like),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
