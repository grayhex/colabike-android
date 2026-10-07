package ru.colabike.app.rides

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.RouteSketch
import ru.colabike.app.rides.map.SketchRouteMaps
import ru.colabike.app.safety.ReportMenu
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
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.CommentCountChange
import ru.colabike.core.model.Range
import ru.colabike.core.model.ReportKind
import ru.colabike.core.model.ReportTarget
import ru.colabike.core.model.RideDetail
import ru.colabike.core.model.RideId
import ru.colabike.core.model.RidePassport
import ru.colabike.core.model.RideRecurrence
import ru.colabike.core.model.RideRoute
import ru.colabike.core.model.RideStatus
import ru.colabike.core.model.RidesRepository
import ru.colabike.core.model.SafetyRepository

/** Where a ride leads. Callbacks, so the screen never touches navigation itself. */
data class RideActions(
    val onBack: () -> Unit,
    val onOpenBike: (BikeId) -> Unit,
    val onOpenAuthor: (ref: String) -> Unit,
    val onOpenComments: (id: RideId, title: String) -> Unit,
    /** The terms of a plan and the viewer's answer; null for a guest and where it is off. */
    val onOpenParticipation: ((RideId) -> Unit)? = null,
)

@Composable
fun RideRoute(
    repository: RidesRepository,
    auth: AuthActions,
    id: RideId,
    actions: RideActions,
    maps: RouteMaps,
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
    safety: SafetyRepository? = null,
) {
    val viewModel = viewModel { RideViewModel(repository, id, commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    RideScreen(
        state = state,
        // "My answer" is a member's: a guest is not offered a page that would only say "sign in".
        actions =
            if (authState is AuthState.SignedIn) actions
            else actions.copy(onOpenParticipation = null),
        onRetry = viewModel::load,
        // A private ride answers a guest "not found" exactly as a missing one does.
        onSignIn = if (authState is AuthState.SignedIn) null else LocalSignInRequest.current,
        analysis = analysis,
        onLoadAnalysis = viewModel::loadAnalysis,
        maps = maps,
        topActions = {
            ReportMenu(
                target = ReportTarget(ReportKind.Ride, id.value),
                authorId = (state as? RideUiState.Loaded)?.ride?.summary?.author?.id,
                safety = safety,
                auth = auth,
            )
        },
    )
}

/**
 * One ride or plan: when (in the device's time zone, said aloud), what it measured, the organizer's
 * passport of a plan, the meeting point if the viewer may see it, the route and its charts, who and
 * which bike, and the discussion. Only what the server gave for this viewer is here.
 *
 * The route is a drawing on a phone (the map opens on its own full screen, so a hand scrolling the
 * page never pans a map by mistake) and a real map beside the page from [WidePane] on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RideScreen(
    state: RideUiState,
    actions: RideActions,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)? = null,
    analysis: AnalysisUiState = AnalysisUiState.NotAsked,
    onLoadAnalysis: () -> Unit = {},
    maps: RouteMaps = SketchRouteMaps,
    topActions: @Composable RowScope.() -> Unit = {},
) {
    val loaded = state as? RideUiState.Loaded
    val route = loaded?.ride?.route
    var mapOpen by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WidePane
        // Only a phone opens the map on its own screen; beside the page it is always there.
        val fullMap = mapOpen && route != null && !wide
        BackHandler(enabled = fullMap) { mapOpen = false }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ColaTopBar(
                    title =
                        stringResource(
                            when {
                                fullMap -> R.string.route_title
                                loaded?.ride?.summary?.status == RideStatus.Planned ->
                                    R.string.ride_plan_title
                                else -> R.string.ride_title
                            }
                        ),
                    compactTitle = true,
                    onBack = if (fullMap) ({ mapOpen = false }) else actions.onBack,
                    actions = if (loaded != null && !fullMap) topActions else ({}),
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
                    is RideUiState.Loaded -> {
                        val ride = state.ride
                        // The charts are a request of their own, made once the page shows a route.
                        LaunchedEffect(ride.route != null) {
                            if (ride.route != null) onLoadAnalysis()
                        }
                        when {
                            fullMap && route != null -> FullMap(route, maps)
                            wide ->
                                Row(Modifier.fillMaxSize()) {
                                    Ride(
                                        ride,
                                        actions,
                                        analysis,
                                        onLoadAnalysis,
                                        route = null,
                                        onOpenMap = {},
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (route != null) {
                                        MapPane(route, maps, Modifier.weight(1f))
                                    }
                                }
                            else ->
                                Ride(
                                    ride,
                                    actions,
                                    analysis,
                                    onLoadAnalysis,
                                    route = route,
                                    onOpenMap = { mapOpen = true },
                                    modifier = Modifier.fillMaxSize(),
                                )
                        }
                    }
                }
            }
        }
    }
}

/** The map on its own screen: the room for it, and what a reader must know about it. */
@Composable
private fun FullMap(route: RideRoute, maps: RouteMaps) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) { maps.Map(route, Modifier.fillMaxSize()) }
        RouteNotes(route, !maps.hasBasemap, Modifier.padding(Spacing.screen))
    }
}

