package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.comments.CommentDrafts
import ru.colabike.app.comments.InlineDiscussionRoute
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.LocalSharer
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.safety.ReportMenu
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.JournalRepository
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository

@Composable
fun BikeDetailRoute(
    repository: BikesRepository,
    auth: AuthActions,
    links: SiteLinks,
    id: BikeId,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenAuthor: (ref: String) -> Unit = {},
    onOpenJournal: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenJournalEntry: (JournalId) -> Unit = {},
    onOpenRides: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenComponent: (modelId: String) -> Unit = {},
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    safety: SafetyRepository? = null,
    journal: JournalRepository? = null,
    comments: CommentsRepository? = null,
    drafts: CommentDrafts? = null,
    onEdit: ((id: BikeId) -> Unit)? = null,
    onEditParts: ((id: BikeId) -> Unit)? = null,
    onEditPhotos: ((id: BikeId) -> Unit)? = null,
    onAddPhoto: ((id: BikeId) -> Unit)? = null,
    onNewJournalEntry: ((id: BikeId) -> Unit)? = null,
) {
    val viewModel = viewModel { BikeDetailViewModel(repository, id, commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val sharer = LocalSharer.current
    val opener = LocalLinkOpener.current
    val signedIn = authState is AuthState.SignedIn
    val loaded = (state as? BikeDetailUiState.Loaded)?.bike
    val isOwner = loaded?.summary?.isOwner == true
    val journalModel = journal?.let {
        viewModel(key = "bike-journal:${id.value}") { BikeJournalPreviewViewModel(it, id) }
    }
    val journalState = journalModel?.state?.collectAsStateWithLifecycle()
    // Only a public bike has a discussion (the API answers 404 for the rest): a private one is not
    // asked for it, and shows no count.
    val discussion: (@Composable (count: Int) -> Unit)? =
        if (comments != null && drafts != null && loaded?.summary?.isPublic == true) {
            { count ->
                InlineDiscussionRoute(
                    repository = comments,
                    drafts = drafts,
                    auth = auth,
                    target = CommentTarget(CommentKind.Bike, id.value),
                    count = count,
                    ownerId = loaded.summary.author?.id,
                    onOpenAuthor = onOpenAuthor,
                    safety = safety,
                )
            }
        } else null
    BikeDetailScreen(
        state,
        showBack = showBack,
        onBack = onBack,
        onRetry = viewModel::load,
        // A private bike answers a guest "not found" exactly as a missing one does.
        onSignIn = if (signedIn) null else signIn,
        // A guest is asked to sign in first; the like is not given for them afterwards.
        onToggleLike = if (signedIn) viewModel::toggleLike else signIn,
        onShare = { title -> sharer.share(title, links.bike(id.value)) },
        onOpenLink = opener::open,
        onOpenAuthor = onOpenAuthor,
        onOpenJournal = onOpenJournal,
        onOpenJournalEntry = onOpenJournalEntry,
        onOpenRides = onOpenRides,
        onOpenComponent = onOpenComponent,
        // The build, the photos and the journal are changed by the owner, on screens of their own.
        onEditParts = if (onEditParts != null && isOwner) ({ onEditParts(id) }) else null,
        onEditPhotos = if (onEditPhotos != null && isOwner) ({ onEditPhotos(id) }) else null,
        onAddPhoto = if (onAddPhoto != null && isOwner) ({ onAddPhoto(id) }) else null,
        onNewJournalEntry =
            if (onNewJournalEntry != null && isOwner) ({ onNewJournalEntry(id) }) else null,
        journal = journalState?.value,
        onRetryJournal = { journalModel?.load() },
        discussion = discussion,
        topActions = {
            // The owner changes the bike; everyone else may report it.
            if (onEdit != null && isOwner) {
                IconButton(
                    onClick = { onEdit(id) },
                    modifier = Modifier.testTag("bike:edit"),
                ) {
                    Icon(
                        painterResource(ColaIcons.Edit),
                        contentDescription = stringResource(R.string.bike_edit_open),
                    )
                }
            }
            ReportMenu(
                target = ReportTarget(ReportKind.Bike, id.value),
                authorId = loaded?.summary?.author?.id,
                safety = safety,
                auth = auth,
                own = isOwner,
            )
        },
    )
}

/**
 * One bike on one page, compact: the photo whole with the owner's "+ Фото" and "Управлять" under
 * it, the name once with the kind and the year, who rides it with the like and the share, the
 * description and the passport, the build (shut until it is asked for), the latest entry of the
 * journal, the discussion with its first two comments, and the way to the rides. Opening anything
 * happens in place on the same scroll. Nothing the contract does not give is shown.
 *
 * [journal] and [discussion] are the two sections that have a source of their own: a page that is
 * given none shows neither, and a failure of either leaves the rest of the page as it is.
 */
@Composable
fun BikeDetailScreen(
    state: BikeDetailUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)? = null,
    onToggleLike: () -> Unit = {},
    onShare: (title: String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenAuthor: (ref: String) -> Unit = {},
    onOpenJournal: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenJournalEntry: (JournalId) -> Unit = {},
    onOpenRides: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenComponent: (modelId: String) -> Unit = {},
    onEditParts: (() -> Unit)? = null,
    onEditPhotos: (() -> Unit)? = null,
    onAddPhoto: (() -> Unit)? = null,
    onNewJournalEntry: (() -> Unit)? = null,
    journal: JournalPreviewUiState? = null,
    onRetryJournal: () -> Unit = {},
    discussion: (@Composable (count: Int) -> Unit)? = null,
    topActions: @Composable RowScope.() -> Unit = {},
) {
    val loaded = (state as? BikeDetailUiState.Loaded)?.bike
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // The name is the page's own title under the photo; the bar only says what this is.
            ColaTopBar(
                title = stringResource(R.string.bike_title),
                compactTitle = true,
                onBack = if (showBack) onBack else null,
                actions = if (loaded != null) topActions else ({}),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikeDetailUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikeDetailUiState.Failed ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorState(state.message.resolve(), onRetry = onRetry)
                        if (state.notFound && onSignIn != null) {
                            Text(
                                stringResource(R.string.bike_private_hint),
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
                is BikeDetailUiState.Loaded ->
                    BikeContent(
                        state = state,
                        actions =
                            PageActions(
                                onToggleLike = onToggleLike,
                                onShare = onShare,
                                onOpenLink = onOpenLink,
                                onOpenAuthor = onOpenAuthor,
                                onOpenJournal = onOpenJournal,
                                onOpenJournalEntry = onOpenJournalEntry,
                                onOpenRides = onOpenRides,
                                onOpenComponent = onOpenComponent,
                                onEditParts = onEditParts,
                                onEditPhotos = onEditPhotos,
                                onAddPhoto = onAddPhoto,
                                onNewJournalEntry = onNewJournalEntry,
                                onRetryJournal = onRetryJournal,
                            ),
                        journal = journal,
                        discussion = discussion,
                    )
            }
        }
    }
}

/** What the page can do, so that its parts take one argument instead of a dozen. */
private class PageActions(
    val onToggleLike: () -> Unit,
    val onShare: (title: String) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onOpenAuthor: (ref: String) -> Unit,
    val onOpenJournal: (id: BikeId, name: String) -> Unit,
    val onOpenJournalEntry: (JournalId) -> Unit,
    val onOpenRides: (id: BikeId, name: String) -> Unit,
    val onOpenComponent: (modelId: String) -> Unit,
    val onEditParts: (() -> Unit)?,
    val onEditPhotos: (() -> Unit)?,
    val onAddPhoto: (() -> Unit)?,
    val onNewJournalEntry: (() -> Unit)?,
    val onRetryJournal: () -> Unit,
)

/**
 * On a phone the photo is 1.6 : 1, a little wider than a bike is tall, and is shown whole: the
 * sections under it are on the first screen.
 */
private const val PhonePhotoAspect = 1.6f

/**
 * Text that shows its first lines and offers the rest: "Read in full" only when there is a rest to
 * read. It is the person's own long description, not a teaser: nothing is cut for good.
 */
@Composable
private fun CollapsibleText(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else CollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    stringResource(
                        if (expanded) R.string.bike_description_less
                        else R.string.bike_description_more
                    )
                )
                Icon(
                    painterResource(if (expanded) ColaIcons.ArrowUp else ColaIcons.ArrowDown),
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
                )
            }
        }
    }
}

