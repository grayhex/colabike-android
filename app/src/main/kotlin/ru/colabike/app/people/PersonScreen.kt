package ru.colabike.app.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.messages.WriteState
import ru.colabike.app.messages.WriteViewModel
import ru.colabike.app.safety.BlockOffer
import ru.colabike.app.safety.SafetyMenu
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.BikeCard
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatRepository
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.PeopleRepository
import ru.colabike.core.model.Profile
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

/** What a person's page can open. Callbacks, so the screen never touches navigation itself. */
class PersonActions(
    val onBack: () -> Unit,
    val onOpenBike: (BikeId) -> Unit,
    val onOpenFollowers: (ref: String) -> Unit,
    val onOpenFollowing: (ref: String) -> Unit,
    val onOpenAccount: () -> Unit,
    /** A dialogue with this person was made or found: open it. */
    val onOpenConversation: (ChannelCid) -> Unit = {},
)

@Composable
fun PersonRoute(
    people: PeopleRepository,
    bikes: BikesRepository,
    auth: AuthActions,
    ref: String,
    actions: PersonActions,
    chat: ChatRepository? = null,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    safety: SafetyRepository? = null,
) {
    val viewModel = viewModel { PersonViewModel(people, bikes, ref, commentChanges, safety) }
    val writer = chat?.let { viewModel(key = "write:$ref") { WriteViewModel(it) } }
    val writing by
        (writer?.state ?: remember { MutableStateFlow<WriteState>(WriteState.Idle) })
            .collectAsStateWithLifecycle()
    val opened by
        (writer?.opened ?: remember { MutableStateFlow<ChannelCid?>(null) })
            .collectAsStateWithLifecycle()
    LaunchedEffect(opened) {
        opened?.let {
            writer?.consumed()
            actions.onOpenConversation(it)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val signedIn = authState is AuthState.SignedIn
    PersonScreen(
        state = state,
        actions = actions,
        onRetry = viewModel::load,
        onLoadMore = viewModel::loadMoreBikes,
        // A guest is asked to sign in first; the subscription is not made for them afterwards.
        onToggleFollow = if (signedIn) viewModel::toggleFollow else signIn,
        safety = safety,
        signedIn = signedIn,
        onSignIn = signIn,
        onToggleBlock = viewModel::toggleBlock,
        onConfirmBlock = viewModel::confirmBlock,
        onDismissBlock = viewModel::dismissBlockPrompt,
        writing = writing,
        // Messages are a member's: a guest is asked to sign in first.
        onWrite =
            when {
                writer == null -> null
                signedIn -> {
                    {
                        state.profile?.person?.id?.let { writer.write(UserId(it.value)) }
                        Unit
                    }
                }
                else -> signIn
            },
    )
}

/**
 * A person's public page: who they are, what they are to the viewer (follow, or "this is you"),
 * their numbers, and their public bikes. Private bikes of the owner are in their garage, not here.
 */
@Composable
fun PersonScreen(
    state: PersonUiState,
    actions: PersonActions,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleFollow: () -> Unit,
    writing: WriteState = WriteState.Idle,
    onWrite: (() -> Unit)? = null,
    safety: SafetyRepository? = null,
    signedIn: Boolean = false,
    onSignIn: () -> Unit = {},
    onToggleBlock: () -> Unit = {},
    onConfirmBlock: () -> Unit = {},
    onDismissBlock: () -> Unit = {},
) {
    val profile = state.profile
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = profile?.person?.displayName ?: stringResource(R.string.person_title),
                subtitle =
                    profile?.person?.let {
                        stringResource(
                            ru.colabike.core.designsystem.R.string.cola_username,
                            it.username,
                        )
                    },
                onBack = actions.onBack,
                actions = {
                    // Not for oneself: there is nobody to report or to block.
                    if (profile != null && profile.relationship?.isSelf != true)
                        SafetyMenu(
                            target = ReportTarget(ReportKind.Profile, profile.person.id.value),
                            safety = safety,
                            signedIn = signedIn,
                            onSignIn = onSignIn,
                            block =
                                safety?.let {
                                    BlockOffer(
                                        blocked = profile.relationship?.blockedByMe == true,
                                        onToggle = onToggleBlock,
                                    )
                                },
                        )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.blockPrompt && profile != null) {
                BlockDialog(profile.person.displayName, onConfirmBlock, onDismissBlock)
            }
            when {
                state.loading -> LoadingState(Modifier.fillMaxSize())
                profile == null ->
                    ErrorState(
                        (state.error ?: return@Box).resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PersonContent(
                        state,
                        profile,
                        actions,
                        onLoadMore,
                        onToggleFollow,
                        writing,
                        onWrite,
                        onToggleBlock,
                    )
            }
        }
    }
}

@Composable
private fun PersonContent(
    state: PersonUiState,
    profile: Profile,
    actions: PersonActions,
    onLoadMore: () -> Unit,
    onToggleFollow: () -> Unit,
    writing: WriteState,
    onWrite: (() -> Unit)?,
    onToggleBlock: () -> Unit,
) {
    val grid = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                grid.layoutInfo.totalItemsCount - 4
        }
    }
    LaunchedEffect(grid) { snapshotFlow { nearEnd }.collect { if (it) onLoadMore() } }
    LazyVerticalGrid(
        state = grid,
        columns = GridCells.Adaptive(minSize = 280.dp),
        contentPadding = PaddingValues(Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        modifier = Modifier.fillMaxSize().testTag("person:grid"),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(state, profile, actions, onToggleFollow, writing, onWrite, onToggleBlock)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                stringResource(R.string.person_bikes),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = Spacing.m).semantics { heading() },
            )
        }
        items(state.bikes, key = { it.id.value }) { bike ->
            BikeCard(
                bike,
                onClick = { actions.onOpenBike(bike.id) },
                modifier = Modifier.testTag("bike:${bike.id.value}"),
            )
        }
        when {
            state.bikesLoading ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().padding(Spacing.l),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            state.bikesError != null ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            state.bikesError.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onLoadMore) {
                            Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
                        }
                    }
                }
            state.bikes.isEmpty() ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.person_no_bikes_title),
                        message = stringResource(R.string.person_no_bikes),
                        icon = ColaIcons.Bike,
                    )
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: PersonUiState,
    profile: Profile,
    actions: PersonActions,
    onToggleFollow: () -> Unit,
    writing: WriteState,
    onWrite: (() -> Unit)?,
    onToggleBlock: () -> Unit,
) {
    val person = profile.person
    val relationship = profile.relationship
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Avatar(person.displayName, person.avatarUrl, size = 96.dp)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (profile.location.isNotBlank())
                PillBadge(profile.location, icon = ColaIcons.Location)
            PillBadge(joinedText(profile), icon = ColaIcons.Calendar)
        }
        if (profile.bio.isNotBlank()) {
            Text(
                profile.bio,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
        when {
            relationship?.isSelf == true -> {
                PillBadge(stringResource(R.string.person_it_is_you))
                OutlinedButton(
                    onClick = actions.onOpenAccount,
                    modifier =
                        Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = Spacing.touch),
                ) {
                    Text(stringResource(R.string.person_my_account))
                }
            }
            relationship?.blockedByMe == true -> BlockedBanner(state.blockBusy, onToggleBlock)
            else -> {
                FollowButton(state, profile, onToggleFollow)
                if (onWrite != null) WriteButton(person.displayName, writing, onWrite)
            }
        }
        state.blockError?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        (writing as? WriteState.Failed)?.let {
            Text(
                it.message.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        state.followError?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Counts(profile, actions)
    }
}

/**
 * "Write": opens a dialogue with the person; the follow button is the main action, this the second.
 */
@Composable
private fun WriteButton(name: String, writing: WriteState, onWrite: () -> Unit) {
    val label = stringResource(R.string.chat_write_named, name)
    OutlinedButton(
        onClick = onWrite,
        enabled = writing != WriteState.Opening,
        modifier =
            Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = Spacing.touch).semantics {
                contentDescription = label
            },
    ) {
        if (writing == WriteState.Opening) {
            CircularProgressIndicator(Modifier.size(18.dp).padding(end = Spacing.s))
        } else {
            Icon(painterResource(ColaIcons.Chat), contentDescription = null)
            Spacer(Modifier.width(Spacing.s))
        }
        Text(stringResource(R.string.chat_write))
    }
}

