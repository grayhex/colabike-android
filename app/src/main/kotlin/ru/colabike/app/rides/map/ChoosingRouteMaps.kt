package ru.colabike.app.rides.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import ru.colabike.app.R
import ru.colabike.app.settings.AppSettings
import ru.colabike.app.settings.MapProvider
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideAreaPoint
import ru.colabike.core.model.RideRoute

/**
 * The map the person chose, with the other one behind it. OpenStreetMap ([osm]) is the default and
 * the way back; the Yandex map ([yandex], null in a build without the owner's key) is shown while
 * it is chosen and while it works. When it does not (no SDK, no network, a key the service refuses)
 * the same screen shows OpenStreetMap with a short note and two ways out: try Yandex again, or keep
 * OpenStreetMap. A route never meets an empty screen. The choice is read live: it changes the map
 * on the screen at once, without a restart.
 */
internal class ChoosingRouteMaps(
    private val settings: AppSettings,
    private val osm: RouteMaps,
    private val yandex: YandexMaps?,
) : RouteMaps {
    override val hasBasemap: Boolean = true
    override val offersYandex: Boolean = yandex != null

    /**
     * Why Yandex was given up on in this run of the app; null while it is trusted. Kept while the
     * map is closed and opened again, so that a person with no network is not made to wait out the
     * Yandex map at every route; forgotten when they choose a map in the settings or tap "retry".
     */
    private val failure = MutableStateFlow<YandexFailure?>(null)

    override fun forgetFailure() {
        failure.value = null
    }

    @Composable
    override fun Map(route: RideRoute, modifier: Modifier) {
        ChosenMap(
            modifier,
            osmContent = { osm.Map(route, it) },
            yandexContent = { maps, mod, failed -> maps.Map(route, mod, failed) },
        )
    }

    // Previews use the lightweight OSM snapshot in either provider mode; its attribution is
    // embedded. The full interactive map still honours the person's chosen provider.
    @Composable
    override fun Preview(route: RideRoute, modifier: Modifier) = osm.Preview(route, modifier)

    @Composable
    override fun Area(point: RideAreaPoint, onCenter: (GeoPoint) -> Unit, modifier: Modifier) {
        ChosenMap(
            modifier,
            osmContent = { osm.Area(point, onCenter, it) },
            yandexContent = { maps, mod, failed -> maps.Area(point, onCenter, mod, failed) },
        )
    }

    @Composable
    private fun ChosenMap(
        modifier: Modifier,
        osmContent: @Composable (Modifier) -> Unit,
        yandexContent: @Composable (YandexMaps, Modifier, (YandexFailure) -> Unit) -> Unit,
    ) {
        val provider by settings.mapProvider.collectAsStateWithLifecycle()
        val failed by failure.collectAsStateWithLifecycle()
        val maps = yandex
        val wantsYandex = maps != null && provider == MapProvider.Yandex
        Column(modifier) {
            if (wantsYandex && failed != null) {
                YandexFallbackNotice(
                    onRetry = { failure.value = null },
                    onKeepOpenStreetMap = { settings.setMapProvider(MapProvider.OpenStreetMap) },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.s),
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (maps != null && wantsYandex && failed == null) {
                    yandexContent(maps, Modifier.fillMaxSize()) { failure.value = it }
                } else {
                    osmContent(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** What the person is told when the map they chose is not there, and what they can do about it. */
@Composable
internal fun YandexFallbackNotice(
    onRetry: () -> Unit,
    onKeepOpenStreetMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.testTag("map:yandex-fallback").semantics {
            liveRegion = LiveRegionMode.Polite
        },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = Spacing.xs,
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                stringResource(R.string.map_yandex_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.map_yandex_retry)) }
                TextButton(onClick = onKeepOpenStreetMap) {
                    Text(stringResource(R.string.map_yandex_keep_osm))
                }
            }
        }
    }
}
