package ru.colabike.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import ru.colabike.app.AppDependencies
import ru.colabike.app.R
import ru.colabike.app.about.AboutRoute
import ru.colabike.app.about.LicensesRoute
import ru.colabike.app.bikes.BikeDetailRoute
import ru.colabike.app.bikes.BikesRoute
import ru.colabike.app.comments.CommentsRoute
import ru.colabike.app.devices.DevicesRoute
import ru.colabike.app.feed.FeedActions
import ru.colabike.app.feed.FeedRoute
import ru.colabike.app.journal.JournalActions
import ru.colabike.app.journal.JournalListRoute
import ru.colabike.app.journal.JournalRoute
import ru.colabike.app.journal.JournalSource
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.Navigator
import ru.colabike.app.navigation.TopLevel
import ru.colabike.app.navigation.rememberNavigationState
import ru.colabike.app.people.PeopleListKind
import ru.colabike.app.people.PeopleListRoute
import ru.colabike.app.people.PersonActions
import ru.colabike.app.people.PersonRoute
import ru.colabike.app.profile.ProfileRoute
import ru.colabike.app.search.SearchRoute
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaNavigationRail
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentKind
import ru.colabike.core.model.CommentTarget
import ru.colabike.core.model.JournalId

/**
 * The signed-in shell. The window decides the navigation: a floating bar below the medium width, a
 * rail from it up. Each top-level section keeps its own back stack ([TopLevel.shown]); the
 * list-detail scene shows the bike list and a bike side by side on expanded widths and one pane at
 * a time below them. Not a phone layout stretched: the scene, not the screen, decides.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell(dependencies: AppDependencies, modifier: Modifier = Modifier) {
    val sections = TopLevel.shown
    val state =
        rememberNavigationState(
            startRoute = TopLevel.start.root,
            topLevelRoutes = sections.map { it.root }.toSet(),
        )
    val navigator = remember(state) { Navigator(state) }
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
    // The box to write in sits at the bottom edge, where the floating bar would cover it: while a
    // discussion is open on a phone the bar steps aside (the rail on wider windows is at the side).
    val barHidden = bottomBar && state.currentStack.lastOrNull() is Destination.Comments

    val items = sections.map { ColaNavItem(stringResource(it.label), it.icon, it.selectedIcon) }
    val selectedIndex = sections.indexOfFirst { it.root == state.topLevelRoute }.coerceAtLeast(0)
    val onSelect: (Int) -> Unit = { navigator.select(sections[it].root) }

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
            // The bar or the rail already keeps clear of the system bars on its side. Above a bar
            // the
            // page fades out instead of ending in a hard edge.
            Box(
                Modifier.then(
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
                    .then(
                        if (bottomBar && !barHidden) Modifier.fadeBottomEdge(FadeHeight)
                        else Modifier
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
                                                onOpenBike = { id -> navigator.openBike(id.value) },
                                                onOpenJournal = { id ->
                                                    navigator.open(Destination.Journal(id.value))
                                                },
                                                onFindPeople = {
                                                    navigator.open(
                                                        Destination.Search(people = true)
                                                    )
                                                },
                                                onBrowseBikes = {
                                                    navigator.select(Destination.Bikes)
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
                                        id = JournalId(key.id),
                                        actions =
                                            JournalActions(
                                                onBack = { navigator.back() },
                                                onOpenBike = { id -> navigator.openBike(id.value) },
                                                onOpenAuthor = { ref ->
                                                    navigator.open(Destination.Person(ref))
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
                                            ),
                                        commentChanges = dependencies.comments.countChanges,
                                    )
                                }
                                entry<Destination.Comments> { key ->
                                    CommentsRoute(
                                        repository = dependencies.comments,
                                        drafts = dependencies.drafts,
                                        auth = dependencies.auth,
                                        target = CommentTarget(commentKind(key.kind), key.id),
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
                                        title = stringResource(R.string.journal_saved_title),
                                        subtitle = null,
                                        onBack = { navigator.back() },
                                        onOpen = { id ->
                                            navigator.open(Destination.Journal(id.value))
                                        },
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
                                        scrollToTop =
                                            remember(navigator) {
                                                navigator.reselects(Destination.Bikes)
                                            },
                                    )
                                }
                                entry<Destination.Bike>(
                                    metadata = ListDetailSceneStrategy.detailPane()
                                ) { key ->
                                    // Side by side with the list there is nothing to go back
                                    // from; opened over a person or a search it fills the area
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
                                        id = BikeId(key.id),
                                        showBack = !besideList,
                                        onBack = { navigator.back() },
                                        onOpenAuthor = { ref ->
                                            navigator.open(Destination.Person(ref))
                                        },
                                        onOpenJournal = { id, name ->
                                            navigator.open(Destination.BikeJournal(id.value, name))
                                        },
                                        onOpenComments = { id, name ->
                                            navigator.open(
                                                Destination.Comments("bike", id.value, name)
                                            )
                                        },
                                        commentChanges = dependencies.comments.countChanges,
                                    )
                                }
                                entry<Destination.Person> { key ->
                                    PersonRoute(
                                        commentChanges = dependencies.comments.countChanges,
                                        people = dependencies.people,
                                        bikes = dependencies.bikes,
                                        auth = dependencies.auth,
                                        ref = key.ref,
                                        actions =
                                            PersonActions(
                                                onBack = { navigator.back() },
                                                onOpenBike = { id -> navigator.openBike(id.value) },
                                                onOpenFollowers = { ref ->
                                                    navigator.open(
                                                        Destination.People(ref, following = false)
                                                    )
                                                },
                                                onOpenFollowing = { ref ->
                                                    navigator.open(
                                                        Destination.People(ref, following = true)
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
                                        onOpenDevices = { navigator.open(Destination.Devices) },
                                        onOpenAbout = { navigator.open(Destination.About) },
                                        onOpenPublicProfile = { id ->
                                            navigator.open(Destination.Person(id))
                                        },
                                        onOpenSaved = { navigator.open(Destination.SavedJournal) },
                                    )
                                }
                                entry<Destination.Devices> {
                                    DevicesRoute(
                                        dependencies.sessions,
                                        dependencies.clock,
                                        onBack = { navigator.back() },
                                    )
                                }
                                entry<Destination.About> {
                                    AboutRoute(
                                        dependencies.links,
                                        onBack = { navigator.back() },
                                        onLicenses = { navigator.open(Destination.Licenses) },
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

private val BrandMarkRail = 40.dp

/** How far above the floating bar the page dissolves. */
private val FadeHeight = 24.dp

/** Erases the last [height] of the content softly, so a list does not end in a straight cut. */
private fun Modifier.fadeBottomEdge(height: Dp): Modifier =
    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Black, Color.Transparent),
                startY = size.height - height.toPx(),
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun DetailPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(
            title = stringResource(R.string.nav_bikes),
            message = stringResource(R.string.bikes_pick),
            icon = ColaIcons.Bike,
        )
    }
}

/** The kind a navigation key names; the keys come from this app, so an unknown one is a bike. */
private fun commentKind(key: String): CommentKind =
    CommentKind.entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: CommentKind.Bike