/**
 * Bikes, followers, following. Side by side while the text is small; at a large font a caption does
 * not fit in a third of the width, so each count takes a row of its own, caption left, number
 * right.
 */
@Composable
private fun Counts(profile: Profile, actions: PersonActions) {
    val stacked = LocalDensity.current.fontScale > LargeFont
    val counts = profile.counts
    val id = profile.person.id.value
    val tiles =
        listOf<Triple<Int, Int, (() -> Unit)?>>(
            Triple(R.string.person_count_bikes, counts.bikes, null),
            Triple(R.string.person_count_followers, counts.followers) {
                actions.onOpenFollowers(id)
            },
            Triple(R.string.person_count_following, counts.following) {
                actions.onOpenFollowing(id)
            },
        )
    if (stacked) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            tiles.forEach { (label, value, open) ->
                CountRow(stringResource(label), value, onClick = open)
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 560.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            tiles.forEach { (label, value, open) ->
                CountTile(stringResource(label), value, Modifier.weight(1f), onClick = open)
            }
        }
    }
}

private const val LargeFont = 1.3f

@Composable
private fun FollowButton(state: PersonUiState, profile: Profile, onToggle: () -> Unit) {
    val name = profile.person.displayName
    val following = profile.relationship?.following == true
    val label =
        stringResource(
            if (following) R.string.person_unfollow_named else R.string.person_follow_named,
            name,
        )
    val modifier =
        Modifier.widthIn(max = 420.dp)
            .fillMaxWidth()
            .heightIn(min = Spacing.touch + Spacing.xs)
            .semantics { contentDescription = label }
    if (following) {
        OutlinedButton(onClick = onToggle, enabled = !state.following, modifier = modifier) {
            Text(
                stringResource(
                    if (profile.relationship?.friends == true) R.string.person_friends
                    else R.string.person_following
                )
            )
        }
    } else {
        Button(onClick = onToggle, enabled = !state.following, modifier = modifier) {
            Text(stringResource(R.string.person_follow))
        }
    }
}

