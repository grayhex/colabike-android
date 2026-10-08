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
import ru.colabike.app.settings.MapProvider
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.HeaderActions
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaRowDivider
import ru.colabike.core.designsystem.component.ColaRowGroup
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.SoftIconTile
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Account
import ru.colabike.core.model.BikeId

/** What the profile can open, as callbacks: the screen never touches navigation itself. */
@Composable
fun ProfileRoute(
    dependencies: AppDependencies,
    onOpenDevices: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAbout: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    onOpenBlocked: () -> Unit = {},
    onOpenPublicProfile: (id: String) -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenSavedMarket: (() -> Unit)? = {},
    onOpenBike: (BikeId) -> Unit = {},
) {
    val viewModel = viewModel { ProfileViewModel(dependencies.account, dependencies.auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val theme by dependencies.settings.themeMode.collectAsStateWithLifecycle()
    val mapProvider by dependencies.settings.mapProvider.collectAsStateWithLifecycle()
    // Two maps to choose between only in a build that has the owner's key for the second.
    val mapChoice =
        if (dependencies.maps.offersYandex) {
            MapChoice(mapProvider) {
                // A new choice is a new try of whatever was given up on.
                dependencies.settings.setMapProvider(it)
                dependencies.maps.forgetFailure()
            }
        } else {
            null
        }
    val signIn = LocalSignInRequest.current
    val opener = LocalLinkOpener.current
    ProfileScreen(
        state = state,
        themeMode = theme,
        onThemeMode = dependencies.settings::setThemeMode,
        mapChoice = mapChoice,
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
        onOpenBlocked = onOpenBlocked,
        bikeContent = { account ->
            ProfileBikesRoute(
                dependencies.people,
                dependencies.bikes,
                account.id.value,
                onOpenBike,
                { onOpenPublicProfile(account.id.value) },
            )
        },
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
    onOpenBlocked: () -> Unit = {},
    onOpenPublicProfile: (id: String) -> Unit = {},
    onOpenSaved: () -> Unit = {},
    onOpenSavedMarket: (() -> Unit)? = {},
    mapChoice: MapChoice? = null,
    bikeContent: (@Composable (Account) -> Unit)? = null,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.profile_title),
                actions = { HeaderActions() },
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
                            AppearanceAndAbout(themeMode, onThemeMode, mapChoice, onOpenAbout)
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
                                        onOpenBlocked = onOpenBlocked,
                                        bikeContent = bikeContent,
                                    )
                                else -> Unit
                            }
                            AppearanceAndAbout(themeMode, onThemeMode, mapChoice, onOpenAbout)
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
                            // The name of the app, quietly, at the foot of the page.
                            Eyebrow(
                                stringResource(R.string.app_name),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
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
    onOpenBlocked: () -> Unit,
    bikeContent: (@Composable (Account) -> Unit)?,
) {
    val account = state.account
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Avatar(account.displayName, account.avatarUrl, size = 88.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(account.displayName, style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(
                        ru.colabike.core.designsystem.R.string.cola_username,
                        account.username,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.location.isNotBlank()) {
                    PillBadge(account.location, icon = ColaIcons.Location)
                }
            }
        }
        if (account.bio.isNotBlank()) {
            Text(account.bio, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (bikeContent != null) {
        bikeContent(account)
    } else {
        // The page of the person as others see it: the one row the reference sets apart, in the
        // accent.
        ColaListItem(
            title = stringResource(R.string.profile_public),
            supporting = stringResource(R.string.profile_public_hint),
            icon = ColaIcons.Person,
            onClick = { onOpenPublicProfile(account.id.value) },
        )
    }
    Section(stringResource(R.string.profile_section_saved)) {
        ColaRowGroup {
            ColaListItem(
                title = stringResource(R.string.profile_saved),
                icon = ColaIcons.Bookmark,
                tone = HaloTone.Secondary,
                onClick = onOpenSaved,
                grouped = true,
            )
            // The market may be switched off by the server: then its saved listings have no row.
            if (onOpenSavedMarket != null) {
                ColaRowDivider()
                ColaListItem(
                    title = stringResource(R.string.profile_saved_market),
                    icon = ColaIcons.Tag,
                    tone = HaloTone.Secondary,
                    onClick = onOpenSavedMarket,
                    grouped = true,
                )
            }
        }
    }
    Section(stringResource(R.string.profile_section_account)) {
        ColaRowGroup {
            if (!account.emailVerified) {
                ColaListItem(
                    title = stringResource(R.string.profile_verify_email),
                    supporting = stringResource(R.string.profile_unverified),
                    icon = ColaIcons.MailUnread,
                    tone = HaloTone.Secondary,
                    action = ListItemAction.External,
                    onClick = onManageOnWeb,
                    grouped = true,
                )
                ColaRowDivider()
            }

            ColaListItem(
                title = stringResource(R.string.profile_notifications),
                supporting = stringResource(R.string.profile_notifications_hint),
                icon = ColaIcons.Notifications,
                tone = HaloTone.Secondary,
                onClick = onOpenNotifications,
                grouped = true,
            )
            ColaRowDivider()
            ColaListItem(
                title = stringResource(R.string.profile_devices),
                supporting = stringResource(R.string.profile_devices_hint),
                icon = ColaIcons.Devices,
                tone = HaloTone.Secondary,
                onClick = onOpenDevices,
                grouped = true,
            )
            ColaRowDivider()
            ColaListItem(
                title = stringResource(R.string.profile_blocked),
                supporting = stringResource(R.string.profile_blocked_hint),
                icon = ColaIcons.Block,
                tone = HaloTone.Secondary,
                onClick = onOpenBlocked,
                grouped = true,
            )
            ColaRowDivider()
            ColaListItem(
                title = stringResource(R.string.profile_manage_web),
                supporting = stringResource(R.string.profile_manage_web_hint),
                icon = ColaIcons.Person,
                tone = HaloTone.Secondary,
                action = ListItemAction.External,
                onClick = onManageOnWeb,
                grouped = true,
            )
            // In the app, not only on the site: a store asks for it, and a person who has no
            // password has no other way (docs/adr/0021).
            ColaRowDivider()
            ColaListItem(
                title = stringResource(R.string.profile_delete_account),
                supporting = stringResource(R.string.profile_delete_account_hint),
                icon = ColaIcons.Info,
                tone = HaloTone.Secondary,
                onClick = onDeleteAccount,
                grouped = true,
            )
        }
    }
}

/** The map under routes, as the setting shows it: what is chosen and how to choose another. */
data class MapChoice(val selected: MapProvider, val onSelect: (MapProvider) -> Unit)

@Composable
private fun AppearanceAndAbout(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    mapChoice: MapChoice?,
    onOpenAbout: () -> Unit,
) {
    Section(stringResource(R.string.profile_section_appearance)) {
        ThemeChoice(themeMode, onThemeMode)
    }
    if (mapChoice != null) {
        Section(stringResource(R.string.profile_section_map)) {
            MapProviderChoice(mapChoice.selected, mapChoice.onSelect)
        }
    }
    Section(stringResource(R.string.profile_section_app)) {
        ColaListItem(
            title = stringResource(R.string.profile_about),
            supporting = stringResource(R.string.profile_about_hint),
            icon = ColaIcons.Info,
            tone = HaloTone.Secondary,
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
    ChoiceGroup(
        options = ThemeMode.entries.map { Choice(it, stringResource(it.label)) },
        selected = selected,
        onSelect = onSelect,
    )
}

/** OpenStreetMap or Yandex: the map under every route, changed on the screen at once. */
@Composable
private fun MapProviderChoice(selected: MapProvider, onSelect: (MapProvider) -> Unit) {
    ChoiceGroup(
        options =
            listOf(
                Choice(
                    MapProvider.OpenStreetMap,
                    stringResource(R.string.map_choice_osm),
                    stringResource(R.string.map_choice_osm_hint),
                ),
                Choice(
                    MapProvider.Yandex,
                    stringResource(R.string.map_choice_yandex),
                    stringResource(R.string.map_choice_yandex_hint),
                ),
            ),
        selected = selected,
        onSelect = onSelect,
    )
}

private data class Choice<T>(val value: T, val title: String, val hint: String? = null)

/** A card of radio rows, one chosen; a row is a whole touch target with its title and hint. */
@Composable
private fun <T> ChoiceGroup(options: List<Choice<T>>, selected: T, onSelect: (T) -> Unit) {
    ColaCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = Spacing.touch)
                        .selectable(
                            selected = option.value == selected,
                            onClick = { onSelect(option.value) },
                            role = Role.RadioButton,
                        )
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                ) {
                    RadioButton(selected = option.value == selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(option.title, style = MaterialTheme.typography.bodyLarge)
                        if (option.hint != null) {
                            Text(
                                option.hint,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
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
