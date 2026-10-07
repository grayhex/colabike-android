package ru.colabike.app.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.colabike.app.R
import ru.colabike.app.bikes.PhotosActions
import ru.colabike.app.bikes.PhotosList
import ru.colabike.app.bikes.PhotosTexts
import ru.colabike.app.bikes.PhotosView
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.model.PhotoRules

/**
 * The pictures of one's own journal entry. "Add" opens the system picker (no permission is asked);
 * what is picked goes one after another with its progress, and stays in the list with the reason if
 * it did not go. A photo can be removed, after a question.
 */
@Composable
fun JournalPhotosScreen(state: JournalPhotosUiState, actions: PhotosActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.journal_photos_title),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                JournalPhotosUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is JournalPhotosUiState.Failed ->
                    ErrorState(state.message.resolve(), actions.onRetry, Modifier.fillMaxSize())
                JournalPhotosUiState.Unavailable ->
                    EmptyState(
                        title = stringResource(R.string.journal_unavailable_title),
                        message = stringResource(R.string.journal_cannot_edit),
                        icon = ColaIcons.Bike,
                        actionLabel = stringResource(R.string.bike_open_garage),
                        onAction = actions.onOpenGarage,
                        modifier = Modifier.fillMaxSize().testTag("photos:unavailable"),
                    )
                is JournalPhotosUiState.Ready ->
                    PhotosList(
                        state.toView(),
                        actions,
                        // An entry has no cover: its pictures go in the order they were added.
                        withCover = false,
                        texts =
                            PhotosTexts(
                                rules = R.string.journal_photos_rules,
                                full = R.string.journal_photos_full,
                                empty = R.string.journal_photos_empty,
                                deleteMessage = R.string.journal_photos_delete_message,
                            ),
                    )
            }
        }
    }
}

private fun JournalPhotosUiState.Ready.toView() =
    PhotosView(
        photos = entry.photos,
        coverId = null,
        pending = pending,
        busy = busy,
        confirmingDelete = confirmingDelete,
        problem = problem,
        skipped = skipped,
        occupied = occupied,
        slotsLeft = slotsLeft,
        limit = PhotoRules.MAX_PER_ENTRY,
        sending = sending,
    )
