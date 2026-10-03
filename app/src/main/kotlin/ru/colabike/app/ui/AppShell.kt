package ru.colabike.app.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.window.core.layout.WindowSizeClass
import ru.colabike.app.AppDependencies
import ru.colabike.app.R
import ru.colabike.app.bikes.BikeDetailRoute
import ru.colabike.app.bikes.BikesRoute
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.openBike
import ru.colabike.app.navigation.selectTab
import ru.colabike.app.profile.ProfileRoute
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.model.BikeId

private enum class Tab(
    val destination: Destination,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Bikes(Destination.Bikes, R.string.nav_bikes, ColaIcons.Bike, ColaIcons.BikeFilled),
    Profile(Destination.Profile, R.string.nav_profile, ColaIcons.Person, ColaIcons.PersonFilled),
}

/**
 * The signed-in shell. The navigation suite picks a bottom bar or a rail from the window; the
 * list-detail scene shows the bike list and a bike side by side on expanded widths and one pane at
 * a time below them. Not a phone layout stretched: the scene, not the screen, decides.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun AppShell(dependencies: AppDependencies, modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Destination.Bikes)
    val listDetail = rememberListDetailSceneStrategy<NavKey>()
    val twoPane =
        currentWindowAdaptiveInfo()
            .windowSizeClass
            .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val current = backStack.firstOrNull()
    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                val selected = current == tab.destination
                item(
                    selected = selected,
                    onClick = { backStack.selectTab(tab.destination) },
                    icon = {
                        Icon(
                            painterResource(if (selected) tab.selectedIcon else tab.icon),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(tab.label)) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            sceneStrategies = listOf(listDetail),
            entryProvider =
                entryProvider {
                    entry<Destination.Bikes>(
                        metadata =
                            ListDetailSceneStrategy.listPane(
                                detailPlaceholder = { DetailPlaceholder() }
                            )
                    ) {
                        BikesRoute(
                            dependencies.bikes,
                            onOpen = { id -> backStack.openBike(Destination.Bike(id.value)) },
                        )
                    }
                    entry<Destination.Bike>(metadata = ListDetailSceneStrategy.detailPane()) { key
                        ->
                        BikeDetailRoute(
                            repository = dependencies.bikes,
                            id = BikeId(key.id),
                            showBack = !twoPane,
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<Destination.Profile> {
                        ProfileRoute(dependencies.account, dependencies.auth)
                    }
                },
        )
    }
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
