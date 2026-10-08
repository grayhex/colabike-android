package ru.colabike.app.comments

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.safety.ReportDialog
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.ColaMotion
import ru.colabike.core.designsystem.theme.LocalReducedMotion
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Account
import ru.colabike.core.model.Comment
import ru.colabike.core.model.CommentRules
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.CommentsRepository
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.SafetyRepository
import ru.colabike.core.model.UserId

/** How many root comments the page shows before the person asks for the rest. */
const val DISCUSSION_PREVIEW_ROOTS = 2

/** Lines of a comment shown before "Ещё". */
private const val BODY_LINES = 3

/** From this length on the counter of the box is shown; before it the box is quiet. */
private const val COUNTER_FROM = 800

/**
 * The discussion under a page's object, on the page itself: the model is the one of the full screen
 * ([CommentsViewModel]), so writing, answering, editing, deleting and reporting are the same rules
 * and the same drafts. [count] is the page's own count of comments (it follows the changes made
 * here); [ownerId] marks the object's author in the replies.
 */
@Composable
fun InlineDiscussionRoute(
    repository: CommentsRepository,
    drafts: CommentDrafts,
    auth: AuthActions,
    target: CommentTarget,
    count: Int,
    ownerId: UserId?,
    onOpenAuthor: (ref: String) -> Unit,
    modifier: Modifier = Modifier,
    safety: SafetyRepository? = null,
) {
    var reporting by remember { mutableStateOf<Comment?>(null) }
    val viewModel =
        viewModel(key = "discussion:${target.kind}:${target.id}") {
            CommentsViewModel(repository, drafts, target, focus = null)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val signedIn = authState as? AuthState.SignedIn
    InlineDiscussion(
        state = state,
        count = count,
        me = signedIn?.account,
        ownerId = ownerId,
        reportable = safety != null,
        actions =
            CommentsActions(
                onSignIn = signIn,
                onOpenAuthor = onOpenAuthor,
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
                onReport = { reporting = it },
            ),
        modifier = modifier,
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

/** The ids of the roots whose replies are open, kept over a turn of the screen. */
private val OpenedSaver =
    listSaver<SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { it.toMutableStateList() },
    )

/**
 * The discussion as a section of a page: the heading with the count, the first two root comments
 * (or a short invitation when there are none), "all comments" which opens the rest in place with
 * its own paging, and "collapse" which goes back to the two, never to nothing. Replies open under
 * their parent, a long text goes on with "Ещё". The box to write in is the last thing of the
 * section (a sign-in for a guest); the text typed there lives in the model, so opening or closing
 * anything leaves it as it is.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun InlineDiscussion(
    state: CommentsUiState,
    count: Int,
    me: Account?,
    ownerId: UserId?,
    actions: CommentsActions,
    modifier: Modifier = Modifier,
    reportable: Boolean = true,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val opened = rememberSaveable(saver = OpenedSaver) { mutableStateListOf() }
    val page = state.page
    val composer = state.composer
    val signedIn = me != null
    val roots = page.items
    val section = remember { BringIntoViewRequester() }

    // Back first puts away what the box was set up for, and only then leaves the page.
    BackHandler(enabled = composer.replyTo != null || composer.editing != null) {
        if (composer.editing != null) actions.onCancelEdit() else actions.onCancelReply()
    }

    // A comment that came at the end, or a branch that was asked for, is not hidden by the preview.
    val last = roots.lastOrNull()?.comment?.id
    var seen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(last, page.loading) {
        if (!page.loading) {
            if (seen != null && last != null && last != seen) expanded = true
            seen = last
        }
    }
    LaunchedEffect(state.focus) { if (state.focus != null) expanded = true }

    // An answer that came opens the replies of its root, so it is seen where it was made; so does a
    // branch that was asked for (a link to one comment): its root is opened to the comment.
    val repliesSeen = remember { mutableMapOf<String, Int>() }
    LaunchedEffect(roots) {
        roots.forEach { node ->
            val before = repliesSeen[node.comment.id]
            if (before != null && node.replies.size > before && node.comment.id !in opened) {
                opened.add(node.comment.id)
            }
            repliesSeen[node.comment.id] = node.replies.size
        }
    }
    LaunchedEffect(state.focusPath) {
        val root = state.focusPath.firstOrNull()?.id
        if (root != null && root !in opened) opened.add(root)
    }

    // After "collapse" the section is short again: it is brought back into view instead of leaving
    // the person somewhere under the end of the page.
    var collapsed by remember { mutableStateOf(false) }
    var collapseFinished by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(expanded) { if (expanded) collapsed = false }
    LaunchedEffect(collapsed, collapseFinished, reducedMotion) {
        if (collapsed && (collapseFinished || reducedMotion)) {
            section.bringIntoView()
            collapsed = false
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .bringIntoViewRequester(section)
            .animateContentSize(
                animationSpec = if (reducedMotion) snap() else ColaMotion.spatial(),
                finishedListener = { before, after ->
                    if (after.height < before.height) collapseFinished = true
                },
            )
            .testTag("discussion"),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DiscussionHeader(
            count = count,
            expanded = expanded,
            onCollapse = {
                expanded = false
                collapseFinished = false
                collapsed = true
            },
        )
        when {
            page.loading ->
                Box(
                    Modifier.fillMaxWidth()
                        .padding(vertical = Spacing.m)
                        .testTag("discussion:loading"),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            page.error != null ->
                SectionError(
                    page.error.resolve(),
                    actions.onRetry,
                    Modifier.testTag("discussion:error"),
                )
            else -> {
                if (state.focus != null) FocusRow(actions.onShowAll)
                if (page.isEmpty) {
                    Text(
                        stringResource(
                            if (signedIn) R.string.comments_prompt
                            else R.string.comments_prompt_guest
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val shown = if (expanded) roots else roots.take(DISCUSSION_PREVIEW_ROOTS)
                    shown.forEachIndexed { index, node ->
                        if (index > 0)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DiscussionThread(
                            node = node,
                            open = node.comment.id in opened,
                            onToggle = {
                                if (node.comment.id in opened) opened.remove(node.comment.id)
                                else opened.add(node.comment.id)
                            },
                            ownerId = ownerId,
                            me = me,
                            reportable = reportable,
                            actions = actions,
                        )
                    }
                    val hasMore = roots.size > DISCUSSION_PREVIEW_ROOTS || page.nextCursor != null
                    if (!expanded && hasMore) {
                        TextButton(
                            onClick = { expanded = true },
                            modifier =
                                Modifier.heightIn(min = Spacing.touch).testTag("discussion:all"),
                        ) {
                            Text(stringResource(R.string.comments_all))
                            Icon(
                                painterResource(ColaIcons.ArrowDown),
                                contentDescription = null,
                                modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
                            )
                        }
                    }
                    if (expanded) {
                        when {
                            page.loadingMore ->
                                Box(Modifier.fillMaxWidth().padding(Spacing.s), Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            page.moreError != null ->
                                SectionError(page.moreError.resolve(), actions.onLoadMore)
                            page.nextCursor != null ->
                                TextButton(
                                    onClick = actions.onLoadMore,
                                    modifier =
                                        Modifier.heightIn(min = Spacing.touch)
                                            .testTag("discussion:more"),
                                ) {
                                    Text(stringResource(R.string.comments_more_roots))
                                }
                        }
                    }
                }
                if (me != null) InlineComposer(composer, state.notice?.resolve(), me, actions)
                else SignInRow(actions.onSignIn)
            }
        }
        state.deleting?.let { DeleteQuestion(actions.onConfirmDelete, actions.onDismissDelete) }
    }
}

@Composable
private fun DiscussionHeader(count: Int, expanded: Boolean, onCollapse: () -> Unit) {
    val title = stringResource(R.string.comments_title)
    Row(
        // Shut, the heading is only a heading; opened, it holds "Свернуть", a button.
        Modifier.fillMaxWidth().heightIn(min = if (expanded) Spacing.touch else 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                heading()
                contentDescription = "$title, $count"
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(
                count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            TextButton(
                onClick = onCollapse,
                modifier = Modifier.heightIn(min = Spacing.touch).testTag("discussion:collapse"),
            ) {
                Text(
                    stringResource(R.string.comments_collapse),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FocusRow(onShowAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            stringResource(R.string.comments_branch),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onShowAll, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(R.string.comments_show_all))
        }
    }
}

@Composable
private fun SectionError(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
        }
    }
}

/** A root comment, the way to its replies and, when they are open, the replies under it. */
@Composable
private fun DiscussionThread(
    node: CommentNode,
    open: Boolean,
    onToggle: () -> Unit,
    ownerId: UserId?,
    me: Account?,
    reportable: Boolean,
    actions: CommentsActions,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        DiscussionComment(
            comment = node.comment,
            reply = false,
            ownerId = ownerId,
            me = me,
            reportable = reportable,
            actions = actions,
            replies =
                if (node.comment.replyCount > 0) {
                    { RepliesToggle(node.comment.replyCount, open, onToggle) }
                } else null,
        )
        if (open) {
            Row(
                Modifier.fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(start = Spacing.l + Spacing.xs, top = Spacing.xs)
            ) {
                Box(
                    Modifier.width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    node.replies.forEach { reply ->
                        DiscussionComment(
                            comment = reply,
                            reply = true,
                            ownerId = ownerId,
                            me = me,
                            reportable = reportable,
                            actions = actions,
                            replies = null,
                        )
                    }
                    ReplyFooter(node, actions)
                }
            }
        }
    }
}

@Composable
private fun ReplyFooter(node: CommentNode, actions: CommentsActions) {
    when {
        node.loadingReplies ->
            Box(Modifier.fillMaxWidth().padding(Spacing.s), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        node.repliesError != null ->
            SectionError(node.repliesError.resolve(), { actions.onLoadReplies(node.comment.id) })
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

@Composable
private fun RepliesToggle(count: Int, open: Boolean, onToggle: () -> Unit) {
    val shown = stringResource(R.string.comments_replies_shown)
    val hidden = stringResource(R.string.comments_replies_hidden)
    TextButton(
        onClick = onToggle,
        modifier =
            Modifier.heightIn(min = Spacing.touch).semantics {
                stateDescription = if (open) shown else hidden
            },
    ) {
        Text(
            pluralStringResource(R.plurals.comments_replies, count, count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            painterResource(if (open) ColaIcons.ArrowUp else ColaIcons.ArrowDown),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs).size(18.dp),
        )
    }
}

private fun commentTime(comment: Comment, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(ZoneId.systemDefault())
        .format(comment.createdAt)

/**
 * One comment: the picture, the name (and "Автор" for the object's own author), the time, the
 * actions menu, the text, then "Ответить", "Ещё" for a long text and [replies], the way to the
 * answers. TalkBack reads the head and the text as one sentence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscussionComment(
    comment: Comment,
    reply: Boolean,
    ownerId: UserId?,
    me: Account?,
    reportable: Boolean,
    actions: CommentsActions,
    replies: (@Composable () -> Unit)?,
) {
    val locale = LocalConfiguration.current.locales[0]
    val author = comment.author
    val mine = author != null && me != null && author.id == me.id
    val signedIn = me != null
    val avatar = if (reply) 32.dp else 40.dp
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs).testTag("comment:${comment.id}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (comment.deleted || author == null) {
            Box(Modifier.size(avatar))
        } else {
            Avatar(author.displayName, author.avatarUrl, size = avatar)
        }
        Column(Modifier.weight(1f)) {
            if (comment.deleted) {
                Text(
                    stringResource(R.string.comments_deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.s),
                )
                replies?.invoke()
                return@Column
            }
            val time = commentTime(comment, locale)
            val edited = stringResource(R.string.comments_edited)
            val name = author?.displayName.orEmpty()
            val body = comment.body.orEmpty()
            var showAll by rememberSaveable(comment.id) { mutableStateOf(false) }
            var overflows by remember(comment.id, body) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = Spacing.xs)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (author != null && ownerId != null && author.id == ownerId) {
                            PillBadge(stringResource(R.string.comments_author_badge))
                        }
                    }
                    Text(
                        buildString {
                            append(time)
                            if (comment.editedAt != null) append(" · ").append(edited)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CommentMenu(comment, mine, signedIn, reportable, actions)
            }
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (showAll) Int.MAX_VALUE else BODY_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!showAll) overflows = it.hasVisualOverflow },
                modifier =
                    Modifier.semantics {
                        contentDescription = buildString {
                            append(name).append(", ").append(time)
                            if (comment.editedAt != null) append(", ").append(edited)
                            append(": ").append(body)
                        }
                    },
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                // A guest is asked to sign in first; the answer is not written for them afterwards.
                TextButton(
                    onClick = { if (signedIn) actions.onReply(comment) else actions.onSignIn() },
                    modifier = Modifier.heightIn(min = Spacing.touch),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                    contentPadding = ActionPadding,
                ) {
                    Text(stringResource(R.string.comments_reply))
                }
                if (overflows || showAll) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        modifier = Modifier.heightIn(min = Spacing.touch),
                        contentPadding = ActionPadding,
                    ) {
                        Text(
                            stringResource(
                                if (showAll) R.string.comments_text_less
                                else R.string.comments_text_more
                            )
                        )
                    }
                }
                replies?.invoke()
            }
        }
    }
}

/** What can be done with one comment: the own one is changed or deleted, another's is reported. */
@Composable
private fun CommentMenu(
    comment: Comment,
    mine: Boolean,
    signedIn: Boolean,
    reportable: Boolean,
    actions: CommentsActions,
) {
    if (!mine && !reportable) return
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painterResource(ColaIcons.MoreVert),
                contentDescription = stringResource(R.string.comments_menu),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (mine) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.comments_edit)) },
                    onClick = {
                        open = false
                        actions.onEdit(comment)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.comments_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        open = false
                        actions.onAskDelete(comment)
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.safety_report)) },
                    onClick = {
                        open = false
                        if (signedIn) actions.onReport(comment) else actions.onSignIn()
                    },
                )
            }
        }
    }
}

