package ru.colabike.app.journal

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.bikes.PhotoFiles
import ru.colabike.app.bikes.PhotosActions
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.PhotoRules

/**
 * The pictures of one's own journal entry. They are chosen in the system picker, which needs no
 * permission and hands over only what the person picked; [photoFiles] makes files of them for the
 * transfer.
 */
@Composable
fun JournalPhotosRoute(
    repository: JournalRepository,
    entryId: String,
    onBack: () -> Unit,
    onOpenGarage: () -> Unit,
    photoFiles: PhotoFiles,
) {
    val viewModel =
        viewModel(key = "journal-photos:$entryId") {
            JournalPhotosViewModel(repository, JournalId(entryId), photoFiles)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(PhotoRules.MAX_PER_ENTRY)
        ) { uris ->
            if (uris.isNotEmpty()) viewModel.add(uris.map { it.toString() })
        }
    val actions =
        remember(viewModel, picker) {
            PhotosActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onPick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onCancelPending = viewModel::cancel,
                onRetryPending = viewModel::retry,
                onAskDelete = viewModel::askDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onOpenGarage = onOpenGarage,
            )
        }
    JournalPhotosScreen(state, actions)
}
