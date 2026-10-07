package ru.colabike.app.bikes

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Photo

/** What the photos screens can ask for; a screen without covers never calls [onSetCover]. */
data class PhotosActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPick: () -> Unit = {},
    val onCancelPending: (Int) -> Unit = {},
    val onRetryPending: (Int) -> Unit = {},
    val onSetCover: (String) -> Unit = {},
    val onAskDelete: (String) -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenGarage: () -> Unit = {},
)

/** What a list of pictures shows: the same for a bike and for a journal entry. */
@Immutable
internal data class PhotosView(
    val photos: List<Photo>,
    val coverId: String?,
    val pending: List<PendingPhoto>,
    val busy: String?,
    val confirmingDelete: String?,
    val problem: UiText?,
    val skipped: Int?,
    val occupied: Int,
    val slotsLeft: Int,
    val limit: Int,
    val sending: Boolean,
)

/** The words that differ between a bike's pictures and an entry's. */
internal class PhotosTexts(
    @param:StringRes val rules: Int,
    @param:StringRes val full: Int,
    @param:StringRes val empty: Int,
    @param:StringRes val deleteMessage: Int,
)

internal val PhotosContentWidth = 600.dp
private val ThumbnailWidth = 120.dp
private val ThumbnailHeight = 90.dp

/**
 * The pictures of one's own bike or entry: "Add", the count, the ones on their way and the ones on
 * the server, each with buttons that have names (made the cover, removed after a question).
 */
@Composable
internal fun PhotosList(
    view: PhotosView,
    actions: PhotosActions,
    withCover: Boolean,
    texts: PhotosTexts,
) {
    val photos = view.photos
    LazyColumn(
        Modifier.fillMaxSize().testTag("photos"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = PhotosContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Button(
                    onClick = actions.onPick,
                    enabled = view.slotsLeft > 0,
                    modifier = Modifier.fillMaxWidth().testTag("photos:add"),
                ) {
                    Text(stringResource(R.string.photos_add))
                }
                Text(
                    stringResource(
                        R.string.photos_count,
                        view.occupied,
                        view.limit,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("photos:count"),
                )
                Text(
                    stringResource(if (view.slotsLeft > 0) texts.rules else texts.full),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("photos:rules"),
                )
                view.skipped?.let {
                    Text(
                        stringResource(R.string.photos_skipped, it, view.limit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("photos:skipped"),
                    )
                }
                view.problem?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("photos:problem"),
                    )
                }
                if (view.sending) {
                    Text(
                        stringResource(R.string.photos_keep_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("photos:keep-open"),
                    )
                }
                view.pending.forEachIndexed { index, item ->
                    PendingCard(item, index + 1, actions)
                }
                if (photos.isEmpty() && view.pending.isEmpty()) {
                    Text(
                        stringResource(texts.empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("photos:empty"),
                    )
                }
                photos.forEachIndexed { index, photo ->
                    PhotoCard(
                        photo = photo,
                        number = index + 1,
                        total = photos.size,
                        cover = withCover && photo.id == view.coverId,
                        withCover = withCover,
                        busy = view.busy == photo.id,
                        idle = view.busy == null,
                        actions = actions,
                    )
                }
            }
        }
    }
    view.confirmingDelete?.let { DeleteDialog(it, photos, actions, texts) }
}

@Composable
private fun PhotoCard(
    photo: Photo,
    number: Int,
    total: Int,
    cover: Boolean,
    withCover: Boolean,
    busy: Boolean,
    idle: Boolean,
    actions: PhotosActions,
) {
    ColaCard(Modifier.fillMaxWidth().testTag("photos:photo:${photo.id}")) {
        Row(
            Modifier.padding(Spacing.card),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            BikePhoto(
                photo.thumbnailUrl(),
                Modifier.size(ThumbnailWidth, ThumbnailHeight).clip(MaterialTheme.shapes.small),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Text(
                        stringResource(R.string.photos_photo_named, number, total),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (busy) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp).testTag("photos:busy:${photo.id}"),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                if (cover) {
                    Eyebrow(
                        stringResource(R.string.photos_cover),
                        modifier = Modifier.testTag("photos:is-cover:${photo.id}"),
                    )
                } else if (withCover) {
                    TextButton(
                        onClick = { actions.onSetCover(photo.id) },
                        enabled = idle,
                        modifier = Modifier.testTag("photos:cover:${photo.id}"),
                    ) {
                        Text(stringResource(R.string.photos_make_cover_named, number))
                    }
                }
                TextButton(
                    onClick = { actions.onAskDelete(photo.id) },
                    enabled = idle,
                    modifier = Modifier.testTag("photos:delete:${photo.id}"),
                ) {
                    Text(
                        stringResource(R.string.photos_delete_named, number),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingCard(item: PendingPhoto, number: Int, actions: PhotosActions) {
    ColaCard(Modifier.fillMaxWidth().testTag("photos:pending:${item.id}")) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Icon(
                    painterResource(ColaIcons.Image),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    stringResource(R.string.photos_new_named, number),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val state = item.state) {
                PendingState.Preparing ->
                    Text(
                        stringResource(R.string.photos_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                PendingState.Waiting ->
                    Text(
                        stringResource(R.string.photos_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                is PendingState.Sending -> {
                    Text(
                        stringResource(R.string.photos_sending, (state.progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().testTag("photos:progress:${item.id}"),
                    )
                }
                is PendingState.Failed ->
                    Text(
                        state.message.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("photos:failure:${item.id}"),
                    )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val failed = item.state as? PendingState.Failed
                if (failed?.retry == true) {
                    TextButton(
                        onClick = { actions.onRetryPending(item.id) },
                        modifier = Modifier.testTag("photos:retry:${item.id}"),
                    ) {
                        Text(stringResource(R.string.photos_retry_named, number))
                    }
                }
                TextButton(
                    onClick = { actions.onCancelPending(item.id) },
                    modifier = Modifier.testTag("photos:cancel:${item.id}"),
                ) {
                    Text(
                        stringResource(
                            if (failed != null) R.string.photos_drop_named
                            else R.string.photos_cancel_named,
                            number,
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteDialog(
    photoId: String,
    photos: List<Photo>,
    actions: PhotosActions,
    texts: PhotosTexts,
) {
    val number = photos.indexOfFirst { it.id == photoId } + 1
    AlertDialog(
        onDismissRequest = actions.onCancelDelete,
        title = { Text(stringResource(R.string.photos_delete_title)) },
        text = { Text(stringResource(texts.deleteMessage, number)) },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirmDelete,
                modifier = Modifier.testTag("photos:delete-confirm"),
            ) {
                Text(
                    stringResource(R.string.bike_delete_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = actions.onCancelDelete,
                modifier = Modifier.testTag("photos:delete-cancel"),
            ) {
                Text(stringResource(R.string.bike_delete_cancel))
            }
        },
    )
}
