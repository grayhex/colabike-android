package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing

/**
 * The screen title on the bare canvas (the reference "top app bar"): no fill, no divider. It keeps
 * clear of the status bar and the cutout, and the title is the screen's heading for TalkBack. A
 * back arrow shows only where the screen was opened from another one. It grows with the system font
 * instead of clipping.
 *
 * - [eyebrow]: a small kicker above the title ("COLABIKE" on the first screen of a section).
 * - [compactTitle]: a detail page's title in the 20 sp section size instead of the 26 sp page size,
 *   which leaves the room to what the page is about.
 * - [actionsBelow]: at a large system font the actions go under the title, where the title has the
 *   whole width; beside it there is not enough room for the word.
 */
@Composable
fun ColaTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleMaxLines: Int = 2,
    eyebrow: String? = null,
    compactTitle: Boolean = false,
    actionsBelow: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val titleBlock: @Composable (Modifier) -> Unit = { blockModifier ->
        Column(blockModifier) {
            if (eyebrow != null) Eyebrow(eyebrow)
            Text(
                title,
                style =
                    if (compactTitle) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.headlineLarge,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(
                    start = if (onBack == null) Spacing.screen else Spacing.s,
                    end = Spacing.screen,
                    top = if (compactTitle) Spacing.xs else Spacing.s,
                    bottom = Spacing.xs,
                )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        painterResource(ColaIcons.ArrowBack),
                        contentDescription = stringResource(R.string.cola_back),
                    )
                }
            }
            titleBlock(Modifier.weight(1f))
            if (!actionsBelow) actions()
        }
        if (actionsBelow) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}
