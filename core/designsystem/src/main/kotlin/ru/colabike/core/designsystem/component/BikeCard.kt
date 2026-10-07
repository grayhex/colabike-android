package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import coil3.compose.SubcomposeAsyncImage
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeSummary

/**
 * Wide and low: a bike photographed from the side is shown whole, wheels and handlebar included,
 * and the card stays short enough for two of them on a phone screen (see `BikesScreenSizeTest`).
 */
internal const val BikePhotoAspect = 2.4f

/**
 * A bike as a card: its picture whole on a quiet backing (a photo is never cropped here: free space
 * at the sides is the price), then what it is, its name in full (it wraps, it is not cut), the
 * author and the counters. TalkBack reads one sentence and offers one action.
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
    ColaCard(
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
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(BikePhotoAspect)) {
            BikePhoto(bike.cover?.url, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            if (former != null) {
                PillBadge(former, modifier = Modifier.padding(Spacing.s).align(Alignment.TopStart))
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.card)
                .padding(top = Spacing.m, bottom = Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(bike.name, style = MaterialTheme.typography.headlineSmall)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                bike.author?.let {
                    Avatar(it.displayName, it.avatarUrl, size = 24.dp)
                    Text(
                        it.displayName,
                        style = MaterialTheme.typography.bodyMedium,
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

/**
 * A bike's picture: the photo, or, when there is none, a quiet tile that says so. A photo that
 * cannot be loaded (deleted, no right to it, no connection) shows its own quiet tile, not an empty
 * grey box. Never a made-up picture. [contentScale] is `Crop` where the picture only fills a frame
 * (a thumbnail), `Fit` where the bike has to be seen whole.
 */
@Composable
fun BikePhoto(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                loading = {},
                error = { PhotoTile(stringResource(R.string.cola_photo_unavailable)) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            PhotoTile(stringResource(R.string.cola_no_photo))
        }
    }
}

/** The tile that stands in for a picture: a thin icon and a word about why it is not there. */
@Composable
fun PhotoTile(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Icon(
                painter = painterResource(ColaIcons.Image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Eyebrow(text)
        }
    }
}

@Composable
internal fun Counter(icon: Int, value: Int, liked: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (liked) ColaTheme.colors.like else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