/** A number under its caption; with [onClick] the whole tile opens the list behind the number. */
@Composable
private fun CountTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    ColaCard(
        modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $value" },
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(value.toString(), style = ColaTheme.textStyles.numeral)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The same count as a full-width row, for large text. */
@Composable
private fun CountRow(label: String, value: Int, onClick: (() -> Unit)? = null) {
    ColaCard(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "$label, $value"
        },
        onClick = onClick,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(Spacing.m),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(value.toString(), style = ColaTheme.textStyles.numeral)
        }
    }
}

@Composable
private fun joinedText(profile: Profile): String {
    val locale = LocalConfiguration.current.locales[0]
    val date =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(profile.joined)
    return stringResource(R.string.person_joined, date)
}

/** "You blocked this person": what it means and the way back, in place of the follow button. */
@Composable
private fun BlockedBanner(busy: Boolean, onUnblock: () -> Unit) {
    ColaCard(Modifier.widthIn(max = 420.dp).fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(R.string.block_banner),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.block_banner_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedButton(
                onClick = onUnblock,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch),
            ) {
                Text(stringResource(R.string.block_unblock))
            }
        }
    }
}

/** The question before a block: what it does, in words, and a button that says so. */
@Composable
private fun BlockDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.block_title, name)) },
        text = { Text(stringResource(R.string.block_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(
                    stringResource(R.string.block_confirm),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.block_cancel))
            }
        },
    )
}
