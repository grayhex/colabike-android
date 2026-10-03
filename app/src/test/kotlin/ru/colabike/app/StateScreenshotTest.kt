package ru.colabike.app

import android.content.Context
import android.provider.Settings
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.bikes.BikeDetailScreen
import ru.colabike.app.bikes.BikeDetailUiState
import ru.colabike.app.bikes.BikesScreen
import ru.colabike.app.bikes.BikesUiState
import ru.colabike.app.devices.DevicesScreen
import ru.colabike.app.devices.DevicesUiState
import ru.colabike.app.profile.ProfileScreen
import ru.colabike.app.profile.ProfileUiState
import ru.colabike.app.settings.ThemeMode
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeScope

/** The states a screen has besides its content (DESIGN.md): loading, empty, error, an odd bike. */
enum class ScreenState(val file: String) {
    BikesLoading("bikes_loading"),
    BikesEmptyMine("bikes_empty_mine"),
    BikesError("bikes_error"),
    BikeDetailError("bike_detail_error"),
    BikeDetailBare("bike_detail_bare"),
    BikeDetailPrivateGuest("bike_detail_private_guest"),
    DevicesLoading("devices_loading"),
    DevicesError("devices_error"),
    ProfileFailed("profile_failed"),
}

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

private val bareBike =
    BikeDetail(
        summary =
            PreviewData.bikeWithoutPhoto.copy(
                name = "Старый шоссейник",
                year = 1992,
                isPublic = false,
                author = PreviewData.rider,
            ),
        description = "",
        color = "Графит",
        size = "L",
        weightKg = null,
        mileageKm = 0,
        photos = emptyList(),
        components =
            listOf(
                BikeComponent("c1", "build", "Рама", "Reynolds 531, сталь", ""),
                BikeComponent("c2", "build", "Вилка", "Жёсткая, сталь", ""),
                BikeComponent("c3", "accessories", "Звонок", "Латунный", ""),
            ),
    )

/** Phone width, both themes and 200 % text. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class StateScreenshotTest(private val state: ScreenState, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun capture() {
        // No pulsing skeletons: the picture must not depend on the virtual clock.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) { ColaCanvas { Content(state) } }
            }
        }
        // A capture right after waitForIdle() can come out as the bare window, before the first
        // frame is drawn (seen on CI as an all-white image); the clock lets that frame happen.
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        compose.captureWhenDrawn(
            "src/test/screenshots/state_${state.file}_compact_${look.file}.png"
        )
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            ScreenState.entries.flatMap { state -> Look.entries.map { arrayOf(state, it) } }
    }
}

@Composable
private fun Content(state: ScreenState) {
    when (state) {
        ScreenState.BikesLoading -> BikesWith(BikesUiState(loading = true))
        ScreenState.BikesEmptyMine ->
            BikesWith(BikesUiState(scope = BikeScope.Mine, loading = false, bikes = emptyList()))
        ScreenState.BikesError ->
            BikesWith(BikesUiState(loading = false, error = UiText.Res(R.string.error_offline)))
        ScreenState.BikeDetailError ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found)),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
        ScreenState.DevicesLoading -> DevicesWith(DevicesUiState.Loading)
        ScreenState.DevicesError ->
            DevicesWith(DevicesUiState.Failed(UiText.Res(R.string.error_offline)))
        ScreenState.ProfileFailed ->
            ProfileScreen(
                ProfileUiState.Failed(UiText.Res(R.string.error_offline)),
                themeMode = ThemeMode.System,
                onThemeMode = {},
                onRetry = {},
                onSignOut = {},
                onSignIn = {},
                onRegister = {},
                onOpenDevices = {},
                onManageOnWeb = {},
                onOpenAbout = {},
            )
        ScreenState.BikeDetailPrivateGuest ->
            BikeDetailScreen(
                BikeDetailUiState.Failed(UiText.Res(R.string.error_not_found), notFound = true),
                showBack = true,
                onBack = {},
                onRetry = {},
                onSignIn = {},
            )
        ScreenState.BikeDetailBare ->
            BikeDetailScreen(
                BikeDetailUiState.Loaded(bareBike),
                showBack = true,
                onBack = {},
                onRetry = {},
            )
    }
}

@Composable
private fun DevicesWith(state: DevicesUiState) =
    DevicesScreen(
        state = state,
        now = Instant.parse("2026-10-03T20:00:00Z"),
        onBack = {},
        onRetry = {},
        onEnd = {},
        onConfirmEnd = {},
        onDismissQuestion = {},
    )

@Composable
private fun BikesWith(state: BikesUiState) =
    BikesScreen(
        state = state,
        onScope = {},
        onRefresh = {},
        onRetry = {},
        onLoadMore = {},
        onOpen = {},
    )
