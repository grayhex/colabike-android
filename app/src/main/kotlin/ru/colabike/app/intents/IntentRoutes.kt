package ru.colabike.app.intents

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import ru.colabike.app.AppDependencies
import ru.colabike.app.messages.WriteState
import ru.colabike.app.messages.WriteViewModel
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.UserId

/**
 * The intentions of the community and the person's own. The list is read again whenever the screen
 * comes back (after creating, changing or cancelling one), but not on the first resume, when it is
 * already on its way.
 */
@Composable
fun IntentsRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val viewModel = viewModel { IntentsViewModel(dependencies.intents) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.resumed()
        onPauseOrDispose {}
    }
    val actions =
        remember(viewModel) {
            IntentsActions(
                onBack = onBack,
                onSegment = viewModel::select,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onOpen = { onOpen(it.id) },
                onCreate = onCreate,
            )
        }
    IntentsScreen(state, actions)
}

/** One intention; "Write" opens the conversation with its author and sends nothing itself. */
@Composable
fun IntentRoute(
    dependencies: AppDependencies,
    id: String,
    chat: ChatRepository?,
    onBack: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onOpenConversation: (ChannelCid) -> Unit,
    onEdit: () -> Unit,
    onOpenIntents: () -> Unit,
) {
    val viewModel = viewModel(key = "intent:$id") { IntentViewModel(dependencies.intents, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val writer = chat?.let { viewModel(key = "write:intent:$id") { WriteViewModel(it) } }
    val writing by
        (writer?.state ?: remember { MutableStateFlow<WriteState>(WriteState.Idle) })
            .collectAsStateWithLifecycle()
    val opened by
        (writer?.opened ?: remember { MutableStateFlow<ChannelCid?>(null) })
            .collectAsStateWithLifecycle()
    LaunchedEffect(opened) {
        opened?.let {
            writer?.consumed()
            onOpenConversation(it)
        }
    }
    LifecycleResumeEffect(viewModel) {
        // Back from the editor: show what the server now holds.
        viewModel.resumed()
        onPauseOrDispose {}
    }
    val loaded = state as? IntentUiState.Loaded
    LaunchedEffect(loaded?.deleted) { if (loaded?.deleted == true) onBack() }
    val author = loaded?.intent?.author?.id
    val actions =
        remember(viewModel, author, writer) {
            IntentActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onOpenAuthor = onOpenAuthor,
                onWrite =
                    if (writer != null && author != null) {
                        { writer.write(UserId(author.value)) }
                    } else null,
                onEdit = onEdit,
                onCancel = viewModel::cancel,
                onDelete = viewModel::delete,
                onOpenIntents = onOpenIntents,
            )
        }
    IntentScreen(state, writing, actions)
}

/** The form: the platform's own date and time dialogs, which are accessible and need no code. */
@Composable
fun IntentEditorRoute(
    dependencies: AppDependencies,
    id: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenIntents: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel =
        viewModel(key = "intent-editor:${id ?: "new"}") {
            IntentEditorViewModel(
                repository = dependencies.intents,
                clock = dependencies.clock,
                phoneZone = ZoneId.systemDefault(),
                id = id,
                location = dependencies.coarseLocation,
            )
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var permissionToken by rememberSaveable { mutableStateOf<Long?>(null) }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionToken?.let { viewModel.locationPermission(it, granted) }
            permissionToken = null
        }
    DisposableEffect(viewModel) { onDispose { viewModel.abandonLocation() } }
    val saved = (state as? IntentEditorUiState.Editing)?.saved
    LaunchedEffect(saved?.id) { saved?.let { onSaved(it.id) } }
    val actions =
        remember(viewModel, context) {
            IntentEditorActions(
                onBack = onBack,
                onRetryLoad = viewModel::load,
                onReload = viewModel::load,
                onReadiness = viewModel::setReadiness,
                onAreaLabel = viewModel::setAreaLabel,
                onOpenArea = viewModel::openArea,
                onCancelArea = viewModel::cancelArea,
                onConfirmArea = viewModel::confirmArea,
                onCancelLocation = viewModel::abandonLocation,
                onAreaDraftLabel = viewModel::setAreaDraftLabel,
                onAreaCenter = viewModel::setAreaCenter,
                onAreaRadius = viewModel::setAreaRadius,
                onRemoveAreaGeometry = viewModel::removeAreaGeometry,
                onLocateArea = {
                    val token = viewModel.requestLocation()
                    if (token != null) {
                        if (dependencies.coarseLocation.granted())
                            viewModel.locationPermission(token, true)
                        else if (permissionToken == null) {
                            permissionToken = token
                            permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        } else viewModel.abandonLocation()
                    }
                },
                onPurpose = viewModel::setPurpose,
                onPace = viewModel::setPace,
                onSurface = viewModel::setSurface,
                onMeetNewPeople = viewModel::setMeetNewPeople,
                onVisibility = viewModel::setVisibility,
                onAllowSuggestions = viewModel::setAllowSuggestions,
                onAddWindow = viewModel::addWindow,
                onRemoveWindow = viewModel::removeWindow,
                onPickDate = viewModel::setDate,
                onPickStart = viewModel::setStart,
                onPickEnd = viewModel::setEnd,
                onFold = viewModel::setFold,
                onSave = viewModel::save,
                onOpenIntents = onOpenIntents,
                askDate = { current: LocalDate, picked: (LocalDate) -> Unit ->
                    DatePickerDialog(
                            context,
                            { _, year, month, day -> picked(LocalDate.of(year, month + 1, day)) },
                            current.year,
                            current.monthValue - 1,
                            current.dayOfMonth,
                        )
                        .show()
                },
                askTime = { current: LocalTime, picked: (LocalTime) -> Unit ->
                    TimePickerDialog(
                            context,
                            { _, hour, minute -> picked(LocalTime.of(hour, minute)) },
                            current.hour,
                            current.minute,
                            true,
                        )
                        .show()
                },
            )
        }
    IntentEditorScreen(state, editing = id != null, actions = actions, maps = dependencies.maps)
}
