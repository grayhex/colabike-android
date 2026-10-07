package ru.colabike.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing

/**
 * The reference card: a raised surface bordered by a 1 dp hairline and no shadow, because depth
 * comes from the hairline, not from elevation. With [onClick] the whole card is one touch target.
 */
@Composable
fun ColaCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors =
        CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick == null) {
        OutlinedCard(modifier = modifier, shape = shape, colors = colors, content = content)
    } else {
        OutlinedCard(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            content = content,
        )
    }
}

/**
 * A kicker or metadata caption: small, capitals, wide tracking, in the quiet text colour, so it
 * whispers under the headline it belongs to.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines: Int = 1,
) {
    Text(
        text = text.uppercase(),
        style = ColaTheme.textStyles.eyebrow,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * A short fact in a hairline pill, optionally with a thin icon: a place, a state, a count. With
 * [accent] it is the one that says what the page is in the accent colour: "took place".
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (accent) scheme.onPrimaryContainer else scheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = if (accent) scheme.primaryContainer else scheme.surfaceContainer,
        border =
            BorderStroke(
                1.dp,
                if (accent) scheme.primary.copy(alpha = 0.4f) else scheme.outlineVariant,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s - Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (accent) content else iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
            // Two lines at most: a long badge wraps at big system fonts instead of being cut.
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class HaloTone {
    /** Sand: the lead category. */
    Primary,

    /** Sage: the rare alternate. */
    Secondary,
}

/**
 * The reference "tinted icon halo": a rounded square washed with 20 % of a point colour holding one
 * icon in that colour. Leading icon of a list row or a notice.
 */
@Composable
fun IconHalo(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: HaloTone = HaloTone.Primary,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) =
        when (tone) {
            HaloTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
            HaloTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        }
    Box(
        modifier = modifier.size(Spacing.touch).background(container, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The reference "soft icon tile": a hairline square with one thin icon, for empty states. */
@Composable
fun SoftIconTile(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier.size(72.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/**
 * A fact as a card: a quiet label over a value. [compact] is the 20 sp value of a passport on a
 * detail page; without it the value is the 30 sp figure of a ride.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    ColaCard(modifier) {
        Column(
            Modifier.padding(if (compact) Spacing.m else Spacing.card),
            verticalArrangement = Arrangement.spacedBy(if (compact) Spacing.xs else Spacing.s),
        ) {
            Eyebrow(label, maxLines = 2)
            Text(
                value,
                style = if (compact) ColaTheme.textStyles.figure else ColaTheme.textStyles.numeral,
                maxLines = 2,
            )
        }
    }
}

/**
 * The ColaBike mark: the brand yellow disk with the bike. The one place the yellow is a fill, and
 * small by design.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    Box(
        modifier = modifier.size(size).background(ColaTheme.colors.brand, PillShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(ColaIcons.Bike),
            contentDescription = null,
            tint = ColaTheme.colors.onBrand,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}
