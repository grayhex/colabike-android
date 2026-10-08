package ru.colabike.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.rides.map.ChoosingRouteMaps
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.rides.map.YandexFailure
import ru.colabike.app.rides.map.YandexMaps
import ru.colabike.app.rides.map.areaOutline
import ru.colabike.app.settings.MapProvider
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.GeoPoint
import ru.colabike.core.model.RideAreaPoint
import ru.colabike.core.model.RideRoute

private val route =
    RideRoute(
        listOf(
            listOf(GeoPoint(55.750, 37.610), GeoPoint(55.760, 37.625)),
            listOf(GeoPoint(55.765, 37.640), GeoPoint(55.770, 37.650)),
        )
    )

/** A map that only says which one it is, and with what route it was asked. */
private class NamedMaps(private val name: String) : RouteMaps {
    override val hasBasemap: Boolean = true
    val routes = mutableListOf<RideRoute>()

    @Composable
    override fun Map(route: RideRoute, modifier: Modifier) {
        routes += route
        Box(modifier.testTag("map:$name")) { Text(name) }
    }
}

/** The Yandex map as the screen meets it: it works, or it says it cannot ([failure]). */
private class FakeYandex(var failure: YandexFailure? = null) : YandexMaps {
    val routes = mutableListOf<RideRoute>()

    @Composable
    override fun Map(route: RideRoute, modifier: Modifier, onUnavailable: (YandexFailure) -> Unit) {
        routes += route
        Box(modifier.testTag("map:yandex")) { Text("yandex") }
        val failing = failure
        if (failing != null) LaunchedEffect(Unit) { onUnavailable(failing) }
    }
}

