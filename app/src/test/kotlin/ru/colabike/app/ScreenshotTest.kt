package ru.colabike.app

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.login.LoginScreen
import ru.colabike.app.login.LoginUiState
import ru.colabike.app.ui.AppShell
import ru.colabike.app.ui.UiText
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Page

/**
 * Screens with fake data: compact (phone) and expanded (tablet, foldable open) windows, both
 * themes. Golden images live in src/test/screenshots (verifyRoborazziDebug).
 */
@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val photos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            // Ripples are drawn by the platform on its own clock: off, so captures are stable.
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
            ) {
                ColaBikeTheme(darkTheme = dark) { content() }
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) =
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")

    // Let navigation and loading finish after a click before the capture.
    private fun settle() {
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
    }

    private val manyBikes = FakeBikes(mapOf(null to Page(bikes(0, 6), "c1")))

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
    fun loginCompactLight() {
        shot("login", dark = false) {
            LoginScreen(
                LoginUiState(
                    email = "rider@example.test",
                    password = "secret",
                    error = UiText.Res(R.string.login_wrong_credentials),
                    yandexEnabled = true,
                ),
                {},
                {},
                {},
                {},
                {},
            )
        }
        capture("login_compact_light")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-night-xhdpi")
    fun loginCompactDark() {
        shot("login", dark = true) {
            LoginScreen(LoginUiState(yandexEnabled = true), {}, {}, {}, {}, {})
        }
        capture("login_compact_dark")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
    fun bikesCompactLight() {
        shot("bikes", dark = false) { AppShell(FakeDependencies(bikes = manyBikes)) }
        capture("bikes_compact_light")
    }

    @Test
    @Config(qualifiers = "ru-w360dp-h800dp-night-xhdpi")
    fun bikeDetailCompactDark() {
        shot("detail", dark = true) { AppShell(FakeDependencies(bikes = manyBikes)) }
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        settle()
        capture("bike_detail_compact_dark")
    }

    @Test
    @Config(qualifiers = "ru-w1280dp-h800dp-mdpi")
    fun listDetailExpandedLight() {
        shot("expanded", dark = false) { AppShell(FakeDependencies(bikes = manyBikes)) }
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        settle()
        capture("list_detail_expanded_light")
    }

    @Test
    @Config(qualifiers = "ru-w1280dp-h800dp-night-mdpi")
    fun listDetailExpandedDark() {
        shot("expanded", dark = true) { AppShell(FakeDependencies(bikes = manyBikes)) }
        capture("list_detail_expanded_dark_placeholder")
    }
}
