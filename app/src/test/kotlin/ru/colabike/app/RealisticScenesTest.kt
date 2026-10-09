package ru.colabike.app

import android.graphics.BitmapFactory
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
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

/** Real licensed photographs, invented people and local repositories. No production requests. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RealisticScenesTest(private val scene: Scene, private val look: Look) {
    @get:Rule val compose = createComposeRule()

    enum class Scene {
        Feed,
        Catalog,
        Bike,
        Ride,
        Together,
        Profile,
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
    private fun capture(window: String) {
        val dependencies = realisticDependencies()
        val images = mutableMapOf<String, coil3.Image>()
        val photos = AsyncImagePreviewHandler { request ->
            val name = request.data.toString().substringAfterLast('/')
            images.getOrPut(name) {
                val stream =
                    javaClass.getResourceAsStream("/visual/photos/$name")
                        ?: error("Unregistered visual fixture: $name")
                stream.use { BitmapFactory.decodeStream(it) }.asImage()
            }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides photos,
                LocalRippleConfiguration provides null,
                LocalDensity provides Density(LocalDensity.current.density, look.fontScale),
            ) {
                ColaBikeTheme(darkTheme = look.dark) { AppShell(dependencies) }
            }
        }
        compose.settle()
        when (scene) {
            Scene.Catalog -> Unit
            Scene.Bike ->
                compose.onNodeWithContentDescription("Ocean Blue", substring = true).performClick()
            Scene.Feed -> compose.section("Лента").performClick()
            Scene.Profile -> compose.section("Профиль").performClick()
            Scene.Ride -> {
                compose.section("Покатушки").performClick()
                compose.onNodeWithText("Состоявшиеся").performClick()
                compose
                    .onNodeWithContentDescription("Вдоль реки к утреннему кофе", substring = true)
                    .performClick()
            }
            Scene.Together -> {
                compose.section("Покатушки").performClick()
                compose.onNodeWithTag("rides:intents").performClick()
            }
        }
        compose.settle()
        compose.captureWhenDrawn(
            "src/test/screenshots/realistic_${scene.name.lowercase()}_${window}_${look.file}.png"
        )
    }

    @Test @Config(qualifiers = "ru-w412dp-h915dp-xhdpi") fun phone() = capture("phone")

    @Test @Config(qualifiers = "ru-w1200dp-h900dp-mdpi") fun expanded() = capture("expanded")

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
        fun cases(): List<Array<Any>> =
            Scene.entries.flatMap { scene -> Look.entries.map { arrayOf(scene, it) } }
    }
}