/** Which map lies under a route, and what the person is told when the one they chose is gone. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class ChoosingRouteMapsTest {
    @get:Rule val compose = createComposeRule()

    private val osm = NamedMaps("osm")

    private val shown = mutableStateOf(true)

    private fun show(maps: RouteMaps) {
        compose.setContent {
            ColaBikeTheme {
                Box(Modifier.fillMaxSize()) {
                    if (shown.value) maps.Map(route, Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `area uses the selected provider and falls back with the same geometry`() {
        val point = RideAreaPoint(37.6, 55.73, 5000)
        val yandex = FakeYandex(YandexFailure.NotLoaded)
        val maps = ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex)
        compose.setContent { ColaBikeTheme { maps.Area(point, {}, Modifier.fillMaxSize()) } }
        compose.waitForIdle()
        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex-fallback").assertIsDisplayed()
        assertThat(yandex.routes.first()).isEqualTo(areaOutline(point))
        assertThat(osm.routes.last()).isEqualTo(areaOutline(point))
    }

    @Test
    fun `OpenStreetMap is what lies under a route until another map is chosen`() {
        val yandex = FakeYandex()
        show(ChoosingRouteMaps(FakeSettings(), osm, yandex))

        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex").assertDoesNotExist()
        assertThat(yandex.routes).isEmpty()
    }

    @Test
    fun `the Yandex map is shown while it is chosen and while it works, with the same route`() {
        val yandex = FakeYandex()
        show(ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex))

        compose.onNodeWithTag("map:yandex").assertIsDisplayed()
        compose.onNodeWithTag("map:osm").assertDoesNotExist()
        compose.onNodeWithTag("map:yandex-fallback").assertDoesNotExist()
        // The cuts of the route are the server's: no map joins or drops a line.
        assertThat(yandex.routes.last()).isSameInstanceAs(route)
    }

    @Test
    fun `the choice changes the map on the screen at once, in both directions`() {
        val settings = FakeSettings()
        show(ChoosingRouteMaps(settings, osm, FakeYandex()))

        settings.setMapProvider(MapProvider.Yandex)
        compose.waitForIdle()
        compose.onNodeWithTag("map:yandex").assertIsDisplayed()

        settings.setMapProvider(MapProvider.OpenStreetMap)
        compose.waitForIdle()
        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex").assertDoesNotExist()
    }

    @Test
    fun `a Yandex map that cannot be had gives the screen to OpenStreetMap and says so`() {
        val yandex = FakeYandex(failure = YandexFailure.NotLoaded)
        show(ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex))

        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex").assertDoesNotExist()
        compose
            .onNodeWithText("Яндекс Карты недоступны, показана OpenStreetMap.")
            .assertIsDisplayed()
        assertThat(osm.routes.last()).isSameInstanceAs(route)
    }

    @Test
    fun `trying again asks Yandex once more, and the note goes when it answers`() {
        val yandex = FakeYandex(failure = YandexFailure.SdkFailed)
        show(ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex))
        compose.onNodeWithTag("map:yandex-fallback").assertIsDisplayed()

        yandex.failure = null
        compose.onNodeWithText("Повторить").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("map:yandex").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex-fallback").assertDoesNotExist()
    }

    @Test
    fun `a Yandex that failed is not asked again at every route, until the choice is made anew`() {
        val yandex = FakeYandex(failure = YandexFailure.NotLoaded)
        val maps = ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex)
        show(maps)
        val asked = yandex.routes.size

        // The page closes and another route's map opens: Yandex is not asked, the note is there.
        compose.runOnUiThread { shown.value = false }
        compose.waitForIdle()
        compose.runOnUiThread { shown.value = true }
        compose.waitForIdle()
        assertThat(yandex.routes.size).isEqualTo(asked)
        compose.onNodeWithTag("map:yandex-fallback").assertIsDisplayed()

        // The person chooses a map in the settings anew, and Yandex is tried again.
        yandex.failure = null
        compose.runOnUiThread { maps.forgetFailure() }
        compose.waitForIdle()
        compose.onNodeWithTag("map:yandex").assertIsDisplayed()
    }

    @Test
    fun `keeping OpenStreetMap makes it the choice and stops asking Yandex`() {
        val settings = FakeSettings(map = MapProvider.Yandex)
        val yandex = FakeYandex(failure = YandexFailure.NotLoaded)
        show(ChoosingRouteMaps(settings, osm, yandex))

        compose.onNodeWithText("Оставить OpenStreetMap").performClick()
        compose.waitForIdle()

        assertThat(settings.mapProvider.value).isEqualTo(MapProvider.OpenStreetMap)
        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex-fallback").assertDoesNotExist()
    }

    @Test
    fun `a build without the owner's key shows OpenStreetMap quietly, whatever was chosen`() {
        val maps = ChoosingRouteMaps(FakeSettings(map = MapProvider.Yandex), osm, yandex = null)
        show(maps)

        assertThat(maps.offersYandex).isFalse()
        compose.onNodeWithTag("map:osm").assertIsDisplayed()
        compose.onNodeWithTag("map:yandex-fallback").assertDoesNotExist()
    }
}

/** The setting: two maps to choose between, only where the second one can be had. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class MapSettingTest {
    @get:Rule val compose = createComposeRule()

    private class OffersYandex(override val offersYandex: Boolean) : RouteMaps {
        override val hasBasemap: Boolean = true
        var forgotten = 0

        override fun forgetFailure() {
            forgotten++
        }

        @Composable override fun Map(route: RideRoute, modifier: Modifier) = Unit
    }

    private fun profile(
        offersYandex: Boolean,
        settings: FakeSettings = FakeSettings(),
        maps: OffersYandex = OffersYandex(offersYandex),
    ) {
        val dependencies = FakeDependencies(settings = settings, maps = maps)
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
        compose.section("Профиль").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `a build with no Yandex key has no setting to show, there is one map`() {
        profile(offersYandex = false)

        compose.onNodeWithText("Яндекс Карты").assertDoesNotExist()
        compose.onNodeWithText("OpenStreetMap — по умолчанию").assertDoesNotExist()
    }

    @Test
    fun `with the key both maps are on offer, OpenStreetMap chosen by default`() {
        profile(offersYandex = true)

        compose.onNodeWithText("OpenStreetMap — по умолчанию").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Яндекс Карты").performScrollTo().assertIsNotSelected()
    }

    @Test
    fun `choosing Yandex is kept in the settings, and the way back is one tap`() {
        val settings = FakeSettings()
        val maps = OffersYandex(true)
        profile(offersYandex = true, settings = settings, maps = maps)

        compose.onNodeWithText("Яндекс Карты").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.mapProvider.value).isEqualTo(MapProvider.Yandex)
        // A map chosen anew is a map tried anew.
        assertThat(maps.forgotten).isEqualTo(1)
        compose.onNodeWithText("Яндекс Карты").assertIsSelected()

        compose.onNodeWithText("OpenStreetMap — по умолчанию").performScrollTo().performClick()
        compose.waitForIdle()
        assertThat(settings.mapProvider.value).isEqualTo(MapProvider.OpenStreetMap)
    }
}
