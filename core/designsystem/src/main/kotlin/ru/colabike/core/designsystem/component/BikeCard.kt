package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeSummary

/**
 * Photo-first bike card: the cover fills the top, name and what the bike is below, then the author
 * and the counters. TalkBack reads one sentence and offers one action.
 */
@Composable
fun BikeCard(bike: BikeSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val subtitle = bikeSubtitle(bike.brand, bike.model, bike.year)
    val likes = pluralStringResource(R.plurals.cola_likes, bike.likes, bike.likes)
    val comments = pluralStringResource(R.plurals.cola_comments, bike.comments, bike.comments)
    val author = bike.author?.let { stringResource(R.string.cola_by_author, it.displayName) }
    val former = if (bike.isFormer) stringResource(R.string.cola_former_bike) else null
    val description =
        listOfNotNull(bike.name, subtitle.ifBlank { null }, former, author, likes, comments)
            .joinToString(", ")
    val openLabel = stringResource(R.string.cola_open)
    OutlinedCard(
        onClick = onClick,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            },
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            Modifier.fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (bike.cover != null) {
                AsyncImage(
                    model = bike.cover?.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(ColaIcons.Bike),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp).align(Alignment.Center),
                )
            }
            if (former != null) {
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(Spacing.s).align(Alignment.TopStart),
                ) {
                    Text(
                        former,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                    )
                }
            }
        }
        Column(
            Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                bike.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                bike.author?.let {
                    Avatar(it.displayName, it.avatarUrl, size = 20.dp)
                    Text(
                        it.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Box(Modifier.weight(1f))
                Counter(
                    if (bike.liked) ColaIcons.LikeFilled else ColaIcons.Like,
                    bike.likes,
                    liked = bike.liked,
                )
                Counter(ColaIcons.Comment, bike.comments)
            }
        }
    }
}

@Composable
private fun Counter(icon: Int, value: Int, liked: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (liked) ColaTheme.colors.like else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
