package ru.colabike.app.comments

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.safety.ReportDialog
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentRules
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

@Composable
fun CommentsRoute(
    repository: CommentsRepository,
    drafts: CommentDrafts,
    auth: AuthActions,
    target: CommentTarget,
    title: String,
    focus: String?,
    onBack: () -> Unit,
    onOpenAuthor: (ref: String) -> Unit,
    safety: SafetyRepository? = null,
) {
    var reporting by remember { mutableStateOf<Comment?>(null) }
    val viewModel =
        viewModel(key = "comments:${target.kind}:${target.id}:$focus") {
            CommentsViewModel(repository, drafts, target, focus)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val signedIn = authState as? AuthState.SignedIn
    CommentsScreen(
        state = state,
        title = title,
        meId = signedIn?.account?.id,
        signedIn = signedIn != null,
        actions =
            CommentsActions(
                onBack = onBack,
                onSignIn = signIn,
                onOpenAuthor = onOpenAuthor,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onShowAll = viewModel::showAll,
                onLoadReplies = viewModel::loadReplies,
                onText = viewModel::onText,
                onSend = viewModel::send,
                onReply = viewModel::startReply,
                onCancelReply = viewModel::cancelReply,
                onEdit = viewModel::startEdit,
                onCancelEdit = viewModel::cancelEdit,
                onAskDelete = viewModel::askDelete,
                onDismissDelete = viewModel::dismissDelete,
                onConfirmDelete = viewModel::confirmDelete,
                // Only where there is a place to send it; a guest is asked to sign in first.
                onReport = if (safety != null) ({ reporting = it }) else ({}),
            ),
    )
    val reported = reporting
    if (safety != null && reported != null) {
        ReportDialog(
            target = ReportTarget(reportKindOf(target.kind), reported.id),
            safety = safety,
            onDismiss = { reporting = null },
        )
    }
}

/** What a comment under [kind] is reported as. */
internal fun reportKindOf(kind: CommentKind): ReportKind =
    when (kind) {
        CommentKind.Bike -> ReportKind.BikeComment
        CommentKind.Journal -> ReportKind.JournalComment
        CommentKind.Ride -> ReportKind.RideComment
        CommentKind.Component -> ReportKind.ComponentComment
    }

/** What the discussion can do. Callbacks, so the screen never touches the model itself. */
class CommentsActions(
    val onBack: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onOpenAuthor: (ref: String) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onShowAll: () -> Unit = {},
    val onLoadReplies: (rootId: String) -> Unit = {},
    val onText: (String) -> Unit = {},
    val onSend: () -> Unit = {},
    val onReply: (Comment) -> Unit = {},
    val onCancelReply: () -> Unit = {},
    val onEdit: (Comment) -> Unit = {},
    val onCancelEdit: () -> Unit = {},
    val onAskDelete: (Comment) -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onReport: (Comment) -> Unit = {},
)

/**
 * A discussion: root comments oldest first, each with its first replies and a way to the rest, and
 * the box to write in at the bottom (a sign-in for a guest). The box lifts with the keyboard; Back
 * first leaves an answer or an edit, then the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    state: CommentsUiState,
    title: String,
    meId: UserId?,
    signedIn: Boolean,
    actions: CommentsActions,
) {
    val composer = state.composer
    // Back first puts away what the box was set up for, and only then leaves.
    BackHandler(enabled = composer.replyTo != null || composer.editing != null) {
        if (composer.editing != null) actions.onCancelEdit() else actions.onCancelReply()
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.comments_title),
                subtitle = title,
                onBack = actions.onBack,
            )
        },
        bottomBar = {
            if (signedIn) Composer(composer, state.notice?.resolve(), actions)
            else SignInBar(actions.onSignIn)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val page = state.page
            when {
                page.loading -> LoadingState(Modifier.fillMaxSize())
                page.error != null ->
                    ErrorState(
                        page.error.resolve(),
                        onRetry = actions.onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                page.isEmpty ->
                    EmptyState(
                        title = stringResource(R.string.comments_empty_title),
                        message =
                            stringResource(
                                if (signedIn) R.string.comments_empty
                                else R.string.comments_empty_guest
                            ),
                        icon = ColaIcons.Comment,
                        modifier = Modifier.fillMaxSize(),
                    )
                else ->
                    PullToRefreshBox(
                        isRefreshing = page.refreshing,
                        onRefresh = actions.onRefresh,
                    ) {
                        Threads(state, meId, signedIn, actions)
                    }
            }
            state.deleting?.let { DeleteQuestion(actions.onConfirmDelete, actions.onDismissDelete) }
        }
    }
}

@Composable
private fun Threads(
    state: CommentsUiState,
    meId: UserId?,
    signedIn: Boolean,
    actions: CommentsActions,
) {
    val page = state.page
    val list = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >=
                list.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(list) { snapshotFlow { nearEnd }.collect { if (it) actions.onLoadMore() } }
    // A comment just written at the end of the discussion is brought into view.
    val last = page.items.lastOrNull()?.comment?.id
    LaunchedEffect(last) {
        if (state.focus == null && last != null && page.nextCursor == null) Unit
    }
    LazyColumn(
        state = list,
        modifier = Modifier.fillMaxSize().testTag("comments:list"),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.focus != null) {
            item(key = "focus") { FocusBanner(actions.onShowAll) }
        }
        page.refreshError?.let { error ->
            item(key = "refresh-error") { RetryRow(error.resolve(), actions.onRefresh) }
        }
        items(page.items, key = { it.comment.id }) { node ->
            Thread(node, state.focus, meId, signedIn, actions)
        }
        if (page.loadingMore || page.moreError != null) {
            item(key = "more") {
                Box(
                    Modifier.fillMaxWidth().padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (page.loadingMore) CircularProgressIndicator()
                    else RetryRow(page.moreError!!.resolve(), actions.onLoadMore)
                }
            }
        }
    }
}

@Composable
private fun FocusBanner(onShowAll: () -> Unit) {
    ColaCard(
        Modifier.widthIn(max = ColumnWidth).fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(R.string.comments_branch),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowAll, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.comments_show_all))
            }
        }
    }
}

/** A root comment with its replies, in one hairline card. */
@Composable
private fun Thread(
    node: CommentNode,
    focus: String?,
    meId: UserId?,
    signedIn: Boolean,
    actions: CommentsActions,
) {
    ColaCard(Modifier.widthIn(max = ColumnWidth).fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            CommentRow(node.comment, focus == node.comment.id, false, meId, signedIn, actions)
            node.replies.forEach { reply ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CommentRow(reply, focus == reply.id, true, meId, signedIn, actions)
            }
            ReplyFooter(node, actions)
        }
    }
}

