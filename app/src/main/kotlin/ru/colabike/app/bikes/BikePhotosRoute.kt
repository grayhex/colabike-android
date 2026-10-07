package ru.colabike.app.bikes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.PhotoRules

/**
 * The pictures of one's own bike. They are chosen in the system picker, which needs no permission
 * and hands over only what the person picked; [photoFiles] makes files of them for the transfer.
 */
@Composable
fun BikePhotosRoute(
    repository: BikesRepository,
    bikeId: String,
    onBack: () -> Unit,
    onOpenGarage: () -> Unit,
    photoFiles: PhotoFiles,
    pickOnOpen: Boolean = false,
) {
    val viewModel =
        viewModel(key = "bike-photos:$bikeId") {
            BikePhotosViewModel(repository, BikeId(bikeId), photoFiles)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(PhotoRules.MAX_PER_BIKE)
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
                onSetCover = viewModel::setCover,
                onAskDelete = viewModel::askDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onOpenGarage = onOpenGarage,
            )
        }
    // "+ Фото" on the page of the bike opens the picker at once, and once: a turn of the screen or
    // coming back to it does not open it again.
    var picked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(pickOnOpen) {
        if (pickOnOpen && !picked) {
            picked = true
            actions.onPick()
        }
    }
    BikePhotosScreen(state, actions)
}
