package ru.colabike.app.intents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import kotlin.math.roundToInt
import ru.colabike.app.R
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideAreaPoint

@Composable
internal fun IntentAreaContent(
    draft: IntentAreaDraft,
    maps: RouteMaps,
    actions: IntentEditorActions,
) {
    var coordinates by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(horizontal = Spacing.screen, vertical = Spacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Button(
                    onClick = actions.onConfirmArea,
                    enabled = draft.canConfirm && !coordinates,
                    modifier =
                        Modifier.widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .testTag("intent-area:confirm"),
                ) {
                    Text(stringResource(R.string.intent_area_confirm))
                }
            }
        },
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.intent_area_choose),
                onBack = actions.onCancelArea,
            )
        },
    ) { insets ->
        LazyColumn(
            Modifier.padding(insets)
                .consumeWindowInsets(insets)
                .fillMaxSize()
                .testTag("intent-area:picker"),
            contentPadding = PaddingValues(Spacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Column(
                    Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    Text(
                        stringResource(R.string.intent_area_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = {
                            coordinates = false
                            actions.onLocateArea()
                        },
                        enabled = !draft.locating,
                        modifier = Modifier.testTag("intent-area:locate"),
                    ) {
                        Text(
                            stringResource(
                                if (draft.locating) R.string.intent_area_locating
                                else R.string.intent_area_locate
                            )
                        )
                    }
                    if (draft.locating) {
                        TextButton(onClick = actions.onCancelLocation) {
                            Text(stringResource(R.string.intent_area_cancel_location))
                        }
                    }
                    draft.problem?.let {
                        Text(
                            it.resolve(),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    draft.point?.let { point ->
                        maps.Area(
                            point,
                            actions.onAreaCenter,
                            Modifier.fillMaxWidth()
                                .height(240.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                        Text(
                            stringResource(R.string.intent_area_map_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.intent_area_center,
                                point.latitude,
                                point.longitude,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            stringResource(
                                R.string.intent_area_radius,
                                areaRadiusKm(point.radiusM),
                            ),
                            modifier = Modifier.semantics { heading() },
                        )
                        val radiusLabel =
                            stringResource(R.string.intent_area_radius, areaRadiusKm(point.radiusM))
                        val radiusDescription = stringResource(R.string.nearby_radius_label)
                        Slider(
                            value = point.radiusM.toFloat(),
                            onValueChange = {
                                actions.onAreaRadius((it / 1000).roundToInt() * 1000)
                            },
                            valueRange = 1000f..100000f,
                            enabled = !draft.locating,
                            modifier =
                                Modifier.testTag("intent-area:radius").semantics {
                                    contentDescription = radiusDescription
                                    stateDescription = radiusLabel
                                },
                        )
                    }
                    TextButton(
                        onClick = { coordinates = true },
                        enabled = !draft.locating,
                        modifier = Modifier.testTag("intent-area:coordinates"),
                    ) {
                        Text(stringResource(R.string.intent_area_coordinates))
                    }
                    if (coordinates) {
                        CoordinatesFields(
                            draft.point,
                            onDismiss = { coordinates = false },
                            onConfirm = {
                                actions.onAreaCenter(it)
                                coordinates = false
                            },
                        )
                    }
                    val nameTooLong =
                        draft.label.trim().length >
                            ru.colabike.core.model.IntentDraft.MAX_AREA_LABEL
                    OutlinedTextField(
                        value = draft.label,
                        isError = nameTooLong,
                        onValueChange = actions.onAreaDraftLabel,
                        label = { Text(stringResource(R.string.intent_area_label)) },
                        supportingText = {
                            Text(
                                stringResource(
                                    if (nameTooLong) R.string.intent_problem_area_long
                                    else R.string.intent_area_confirm_hint
                                )
                            )
                        },
                        colors = colaTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("intent-area:label"),
                    )
                    TextButton(
                        onClick = actions.onCancelArea,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.intent_area_cancel))
                    }
                }
            }
        }
    }
}

/** Keyboard/TalkBack alternative to tapping the map; incomplete input never replaces its centre. */
@Composable
private fun CoordinatesFields(
    point: RideAreaPoint?,
    onDismiss: () -> Unit,
    onConfirm: (GeoPoint) -> Unit,
) {
    var latitude by rememberSaveable { mutableStateOf(point?.latitude?.toString().orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf(point?.longitude?.toString().orEmpty()) }
    val lat =
        latitude.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 }
    val lon =
        longitude.replace(',', '.').toDoubleOrNull()?.takeIf {
            it.isFinite() && it in -180.0..180.0
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(stringResource(R.string.intent_area_latitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = colaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("intent-area:latitude"),
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(stringResource(R.string.intent_area_longitude)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            colors = colaTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("intent-area:longitude"),
        )
        TextButton(
            onClick = { if (lat != null && lon != null) onConfirm(GeoPoint(lat, lon)) },
            enabled = lat != null && lon != null,
            modifier = Modifier.testTag("intent-area:apply-coordinates"),
        ) {
            Text(stringResource(R.string.intent_area_apply_center))
        }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.intent_area_cancel)) }
    }
}

/** Preserve radii from the website down to the metre; the slider only snaps new user input. */
@Composable
internal fun areaRadiusKm(radiusM: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(radiusM, locale) {
        NumberFormat.getNumberInstance(locale)
            .apply { maximumFractionDigits = 3 }
            .format(radiusM / 1000.0)
    }
}
