package ru.colabike.app.participation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.format.DateTimeParseException
import ru.colabike.app.AppDependencies

/**
 * The part of the person in one date of a plan. [occurrenceAt] is the date a notification named (an
 * ISO instant); one that cannot be read is dropped and the nearest date is asked for.
 */
@Composable
fun ParticipationRoute(
    dependencies: AppDependencies,
    rideId: String,
    occurrenceAt: String?,
    onBack: () -> Unit,
    onOpenRide: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRides: () -> Unit,
) {
    val date = remember(occurrenceAt) { occurrenceAt?.let(::parseInstant) }
    val viewModel =
        viewModel(key = "participation:$rideId:${date ?: "-"}") {
            ParticipationViewModel(dependencies.participation, rideId, date)
        }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel) {
            ParticipationActions(
                onBack = onBack,
                onRetry = viewModel::load,
                onRespond = viewModel::respond,
                onOpenRide = onOpenRide,
                onOpenSettings = onOpenSettings,
                onOpenRides = onOpenRides,
            )
        }
    ParticipationScreen(state, actions)
}

private fun parseInstant(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
