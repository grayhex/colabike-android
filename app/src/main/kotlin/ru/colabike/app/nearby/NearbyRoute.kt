package ru.colabike.app.nearby

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.AppDependencies

/**
 * Rides near me. The approximate location is asked for only after the screen has said why (the
 * person's yes to its explanation) and only that one permission: not the precise one and not the
 * one for the background. A "no" is an answer and is not asked again in a loop.
 */
@Composable
fun NearbyRoute(
    dependencies: AppDependencies,
    onBack: () -> Unit,
    onOpenOffers: (() -> Unit)?,
) {
    val viewModel = viewModel { NearbyViewModel(dependencies.nearby, dependencies.coarseLocation) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.locate() else viewModel.permissionDenied()
        }
    val actions =
        remember(viewModel, onOpenOffers) {
            NearbyActions(
                onBack = onBack,
                onRetryLoad = viewModel::load,
                onEnabled = viewModel::setEnabled,
                onHorizon = viewModel::setHorizon,
                onToggle = viewModel::toggle,
                onLocate = {
                    if (dependencies.coarseLocation.granted()) viewModel.locate()
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                onRadius = viewModel::setDraftRadius,
                onConfirm = { viewModel.confirmDraft() },
                onDiscard = viewModel::discardDraft,
                onReplaceYes = { viewModel.confirmDraft(replaceManual = true) },
                onReplaceNo = viewModel::cancelReplace,
                onRemoveArea = viewModel::removeArea,
                onForget = viewModel::forget,
                onOpenOffers = onOpenOffers,
            )
        }
    NearbyScreen(state, actions)
}