private const val CollapsedLines = 2

/** From this pane width the photo is wide (16:9) so it does not push the facts off the screen. */
private val WidePane = 600.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BikeContent(
    state: BikeDetailUiState.Loaded,
    actions: PageActions,
    journal: JournalPreviewUiState?,
    discussion: (@Composable (count: Int) -> Unit)?,
) =
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bike = state.bike
        val summary = bike.summary
        val locale = LocalConfiguration.current.locales[0]
        val photoAspect = if (maxWidth >= WidePane) 16f / 9f else PhonePhotoAspect
        val photos = bike.photos.ifEmpty { listOfNotNull(summary.cover) }
        val sections =
            remember(bike.components, bike.groupOrder) {
                orderComponents(bike.components, bike.groupOrder)
            }
        val facts = passport(bike, locale)
        val info = bikeInfoLine(bike)
        Column(
            // The keyboard lifts the page; the box to write in is brought into view with it.
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen)
                    .padding(top = Spacing.xs, bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Column {
                    BikeGallery(photos, photoAspect)
                    if (actions.onAddPhoto != null || actions.onEditPhotos != null) {
                        PhotoActions(
                            hasPhotos = photos.isNotEmpty(),
                            onAdd = { (actions.onAddPhoto ?: actions.onEditPhotos)?.invoke() },
                            onManage = { actions.onEditPhotos?.invoke() },
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        summary.name,
                        style = ColaTheme.textStyles.pageTitle,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (info.isNotBlank()) {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // One's own and private bikes cannot be liked: the count shows, no switch.
                val likeable = !summary.isOwner && summary.isPublic
                AuthorRow(
                    author = summary.author,
                    liked = summary.liked,
                    likes = summary.likes,
                    busy = state.liking,
                    onToggleLike = actions.onToggleLike.takeIf { likeable },
                    onShare = if (summary.isPublic) ({ actions.onShare(summary.name) }) else null,
                    onOpenAuthor = actions.onOpenAuthor,
                )
                state.likeError?.let {
                    Text(
                        it.resolve(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (summary.isFormer || !summary.isPublic) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        if (summary.isFormer) {
                            PillBadge(
                                stringResource(
                                    ru.colabike.core.designsystem.R.string.cola_former_bike
                                )
                            )
                        }
                        if (!summary.isPublic) {
                            PillBadge(stringResource(R.string.bike_private), icon = ColaIcons.Lock)
                        }
                    }
                }
                if (bike.description.isNotBlank() || facts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        if (bike.description.isNotBlank()) CollapsibleText(bike.description)
                        if (facts.isNotEmpty()) PassportLine(facts)
                    }
                }
                if (sections.isNotEmpty() || actions.onEditParts != null) {
                    EquipmentCard(
                        sections = sections,
                        locale = locale,
                        onEdit = actions.onEditParts,
                        onOpenLink = actions.onOpenLink,
                        onOpenModel = actions.onOpenComponent,
                    )
                }
                if (journal != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    JournalSection(
                        state = journal,
                        locale = locale,
                        onNew = actions.onNewJournalEntry,
                        onOpenAll = { actions.onOpenJournal(summary.id, summary.name) },
                        onOpenEntry = actions.onOpenJournalEntry,
                        onRetry = actions.onRetryJournal,
                    )
                }
                if (summary.isPublic && discussion != null) {
                    // The hairline belongs to the section under it, so the gaps are its own.
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        discussion(summary.comments)
                    }
                }
                // Only a public bike has rides to show (the API answers 404 for the rest).
                if (summary.isPublic) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LinkRow(
                            stringResource(R.string.bike_rides),
                            ColaIcons.Route,
                            onClick = { actions.onOpenRides(summary.id, summary.name) },
                            modifier = Modifier.testTag("bike:rides"),
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                bike.manufacturerUrl?.let { url ->
                    LinkRow(
                        stringResource(R.string.bike_manufacturer),
                        ColaIcons.Info,
                        onClick = { actions.onOpenLink(url) },
                        external = true,
                        modifier = Modifier.testTag("bike:manufacturer"),
                    )
                }
            }
        }
    }
