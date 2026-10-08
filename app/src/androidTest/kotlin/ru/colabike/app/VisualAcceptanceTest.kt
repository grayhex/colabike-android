package ru.colabike.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.colabike.app.rides.map.BasemapStyle
import ru.colabike.app.rides.map.MapLibreRoute
import ru.colabike.app.rides.map.MapLibreRouteMaps
import ru.colabike.app.rides.map.MapProbe
import ru.colabike.app.rides.map.RouteMaps
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.RideRoute

/**
 * Offline repositories and licensed local photographs. No smoke account, password or production
 * API.
 */
@OptIn(DelicateCoilApi::class)
@RunWith(AndroidJUnit4::class)
class VisualAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val arguments
        get() = InstrumentationRegistry.getArguments()

    private val recording
        get() = arguments.getString("recordVisual") == "true"

    private val dark
        get() = arguments.getString("visualTheme") == "dark"

    private val mapProbe = MapProbe()
    private lateinit var dependencies: FakeDependencies

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val client =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val name = request.url.pathSegments.last()
                    val bytes = runCatching {
                        assets.open("visual/photos/$name").use { it.readBytes() }
                    }
                        .getOrNull()
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(if (bytes == null) 404 else 200)
                        .message("Local visual fixture")
                        .body((bytes ?: ByteArray(0)).toResponseBody("image/jpeg".toMediaType()))
                        .build()
                }
                .build()
        SingletonImageLoader.setUnsafe(
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
        )
        val route = assets.open("visual/routes/river.gpx").use(VisualRoutes::parse)
        val maps =
            object : RouteMaps by MapLibreRouteMaps(null) {
                @Composable
                override fun Map(route: RideRoute, modifier: Modifier) {
                    MapLibreRoute(route, BasemapStyle.choose(null, dark), modifier, mapProbe)
                }
            }
        dependencies =
            realisticDependencies(
                route,
                maps,
                FakePhotoFiles(
                    directory = java.io.File(context.cacheDir, "visual-photos").apply { mkdirs() }
                ),
            )
        dependencies.catalog.site()
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle =
                    SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            )
        }
        compose.setContent {
            val configuration =
                Configuration(LocalConfiguration.current).apply {
                    setLocale(Locale.forLanguageTag("ru"))
                }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                ColaBikeTheme(darkTheme = dark) { AppShell(dependencies) }
            }
        }
        pause()
    }

    @After fun resetImages() = SingletonImageLoader.reset()

    private fun pause() {
        compose.waitForIdle()
        if (recording) Thread.sleep(1000)
    }

    private fun click(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
        pause()
    }

    private fun back() {
        compose.onNodeWithContentDescription("Назад").performClick()
        pause()
    }

    @Test
    fun rideMapAndFindCompany() {
        compose.section("Лента").performClick()
        pause()
        compose
            .onNodeWithContentDescription("Вдоль реки к утреннему кофе", substring = true)
            .performClick()
        pause()
        compose.onNodeWithTag("ride:map-preview").performClick()
        compose.waitUntil(60_000) { mapProbe.routeShown }
        pause()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        pause()
        compose.onNodeWithText("Весь маршрут").assertIsDisplayed().performClick()
        pause()
        back()
        back()
        compose.section("Покатушки").performClick()
        click("rides:intents")
        click("intent:${dependencies.intents.community.first().id}")
        compose.onNodeWithText("Договоритесь о встрече в чате.").assertExists()
        click("intent:write")
        compose.onNodeWithTag("chat:conversation").assertExists()
        pause()
    }

    @Test
    fun routeSurvivesNetworkLossAndBackground() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifiWasEnabled =
            Settings.Global.getInt(context.contentResolver, Settings.Global.WIFI_ON, 0) == 1
        val dataWasEnabled = Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
        compose.section("Лента").performClick()
        compose
            .onNodeWithContentDescription("Вдоль реки к утреннему кофе", substring = true)
            .performClick()
        compose.onNodeWithTag("ride:map-preview").performClick()
        compose.waitUntil(60_000) { mapProbe.routeShown }
        try {
            shell("svc wifi disable")
            shell("svc data disable")
            compose.waitUntil(20_000) { connectivity.activeNetwork == null }
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithText("Весь маршрут").assertIsDisplayed().performClick()
            back()
            mapProbe.routeShown = false
            compose.onNodeWithTag("ride:map-preview").performClick()
            compose.waitUntil(60_000) { mapProbe.routeShown }
            compose.onNodeWithText("Весь маршрут").assertIsDisplayed().performClick()
            back()
            compose.onNodeWithTag("ride:map-preview").assertExists()
        } finally {
            if (wifiWasEnabled) shell("svc wifi enable")
            if (dataWasEnabled) shell("svc data enable")
            if (wifiWasEnabled || dataWasEnabled)
                compose.waitUntil(30_000) { connectivity.activeNetwork != null }
        }
    }

    private fun shell(command: String) {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    @Test
    fun bicyclePhotosAndProfile() {
        compose.onNodeWithContentDescription("Ocean Blue", substring = true).performClick()
        pause()
        compose.onNodeWithContentDescription("Фото 1 из 2").performClick()
        compose.waitUntil(20_000) {
            compose
                .onAllNodes(hasText("Увеличить фото") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        pause()
        compose.onNodeWithText("Увеличить фото").performClick()
        pause()
        compose.onNodeWithText("Фото целиком").performClick()
        pause()
        compose.onNodeWithTag("gallery:photo:blue").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Фото 2 из 2").assertIsDisplayed()
        pause()
        compose.onNodeWithContentDescription("Закрыть").performClick()
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        pause()
        back()
        compose.section("Профиль").performClick()
        compose.onNodeWithText("Велосипеды в профиле").assertIsDisplayed()
        pause()
    }

    @Test
    fun addBicycleFromBuild() {
        dependencies.wizard.answers += { FakeBikeWizard.resolved(it.query) }
        click("bikes:add")
        compose.onNodeWithTag("wizard:line").performTextInput("Giant Contend AR 1 2024")
        Espresso.closeSoftKeyboard()
        pause()
        click("wizard:search")
        compose.onNodeWithText("Шаг 2 из 3 · Комплектация").assertIsDisplayed()
        pause()
        click("wizard:next")
        click("bike-editor:category")
        compose.onNode(hasText("MTB") and hasAnyAncestor(isPopup())).performClick()
        pause()
        click("wizard:save")
        assertEquals(1, dependencies.wizard.made.size)
        compose.onNodeWithTag("bike:author").assertExists()
        pause()
    }
}
