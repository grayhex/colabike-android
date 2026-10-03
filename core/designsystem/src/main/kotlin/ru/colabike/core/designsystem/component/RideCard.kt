package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary

/** A ride in a list: title and status, then when, how far, how long and on which bike. */
@Composable
fun RideCard(ride: RideSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val status =
        when (ride.status) {
            RideStatus.Planned -> stringResource(R.string.cola_ride_planned)
            RideStatus.Completed -> stringResource(R.string.cola_ride_completed)
            RideStatus.Unknown -> null
        }
    val day = ride.time?.let { date(it, locale) }
    val distance =
        ride.distanceMeters?.let {
            stringResource(R.string.cola_distance_km, kilometers(it, locale))
        }
    val duration =
        ride.movingTimeSeconds?.let {
            val hours = it / 3600
            val minutes = it % 3600 / 60
            if (hours > 0) stringResource(R.string.cola_duration_hours_minutes, hours, minutes)
            else stringResource(R.string.cola_duration_minutes, minutes)
        }
    val description =
        listOfNotNull(ride.title, status, day, distance, duration, ride.bikeName).joinToString(", ")
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            },
    ) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    ride.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (status != null) PillBadge(status)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                day?.let { Fact(ColaIcons.Calendar, it) }
                distance?.let { Fact(ColaIcons.Route, it) }
                duration?.let { Fact(ColaIcons.Timer, it) }
                ride.bikeName?.let { Fact(ColaIcons.Bike, it) }
            }
        }
    }
}

@Composable
private fun Fact(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
