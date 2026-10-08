package ru.colabike.app

import android.graphics.BitmapFactory
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.bikes.PhotoViewer
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.Photo

/** Real photos retain their whole frame; viewer chrome is legible in either surrounding theme. */
@OptIn(ExperimentalCoilApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryVisualTest(private val photo: String, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    private fun capture(window: String) {
        // Dialog owns a separate Android window; set its real configuration, not only the caller's
        // LocalDensity.
        org.robolectric.RuntimeEnvironment.setFontScale(look.fontScale)
        val bitmap =
            javaClass.getResourceAsStream("/visual/photos/$photo.jpg")!!.use {
                BitmapFactory.decodeStream(it).asImage()
            }
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { bitmap },
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    PhotoViewer(listOf(Photo(photo, "https://example.test/$photo.jpg")), 0, {})
                }
            }
        }
        compose.settle()
        compose.onNodeWithText("Увеличить фото").assertIsDisplayed()
        compose
            .onNodeWithTag("gallery:viewer")
            .captureRoboImage("src/test/screenshots/gallery_${photo}_${window}_${look.file}.png")
    }

    @Test @Config(qualifiers = "ru-w360dp-h800dp-xhdpi") fun compact() = capture("compact")

    @Test @Config(qualifiers = "ru-w1200dp-h900dp-mdpi") fun expanded() = capture("expanded")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            listOf("gravel-blue", "gravel-dark", "bike-portrait").flatMap { photo ->
                Look.entries.map { arrayOf(photo, it) }
            }
    }
}