/** The map beside the page on a wide window, in a frame like the cards. */
@Composable
private fun MapPane(route: RideRoute, maps: RouteMaps, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().padding(end = Spacing.screen, bottom = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ColaCard(Modifier.weight(1f).fillMaxWidth()) { maps.Map(route, Modifier.fillMaxSize()) }
        RouteNotes(route, !maps.hasBasemap)
    }
}

/** Two honest sentences under a route: the cuts are on purpose, and the map may be only a route. */
@Composable
private fun RouteNotes(
    route: RideRoute,
    basemapMissing: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (route.lines.size > 1) {
            Text(
                stringResource(R.string.route_cut_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (basemapMissing) {
            Text(
                stringResource(R.string.route_no_basemap),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * On a phone: the drawing of the route in a card with its heading and the way to the real map. The
 * drawing is low (the card, the figures and the note have to share the first screen).
 */
@Composable
private fun RouteCard(route: RideRoute, onOpenMap: () -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // The heading and the way to the map: side by side, one under the other at a big font.
            val title: @Composable (Modifier) -> Unit = {
                Text(
                    stringResource(R.string.route_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = it.semantics { heading() },
                )
            }
            val open: @Composable () -> Unit = {
                TextButton(onClick = onOpenMap) {
                    Icon(
                        painterResource(ColaIcons.Route),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.route_open_map),
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
            if (LocalDensity.current.fontScale > LargeFont) {
                title(Modifier)
                open()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    title(Modifier.weight(1f))
                    open()
                }
            }
            RouteSketch(
                route,
                Modifier.fillMaxWidth().height(SketchHeight).clip(MaterialTheme.shapes.small),
            )
            RouteNotes(route, basemapMissing = false)
        }
    }
}

/** A line of text of the person's own in a card: the note of a ride, the words of a plan. */
@Composable
private fun NoteCard(label: String, text: String) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Ride(
    ride: RideDetail,
    actions: RideActions,
    analysis: AnalysisUiState,
    onLoadAnalysis: () -> Unit,
    route: RideRoute?,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = ride.summary
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    Column(
        modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                summary.title,
                style = ColaTheme.textStyles.pageTitle,
                modifier = Modifier.semantics { heading() },
            )
            val planned = summary.status == RideStatus.Planned
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    PillBadge(
                        stringResource(
                            if (planned) ru.colabike.core.designsystem.R.string.cola_ride_planned
                            else ru.colabike.core.designsystem.R.string.cola_ride_completed
                        ),
                        icon = if (planned) ColaIcons.Schedule else ColaIcons.CheckCircle,
                        accent = !planned,
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
            }
            // The route first: on a page of a ride it is what the ride was.
            if (route != null) RouteCard(route, onOpenMap)
            Metrics(ride, locale)
            if (ride.description.isNotBlank()) {
                NoteCard(
                    stringResource(if (planned) R.string.ride_description else R.string.ride_note),
                    ride.description,
                )
            }
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
            if (planned) {
                actions.onOpenParticipation?.let { open ->
                    ColaListItem(
                        title = stringResource(R.string.ride_participation),
                        supporting = stringResource(R.string.ride_participation_hint),
                        icon = ColaIcons.Calendar,
                        onClick = { open(summary.id) },
                        modifier = Modifier.testTag("ride:participation"),
                    )
                }
            }
            if (ride.features.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    ride.features.forEach { PillBadge(it) }
                }
            }
            AnalysisSection(analysis, onRetry = onLoadAnalysis)
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

/**
 * The day and the hour, in the device's time zone, with the zone's name in the same line, so
 * nothing is guessed: "16 сентября 2026 г., 18:28 МСК". A plan says it begins; a ride that took
 * place is already said so by its badge.
 */
@Composable
private fun When(ride: RideDetail, locale: Locale, zone: ZoneId) {
    val summary = ride.summary
    val planned = summary.status == RideStatus.Planned
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        summary.time?.let {
            val moment = "${zoned(it, locale, zone)} ${zoneName(zone, locale)}"
            Text(
                if (planned) stringResource(R.string.ride_when_planned, moment) else moment,
                style = MaterialTheme.typography.bodyLarge,
                color = muted,
            )
        }
        ride.expectedEndAt?.let {
            Text(
                stringResource(R.string.ride_expected_end, zoned(it, locale, zone)),
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
            )
        }
        if (summary.recurrence == RideRecurrence.Weekly) {
            Text(
                stringResource(R.string.ride_weekly_note),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
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

/**
 * What the ride measured, only the numbers it has. The first two (the distance and the time on the
 * move) are the figures of the page, large, side by side; the others (all the time, the average
 * speed, the climb) are three columns in a card under them, a value over its caption. At a large
 * system font everything stands in one column.
 */
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
    val hero = tiles.take(2)
    val rest = tiles.drop(2)
    if (LocalDensity.current.fontScale > LargeFont) {
        ColaCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = Spacing.card, vertical = Spacing.s)) {
                tiles.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.s).semantics(
                            mergeDescendants = true
                        ) {}
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            figure(value, ColaTheme.textStyles.figure),
                            style = ColaTheme.textStyles.figure,
                        )
                    }
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        hero.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                VerticalDivider(
                    Modifier.padding(horizontal = Spacing.m),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    figure(value, ColaTheme.textStyles.numeral),
                    style = ColaTheme.textStyles.numeral,
                    color =
                        if (index == 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (rest.isNotEmpty()) {
        ColaCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = Spacing.m).height(IntrinsicSize.Min)) {
                rest.forEachIndexed { index, (label, value) ->
                    if (index > 0) {
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    Column(
                        Modifier.weight(1f).padding(horizontal = Spacing.m).semantics(
                            mergeDescendants = true
                        ) {},
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        Text(
                            figure(value, ColaTheme.textStyles.figure),
                            style = ColaTheme.textStyles.figure,
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A value with its unit set smaller: "26,5 км", "1 ч 33 мин", "16,9 км/ч". The digits keep the size
 * of [style]; letters and the slash of a unit are 60 % of it, the spaces keep the size.
 */
@Composable
private fun figure(value: String, style: TextStyle): AnnotatedString {
    val unit = SpanStyle(fontSize = style.fontSize * UnitScale, fontWeight = FontWeight.Medium)
    return buildAnnotatedString {
        value.forEachIndexed { i, c ->
            val small = c.isLetter() || c == '/'
            if (small) withStyle(unit) { append(c) } else append(c)
        }
    }
}

private const val UnitScale = 0.6f

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

/** From this width the map stands beside the page instead of opening on its own screen. */
private val WidePane = 840.dp

private val SketchHeight = 140.dp

/** From this font scale on, metric tiles stack. */
private const val LargeFont = 1.3f
