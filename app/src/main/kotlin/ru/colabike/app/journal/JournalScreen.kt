package ru.colabike.app.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.bikes.BikeGallery
import ru.colabike.app.bikes.ComponentsCard
import ru.colabike.app.bikes.orderComponents
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.markdown.MarkdownText
import ru.colabike.app.safety.ReportMenu
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.journalKindLabel
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository

/** Where an entry leads. Callbacks, so the screen never touches navigation itself. */
class JournalActions(
    val onBack: () -> Unit,
    val onOpenBike: (BikeId) -> Unit,
    val onOpenAuthor: (ref: String) -> Unit,
    val onOpenComments: (id: JournalId, title: String) -> Unit = { _, _ -> },
    val onOpenComponent: (modelId: String) -> Unit = {},
    /** The author changes the entry: the bike it is about and the entry. */
    val onEdit: (bike: BikeId, id: JournalId) -> Unit = { _, _ -> },
)

@Composable
fun JournalRoute(
    repository: JournalRepository,
    auth: AuthActions,
    id: JournalId,
    actions: JournalActions,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    safety: SafetyRepository? = null,
) {
    val viewModel = viewModel { JournalViewModel(repository, id, commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val opener = LocalLinkOpener.current
    val signedIn = authState is AuthState.SignedIn
    JournalScreen(
        state = state,
        actions = actions,
        onRetry = viewModel::load,
        // A closed entry answers a guest "not found" exactly as a missing one does.
        onSignIn = if (signedIn) null else signIn,
        // A guest is asked to sign in first; the entry is not saved for them afterwards.
        onToggleSaved = if (signedIn) viewModel::toggleSaved else signIn,
        onOpenLink = opener::open,
        topActions = {
            // Only the author is given the version a change names: the others have nothing to edit.
            val own = (state as? JournalUiState.Loaded)?.entry?.takeIf { it.version != null }
            if (own != null) {
                IconButton(
                    onClick = { actions.onEdit(own.summary.bike.id, own.summary.id) },
                    modifier = Modifier.testTag("journal:edit"),
                ) {
                    Icon(
                        painterResource(ColaIcons.Edit),
                        contentDescription = stringResource(R.string.journal_edit),
                    )
                }
            }
            ReportMenu(
                target = ReportTarget(ReportKind.Journal, id.value),
                authorId = (state as? JournalUiState.Loaded)?.entry?.summary?.author?.id,
                safety = safety,
                auth = auth,
            )
        },
    )
}

/**
 * One journal entry: what it is about and when, the text, the photos, the components as they were
 * then, and the save. Only what the server gave for this viewer is here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JournalScreen(
    state: JournalUiState,
    actions: JournalActions,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)? = null,
    onToggleSaved: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    topActions: @Composable RowScope.() -> Unit = {},
) {
    val loaded = state as? JournalUiState.Loaded
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    loaded?.entry?.summary?.kind?.let { journalKindLabel(it) }
                        ?: stringResource(R.string.journal_title),
                onBack = actions.onBack,
                actions = if (loaded != null) topActions else ({}),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                JournalUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is JournalUiState.Failed ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorState(state.message.resolve(), onRetry = onRetry)
                        if (state.notFound && onSignIn != null) {
                            Text(
                                stringResource(R.string.journal_private_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.xl),
                            )
                            Button(
                                onClick = onSignIn,
                                modifier =
                                    Modifier.padding(Spacing.l).heightIn(min = Spacing.touch),
                            ) {
                                Text(stringResource(R.string.profile_sign_in))
                            }
                        }
                    }
                is JournalUiState.Loaded -> Entry(state, actions, onToggleSaved, onOpenLink)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Entry(
    state: JournalUiState.Loaded,
    actions: JournalActions,
    onToggleSaved: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val entry = state.entry
    val summary = entry.summary
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                summary.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            val draft = summary.status == JournalStatus.Draft
            val day =
                summary.eventDate?.let {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        .withLocale(locale)
                        .format(it)
                }
            val mileage =
                summary.mileageKm?.let {
                    stringResource(
                        ru.colabike.core.designsystem.R.string.cola_distance_km,
                        NumberFormat.getIntegerInstance(locale).format(it),
                    )
                }
            if (draft || !summary.isPublic || day != null || mileage != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    if (draft) {
                        PillBadge(
                            stringResource(
                                ru.colabike.core.designsystem.R.string.cola_journal_draft
                            )
                        )
                    }
                    day?.let { PillBadge(it, icon = ColaIcons.Calendar) }
                    mileage?.let { PillBadge(it, icon = ColaIcons.Route) }
                }
            }
            entry.installationResult?.let {
                Text(
                    stringResource(
                        R.string.journal_install_named,
                        stringResource(installLabel(it)),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("journal:installation"),
                )
            }
            ColaListItem(
                title = summary.bike.name,
                supporting = stringResource(R.string.journal_open_bike),
                icon = ColaIcons.Bike,
                onClick = { actions.onOpenBike(summary.bike.id) },
            )
            UserRow(
                summary.author,
                onClick = { actions.onOpenAuthor(summary.author.id.value) },
            )
            if (entry.photos.isNotEmpty()) BikeGallery(entry.photos, aspect = 4f / 3f)
            if (entry.body.isNotBlank()) MarkdownText(entry.body, onOpenLink)
            val updated = summary.updatedAt
            if (updated > summary.createdAt.plusSeconds(60)) {
                Text(
                    stringResource(
                        R.string.journal_updated,
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(locale)
                            .withZone(ZoneId.systemDefault())
                            .format(updated),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.components.isNotEmpty()) {
                Text(
                    stringResource(R.string.journal_components),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(top = Spacing.s).semantics { heading() },
                )
                orderComponents(entry.components, emptyList()).forEach { (_, components) ->
                    ComponentsCard(
                        components,
                        locale,
                        onOpenLink,
                        onOpenModel = actions.onOpenComponent,
                    )
                }
            }
            Counts(summary.likes, summary.comments)
            // Only a published public entry has a discussion (the API answers 404 for the rest).
            if (state.canSave) {
                ColaListItem(
                    title = stringResource(R.string.comments_open),
                    supporting = stringResource(R.string.comments_open_hint),
                    icon = ColaIcons.Comment,
                    onClick = { actions.onOpenComments(summary.id, summary.title) },
                )
            }
            if (state.canSave) SaveButton(state, onToggleSaved)
            state.saveError?.let {
                Text(
                    it.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun Counts(likes: Int, comments: Int) {
    val text =
        pluralStringResource(ru.colabike.core.designsystem.R.plurals.cola_likes, likes, likes) +
            " · " +
            pluralStringResource(
                ru.colabike.core.designsystem.R.plurals.cola_comments,
                comments,
                comments,
            )
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SaveButton(state: JournalUiState.Loaded, onToggle: () -> Unit) {
    val saved = state.saved == true
    val description =
        stringResource(
            if (saved) R.string.journal_unsave_description else R.string.journal_save_description
        )
    val content: @Composable () -> Unit = {
        Icon(
            painterResource(if (saved) ColaIcons.BookmarkFilled else ColaIcons.Bookmark),
            contentDescription = null,
        )
        Text(
            stringResource(if (saved) R.string.journal_saved else R.string.journal_save),
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
    val modifier =
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).semantics {
            contentDescription = description
        }
    Row(Modifier.fillMaxWidth()) {
        if (saved) {
            FilledTonalButton(
                onClick = onToggle,
                enabled = !state.saving,
                modifier = modifier,
                content = { content() },
            )
        } else {
            OutlinedButton(
                onClick = onToggle,
                enabled = !state.saving,
                modifier = modifier,
                content = { content() },
            )
        }
    }
}

private val ContentWidth = 720.dp
