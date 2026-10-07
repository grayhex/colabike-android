package ru.colabike.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import ru.colabike.app.AppDependencies
import ru.colabike.app.R
import ru.colabike.app.about.AboutRoute
import ru.colabike.app.about.LicensesRoute
import ru.colabike.app.account.DeleteAccountRoute
import ru.colabike.app.bikes.BikeDetailRoute
import ru.colabike.app.bikes.BikeEditorRoute
import ru.colabike.app.bikes.BikePartEditorRoute
import ru.colabike.app.bikes.BikePartsRoute
import ru.colabike.app.bikes.BikePhotosRoute
import ru.colabike.app.bikes.BikeWizardRoute
import ru.colabike.app.bikes.BikesRoute
import ru.colabike.app.comments.CommentsRoute
import ru.colabike.app.components.ComponentRoute
import ru.colabike.app.components.ComponentsRoute
import ru.colabike.app.config.NoticeBanner
import ru.colabike.app.config.UpdateBanner
import ru.colabike.app.config.rememberUpdateState
import ru.colabike.app.devices.DevicesRoute
import ru.colabike.app.feed.FeedActions
import ru.colabike.app.feed.FeedRoute
import ru.colabike.app.intents.IntentEditorRoute
import ru.colabike.app.intents.IntentRoute
import ru.colabike.app.intents.IntentsRoute
import ru.colabike.app.journal.JournalActions
import ru.colabike.app.journal.JournalEditorRoute
import ru.colabike.app.journal.JournalListRoute
import ru.colabike.app.journal.JournalPhotosRoute
import ru.colabike.app.journal.JournalRoute
import ru.colabike.app.journal.JournalSource
import ru.colabike.app.links.AppLink
import ru.colabike.app.links.AppLinkParser
import ru.colabike.app.links.LinkTarget
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.target
import ru.colabike.app.market.ListingActions
import ru.colabike.app.market.ListingRoute
import ru.colabike.app.market.MarketRoute
import ru.colabike.app.market.SavedMarketRoute
import ru.colabike.app.messages.ChatsEntry
import ru.colabike.app.messages.ConversationRoute
import ru.colabike.app.messages.ConversationsRoute
import ru.colabike.app.messages.LocalChatsEntry
import ru.colabike.app.messages.NewConversationRoute
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.Navigator
import ru.colabike.app.navigation.TopLevel
import ru.colabike.app.navigation.rememberNavigationState
import ru.colabike.app.nearby.NearbyOffersRoute
import ru.colabike.app.nearby.NearbyRoute
import ru.colabike.app.notifications.LocalNotificationsEntry
import ru.colabike.app.notifications.NotificationBadgeViewModel
import ru.colabike.app.notifications.NotificationsEntry
import ru.colabike.app.notifications.NotificationsRoute
import ru.colabike.app.notifications.badge
import ru.colabike.app.notifications.settings.NotificationSettingsRoute
import ru.colabike.app.participation.ParticipationRoute
import ru.colabike.app.people.PeopleListKind
import ru.colabike.app.people.PeopleListRoute
import ru.colabike.app.people.PersonActions
import ru.colabike.app.people.PersonRoute
import ru.colabike.app.profile.ProfileRoute
import ru.colabike.app.rides.BikeRidesRoute
import ru.colabike.app.rides.RideActions
import ru.colabike.app.rides.RideRoute
import ru.colabike.app.rides.RidesRoute
import ru.colabike.app.safety.BlockedRoute
import ru.colabike.app.search.SearchRoute
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaNavigationRail
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.ChannelCid
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.Feature
import ru.colabike.core.model.JournalId
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.RideId
import ru.colabike.core.model.UpdateState

