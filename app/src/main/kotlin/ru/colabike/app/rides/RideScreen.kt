package ru.colabike.app.rides

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.StatTile
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.Range
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RidesRepository

/** Where a ride leads. Callbacks, so the screen never touches navigation itself. */
class RideActions(
    val onBack: () -> Unit,
    val onOpenBike: (BikeId) -> Unit,
    val onOpenAuthor: (ref: String) -> Unit,
    val onOpenComments: (id: RideId, title: String) -> Unit,
)

@Composable
fun RideRoute(
    repository: RidesRepository,
    auth: AuthActions,
    id: RideId,
    actions: RideActions,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) {
    val viewModel = viewModel { RideViewModel(repository, id, commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    RideScreen(
        state = state,
        actions = actions,
        onRetry = viewModel::load,
        // A private ride answers a guest "not found" exactly as a missing one does.
        onSignIn = if (authState is AuthState.SignedIn) null else LocalSignInRequest.current,
    )
}

/**
 * One ride or plan: when (in the device's time zone, said aloud), what it measured, the organizer's
 * passport of a plan, the meeting point if the viewer may see it, who and which bike, and the
 * discussion. Only what the server gave for this viewer is here; the route comes with the map.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideScreen(
    state: RideUiState,
    actions: RideActions,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)? = null,
) {
    val loaded = state as? RideUiState.Loaded
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title =
                    stringResource(
                        if (loaded?.ride?.summary?.status == RideStatus.Planned)
                            R.string.ride_plan_title
                        else R.string.ride_title
                    ),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RideUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is RideUiState.Failed ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorState(state.message.resolve(), onRetry = onRetry)
                        if (state.notFound && onSignIn != null) {
                            Text(
                                stringResource(R.string.ride_private_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.xl),
                            )
                            Button(
                                onClick = onSignIn,
                                modifier =
                                    Modifier.padding(Spacing.l).heightIn(min = Spacing.touch),
                            ) {
                                Text(stringResource(R.string.profile_sign_in))
                            }
                        }
                    }
                is RideUiState.Loaded -> Ride(state.ride, actions)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ride(ride: RideDetail, actions: RideActions) {
    val summary = ride.summary
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                summary.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            val planned = summary.status == RideStatus.Planned
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                PillBadge(
                    stringResource(
                        if (planned) ru.colabike.core.designsystem.R.string.cola_ride_planned
                        else ru.colabike.core.designsystem.R.string.cola_ride_completed
                    ),
                    icon = ColaIcons.Route,
                )
                if (summary.recurrence == RideRecurrence.Weekly) {
                    PillBadge(
                        stringResource(ru.colabike.core.designsystem.R.string.cola_ride_weekly),
                        icon = ColaIcons.Calendar,
                    )
                }
                if (ride.recruitmentClosed) {
                    PillBadge(stringResource(R.string.ride_recruitment_closed))
                }
            }
            When(ride, locale, zone)
            Metrics(ride, locale)
            ride.passport?.let { Passport(it, locale) }
            Meeting(ride)
            summary.participants?.let {
                Text(
                    stringResource(
                        ru.colabike.core.designsystem.R.string.cola_ride_participants,
                        it.going,
                        it.maybe,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (ride.description.isNotBlank()) {
                Text(ride.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (ride.features.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ride.features.forEach { PillBadge(it) }
                }
            }
            if (ride.hasPublicRoute) {
                Text(
                    stringResource(R.string.ride_route_soon),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ride.extraMetrics.isNotEmpty()) Sensors(ride.extraMetrics, locale)
            summary.bike?.let { bike ->
                ColaListItem(
                    title = bike.name,
                    supporting = stringResource(R.string.ride_open_bike),
                    icon = ColaIcons.Bike,
                    onClick = { actions.onOpenBike(bike.id) },
                )
            }
            UserRow(
                summary.author,
                onClick = { actions.onOpenAuthor(summary.author.id.value) },
            )
            ColaListItem(
                title = stringResource(R.string.comments_open),
                supporting =
                    pluralStringResource(
                        ru.colabike.core.designsystem.R.plurals.cola_comments,
                        summary.comments,
                        summary.comments,
                    ),
                icon = ColaIcons.Comment,
                onClick = { actions.onOpenComments(summary.id, summary.title) },
            )
        }
    }
}

/** The day and the hour, in the device's time zone and with its name, so nothing is guessed. */
@Composable
private fun When(ride: RideDetail, locale: Locale, zone: ZoneId) {
    val summary = ride.summary
    val planned = summary.status == RideStatus.Planned
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        summary.time?.let {
            Text(
                stringResource(
                    if (planned) R.string.ride_when_planned else R.string.ride_when_completed,
                    zoned(it, locale, zone),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        ride.expectedEndAt?.let {
            Text(
                stringResource(R.string.ride_expected_end, zoned(it, locale, zone)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (summary.time != null || ride.expectedEndAt != null) {
            Text(
                stringResource(R.string.ride_timezone_note, zoneName(zone, locale)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (summary.recurrence == RideRecurrence.Weekly) {
            Text(
                stringResource(R.string.ride_weekly_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun zoned(instant: Instant, locale: Locale, zone: ZoneId): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(instant)

/** "MSK" or "GMT+3": the zone as the device calls it. */
private fun zoneName(zone: ZoneId, locale: Locale): String =
    DateTimeFormatter.ofPattern("zzz", locale).withZone(zone).format(Instant.now())

/** Two tiles to a row; one at a large font. Only the numbers the ride has. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Metrics(ride: RideDetail, locale: Locale) {
    val m = ride.summary.metrics
    val numbers = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    val tiles = buildList {
        m.distanceM?.let {
            add(
                stringResource(R.string.ride_distance) to
                    stringResource(
                        ru.colabike.core.designsystem.R.string.cola_distance_km,
                        numbers.format(it / 1000.0),
                    )
            )
        }
        m.movingTimeS?.let { add(stringResource(R.string.ride_moving_time) to duration(it)) }
        m.elapsedTimeS
            ?.takeIf { it != m.movingTimeS }
            ?.let { add(stringResource(R.string.ride_elapsed_time) to duration(it)) }
        m.avgSpeedMps?.let {
            add(
                stringResource(R.string.ride_avg_speed) to
                    stringResource(R.string.ride_speed_value, numbers.format(it * 3.6))
            )
        }
        m.elevationGainM?.let {
            add(
                stringResource(R.string.ride_elevation) to
                    stringResource(R.string.ride_metres_value, numbers.format(it.roundToInt()))
            )
        }
    }
    if (tiles.isEmpty()) return
    val columns = if (LocalDensity.current.fontScale > LargeFont) 1 else 2
    FlowRow(
        maxItemsInEachRow = columns,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        tiles.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
    }
}

@Composable
private fun duration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return if (hours > 0)
        stringResource(
            ru.colabike.core.designsystem.R.string.cola_duration_hours_minutes,
            hours,
            minutes,
        )
    else stringResource(ru.colabike.core.designsystem.R.string.cola_duration_minutes, minutes)
}

/** What the organizer expects of a plan; the open-set words are shown as they came. */
@Composable
private fun Passport(passport: RidePassport, locale: Locale) {
    val numbers = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    fun Range.low(): String = numbers.format(min)
    fun Range.high(): String = numbers.format(max)
    val rows =
        buildList<Pair<String, String>> {
            passport.areaLabel?.let { add(stringResource(R.string.ride_area) to it) }
            passport.purpose?.let { add(stringResource(R.string.ride_purpose) to it) }
            passport.pace?.let { add(stringResource(R.string.ride_pace) to it) }
            passport.surface?.let { add(stringResource(R.string.ride_surface) to it) }
            passport.difficulty?.let { add(stringResource(R.string.ride_difficulty) to it) }
            passport.regroupPolicy?.let { add(stringResource(R.string.ride_regroup) to it) }
            passport.distanceKm?.let {
                add(
                    stringResource(R.string.ride_p_distance) to
                        stringResource(R.string.ride_range_km, it.low(), it.high())
                )
            }
            passport.durationMinutes?.let {
                add(
                    stringResource(R.string.ride_p_duration) to
                        stringResource(R.string.ride_range_minutes, it.low(), it.high())
                )
            }
            passport.groupSize?.let {
                add(
                    stringResource(R.string.ride_p_group) to
                        stringResource(R.string.ride_range_people, it.low(), it.high())
                )
            }
            passport.speedKmh?.let {
                add(
                    stringResource(R.string.ride_p_speed) to
                        stringResource(R.string.ride_range_speed, it.low(), it.high())
                )
            }
            passport.beginnerFriendly?.let {
                add(
                    stringResource(R.string.ride_p_beginners) to
                        stringResource(if (it) R.string.ride_yes else R.string.ride_no)
                )
            }
        }
    if (rows.isEmpty()) return
    Section(stringResource(R.string.ride_passport)) { FactsCard(rows) }
}

@Composable
private fun Meeting(ride: RideDetail) {
    val point = ride.meetingPoint
    if (point == null && !ride.meetingHidden) return
    Section(stringResource(R.string.ride_meeting)) {
        Text(
            point ?: stringResource(R.string.ride_meeting_hidden),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (point == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Numbers of sensors and devices, by the names the server gave them. */
@Composable
private fun Sensors(metrics: Map<String, Double>, locale: Locale) {
    val numbers = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    Section(stringResource(R.string.ride_sensors)) {
        FactsCard(metrics.entries.map { it.key to numbers.format(it.value) })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Label over value, one pair under another, in a hairline card. */
@Composable
private fun FactsCard(rows: List<Pair<String, String>>) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.s)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = Spacing.s).semantics(
                        mergeDescendants = true
                    ) {},
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private val ContentWidth = 720.dp

/** From this font scale on, metric tiles stack. */
private const val LargeFont = 1.3f
