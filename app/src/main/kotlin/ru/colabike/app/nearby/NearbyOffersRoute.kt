package ru.colabike.app.nearby

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.AppDependencies
import ru.colabike.core.model.RideId

/** The rides now in the person's area; a tap opens the ride, "configure" opens the settings. */
@Composable
fun NearbyOffersRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
    onOpenRide: (RideId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = viewModel { NearbyOffersViewModel(dependencies.nearby) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            NearbyOffersActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onRefresh = viewModel::refresh,
                onOpenRide = onOpenRide,
                onOpenSettings = onOpenSettings,
            )
        }
    NearbyOffersScreen(state, actions)
}
