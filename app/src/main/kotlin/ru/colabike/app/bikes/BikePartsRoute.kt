package ru.colabike.app.bikes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.catalog.CatalogSource
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository

/** The build of one's own bike: its parts, the order of its groups. */
@Composable
fun BikePartsRoute(
    repository: BikesRepository,
    catalog: CatalogSource,
    bikeId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenPart: (String) -> Unit,
    onOpenGarage: () -> Unit,
) {
    LaunchedEffect(catalog) { catalog.load() }
    val viewModel =
        viewModel(key = "bike-parts:$bikeId") {
            BikePartsViewModel(
                repository,
                BikeId(bikeId),
                dictionary = { catalog.state.value.catalog.components },
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            BikePartsActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onAdd = onAdd,
                onOpenPart = onOpenPart,
                onMove = viewModel::move,
                onOpenGarage = onOpenGarage,
            )
        }
    BikePartsScreen(state, actions)
}

/**
 * The form of a part: [componentId] null for a new one. The form lives in its ViewModel, so a turn
 * of the phone keeps what was typed; [onDone] closes it after a save or a deletion.
 */
@Composable
fun BikePartEditorRoute(
    repository: BikesRepository,
    catalog: CatalogSource,
    bikeId: String,
    componentId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    LaunchedEffect(catalog) { catalog.load() }
    val viewModel =
        viewModel(key = "bike-part-editor:$bikeId:${componentId ?: "new"}") {
            BikePartEditorViewModel(
                repository,
                BikeId(bikeId),
                componentId,
                dictionary = { catalog.state.value.catalog.components },
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state as? PartEditorUiState.Editing
    LaunchedEffect(editing?.saved) { if (editing?.saved == true) onDone() }
    LaunchedEffect(editing?.deleted) { if (editing?.deleted == true) onDone() }
    val actions =
        remember(viewModel) {
            PartEditorActions(
                onBack = onBack,
                onRetryLoad = viewModel::load,
                onReload = viewModel::load,
                onCategory = viewModel::setCategory,
                onName = viewModel::setName,
                onNotes = viewModel::setNotes,
                onPrice = viewModel::setPrice,
                onUrl = viewModel::setUrl,
                onSection = viewModel::setSection,
                onSave = viewModel::save,
                onAskDelete = viewModel::askDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onOpenGarage = onOpenGarage,
            )
        }
    BikePartEditorScreen(state, editing = componentId != null, actions = actions)
}
