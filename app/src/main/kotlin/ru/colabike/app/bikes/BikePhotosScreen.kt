package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Photo
import ru.colabike.core.model.PhotoRules

/** What the photos screen can ask for. */
data class BikePhotosActions(
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

private val ContentWidth = 600.dp
private val ThumbnailWidth = 120.dp
private val ThumbnailHeight = 90.dp

/**
 * The pictures of one's own bike. "Add" opens the system picker (no permission is asked); what is
 * picked goes one after another with its progress, and stays in the list with the reason if it did
 * not go. A photo can be made the cover or removed, both with buttons that have names.
 */
@Composable
fun BikePhotosScreen(state: BikePhotosUiState, actions: BikePhotosActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.photos_title), onBack = actions.onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikePhotosUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikePhotosUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetry, Modifier.fillMaxSize())
                BikePhotosUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.bike_unavailable_title),
                        message = stringResource(R.string.bike_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("photos:unavailable"),
                    )
                is BikePhotosUiState.Ready -> Ready(state, actions)
            }
        }
    }
}

@Composable
private fun Ready(state: BikePhotosUiState.Ready, actions: BikePhotosActions) {
    val photos = state.bike.photos
    LazyColumn(
        Modifier.fillMaxSize().testTag("photos"),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(
                Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Button(
                    onClick = actions.onPick,
                    enabled = state.slotsLeft > 0,
                    modifier = Modifier.fillMaxWidth().testTag("photos:add"),
                ) {
                    Text(stringResource(R.string.photos_add))
                }
                Text(
                    stringResource(
                        R.string.photos_count,
                        state.occupied,
                        PhotoRules.MAX_PER_BIKE,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.testTag("photos:count"),
                )
                Text(
                    stringResource(
                        if (state.slotsLeft > 0) R.string.photos_rules else R.string.photos_full
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("photos:rules"),
                )
                state.skipped?.let {
                    Text(
                        stringResource(R.string.photos_skipped, it, PhotoRules.MAX_PER_BIKE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("photos:skipped"),
                    )
                }
                state.problem?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier =
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                .testTag("photos:problem"),
                    )
                }
                if (state.sending) {
                    Text(
                        stringResource(R.string.photos_keep_open),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("photos:keep-open"),
                    )
                }
                state.pending.forEachIndexed { index, item ->
                    PendingCard(item, index + 1, actions)
                }
                if (photos.isEmpty() && state.pending.isEmpty()) {
                    Text(
                        stringResource(R.string.photos_empty),
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
                        cover = photo.id == state.bike.summary.cover?.id,
                        busy = state.busy == photo.id,
                        idle = state.busy == null,
                        actions = actions,
                    )
                }
            }
        }
    }
    state.confirmingDelete?.let { DeleteDialog(it, photos, actions) }
}

@Composable
private fun PhotoCard(
    photo: Photo,
    number: Int,
    total: Int,
    cover: Boolean,
    busy: Boolean,
    idle: Boolean,
    actions: BikePhotosActions,
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
                } else {
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
private fun PendingCard(item: PendingPhoto, number: Int, actions: BikePhotosActions) {
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
private fun DeleteDialog(photoId: String, photos: List<Photo>, actions: BikePhotosActions) {
    val number = photos.indexOfFirst { it.id == photoId } + 1
    AlertDialog(
        onDismissRequest = actions.onCancelDelete,
        title = { Text(stringResource(R.string.photos_delete_title)) },
        text = { Text(stringResource(R.string.photos_delete_message, number)) },
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
