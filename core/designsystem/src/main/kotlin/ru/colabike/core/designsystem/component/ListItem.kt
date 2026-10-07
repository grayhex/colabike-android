package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.Spacing

/** Above this font scale a row gives up its icon so that titles do not break mid-word. */
private const val BigFontScale = 1.5f

/** What the end of a [ColaListItem] shows when the row opens something. */
enum class ListItemAction {
    /** Another screen of the app: a chevron. */
    Open,

    /** A page outside the app (the browser): the "open in new" glyph. */
    External,
}

/**
 * A setting or a link as a row of the reference "list row": an icon tile, a title with one line of
 * explanation, and a chevron or the external-link glyph at the end. The whole hairline card is one
 * touch target at least 48 dp tall, and TalkBack reads title and explanation as one phrase. A row
 * with [action] but without [onClick] is not possible; a row with neither is plain information.
 * [trailing] replaces the glyph (a badge, a button of its own). With [grouped] the row has no card
 * of its own: it is one of the rows of a [ColaRowGroup].
 */
@Composable
fun ColaListItem(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    @DrawableRes icon: Int? = null,
    tone: HaloTone = HaloTone.Primary,
    onClick: (() -> Unit)? = null,
    action: ListItemAction = ListItemAction.Open,
    trailing: (@Composable () -> Unit)? = null,
    grouped: Boolean = false,
) {
    val bigFont = LocalDensity.current.fontScale > BigFontScale
    val semantic =
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            // The card is a button already; this gives its action a spoken label.
            if (onClick != null) {
                onClick(label = title) {
                    onClick()
                    true
                }
            }
        }
    val row: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            // At big system fonts the words need the room more than the decoration does.
            if (icon != null && !bigFont) {
                IconHalo(icon, tone = tone)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // A wide trailing element would starve the words at big fonts: it goes below them.
                if (bigFont && trailing != null) {
                    Box(Modifier.padding(top = Spacing.xs)) { trailing() }
                }
            }
            when {
                trailing != null -> if (!bigFont) trailing()
                onClick != null ->
                    Icon(
                        painterResource(
                            when (action) {
                                ListItemAction.Open -> ColaIcons.ChevronRight
                                ListItemAction.External -> ColaIcons.OpenInNew
                            }
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
            }
        }
    }
    if (grouped) {
        Box(
            modifier
                .then(semantic)
                .then(
                    if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                    else Modifier
                )
        ) {
            row()
        }
    } else {
        ColaCard(
            modifier = modifier.then(semantic),
            shape = MaterialTheme.shapes.medium,
            onClick = onClick,
        ) {
            row()
        }
    }
}

/** Rows that belong together in one card, a hairline between them (use [ColaRowDivider]). */
@Composable
fun ColaRowGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ColaCard(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(content = content)
    }
}

/** The hairline between two rows of a [ColaRowGroup], from the text, not from the card's edge. */
@Composable
fun ColaRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = Spacing.l + Spacing.touch + Spacing.l, end = Spacing.l),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
