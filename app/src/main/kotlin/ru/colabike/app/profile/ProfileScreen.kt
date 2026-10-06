package ru.colabike.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.AppDependencies
import ru.colabike.app.R
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.notifications.NotificationsBell
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.SoftIconTile
import ru.colabike.core.designsystem.theme.Spacing

/** What the profile can open, as callbacks: the screen never touches navigation itself. */
@Composable
fun ProfileRoute(
    dependencies: AppDependencies,
    onOpenDevices: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    onOpenPublicProfile: (id: String) -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenSavedMarket: (() -> Unit)? = {},
) {
    val viewModel = viewModel { ProfileViewModel(dependencies.account, dependencies.auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme by dependencies.settings.themeMode.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val opener = LocalLinkOpener.current
    ProfileScreen(
        state = state,
        themeMode = theme,
        onThemeMode = dependencies.settings::setThemeMode,
        onRetry = viewModel::load,
        onSignOut = viewModel::signOut,
        onSignIn = signIn,
        onRegister = { opener.open(dependencies.links.register) },
        onOpenDevices = onOpenDevices,
        onOpenNotifications = onOpenNotifications,
        onOpenPublicProfile = onOpenPublicProfile,
        onOpenSaved = onOpenSaved,
        onOpenSavedMarket = onOpenSavedMarket,
        onManageOnWeb = { opener.open(dependencies.links.account) },
        onOpenAbout = onOpenAbout,
        onDeleteAccount = onDeleteAccount,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onRegister: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenNotifications: () -> Unit,
    onManageOnWeb: () -> Unit,
    onOpenAbout: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    onOpenPublicProfile: (id: String) -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenSavedMarket: (() -> Unit)? = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.profile_title),
                actions = { NotificationsBell() },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ProfileUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is ProfileUiState.Failed ->
                    // The account failed to load, but what needs no account stays reachable.
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ErrorState(state.message.resolve(), onRetry = onRetry)
                        Column(
                            Modifier.widthIn(max = ContentWidth)
                                .padding(horizontal = Spacing.screen)
                                .padding(bottom = Spacing.xxl),
                            verticalArrangement = Arrangement.spacedBy(Spacing.section),
                        ) {
                            AppearanceAndAbout(themeMode, onThemeMode, onOpenAbout)
                        }
                    }
                else ->
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Spacing.screen)
                            .padding(top = Spacing.s, bottom = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(
                            Modifier.widthIn(max = ContentWidth).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Spacing.section),
                        ) {
                            when (state) {
                                ProfileUiState.Guest -> GuestCard(onSignIn, onRegister)
                                is ProfileUiState.Loaded ->
                                    MemberSections(
                                        state,
                                        onOpenPublicProfile = onOpenPublicProfile,
                                        onOpenSaved = onOpenSaved,
                                        onOpenSavedMarket = onOpenSavedMarket,
                                        onOpenDevices = onOpenDevices,
                                        onOpenNotifications = onOpenNotifications,
                                        onManageOnWeb = onManageOnWeb,
                                        onDeleteAccount = onDeleteAccount,
                                    )
                                else -> Unit
                            }
                            AppearanceAndAbout(themeMode, onThemeMode, onOpenAbout)
                            if (state is ProfileUiState.Loaded) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    enabled = !state.signingOut,
                                    modifier =
                                        Modifier.widthIn(max = 420.dp)
                                            .fillMaxWidth()
                                            .heightIn(min = Spacing.touch)
                                            .align(Alignment.CenterHorizontally),
                                ) {
                                    Icon(
                                        painterResource(ColaIcons.Logout),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(
                                        stringResource(R.string.profile_sign_out),
                                        modifier = Modifier.padding(start = Spacing.s),
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

private val ContentWidth = 560.dp

/** The invitation a guest sees instead of an account: why to sign in, and how. */
@Composable
private fun GuestCard(onSignIn: () -> Unit, onRegister: () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.card),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            SoftIconTile(ColaIcons.Person)
            Text(
                stringResource(R.string.profile_guest_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.profile_guest_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch + Spacing.xs),
            ) {
                Text(stringResource(R.string.profile_sign_in))
            }
            TextButton(onClick = onRegister, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.profile_register))
                Icon(
                    painterResource(ColaIcons.OpenInNew),
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs).size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun MemberSections(
    state: ProfileUiState.Loaded,
    onOpenPublicProfile: (id: String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSavedMarket: (() -> Unit)?,
    onOpenDevices: () -> Unit,
    onOpenNotifications: () -> Unit,
    onManageOnWeb: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val account = state.account
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Avatar(account.displayName, account.avatarUrl, size = 96.dp)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                account.displayName,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(
                    ru.colabike.core.designsystem.R.string.cola_username,
                    account.username,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (account.location.isNotBlank()) {
            PillBadge(account.location, icon = ColaIcons.Location)
        }
        if (account.bio.isNotBlank()) {
            Text(
                account.bio,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = ContentWidth),
            )
        }
    }
    if (!account.emailVerified) {
        ColaCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.padding(Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                IconHalo(ColaIcons.MailUnread)
                Text(
                    stringResource(R.string.profile_unverified),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    Section(stringResource(R.string.profile_section_account)) {
        ColaListItem(
            title = stringResource(R.string.profile_public),
            supporting = stringResource(R.string.profile_public_hint),
            icon = ColaIcons.Person,
            onClick = { onOpenPublicProfile(account.id.value) },
        )
        ColaListItem(
            title = stringResource(R.string.profile_saved),
            supporting = stringResource(R.string.profile_saved_hint),
            icon = ColaIcons.Bookmark,
            onClick = onOpenSaved,
        )
        // The market may be switched off by the server: then its saved listings have no row.
        if (onOpenSavedMarket != null) {
            ColaListItem(
                title = stringResource(R.string.profile_saved_market),
                supporting = stringResource(R.string.profile_saved_market_hint),
                icon = ColaIcons.Tag,
                onClick = onOpenSavedMarket,
            )
        }
        ColaListItem(
            title = stringResource(R.string.profile_notifications),
            supporting = stringResource(R.string.profile_notifications_hint),
            icon = ColaIcons.Notifications,
            onClick = onOpenNotifications,
        )
        ColaListItem(
            title = stringResource(R.string.profile_devices),
            supporting = stringResource(R.string.profile_devices_hint),
            icon = ColaIcons.Devices,
            onClick = onOpenDevices,
        )
        ColaListItem(
            title = stringResource(R.string.profile_manage_web),
            supporting = stringResource(R.string.profile_manage_web_hint),
            icon = ColaIcons.Person,
            tone = HaloTone.Secondary,
            action = ListItemAction.External,
            onClick = onManageOnWeb,
        )
        // In the app, not only on the site: a store asks for it, and a person who has no
        // password has no other way (docs/adr/0021).
        ColaListItem(
            title = stringResource(R.string.profile_delete_account),
            supporting = stringResource(R.string.profile_delete_account_hint),
            icon = ColaIcons.Info,
            tone = HaloTone.Secondary,
            onClick = onDeleteAccount,
        )
    }
}

@Composable
private fun AppearanceAndAbout(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenAbout: () -> Unit,
) {
    Section(stringResource(R.string.profile_section_appearance)) {
        ThemeChoice(themeMode, onThemeMode)
    }
    Section(stringResource(R.string.profile_section_app)) {
        ColaListItem(
            title = stringResource(R.string.profile_about),
            supporting = stringResource(R.string.profile_about_hint),
            icon = ColaIcons.Info,
            onClick = onOpenAbout,
        )
    }
}

/** A titled group of rows; the title is the heading for TalkBack. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Eyebrow(title, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** System, light or dark: one choice among three, each row a whole touch target. */
@Composable
private fun ThemeChoice(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    ColaCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = Spacing.touch)
                        .selectable(
                            selected = mode == selected,
                            onClick = { onSelect(mode) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    RadioButton(selected = mode == selected, onClick = null)
                    Text(
                        stringResource(mode.label),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val ThemeMode.label: Int
    get() =
        when (this) {
            ThemeMode.System -> R.string.theme_system
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.Dark -> R.string.theme_dark
        }
