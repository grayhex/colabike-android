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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import ru.colabike.app.AppDependencies
import ru.colabike.app.R
import ru.colabike.app.bikes.BikeDetailRoute
import ru.colabike.app.bikes.BikesRoute
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.Navigator
import ru.colabike.app.navigation.TopLevel
import ru.colabike.app.navigation.rememberNavigationState
import ru.colabike.app.profile.ProfileRoute
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaNavItem
import ru.colabike.core.designsystem.component.ColaNavigationBar
import ru.colabike.core.designsystem.component.ColaNavigationRail
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.BikeId

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

    val items = sections.map { ColaNavItem(stringResource(it.label), it.icon, it.selectedIcon) }
    val selectedIndex = sections.indexOfFirst { it.root == state.topLevelRoute }.coerceAtLeast(0)
    val onSelect: (Int) -> Unit = { navigator.select(sections[it].root) }

    ColaCanvas(modifier.fillMaxSize()) {
        NavigationSuiteScaffoldLayout(
            layoutType =
                if (bottomBar) NavigationSuiteType.NavigationBar
                else NavigationSuiteType.NavigationRail,
            navigationSuite = {
                if (bottomBar) {
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
                Modifier.consumeWindowInsets(
                        if (bottomBar)
                            NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
                        else NavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
                    )
                    .then(if (bottomBar) Modifier.fadeBottomEdge(FadeHeight) else Modifier)
            ) {
                NavDisplay(
                    entries =
                        state.toDecoratedEntries(
                            entryProvider {
                                entry<Destination.Bikes>(
                                    metadata =
                                        ListDetailSceneStrategy.listPane(
                                            detailPlaceholder = { DetailPlaceholder() }
                                        )
                                ) {
                                    BikesRoute(
                                        dependencies.bikes,
                                        onOpen = { id -> navigator.openBike(id.value) },
                                        scrollToTop =
                                            remember(navigator) {
                                                navigator.reselects(Destination.Bikes)
                                            },
                                    )
                                }
                                entry<Destination.Bike>(
                                    metadata = ListDetailSceneStrategy.detailPane()
                                ) { key ->
                                    BikeDetailRoute(
                                        repository = dependencies.bikes,
                                        id = BikeId(key.id),
                                        showBack = !twoPane,
                                        onBack = { navigator.back() },
                                    )
                                }
                                entry<Destination.Profile> {
                                    ProfileRoute(dependencies.account, dependencies.auth)
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