/**
 * The box to write in, inside the section. What is answered or changed is named over it and closed
 * with the cross; a failure is read out when it appears and the text stays, so sending again is one
 * tap. Answering or changing a comment brings the box into focus, and with it into view.
 */
@Composable
private fun InlineComposer(
    composer: Composer,
    notice: String?,
    me: Account,
    actions: CommentsActions,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(composer.replyTo?.id, composer.editing?.id) {
        if (composer.replyTo != null || composer.editing != null) {
            runCatching { requester.requestFocus() }
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Avatar(
                me.displayName,
                me.avatarUrl,
                size = 40.dp,
                modifier = Modifier.padding(top = Spacing.s),
            )
            val over = composer.length > CommentRules.MAX_LENGTH
            val hint = stringResource(R.string.comments_hint)
            val line =
                if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
            BasicTextField(
                value = composer.text,
                onValueChange = actions.onText,
                modifier =
                    Modifier.weight(1f)
                        .focusRequester(requester)
                        .testTag("comments:field")
                        .semantics { contentDescription = hint },
                textStyle =
                    MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = 1,
                maxLines = 5,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { field ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = Spacing.touch)
                            .border(1.dp, line, RoundedCornerShape(28.dp))
                            .padding(start = Spacing.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f).padding(vertical = Spacing.s)) {
                            if (composer.text.isEmpty()) {
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            field()
                        }
                        if (composer.sending) {
                            Box(Modifier.size(Spacing.touch), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(24.dp))
                            }
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
                },
            )
        }
        if (composer.length >= COUNTER_FROM) {
            Text(
                stringResource(R.string.comments_counter, composer.length, CommentRules.MAX_LENGTH),
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (composer.length > CommentRules.MAX_LENGTH) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun SignInRow(onSignIn: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            stringResource(R.string.comments_sign_in_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier.heightIn(min = Spacing.touch).testTag("discussion:sign-in"),
        ) {
            Text(stringResource(R.string.profile_sign_in))
        }
    }
}

/** Two small actions under a comment share a line on a phone. */
private val ActionPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.s)
