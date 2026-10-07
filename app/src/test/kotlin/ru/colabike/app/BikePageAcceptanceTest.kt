package ru.colabike.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.AppShell
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.Page

@OptIn(ExperimentalCoilApi::class)
private val pagePhotos = AsyncImagePreviewHandler { ColorImage(Color(0xFF7A8CA3).toArgb()) }

/**
 * The acceptance of the bike page (issues #42 and #56): on a full portrait screen of 412 × 915 dp
 * at the system font of 1.0, with the system bars, a bike with short facts and one entry of the
 * journal, the first screen holds, without a scroll, the photo whole, the name once with the kind
 * and the year, the author with the like and the share, the shut build, the latest entry of the
 * journal and the start of the discussion (its heading, the invitation and the box to write in).
 * The three states of the page are in the matrix of screenshots (`bike_detail_*`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w412dp-h915dp-xhdpi")
class BikePageAcceptanceTest(private val look: Look) {
    @get:Rule val compose = createComposeRule()

    private val bike =
        PreviewData.bike.copy(
            id = BikeId("b0"),
            name = "Canyon Grail CF SLX 8 AXS (2026)",
            comments = 0,
        )

    /** A bike with short facts, as the mockup has it: a line of description, two facts. */
    private val detail: BikeDetail =
        PreviewData.bikeDetail.copy(
            summary = bike,
            description = "Надёжный горный велосипед.",
            mileageKm = 0,
            color = "",
            priceRub = null,
        )

    private fun open(comments: FakeComments) {
        val bikes =
            FakeBikes(mapOf(null to Page(listOf(bike), null))).also {
                it.details = mapOf("b0" to detail)
            }
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalRippleConfiguration provides null,
                LocalAsyncImagePreviewHandler provides pagePhotos,
                LocalDensity provides Density(LocalDensity.current.density, 1f),
            ) {
                ColaBikeTheme(darkTheme = look.dark) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(top = StatusBar, bottom = GestureStrip)
                    ) {
                        AppShell(FakeDependencies(bikes = bikes, comments = comments))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Canyon Grail", substring = true).performClick()
        compose.waitForIdle()
        compose.settle()
    }

    private val bottom = 915.dp - GestureStrip

    private fun bottomOfTag(tag: String) =
        compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom

    private fun bottomOfText(text: String) =
        compose.onNodeWithText(text, substring = true).getUnclippedBoundsInRoot().bottom

    @Test
    fun `the shut build, the journal and the start of the discussion are on the first screen`() {
        open(FakeComments())
        // The picture first: when a number is off, the picture says where.
        compose.captureWhenDrawn("src/test/screenshots/bike_page_${look.file}.png")

        // The photo is whole: the frame keeps the proportion of the phone (1.6 : 1).
        val photo = compose.onNodeWithContentDescription("Фото 1 из 3").getUnclippedBoundsInRoot()
        assertThat((photo.right - photo.left).value / (photo.bottom - photo.top).value)
            .isWithin(0.05f)
            .of(1.6f)
        assertThat(bottomOfText("Canyon Grail CF SLX 8 AXS (2026)").value).isAtMost(bottom.value)
        assertThat(bottomOfText("14,2 кг").value).isAtMost(bottom.value)
        compose.onNodeWithContentDescription("Поделиться").assertIsDisplayed()

        // The build is shut: its title is there, its parts are not.
        assertThat(bottomOfTag("bike:equipment").value).isAtMost(bottom.value)
        compose.onNodeWithText("Shimano Deore 10-speed").assertDoesNotExist()
        // The latest entry of the journal and the way to the rest.
        assertThat(bottomOfTag("bike:journal-entry").value).isAtMost(bottom.value)
        assertThat(bottomOfTag("bike:journal-all").value).isAtMost(bottom.value)
        // The start of the discussion: the heading, the invitation, the box to write in.
        compose.onNodeWithContentDescription("Комментарии, 0").assertIsDisplayed()
        assertThat(bottomOfText("Начните обсуждение велосипеда").value).isAtMost(bottom.value)
        assertThat(bottomOfTag("comments:field").value).isAtMost(bottom.value)
    }

    @Test
    fun `with comments the heading and the beginning of the first one are on the first screen`() {
        open(sampleDiscussion())

        assertThat(
                compose
                    .onNodeWithContentDescription("Комментарии, 0")
                    .getUnclippedBoundsInRoot()
                    .bottom
                    .value
            )
            .isAtMost(bottom.value)
        // The first comment begins on the first screen: its name and its time are in view.
        val first = compose.onNodeWithTag("comment:r1").getUnclippedBoundsInRoot()
        assertThat((first.top + 48.dp).value).isAtMost(bottom.value)
    }

    private companion object {
        val StatusBar = 32.dp
        val GestureStrip = 24.dp

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun looks(): List<Array<Any>> = listOf(arrayOf(Look.Light), arrayOf(Look.Dark))
    }
}
