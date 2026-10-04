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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing

private val Thumb = 72.dp

/**
 * A model of the component catalog in a list: the cover (or a quiet tool icon), brand, name, the
 * category, and on how many public bikes it is. The whole card is one touch target; TalkBack reads
 * it as one phrase.
 */
@Composable
fun ComponentCard(
    name: String,
    brand: String,
    category: String,
    builds: Int,
    coverUrl: String?,
    archived: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        modifier =
            modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                onClick(label = openLabel) {
                    onClick()
                    true
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
            ComponentThumb(coverUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                if (brand.isNotBlank()) Eyebrow(brand)
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.isNotBlank()) {
                    Text(
                        category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    pluralStringResource(R.plurals.cola_component_builds, builds, builds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (archived) PillBadge(stringResource(R.string.cola_component_archived))
            }
            Icon(
                painterResource(ColaIcons.ChevronRight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** The cover of a model, square; without one a tool icon on the quiet surface. */
@Composable
fun ComponentThumb(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Thumb)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        val placeholder: @Composable () -> Unit = {
            Icon(
                painterResource(ColaIcons.Build),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        if (url == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = {},
                error = { placeholder() },
                modifier = Modifier.size(Thumb),
            )
        }
    }
}
