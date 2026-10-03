package ru.colabike.core.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import ru.colabike.core.designsystem.theme.ColaBikeTheme

/** Photos in screenshots are flat colour: no network, the same pixels on every machine. */
@OptIn(ExperimentalCoilApi::class)
private val fakePhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * Renders [content] in the ColaBike theme and compares it with src/test/screenshots/[name].png
 * (`verifyRoborazziDebug`; re-record with `recordRoborazziDebug` after a deliberate change).
 */
@OptIn(ExperimentalCoilApi::class)
fun ComposeContentTestRule.snapshot(
    name: String,
    dark: Boolean,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    // No pulsing skeletons: the picture must not depend on the virtual clock.
    Settings.Global.putFloat(
        ApplicationProvider.getApplicationContext<Context>().contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        0f,
    )
    setContent {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides fakePhotos,
            LocalDensity provides Density(LocalDensity.current.density, fontScale),
        ) {
            ColaBikeTheme(darkTheme = dark) {
                Surface { Box(Modifier.padding(16.dp)) { content() } }
            }
        }
    }
    // A capture right after setContent can come out as the bare window, before the first frame is
    // drawn (seen on CI as an all-white image); the clock lets that frame happen.
    waitForIdle()
    mainClock.advanceTimeBy(3_000)
    waitForIdle()
    onRoot().captureRoboImage("src/test/screenshots/$name.png")
}