/**
 * The signed-in shell. The window decides the navigation: a floating bar below the medium width, a
 * rail from it up. Each top-level section keeps its own back stack ([TopLevel.shown]); the
 * list-detail scene shows the bike list and a bike side by side on expanded widths and one pane at
 * a time below them. Not a phone layout stretched: the scene, not the screen, decides.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell(dependencies: AppDependencies, modifier: Modifier = Modifier) {
    // The tabs are those of the config the app started on: a section does not vanish under a
    // person who is in it. A change of flags reaches the tabs at the next start (and the entry
    // points inside the screens at once).
    val config by dependencies.appConfig.state.collectAsStateWithLifecycle()
    val features = config.features
    val serviceLinks = config.config.links
    val startFeatures = remember { features }
    val sections = remember(startFeatures) { TopLevel.shown(startFeatures) }
    val state =
        rememberNavigationState(
            startRoute = TopLevel.start.root,
            topLevelRoutes = sections.map { it.root }.toSet(),
        )
    // A screen whose function the server has switched off is not opened from anywhere: the person
    // is told, and stays where they are.
    var unavailable by rememberSaveable { mutableStateOf(false) }
    val currentFeatures by rememberUpdatedState(features)
    val navigator =
        remember(state) {
            Navigator(state, features = { currentFeatures }, onUnavailable = { unavailable = true })
        }
    // One band above the content at most: a firm offer to update, else the server's message, else
    // a quiet offer to update. What the person closed is remembered, a changed one comes back.
    val update = rememberUpdateState(dependencies)
    val noticeClosed by dependencies.settings.noticeClosed.collectAsStateWithLifecycle()
    val offerClosed by dependencies.settings.updateOfferClosed.collectAsStateWithLifecycle()
    var firmOfferLater by rememberSaveable { mutableStateOf(false) }
    val notice = config.config.notice
    val latestVersion = config.config.compatibility.latestVersionCode
    val showFirmOffer = update is UpdateState.Recommended && !firmOfferLater
    val showNotice = !showFirmOffer && notice != null && noticeClosed != notice.revision
    val showOffer =
        !showFirmOffer &&
            !showNotice &&
            update is UpdateState.Available &&
            offerClosed != latestVersion
    val linkOpener = LocalLinkOpener.current
    val linkParser = remember(dependencies.links) { AppLinkParser(dependencies.links.siteUrl) }
    // The button of a message: a page the app has opens there, another page of the site and any
    // other https address open in the browser; nothing else is ever opened.
    val openAddress: (String) -> Unit = { url ->
        val link = linkParser.parse(url)
        when (val target = link.target(dependencies.links)) {
            is LinkTarget.InApp -> navigator.go(target.destination)
            is LinkTarget.OnSite -> linkOpener.open(target.url)
            LinkTarget.None -> if (link !is AppLink.NativeAuth) linkOpener.open(url)
        }
    }
    if (unavailable) {
        AlertDialog(
            onDismissRequest = { unavailable = false },
            title = { Text(stringResource(R.string.feature_off_title)) },
            text = { Text(stringResource(R.string.feature_off)) },
            confirmButton = {
                TextButton(onClick = { unavailable = false }) {
                    Text(stringResource(R.string.feature_off_ok))
                }
            },
        )
    }
    // A link that arrived before the shell could take it (sign-in, a cold start) is carried out
    // now.
    val pendingDestination by dependencies.pending.destination.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDestination) {
        pendingDestination?.let {
            navigator.go(it)
            dependencies.pending.clear()
        }
    }
    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    // Read through State: NavEntries are built once per stack, so a plain value would stay at what
    // the window was when the entry was created (fold or unfold would leave the wrong back arrow).
    val twoPane by
        rememberUpdatedState(
            windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
        )
    val bottomBar =
        !windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    // The bar steps aside on a phone where the bottom edge belongs to something else: the box to
    // write in of a discussion, the main action of the "I want to ride" form, and the page of a
    // bike, which the approved design shows whole (the rail on wider windows is at the side).
    val barHidden =
        bottomBar &&
            state.currentStack.lastOrNull().let {
                it is Destination.Comments ||
                    it is Destination.Conversation ||
                    it is Destination.Bike ||
                    it is Destination.IntentEditor
            }

    val items = sections.map { ColaNavItem(stringResource(it.label), it.icon, it.selectedIcon) }
    val selectedIndex = sections.indexOfFirst { it.root == state.topLevelRoute }.coerceAtLeast(0)
    val onSelect: (Int) -> Unit = { navigator.select(sections[it].root) }

    // The bell: the count the server gave, asked for again when the app returns, and at most once
    // in half a minute while the person moves about. A guest has no inbox, and no bell.
    val authState by dependencies.auth.state.collectAsStateWithLifecycle()
    val member = authState is AuthState.SignedIn
    val badgeModel = viewModel { NotificationBadgeViewModel(dependencies.notifications) }
    val unread by badgeModel.count.collectAsStateWithLifecycle()
    LifecycleResumeEffect(member) {
        if (member) badgeModel.refresh()
        onPauseOrDispose {}
    }
    val here = state.currentStack.lastOrNull()
    LaunchedEffect(here, member) { if (member) badgeModel.refresh() }
    val bell =
        remember(member, unread, navigator) {
            if (member) {
                NotificationsEntry(unread.badge(), { navigator.open(Destination.Notifications) })
            } else null
        }

    // The chats: over the screen the person is on, not a section of the bar. A guest has the
    // invitation to sign in behind the button, as the section had.
    val chatOn = features.isEnabled(Feature.Chat)
    val chats =
        remember(chatOn, navigator) {
            if (chatOn) ChatsEntry { navigator.open(Destination.Messages) } else null
        }

    CompositionLocalProvider(
        LocalNotificationsEntry provides bell,
        LocalChatsEntry provides chats,
    ) {
        ColaCanvas(modifier.fillMaxSize()) {
            NavigationSuiteScaffoldLayout(
                layoutType =
                    when {
                        barHidden -> NavigationSuiteType.None
                        bottomBar -> NavigationSuiteType.NavigationBar
                        else -> NavigationSuiteType.NavigationRail
                    },
                navigationSuite = {
                    if (barHidden) {
                        // The layout type says "none", and so must the content, or the bar is drawn
                        // anyway at the corner.
                    } else if (bottomBar) {
                        ColaNavigationBar(items, selectedIndex, onSelect)
                    } else {
                        ColaNavigationRail(
                            items,
                            selectedIndex,
                            onSelect,
                            header = { BrandMark(size = BrandMarkRail) },
                        )
                    }
                },
            ) {
                // The bar or the rail already keeps clear of the system bars on its side. Above a
                // bar
                // the
                // page fades out instead of ending in a hard edge.
                Column {
                    val banner = showFirmOffer || showNotice || showOffer
                    if (showFirmOffer || showOffer) {
                        UpdateBanner(
                            state = update,
                            onUpdate = linkOpener::open,
                            onClose = {
                                if (showFirmOffer) firmOfferLater = true
                                else latestVersion?.let(dependencies.settings::setUpdateOfferClosed)
                            },
                            modifier = Modifier.bannerInsets(),
                        )
                    } else if (showNotice && notice != null) {
                        NoticeBanner(
                            notice = notice,
                            image = notice.imageUrl?.let(dependencies.configAssets::fileOf),
                            onAction = { openAddress(it.url) },
                            onClose = { dependencies.settings.setNoticeClosed(notice.revision) },
                            modifier = Modifier.bannerInsets(),
                        )
                    }
                    Box(
                        Modifier.weight(1f)
                            .then(
                                if (banner)
                                    Modifier.consumeWindowInsets(
                                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                                    )
                                else Modifier
                            )
                            .then(
                                when {
                                    barHidden -> Modifier
                                    bottomBar ->
                                        Modifier.consumeWindowInsets(
                                            NavigationBarDefaults.windowInsets.only(
                                                WindowInsetsSides.Bottom
                                            )
                                        )
                                    else ->
                                        Modifier.consumeWindowInsets(
                                            NavigationRailDefaults.windowInsets.only(
                                                WindowInsetsSides.Start
                                            )
                                        )
                                }
                            )
                    ) {
                        NavDisplay(
                            entries =
                                state.toDecoratedEntries(
                                    entryProvider {
                                        entry<Destination.Feed> {
                                            FeedRoute(
                                                commentChanges = dependencies.comments.countChanges,
                                                feed = dependencies.feed,
                                                bikes = dependencies.bikes,
                                                auth = dependencies.auth,
                                                actions =
                                                    FeedActions(
                                                        onOpenBike = { id ->
                                                            navigator.openBike(id.value)
                                                        },
                                                        onOpenJournal = { id ->
                                                            navigator.open(
                                                                Destination.Journal(id.value)
                                                            )
                                                        },
                                                        onOpenRide = { id ->
                                                            navigator.open(
                                                                Destination.Ride(id.value)
                                                            )
                                                        },
                                                        onFindPeople = {
                                                            navigator.open(
                                                                Destination.Search(people = true)
                                                            )
                                                        },
                                                        onBrowseBikes = {
                                                            navigator.select(Destination.Bikes)
                                                        },
                                                        onOpenListing = { id ->
                                                            navigator.open(
                                                                Destination.Listing(id.value)
                                                            )
                                                        },
                                                    ),
                                                scrollToTop =
                                                    remember(navigator) {
                                                        navigator.reselects(Destination.Feed)
                                                    },
                                            )
                                        }
                                        entry<Destination.Journal> { key ->
                                            JournalRoute(
                                                repository = dependencies.journal,
                                                auth = dependencies.auth,
                                                safety = dependencies.safety,
                                                id = JournalId(key.id),
                                                actions =
                                                    JournalActions(
                                                        onBack = { navigator.back() },
                                                        onOpenBike = { id ->
                                                            navigator.openBike(id.value)
                                                        },
                                                        onOpenAuthor = { ref ->
                                                            navigator.open(Destination.Person(ref))
                                                        },
                                                        onEditPhotos = { id ->
                                                            navigator.open(
                                                                Destination.JournalPhotos(id.value)
                                                            )
                                                        },
                                                        onEdit = { bike, id ->
                                                            navigator.open(
                                                                Destination.JournalEditor(
                                                                    bike.value,
                                                                    id.value,
                                                                )
                                                            )
                                                        },
                                                        onOpenComments = { id, title ->
                                                            navigator.open(
                                                                Destination.Comments(
                                                                    "journal",
                                                                    id.value,
                                                                    title,
                                                                )
                                                            )
                                                        },
                                                        onOpenComponent = { modelId ->
                                                            navigator.open(
                                                                Destination.Component(modelId)
                                                            )
                                                        },
                                                    ),
                                                commentChanges = dependencies.comments.countChanges,
                                            )
                                        }
                                        entry<Destination.Comments> { key ->
                                            CommentsRoute(
                                                repository = dependencies.comments,
                                                drafts = dependencies.drafts,
                                                auth = dependencies.auth,
                                                safety = dependencies.safety,
                                                target =
                                                    CommentTarget(commentKind(key.kind), key.id),
                                                title = key.title,
                                                focus = key.focus,
                                                onBack = { navigator.back() },
                                                onOpenAuthor = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                            )
                                        }
                                        entry<Destination.BikeJournal> { key ->
                                            JournalListRoute(
                                                commentChanges = dependencies.comments.countChanges,
                                                repository = dependencies.journal,
                                                source = JournalSource.OfBike(BikeId(key.bikeId)),
                                                title = stringResource(R.string.journal_of_bike),
                                                subtitle = key.bikeName,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Journal(id.value))
                                                },
                                            )
                                        }
                                        entry<Destination.SavedJournal> {
                                            JournalListRoute(
                                                commentChanges = dependencies.comments.countChanges,
                                                repository = dependencies.journal,
                                                source = JournalSource.Saved,
                                                title =
                                                    stringResource(R.string.journal_saved_title),
                                                subtitle = null,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Journal(id.value))
                                                },
                                            )
                                        }
                                        entry<Destination.Rides> {
                                            RidesRoute(
                                                repository = dependencies.rides,
                                                auth = dependencies.auth,
                                                onOpen = { id ->
                                                    navigator.open(Destination.Ride(id.value))
                                                },
                                                scrollToTop =
                                                    remember(navigator) {
                                                        navigator.reselects(Destination.Rides)
                                                    },
                                                commentChanges = dependencies.comments.countChanges,
                                                participationChanges =
                                                    dependencies.participation.changes,
                                                onOpenIntents = {
                                                    navigator.open(Destination.Intents)
                                                },
                                            )
                                        }
                                        entry<Destination.Participation> { key ->
                                            ParticipationRoute(
                                                dependencies,
                                                rideId = key.rideId,
                                                occurrenceAt = key.occurrenceAt,
                                                onBack = { navigator.back() },
                                                onOpenRide = { id ->
                                                    navigator.open(Destination.Ride(id))
                                                },
                                                onOpenSettings = {
                                                    navigator.open(Destination.NotificationSettings)
                                                },
                                                onOpenRides = { navigator.open(Destination.Rides) },
                                            )
                                        }
                                        entry<Destination.Intents> {
                                            IntentsRoute(
                                                dependencies,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Intent(id))
                                                },
                                                onCreate = {
                                                    navigator.open(Destination.IntentEditor())
                                                },
                                            )
                                        }
                                        entry<Destination.Intent> { key ->
                                            IntentRoute(
                                                dependencies,
                                                id = key.id,
                                                chat =
                                                    if (features.isEnabled(Feature.Chat)) {
                                                        dependencies.chat
                                                    } else null,
                                                onBack = { navigator.back() },
                                                onOpenAuthor = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                                onOpenConversation = { cid ->
                                                    navigator.open(
                                                        Destination.Conversation(cid.value)
                                                    )
                                                },
                                                onEdit = {
                                                    navigator.open(Destination.IntentEditor(key.id))
                                                },
                                                onOpenIntents = {
                                                    navigator.open(Destination.Intents)
                                                },
                                            )
                                        }
                                        entry<Destination.IntentEditor> { key ->
                                            IntentEditorRoute(
                                                dependencies,
                                                id = key.id,
                                                onBack = { navigator.back() },
                                                onSaved = { id ->
                                                    // Back to the list (or the page being changed),
                                                    // then the page.
                                                    navigator.back()
                                                    if (key.id == null) {
                                                        navigator.open(Destination.Intent(id))
                                                    }
                                                },
                                                onOpenIntents = {
                                                    navigator.open(Destination.Intents)
                                                },
                                            )
                                        }
                                        entry<Destination.Ride> { key ->
                                            RideRoute(
                                                repository = dependencies.rides,
                                                auth = dependencies.auth,
                                                safety = dependencies.safety,
                                                id = RideId(key.id),
                                                maps = dependencies.maps,
                                                actions =
                                                    RideActions(
                                                        onBack = { navigator.back() },
                                                        onOpenBike = { id ->
                                                            navigator.openBike(id.value)
                                                        },
                                                        onOpenAuthor = { ref ->
                                                            navigator.open(Destination.Person(ref))
                                                        },
                                                        onOpenComments = { id, title ->
                                                            navigator.open(
                                                                Destination.Comments(
                                                                    "ride",
                                                                    id.value,
                                                                    title,
                                                                )
                                                            )
                                                        },
                                                        onOpenParticipation = { id ->
                                                            navigator.open(
                                                                Destination.Participation(id.value)
                                                            )
                                                        },
                                                    ),
                                                commentChanges = dependencies.comments.countChanges,
                                            )
                                        }
                                        entry<Destination.BikeRides> { key ->
                                            BikeRidesRoute(
                                                repository = dependencies.rides,
                                                bike = BikeId(key.bikeId),
                                                bikeName = key.bikeName,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Ride(id.value))
                                                },
                                                commentChanges = dependencies.comments.countChanges,
                                            )
                                        }
                                        entry<Destination.Bikes>(
                                            metadata =
                                                ListDetailSceneStrategy.listPane(
                                                    detailPlaceholder = { DetailPlaceholder() }
                                                )
                                        ) {
                                            BikesRoute(
                                                dependencies.bikes,
                                                dependencies.auth,
                                                commentChanges = dependencies.comments.countChanges,
                                                onOpen = { id -> navigator.openBike(id.value) },
                                                onSearch = { navigator.open(Destination.Search()) },
                                                onCreate = {
                                                    navigator.open(Destination.BikeWizard)
                                                },
                                                onOpenCatalog =
                                                    if (
                                                        features.isEnabled(Feature.ComponentCatalog)
                                                    ) {
                                                        { navigator.open(Destination.Components) }
                                                    } else null,
                                                scrollToTop =
                                                    remember(navigator) {
                                                        navigator.reselects(Destination.Bikes)
                                                    },
                                            )
                                        }
                                        entry<Destination.Components> {
                                            ComponentsRoute(
                                                repository = dependencies.components,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Component(id.value))
                                                },
                                            )
                                        }
                                        entry<Destination.Market> { key ->
                                            MarketRoute(
                                                repository = dependencies.market,
                                                seller = key.seller,
                                                // The market is a section: at its root there is
                                                // nothing to go back from. A seller's listings are
                                                // opened over it.
                                                onBack =
                                                    if (key.seller == null) null
                                                    else ({ navigator.back() }),
                                                onOpen = { id ->
                                                    navigator.open(Destination.Listing(id.value))
                                                },
                                            )
                                        }
                                        entry<Destination.Listing> { key ->
                                            ListingRoute(
                                                repository = dependencies.market,
                                                auth = dependencies.auth,
                                                links = dependencies.links,
                                                id = ListingId(key.id),
                                                actions =
                                                    ListingActions(
                                                        onBack = { navigator.back() },
                                                        onOpenListing = { id ->
                                                            navigator.open(
                                                                Destination.Listing(id.value)
                                                            )
                                                        },
                                                        onOpenSeller = { ref ->
                                                            navigator.open(Destination.Person(ref))
                                                        },
                                                        onOpenSellerListings = { username ->
                                                            navigator.open(
                                                                Destination.Market(username)
                                                            )
                                                        },
                                                        onOpenComponent = { modelId ->
                                                            navigator.open(
                                                                Destination.Component(modelId)
                                                            )
                                                        },
                                                        onOpenBike = { id ->
                                                            navigator.openBike(id.value)
                                                        },
                                                    ),
                                            )
                                        }
                                        entry<Destination.SavedMarket> {
                                            SavedMarketRoute(
                                                repository = dependencies.market,
                                                onBack = { navigator.back() },
                                                onOpen = { id ->
                                                    navigator.open(Destination.Listing(id.value))
                                                },
                                            )
                                        }
                                        entry<Destination.Component> { key ->
                                            ComponentRoute(
                                                repository = dependencies.components,
                                                links = dependencies.links,
                                                id = ComponentId(key.id),
                                                onBack = { navigator.back() },
                                                onOpenComments = { id, name ->
                                                    navigator.open(
                                                        Destination.Comments(
                                                            "component",
                                                            id.value,
                                                            name,
                                                        )
                                                    )
                                                },
                                            )
                                        }
                                        entry<Destination.BikeWizard> {
                                            BikeWizardRoute(
                                                repository = dependencies.wizard,
                                                catalog = dependencies.catalog,
                                                onBack = { navigator.back() },
                                                // The wizard is replaced by the bike it made.
                                                onCreated = { id ->
                                                    navigator.back()
                                                    navigator.openBike(id)
                                                },
                                            )
                                        }
                                        entry<Destination.BikeEditor> { key ->
                                            BikeEditorRoute(
                                                repository = dependencies.bikes,
                                                catalog = dependencies.catalog,
                                                id = key.id,
                                                onBack = { navigator.back() },
                                                onSaved = { id ->
                                                    // Back to the page being changed, or to the
                                                    // list a new bike was made from; a new bike
                                                    // is then shown.
                                                    navigator.back()
                                                    if (key.id == null) navigator.openBike(id)
                                                },
                                                onDeleted = {
                                                    // The form goes, and so does the page of the
                                                    // bike that no longer is.
                                                    navigator.back()
                                                    if (
                                                        state.currentStack.lastOrNull() ==
                                                            Destination.Bike(key.id.orEmpty())
                                                    ) {
                                                        navigator.back()
                                                    }
                                                },
                                                onOpenGarage = { navigator.toRoot() },
                                            )
                                        }
                                        entry<Destination.BikeParts> { key ->
                                            BikePartsRoute(
                                                repository = dependencies.bikes,
                                                catalog = dependencies.catalog,
                                                bikeId = key.bikeId,
                                                onBack = { navigator.back() },
                                                onAdd = {
                                                    navigator.open(Destination.BikePart(key.bikeId))
                                                },
                                                onOpenPart = { part ->
                                                    navigator.open(
                                                        Destination.BikePart(key.bikeId, part)
                                                    )
                                                },
                                                onOpenGarage = { navigator.toRoot() },
                                            )
                                        }
                                        entry<Destination.JournalEditor> { key ->
                                            JournalEditorRoute(
                                                journal = dependencies.journal,
                                                bikes = dependencies.bikes,
                                                clock = dependencies.clock,
                                                bikeId = key.bikeId,
                                                id = key.id,
                                                onBack = { navigator.back() },
                                                onSaved = { id ->
                                                    // Back to the entry being changed, or to where
                                                    // a new one was begun; a new one is then shown.
                                                    navigator.back()
                                                    if (key.id == null)
                                                        navigator.open(Destination.Journal(id))
                                                },
                                                onDeleted = {
                                                    // The form goes, and so does the page of the
                                                    // entry that no longer is.
                                                    navigator.back()
                                                    if (
                                                        state.currentStack.lastOrNull() ==
                                                            Destination.Journal(key.id.orEmpty())
                                                    ) {
                                                        navigator.back()
                                                    }
                                                },
                                                onOpenGarage = { navigator.toRoot() },
                                            )
                                        }
                                        entry<Destination.JournalPhotos> { key ->
                                            JournalPhotosRoute(
                                                repository = dependencies.journal,
                                                entryId = key.id,
                                                onBack = { navigator.back() },
                                                onOpenGarage = { navigator.toRoot() },
                                                photoFiles = dependencies.photoFiles,
                                            )
                                        }
                                        entry<Destination.BikePhotos> { key ->
                                            BikePhotosRoute(
                                                repository = dependencies.bikes,
                                                bikeId = key.bikeId,
                                                onBack = { navigator.back() },
                                                onOpenGarage = { navigator.toRoot() },
                                                photoFiles = dependencies.photoFiles,
                                                pickOnOpen = key.add,
                                            )
                                        }
                                        entry<Destination.BikePart> { key ->
                                            BikePartEditorRoute(
                                                repository = dependencies.bikes,
                                                catalog = dependencies.catalog,
                                                bikeId = key.bikeId,
                                                componentId = key.id,
                                                onBack = { navigator.back() },
                                                onDone = { navigator.back() },
                                                onOpenGarage = { navigator.toRoot() },
                                            )
                                        }
                                        entry<Destination.Bike>(
                                            metadata = ListDetailSceneStrategy.detailPane()
                                        ) { key ->
                                            // Side by side with the list there is nothing to go
                                            // back
                                            // from; opened over a person or a search it fills the
                                            // area
                                            // and needs its arrow.
                                            val stack = state.currentStack
                                            val besideList =
                                                twoPane &&
                                                    stack.getOrNull(stack.lastIndexOf(key) - 1) ==
                                                        Destination.Bikes
                                            BikeDetailRoute(
                                                repository = dependencies.bikes,
                                                auth = dependencies.auth,
                                                links = dependencies.links,
                                                safety = dependencies.safety,
                                                onEdit = { id ->
                                                    navigator.open(Destination.BikeEditor(id.value))
                                                },
                                                onEditParts = { id ->
                                                    navigator.open(Destination.BikeParts(id.value))
                                                },
                                                onEditPhotos = { id ->
                                                    navigator.open(Destination.BikePhotos(id.value))
                                                },
                                                onAddPhoto = { id ->
                                                    navigator.open(
                                                        Destination.BikePhotos(id.value, add = true)
                                                    )
                                                },
                                                onNewJournalEntry = { id ->
                                                    navigator.open(
                                                        Destination.JournalEditor(id.value)
                                                    )
                                                },
                                                id = BikeId(key.id),
                                                showBack = !besideList,
                                                onBack = { navigator.back() },
                                                onOpenAuthor = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                                onOpenJournal = { id, name ->
                                                    navigator.open(
                                                        Destination.BikeJournal(id.value, name)
                                                    )
                                                },
                                                onOpenJournalEntry = { id ->
                                                    navigator.open(Destination.Journal(id.value))
                                                },
                                                onOpenRides = { id, name ->
                                                    navigator.open(
                                                        Destination.BikeRides(id.value, name)
                                                    )
                                                },
                                                onOpenComponent = { modelId ->
                                                    navigator.open(Destination.Component(modelId))
                                                },
                                                commentChanges = dependencies.comments.countChanges,
                                                journal = dependencies.journal,
                                                comments = dependencies.comments,
                                                drafts = dependencies.drafts,
                                            )
                                        }
                                        entry<Destination.Person> { key ->
                                            PersonRoute(
                                                commentChanges = dependencies.comments.countChanges,
                                                people = dependencies.people,
                                                bikes = dependencies.bikes,
                                                auth = dependencies.auth,
                                                chat = dependencies.chat,
                                                safety = dependencies.safety,
                                                ref = key.ref,
                                                actions =
                                                    PersonActions(
                                                        onBack = { navigator.back() },
                                                        onOpenBike = { id ->
                                                            navigator.openBike(id.value)
                                                        },
                                                        onOpenConversation = { cid ->
                                                            navigator.open(
                                                                Destination.Conversation(cid.value)
                                                            )
                                                        },
                                                        onOpenFollowers = { ref ->
                                                            navigator.open(
                                                                Destination.People(
                                                                    ref,
                                                                    following = false,
                                                                )
                                                            )
                                                        },
                                                        onOpenFollowing = { ref ->
                                                            navigator.open(
                                                                Destination.People(
                                                                    ref,
                                                                    following = true,
                                                                )
                                                            )
                                                        },
                                                        onOpenAccount = {
                                                            navigator.select(Destination.Profile)
                                                        },
                                                    ),
                                            )
                                        }
                                        entry<Destination.People> { key ->
                                            PeopleListRoute(
                                                repository = dependencies.people,
                                                ref = key.ref,
                                                kind =
                                                    if (key.following) PeopleListKind.Following
                                                    else PeopleListKind.Followers,
                                                onBack = { navigator.back() },
                                                onOpenPerson = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                            )
                                        }
                                        entry<Destination.Search> { key ->
                                            SearchRoute(
                                                startOnPeople = key.people,
                                                bikes = dependencies.bikes,
                                                people = dependencies.people,
                                                onBack = { navigator.back() },
                                                onOpenBike = { id -> navigator.openBike(id.value) },
                                                onOpenPerson = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                            )
                                        }
                                        entry<Destination.Profile> {
                                            ProfileRoute(
                                                dependencies,
                                                onOpenDevices = {
                                                    navigator.open(Destination.Devices)
                                                },
                                                onOpenNotifications = {
                                                    navigator.open(Destination.NotificationSettings)
                                                },
                                                onOpenAbout = { navigator.open(Destination.About) },
                                                onDeleteAccount = {
                                                    navigator.open(Destination.DeleteAccount)
                                                },
                                                onOpenBlocked = {
                                                    navigator.open(Destination.Blocked)
                                                },
                                                onOpenPublicProfile = { id ->
                                                    navigator.open(Destination.Person(id))
                                                },
                                                onOpenSaved = {
                                                    navigator.open(Destination.SavedJournal)
                                                },
                                                onOpenSavedMarket =
                                                    if (features.isEnabled(Feature.Market)) {
                                                        { navigator.open(Destination.SavedMarket) }
                                                    } else null,
                                            )
                                        }
                                        entry<Destination.Messages>(
                                            metadata =
                                                ListDetailSceneStrategy.listPane(
                                                    detailPlaceholder = {
                                                        DetailPlaceholder(
                                                            R.string.nav_messages,
                                                            R.string.chat_pick,
                                                            ColaIcons.Chat,
                                                        )
                                                    }
                                                )
                                        ) {
                                            ConversationsRoute(
                                                session = dependencies.chatSession,
                                                screens = dependencies.chatScreens,
                                                site = dependencies.links,
                                                signedIn = member,
                                                onOpen = { cid ->
                                                    navigator.open(
                                                        Destination.Conversation(cid.value)
                                                    )
                                                },
                                                onNew = {
                                                    navigator.open(Destination.NewConversation)
                                                },
                                            )
                                        }
                                        entry<Destination.Conversation>(
                                            metadata = ListDetailSceneStrategy.detailPane()
                                        ) { key ->
                                            ConversationRoute(
                                                session = dependencies.chatSession,
                                                screens = dependencies.chatScreens,
                                                site = dependencies.links,
                                                cid = ChannelCid(key.cid),
                                                visible = dependencies.visibleConversation,
                                                onBack = { navigator.back() },
                                            )
                                        }
                                        entry<Destination.NewConversation> {
                                            NewConversationRoute(
                                                repository = dependencies.chat,
                                                session = dependencies.chatSession,
                                                site = dependencies.links,
                                                onBack = { navigator.back() },
                                                onOpened = { cid ->
                                                    // Back from the conversation lands on the list,
                                                    // not
                                                    // here.
                                                    navigator.back()
                                                    navigator.open(
                                                        Destination.Conversation(cid.value)
                                                    )
                                                },
                                            )
                                        }
                                        entry<Destination.Notifications> {
                                            NotificationsRoute(
                                                repository = dependencies.notifications,
                                                site = dependencies.links,
                                                onBack = { navigator.back() },
                                                onOpen = { destination ->
                                                    if (destination is Destination.Bike) {
                                                        navigator.openBike(destination.id)
                                                    } else {
                                                        navigator.open(destination)
                                                    }
                                                },
                                                onCount = badgeModel::update,
                                            )
                                        }
                                        entry<Destination.Devices> {
                                            DevicesRoute(
                                                dependencies.sessions,
                                                dependencies.clock,
                                                onBack = { navigator.back() },
                                            )
                                        }
                                        entry<Destination.Blocked> {
                                            BlockedRoute(
                                                dependencies.safety,
                                                onBack = { navigator.back() },
                                                onOpenPerson = { ref ->
                                                    navigator.open(Destination.Person(ref))
                                                },
                                            )
                                        }
                                        entry<Destination.DeleteAccount> {
                                            DeleteAccountRoute(
                                                dependencies.accountDeletion,
                                                dependencies.auth,
                                                onBack = { navigator.back() },
                                            )
                                        }
                                        entry<Destination.NotificationSettings> {
                                            NotificationSettingsRoute(
                                                dependencies,
                                                onBack = { navigator.back() },
                                                onOpenNearby =
                                                    if (features.isEnabled(Feature.Rides)) {
                                                        {
                                                            navigator.open(
                                                                Destination.NearbySettings
                                                            )
                                                        }
                                                    } else null,
                                            )
                                        }
                                        entry<Destination.NearbySettings> {
                                            NearbyRoute(
                                                dependencies,
                                                onBack = { navigator.back() },
                                                onOpenOffers = {
                                                    navigator.open(Destination.NearbyOffers)
                                                },
                                            )
                                        }
                                        entry<Destination.NearbyOffers> {
                                            NearbyOffersRoute(
                                                dependencies,
                                                onBack = { navigator.back() },
                                                onOpenRide = { id ->
                                                    navigator.open(Destination.Ride(id.value))
                                                },
                                                onOpenSettings = {
                                                    navigator.open(Destination.NearbySettings)
                                                },
                                            )
                                        }
                                        entry<Destination.About> {
                                            AboutRoute(
                                                dependencies.links,
                                                service = serviceLinks,
                                                onBack = { navigator.back() },
                                                onLicenses = {
                                                    navigator.open(Destination.Licenses)
                                                },
                                            )
                                        }
                                        entry<Destination.Licenses> {
                                            LicensesRoute(onBack = { navigator.back() })
                                        }
                                    }
                                ),
                            sceneStrategies = listOf(listDetail),
                            onBack = { navigator.back() },
                        )
                    }
                }
            }
        }
    }
}

private val BrandMarkRail = 40.dp

@Composable
private fun DetailPlaceholder(
    @StringRes title: Int = R.string.nav_bikes,
    @StringRes message: Int = R.string.bikes_pick,
    @DrawableRes icon: Int = ColaIcons.Bike,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(title),
            message = stringResource(message),
            icon = icon,
        )
    }
}

/** The kind a navigation key names; the keys come from this app, so an unknown one is a bike. */
private fun commentKind(key: String): CommentKind =
    CommentKind.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: CommentKind.Bike

/** The band sits where the top bar of a screen would: under the status bar and clear of cutouts. */
@Composable
private fun Modifier.bannerInsets(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    )
