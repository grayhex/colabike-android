package ru.colabike.app

import android.graphics.BitmapFactory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.theme.ColaBikeTheme

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveScenesTest(private val scene: Scene, private val dark: Boolean) {
    @get:Rule val compose = createComposeRule()

    enum class Scene {
        Catalog,
        Bike,
        Market,
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
    private fun capture(width: Int) {
        val dependencies = realisticDependencies()
        val images = mutableMapOf<String, coil3.Image>()
        val photos = AsyncImagePreviewHandler { request ->
            val name = request.data.toString().substringAfterLast('/')
            images.getOrPut(name) {
                checkNotNull(javaClass.getResourceAsStream("/visual/photos/$name"))
                    .use { BitmapFactory.decodeStream(it) }
                    .asImage()
            }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
            ) {
                ColaBikeTheme(darkTheme = dark) { AppShell(dependencies) }
            }
        }
        compose.settle()
        when (scene) {
            Scene.Catalog -> Unit
            Scene.Market -> compose.section("Рынок").performClick()
            Scene.Bike ->
                compose.onNodeWithContentDescription("Ocean Blue", substring = true).performClick()
        }
        compose.settle()
        val theme = if (dark) "dark" else "light"
        compose.captureWhenDrawn(
            "src/test/screenshots/adaptive_${scene.name.lowercase()}_${width}_$theme.png"
        )
    }

    @Test @Config(qualifiers = "ru-w600dp-h900dp-mdpi") fun medium() = capture(600)

    @Test @Config(qualifiers = "ru-w840dp-h900dp-mdpi") fun expanded() = capture(840)

    @Test @Config(qualifiers = "ru-w1200dp-h900dp-mdpi") fun wide() = capture(1200)

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_dark{1}")
        fun cases(): List<Array<Any>> =
            Scene.entries.flatMap { scene -> listOf(false, true).map { arrayOf(scene, it) } }
    }
}
