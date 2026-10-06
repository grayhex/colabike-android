package ru.colabike.app.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Clock
import java.time.LocalDate
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository

/**
 * The form of a journal entry of the bike [bikeId]: [id] null for a new one, else a change of that
 * one. The form lives in its ViewModel, so a turn of the phone keeps what was typed; [onSaved] gets
 * the id of the saved entry and [onDeleted] says the entry is gone, so its page goes with the form.
 */
@Composable
fun JournalEditorRoute(
    journal: JournalRepository,
    bikes: BikesRepository,
    clock: Clock,
    bikeId: String,
    id: String?,
    onBack: () -> Unit,
    onSaved: (id: String) -> Unit,
    onDeleted: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    val viewModel =
        viewModel(key = "journal-editor:$bikeId:${id ?: "new"}") {
            JournalEditorViewModel(
                journal,
                bikes,
                BikeId(bikeId),
                id?.let(::JournalId),
                today = { LocalDate.now(clock) },
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editing = state as? JournalEditorUiState.Editing
    val saved = editing?.saved
    LaunchedEffect(saved?.summary?.id) { saved?.let { onSaved(it.summary.id.value) } }
    LaunchedEffect(editing?.deleted) { if (editing?.deleted == true) onDeleted() }
    val actions =
        remember(viewModel) {
            JournalEditorActions(
                onBack = onBack,
                onRetryLoad = viewModel::load,
                onReload = viewModel::load,
                onKind = viewModel::setKind,
                onTitle = viewModel::setTitle,
                onBody = viewModel::setBody,
                onStatus = viewModel::setStatus,
                onPublic = viewModel::setPublic,
                onDate = viewModel::setEventDate,
                onToday = viewModel::setEventDateToday,
                onMileage = viewModel::setMileage,
                onInstallation = viewModel::setInstallationResult,
                onComponent = viewModel::toggleComponent,
                onSave = viewModel::save,
                onAskDelete = viewModel::askDelete,
                onCancelDelete = viewModel::cancelDelete,
                onConfirmDelete = viewModel::confirmDelete,
                onOpenGarage = onOpenGarage,
            )
        }
    JournalEditorScreen(state, editing = id != null, actions = actions)
}
