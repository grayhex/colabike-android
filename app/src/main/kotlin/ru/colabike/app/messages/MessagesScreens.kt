package ru.colabike.app.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.push.VisibleConversation
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaFilterChip
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaSearchField
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.ChatRepository

/**
 * Lets a chat screen show only once the chat is up. The connection is asked for here, when a chat
 * screen is first shown, and never at launch; until then, or when it failed, the person sees why
 * and what to do: wait, try again, or confirm the e-mail on the site.
 */
@Composable
fun ChatGate(
    session: ChatSession,
    title: String,
    site: SiteLinks,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable (ChatLink) -> Unit,
) {
    val connection by session.connection.collectAsStateWithLifecycle()
    LaunchedEffect(session) { session.connect() }
    val links = LocalLinkOpener.current
    when (val state = connection) {
        is ChatConnection.Connected -> content(state.link)
        else ->
            ChatStatusScreen(
                connection = state,
                title = title,
                onBack = onBack,
                onRetry = session::connect,
                onOpenSite = { links.open(site.account) },
                modifier = modifier,
            )
    }
}

/**
 * What a chat screen shows until the chat is up: that it is on its way, or why it is not and what
 * can be done about it. An unconfirmed e-mail is the one failure with a way out of its own, on the
 * site; the others can only be tried again.
 */
@Composable
fun ChatStatusScreen(
    connection: ChatConnection,
    title: String,
    onBack: (() -> Unit)?,
    onRetry: () -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = title, onBack = onBack) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (connection) {
                is ChatConnection.Failed ->
                    if (connection.reason == ChatFailure.EmailUnconfirmed) {
                        EmptyState(
                            title = stringResource(R.string.chat_email_title),
                            message = connection.message.resolve(),
                            icon = ColaIcons.Mail,
                            actionLabel = stringResource(R.string.chat_email_action),
                            onAction = onOpenSite,
                            secondaryLabel = stringResource(R.string.chat_retry),
                            onSecondary = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorState(
                            connection.message.resolve(),
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                else -> LoadingState(Modifier.fillMaxSize())
            }
        }
    }
}

/** The Messages section: the conversations, once the chat is up. */
@Composable
fun ConversationsRoute(
    session: ChatSession,
    screens: ChatScreens,
    site: SiteLinks,
    signedIn: Boolean,
    onOpen: (ChannelCid) -> Unit,
    onNew: () -> Unit,
) {
    if (!signedIn) {
        GuestMessages()
        return
    }
    ChatGate(session, title = stringResource(R.string.chat_title), site = site) { link ->
        screens.Conversations(
            link = link,
            onOpen = onOpen,
            onNew = onNew,
            // The SDK's header does not pad for the status bar; the bar below is the shell's.
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                        )
                    ),
        )
    }
}

/** What a guest sees in place of the conversations: messages are a member's. */
@Composable
fun GuestMessages() {
    val signIn = LocalSignInRequest.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.chat_title)) },
    ) { padding ->
        EmptyState(
            title = stringResource(R.string.chat_guest_title),
            message = stringResource(R.string.chat_guest),
            icon = ColaIcons.Chat,
            actionLabel = stringResource(R.string.profile_sign_in),
            onAction = signIn,
            modifier = Modifier.padding(padding).fillMaxSize(),
        )
    }
}

/** One conversation. The shell's bar steps aside for the box to write in. */
@Composable
fun ConversationRoute(
    session: ChatSession,
    screens: ChatScreens,
    site: SiteLinks,
    cid: ChannelCid,
    visible: VisibleConversation,
    onBack: () -> Unit,
) {
    // In front only while the screen is resumed: in the background a message is worth a ring.
    LifecycleResumeEffect(cid) {
        visible.show(cid.value)
        onPauseOrDispose { visible.hide(cid.value) }
    }
    ChatGate(
        session,
        title = stringResource(R.string.chat_title),
        site = site,
        onBack = onBack,
    ) { link ->
        screens.Conversation(link, cid, onBack, Modifier.fillMaxSize())
    }
}