@Composable
private fun ReplyFooter(node: CommentNode, actions: CommentsActions) {
    when {
        node.loadingReplies ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        node.repliesError != null ->
            RetryRow(node.repliesError.resolve()) {
                actions.onLoadReplies(node.comment.id)
            }
        !node.repliesComplete ->
            TextButton(
                onClick = { actions.onLoadReplies(node.comment.id) },
                modifier = Modifier.heightIn(min = Spacing.touch),
            ) {
                Text(
                    if (node.hidden > 0) stringResource(R.string.comments_more_replies, node.hidden)
                    else stringResource(R.string.comments_more_replies_unknown)
                )
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommentRow(
    comment: Comment,
    focused: Boolean,
    reply: Boolean,
    meId: UserId?,
    signedIn: Boolean,
    actions: CommentsActions,
) {
    val locale = LocalConfiguration.current.locales[0]
    val author = comment.author
    val mine = author != null && author.id == meId
    val focusedState = stringResource(R.string.comments_focused)
    Row(
        Modifier.fillMaxWidth()
            .let { if (reply) it.padding(start = Spacing.m) else it }
            .semantics { if (focused) stateDescription = focusedState },
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (comment.deleted || author == null) {
            Box(Modifier.size(32.dp))
        } else {
            Avatar(author.displayName, author.avatarUrl, size = 32.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (comment.deleted) {
                Text(
                    stringResource(R.string.comments_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val time =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .withZone(ZoneId.systemDefault())
                    .format(comment.createdAt)
            val edited = stringResource(R.string.comments_edited)
            val name = author?.displayName.orEmpty()
            Column(
                Modifier.semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        append(name).append(", ").append(time)
                        if (comment.editedAt != null) append(", ").append(edited)
                        append(": ").append(comment.body.orEmpty())
                    }
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        append(time)
                        if (comment.editedAt != null) append(" · ").append(edited)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(comment.body.orEmpty(), style = MaterialTheme.typography.bodyMedium)
            }
            // Wraps instead of squeezing a word into a column when the text is large.
            // The first button's own padding is pulled back so its word stands under the text.
            FlowRow(Modifier.offset(x = -Spacing.s)) {
                // A guest is asked to sign in first; the answer is not written for them afterwards.
                TextButton(
                    onClick = { if (signedIn) actions.onReply(comment) else actions.onSignIn() },
                    modifier = Modifier.heightIn(min = Spacing.touch),
                    contentPadding = ActionPadding,
                ) {
                    Text(stringResource(R.string.comments_reply))
                }
                if (!mine) {
                    TextButton(
                        onClick = {
                            if (signedIn) actions.onReport(comment) else actions.onSignIn()
                        },
                        modifier = Modifier.heightIn(min = Spacing.touch),
                        contentPadding = ActionPadding,
                    ) {
                        Text(stringResource(R.string.safety_report))
                    }
                }
                if (mine) {
                    TextButton(
                        onClick = { actions.onEdit(comment) },
                        modifier = Modifier.heightIn(min = Spacing.touch),
                        contentPadding = ActionPadding,
                    ) {
                        Text(stringResource(R.string.comments_edit))
                    }
                    TextButton(
                        onClick = { actions.onAskDelete(comment) },
                        modifier = Modifier.heightIn(min = Spacing.touch),
                        contentPadding = ActionPadding,
                    ) {
                        Text(
                            stringResource(R.string.comments_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The box to write in. It sits above the keyboard and the system bars; what is being answered or
 * changed is named over it and closed with the cross; a failure is read out when it appears and the
 * text stays, so sending again is one tap.
 */
@Composable
private fun Composer(composer: Composer, notice: String?, actions: CommentsActions) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(composer.replyTo?.id, composer.editing?.id) {
        if (composer.replyTo != null || composer.editing != null) {
            runCatching { requester.requestFocus() }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = null,
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = Spacing.m, vertical = Spacing.s)
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val context =
                when {
                    composer.editing != null -> stringResource(R.string.comments_editing)
                    composer.replyTo != null ->
                        stringResource(
                            R.string.comments_replying_to,
                            composer.replyTo.author?.displayName.orEmpty(),
                        )
                    else -> null
                }
            if (context != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        context,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(
                        onClick =
                            if (composer.editing != null) actions.onCancelEdit
                            else actions.onCancelReply
                    ) {
                        Icon(
                            painterResource(ColaIcons.Close),
                            contentDescription =
                                stringResource(
                                    if (composer.editing != null) R.string.comments_cancel_edit
                                    else R.string.comments_cancel_reply
                                ),
                        )
                    }
                }
            }
            val error = composer.error?.resolve() ?: notice
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier.padding(vertical = Spacing.xs).semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                val over = composer.length > CommentRules.MAX_LENGTH
                OutlinedTextField(
                    value = composer.text,
                    onValueChange = actions.onText,
                    modifier =
                        Modifier.weight(1f).focusRequester(requester).testTag("comments:field"),
                    placeholder = { Text(stringResource(R.string.comments_hint)) },
                    minLines = 1,
                    maxLines = 5,
                    isError = over,
                    supportingText = {
                        if (composer.length >= COUNTER_FROM) {
                            Text(
                                stringResource(
                                    R.string.comments_counter,
                                    composer.length,
                                    CommentRules.MAX_LENGTH,
                                )
                            )
                        }
                    },
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Box(
                    Modifier.size(Spacing.touch + Spacing.xs).padding(bottom = Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    if (composer.sending) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                    } else {
                        IconButton(onClick = actions.onSend, enabled = composer.canSend) {
                            Icon(
                                painterResource(ColaIcons.Send),
                                contentDescription =
                                    stringResource(
                                        if (composer.editing != null) R.string.comments_save
                                        else R.string.comments_send
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignInBar(onSignIn: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                    )
                )
                .fillMaxWidth()
                .padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(
                stringResource(R.string.comments_sign_in_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSignIn, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.profile_sign_in))
            }
        }
    }
}

@Composable
internal fun DeleteQuestion(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.comments_delete_title)) },
        text = { Text(stringResource(R.string.comments_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(
                    stringResource(R.string.comments_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.comments_cancel))
            }
        },
    )
}

@Composable
private fun RetryRow(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
        }
    }
}

private val ColumnWidth = 640.dp

/** Three small actions under a comment share a line on a phone. */
private val ActionPadding = PaddingValues(horizontal = Spacing.s)

/** From this length on the counter is shown; before it the box is quiet. */
private const val COUNTER_FROM = 800
