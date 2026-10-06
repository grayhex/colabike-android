package ru.colabike.app.bikes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository

/**
 * The form of a bike: [id] null for a new one, else a change of that one. The form lives in its
 * ViewModel, so a turn of the phone keeps what was typed; [onSaved] gets the id of the saved bike
 * and [onDeleted] says the bike is gone, so its page goes with the form.
 */
@Composable
fun BikeEditorRoute(
    repository: BikesRepository,
    id: String?,
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    onDeleted: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    val viewModel =
        viewModel(key = "bike-editor:${id ?: "new"}") {
            BikeEditorViewModel(repository, id?.let(::BikeId))
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state as? BikeEditorUiState.Editing
    val saved = editing?.saved
    LaunchedEffect(saved?.summary?.id) { saved?.let { onSaved(it.summary.id.value) } }
    LaunchedEffect(editing?.deleted) { if (editing?.deleted == true) onDeleted() }
    val actions =
        remember(viewModel) {
            BikeEditorActions(
                onBack = onBack,
                onRetryLoad = viewModel::load,
                onReload = viewModel::load,
                onName = viewModel::setName,
                onBrand = viewModel::setBrand,
                onModel = viewModel::setModel,
                onTrim = viewModel::setTrim,
                onYear = viewModel::setYear,
                onCategory = viewModel::setCategory,
                onSubtype = viewModel::setSubtype,
                onSuspension = viewModel::setSuspension,
                onConstruction = viewModel::setConstruction,
                onUse = viewModel::toggleUse,
                onElectric = viewModel::setElectric,
                onFatbike = viewModel::setFatbike,
                onDescription = viewModel::setDescription,
                onColor = viewModel::setColor,
                onSize = viewModel::setSize,
                onWeight = viewModel::setWeight,
                onMileage = viewModel::setMileage,
                onLink = viewModel::setManufacturerUrl,
                onPrice = viewModel::setPrice,
                onPriceVisibility = viewModel::setPriceVisibility,
                onFormer = viewModel::setFormer,
                onPublic = viewModel::setPublic,
                onSave = viewModel::save,
                onAskDelete = viewModel::askDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onOpenGarage = onOpenGarage,
            )
        }
    BikeEditorScreen(state, editing = id != null, actions = actions)
}
