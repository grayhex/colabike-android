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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** Landscape, like a bike photographed from the side: crops lose the least. */
internal const val BikePhotoAspect = 4f / 3f

/**
 * Photo-first bike card. The cover fills the card; the name sits on the photo in the serif voice,
 * on a gradient that fades into the card surface so it reads on any picture and in both themes.
 * Below the photo: the author and the counters. TalkBack reads one sentence and offers one action.
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
        val fade = MaterialTheme.colorScheme.surfaceContainer
        Box(Modifier.fillMaxWidth().aspectRatio(BikePhotoAspect)) {
            BikePhoto(bike.cover?.url, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.8f to fade.copy(alpha = 0.78f),
                            1f to fade,
                        )
                    )
            )
            if (former != null) {
                PillBadge(former, modifier = Modifier.padding(Spacing.m).align(Alignment.TopStart))
            }
            Column(
                Modifier.align(Alignment.BottomStart).padding(Spacing.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (subtitle.isNotBlank()) Eyebrow(subtitle)
                Text(
                    bike.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.card, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            bike.author?.let {
                Avatar(it.displayName, it.avatarUrl, size = 24.dp)
                Text(
                    it.displayName,
                    style = MaterialTheme.typography.bodySmall,
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

/**
 * A bike's picture: the photo cropped to fill, or, when there is none, a quiet tile that says so. A
 * photo that cannot be loaded (deleted, no right to it, no connection) shows its own quiet tile,
 * not an empty grey box. Never a made-up picture.
 */
@Composable
fun BikePhoto(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        if (url != null) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
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
