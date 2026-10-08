package ru.colabike.app.rides

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import ru.colabike.app.R
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary

class RidePreviewEntry(val model: RidePreviews, val maps: RouteMaps)

val LocalRidePreviewEntry = staticCompositionLocalOf<RidePreviewEntry?> { null }

/** A route preview belongs only to an openable completed ride whose summary advertises a track. */
@Composable
fun PreviewRideCard(
    ride: RideSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badges: List<String> = emptyList(),
    note: String? = null,
) {
    val entry = LocalRidePreviewEntry.current
    val preview: (@Composable () -> Unit)? =
        if (
            entry != null && onClick != null && ride.hasTrack && ride.status == RideStatus.Completed
        ) {
            { RidePreviewMedia(ride.id, entry, Modifier.fillMaxWidth().height(148.dp)) }
        } else null
    RideCard(ride, modifier, onClick, badges, note, preview)
}

@Composable
private fun RidePreviewMedia(id: RideId, entry: RidePreviewEntry, modifier: Modifier) {
    val states by entry.model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(entry.model, id, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            entry.model.show(id)
            try {
                awaitCancellation()
            } finally {
                entry.model.hide(id)
            }
        }
    }
    when (val state = states[id]) {
        is RidePreview.Ready -> entry.maps.Preview(state.route, modifier)
        else ->
            Column(
                modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painterResource(ColaIcons.Route),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    stringResource(
                        if (state == RidePreview.Unavailable) R.string.route_preview_unavailable
                        else R.string.route_preview_loading
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}