@Composable
fun NewConversationRoute(
    repository: ChatRepository,
    session: ChatSession,
    site: SiteLinks,
    onBack: () -> Unit,
    onOpened: (ChannelCid) -> Unit,
) {
    ChatGate(
        session,
        title = stringResource(R.string.chat_new_title),
        site = site,
        onBack = onBack,
    ) {
        val viewModel = viewModel { NewConversationViewModel(repository) }
        val state by viewModel.state.collectAsStateWithLifecycle()
        val opened by viewModel.opened.collectAsStateWithLifecycle()
        LaunchedEffect(opened) {
            opened?.let {
                viewModel.consumed()
                onOpened(it)
            }
        }
        NewConversationScreen(
            state = state,
            onBack = onBack,
            onQuery = viewModel::onQuery,
            onClear = viewModel::clearQuery,
            onRetry = viewModel::retry,
            onGroup = viewModel::setGroup,
            onGroupName = viewModel::onGroupName,
            onPerson = viewModel::onPerson,
            onCreate = viewModel::createGroup,
        )
    }
}

/**
 * Who to write to: a field to search, the people one follows (or the matches), one tap for a
 * dialogue or, in group mode, a choice of two to seven people and a name.
 */
@Composable
fun NewConversationScreen(
    state: NewConversationUiState,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onGroup: (Boolean) -> Unit,
    onGroupName: (String) -> Unit,
    onPerson: (ru.colabike.core.model.Person) -> Unit,
    onCreate: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.chat_new_title), onBack = onBack)
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Field(state.typed, onQuery, onClear)
            Column(
                Modifier.padding(horizontal = Spacing.screen).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ColaFilterChip(
                    selected = state.group,
                    onClick = { onGroup(!state.group) },
                    label = stringResource(R.string.chat_group_mode),
                )
            }
            if (state.group) GroupForm(state, onGroupName, onCreate)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.people.isEmpty() -> LoadingState(Modifier.fillMaxSize())
                    state.error != null ->
                        ErrorState(
                            state.error.resolve(),
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    state.people.isEmpty() ->
                        EmptyState(
                            title =
                                stringResource(
                                    if (state.searching) R.string.chat_people_none_title
                                    else R.string.chat_people_empty_title
                                ),
                            message =
                                stringResource(
                                    if (state.searching) R.string.chat_people_none
                                    else R.string.chat_people_empty
                                ),
                            icon = ColaIcons.Person,
                            modifier = Modifier.fillMaxSize(),
                        )
                    else -> People(state, onPerson)
                }
            }
        }
    }
}

@Composable
private fun Field(text: String, onText: (String) -> Unit, onClear: () -> Unit) {
    val focus = LocalFocusManager.current
    ColaSearchField(
        value = text,
        onValueChange = onText,
        placeholder = stringResource(R.string.chat_people_hint),
        clearLabel = stringResource(R.string.bikes_search_clear),
        onClear = onClear,
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.s)
                .testTag("chat:people:field"),
    )
}

@Composable
private fun GroupForm(
    state: NewConversationUiState,
    onGroupName: (String) -> Unit,
    onCreate: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Column(
        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        OutlinedTextField(
            value = state.groupName,
            onValueChange = onGroupName,
            label = { Text(stringResource(R.string.chat_group_name)) },
            supportingText = {
                Text(
                    stringResource(
                        R.string.chat_group_count,
                        state.selected.size,
                        GROUP_MAX,
                        GROUP_MIN,
                    )
                )
            },
            singleLine = true,
            shape = colaFieldShape,
            colors = colaTextFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.fillMaxWidth().testTag("chat:group:name"),
        )
        state.openError?.let {
            Text(
                it.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = onCreate,
            enabled = state.canCreateGroup,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = Spacing.touch).testTag("chat:group:create"),
        ) {
            if (state.opening) CircularProgressIndicator(Modifier.padding(end = Spacing.s))
            Text(stringResource(R.string.chat_group_create))
        }
    }
}

@Composable
private fun People(
    state: NewConversationUiState,
    onPerson: (ru.colabike.core.model.Person) -> Unit,
) {
    val selectedLabel = stringResource(R.string.chat_person_selected)
    val notSelectedLabel = stringResource(R.string.chat_person_not_selected)
    Column(Modifier.fillMaxSize()) {
        // A dialogue's refusal ("cannot write to this person") is shown above the list.
        if (!state.group) {
            state.openError?.let {
                Text(
                    it.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier =
                        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.s)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("chat:people"),
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            items(state.people, key = { it.id.value }) { person ->
                val chosen = state.selected.any { it.id == person.id }
                UserRow(
                    person,
                    onClick = { onPerson(person) },
                    modifier =
                        Modifier.testTag("chat:person:${person.id.value}").semantics {
                            if (state.group) {
                                stateDescription = if (chosen) selectedLabel else notSelectedLabel
                            }
                        },
                    trailing =
                        if (state.group) {
                            {
                                Icon(
                                    painterResource(
                                        if (chosen) ColaIcons.LikeFilled else ColaIcons.Like
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}
