package ru.colabike.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.ColaMotion
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.LocalReducedMotion
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
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val saving = stringResource(R.string.cola_like_saving)
    val tint by
        animateColorAsState(
            if (liked) ColaTheme.colors.like else MaterialTheme.colorScheme.onSurfaceVariant,
            if (reduced) snap() else ColaMotion.effects(),
            label = "like color",
        )
    val scale by
        animateFloatAsState(
            if (liked) 1.1f else 1f,
            if (reduced) snap() else ColaMotion.fastSpatial(),
            label = "like scale",
        )
    val shape = PillShape
    val interactive =
        if (onToggle != null) {
            Modifier.toggleable(
                value = liked,
                enabled = !busy,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(
                        if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                    )
                    onToggle()
                },
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
            .semantics(mergeDescendants = true) {
                contentDescription = if (busy) "$label, $count, $saving" else "$label, $count"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (busy && !reduced) {
                CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp)
            } else {
                Icon(
                    painterResource(if (liked) ColaIcons.LikeFilled else ColaIcons.Like),
                    contentDescription = null,
                    tint = tint,
                    modifier =
                        Modifier.size(20.dp).graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
