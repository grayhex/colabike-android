package ru.colabike.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.roundToInt
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RideSummary

/**
 * A ride or a plan in a list: author and date, optional map, title and results, then the bike and
 * (for a plan) how many answered. [badges] are extra short facts the list adds (a role, a private
 * ride), [note] a line under the facts (a plan that changed). Without [onClick] the card only tells
 * (a ride that has no page, such as one's private ride) and says no "open".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideCard(
    ride: RideSummary,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badges: List<String> = emptyList(),
    note: String? = null,
    preview: (@Composable () -> Unit)? = null,
) {
    val locale = LocalConfiguration.current.locales[0]
    val status =
        when (ride.status) {
            RideStatus.Planned -> stringResource(R.string.cola_ride_planned)
            RideStatus.Completed -> stringResource(R.string.cola_ride_completed)
            RideStatus.Cancelled -> stringResource(R.string.cola_ride_cancelled)
            RideStatus.Unknown -> null
        }
    val weekly =
        if (ride.recurrence == RideRecurrence.Weekly) stringResource(R.string.cola_ride_weekly)
        else null
    // A plan is about the hour it begins; a ride that happened, about its day.
    val day =
        ride.time?.let {
            if (ride.status == RideStatus.Planned) dateTime(it, locale) else date(it, locale)
        } ?: stringResource(R.string.cola_ride_no_date)
    val distance =
        ride.metrics.distanceM?.let {
            stringResource(R.string.cola_distance_km, kilometers(it, locale))
        }
    val duration =
        ride.metrics.movingTimeS?.let {
            val hours = it / 3600
            val minutes = it % 3600 / 60
            if (hours > 0) stringResource(R.string.cola_duration_hours_minutes, hours, minutes)
            else stringResource(R.string.cola_duration_minutes, minutes)
        }
    val participants =
        ride.participants?.let {
            stringResource(R.string.cola_ride_participants, it.going, it.maybe)
        }
    val elevation =
        ride.metrics.elevationGainM?.let {
            stringResource(R.string.cola_ride_metres, integer(it.roundToInt(), locale))
        }
    val description =
        listOfNotNull(
                ride.title,
                ride.author.displayName,
                status,
                weekly,
                *badges.toTypedArray(),
                day,
                distance,
                duration,
                ride.bike?.name,
                participants,
                elevation,
                pluralStringResource(R.plurals.cola_likes, ride.likes, ride.likes),
                pluralStringResource(R.plurals.cola_comments, ride.comments, ride.comments),
                note,
            )
            .joinToString(", ")
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = openLabel) {
                        onClick()
                        true
                    }
                }
            },
    ) {
        PersonByline(
            ride.author,
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            supporting =
                listOfNotNull(day, status.takeIf { ride.status == RideStatus.Completed })
                    .joinToString(" · "),
        )
        preview?.invoke()
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            if (ride.status != RideStatus.Completed || weekly != null || badges.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (ride.status != RideStatus.Completed) status?.let { PillBadge(it) }
                    weekly?.let { PillBadge(it, icon = ColaIcons.Calendar) }
                    badges.forEach { PillBadge(it) }
                }
            }
            Text(
                ride.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                distance?.let { RideMetric(it, stringResource(R.string.cola_ride_distance)) }
                duration?.let { RideMetric(it, stringResource(R.string.cola_ride_moving_time)) }
                elevation?.let { RideMetric(it, stringResource(R.string.cola_ride_elevation)) }
            }
            participants?.let { Fact(ColaIcons.Person, it) }
            ride.bike?.let { Fact(ColaIcons.Bike, it.name) }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Counter(
                    if (ride.liked) ColaIcons.LikeFilled else ColaIcons.Like,
                    ride.likes,
                    ride.liked,
                )
                Counter(ColaIcons.Comment, ride.comments)
            }
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RideMetric(value: String, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
