package ru.colabike.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import ru.colabike.app.login.LoginScreen
import ru.colabike.app.login.LoginUiState
import ru.colabike.app.ui.AppShell
import ru.colabike.app.ui.UiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page

/** Every screen of the app, as AGENTS.md requires it in screenshots. */
enum class Screen(val file: String) {
    Login("login"),
    Bikes("bikes"),
    BikeDetail("bike_detail"),
    Profile("profile"),

    /** What a guest sees on the profile tab, and the rest of the profile below the fold. */
    ProfileGuest("profile_guest"),
    ProfileMore("profile_more"),
    Devices("devices"),
    About("about"),
}

enum class Look(val dark: Boolean, val fontScale: Float, val file: String) {
    Light(dark = false, fontScale = 1f, file = "light"),
    Dark(dark = true, fontScale = 1f, file = "dark"),
    LargeText(dark = false, fontScale = 2f, file = "font200"),
}

@OptIn(ExperimentalCoilApi::class)
private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * Renders [screen] with fake data and compares it with
 * src/test/screenshots/<screen>_<window>_<look>.png. Ripples are off: the platform draws them on
 * its own clock, so a click would make captures differ between runs.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun ComposeContentTestRule.captureScreen(screen: Screen, window: String, look: Look) {
    setContent {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides photos,
            LocalRippleConfiguration provides null,
            LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
        ) {
            ColaBikeTheme(darkTheme = look.dark) {
                when (screen) {
                    Screen.Login ->
                        LoginScreen(
                            LoginUiState(
                                email = "rider@example.test",
                                password = "password",
                                error = UiText.Res(R.string.login_wrong_credentials),
                                yandexEnabled = true,
                            ),
                            onEmailChange = {},
                            onPasswordChange = {},
                            onTogglePassword = {},
                            onSubmit = {},
                            onYandex = {},
                            onBrowseAsGuest = {},
                        )
                    Screen.ProfileGuest ->
                        AppShell(
                            FakeDependencies(
                                auth = FakeAuth(initial = AuthState.SignedOut),
                                settings = FakeSettings(guest = true),
                            )
                        )
                    else ->
                        AppShell(
                            FakeDependencies(
                                bikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1")))
                            )
                        )
                }
            }
        }
    }
    waitForIdle()
    when (screen) {
        Screen.BikeDetail ->
            onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        Screen.Profile,
        Screen.ProfileGuest -> onNodeWithText("Профиль").performClick()
        Screen.ProfileMore -> {
            onNodeWithText("Профиль").performClick()
            onNodeWithText("О приложении").performScrollTo()
        }
        Screen.Devices -> {
            onNodeWithText("Профиль").performClick()
            onNodeWithText("Устройства и входы").performScrollTo().performClick()
        }
        Screen.About -> {
            onNodeWithText("Профиль").performClick()
            onNodeWithText("О приложении").performScrollTo().performClick()
        }
        else -> Unit
    }
    mainClock.advanceTimeBy(3_000)
    waitForIdle()
    captureWhenDrawn("src/test/screenshots/${screen.file}_${window}_${look.file}.png")
}
