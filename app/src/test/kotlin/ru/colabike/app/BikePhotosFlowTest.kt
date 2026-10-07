package ru.colabike.app

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.bikes.PhotoImportException
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Page
import ru.colabike.core.model.toDraft

/** The system picker, answered with whatever the test says the person picked. */
internal class PickedRegistry(var picked: List<Uri>) : ActivityResultRegistry() {
    var launches = 0

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launches++
        dispatchResult(requestCode, picked)
    }
}

/**
 * The pictures of a bike as its owner changes them (docs/adr/0022): from the bike's page to the
 * screen, pictures picked and sent, a cover chosen, a photo removed after a question.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class BikePhotosFlowTest {
    @get:Rule val compose = createComposeRule()

    private val own =
        PreviewData.bikeDetail.fromDraft(
            BikeId("b-own"),
            PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
            "\"v1\"",
        )

    private val picker =
        PickedRegistry(listOf(Uri.parse("content://media/a"), Uri.parse("content://media/b")))

    private fun bikes() =
        FakeBikes(mapOf(null to Page(listOf(own.summary), null))).also {
            it.details = mapOf("b-own" to own)
        }

    private fun start(bikes: FakeBikes, files: FakePhotoFiles = FakePhotoFiles()) {
        val dependencies =
            FakeDependencies(
                bikes = bikes,
                auth = FakeAuth(AuthState.SignedIn(account)),
                photoFiles = files,
            )
        val owner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = picker
            }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                ColaBikeTheme { ColaBikeApp(dependencies) }
            }
        }
        compose.waitForIdle()
    }

    private fun openPhotos() {
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("bike:photos").performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `someone else's bike has no way to its photos editor`() {
        start(FakeBikes())

        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:photos").assertDoesNotExist()
    }

    @Test
    fun `the photos open from the bike's page with the cover marked and the count`() {
        start(bikes())

        openPhotos()

        compose.onNodeWithTag("photos").assertIsDisplayed()
        compose.onNodeWithText("Фото: 3 из 12").assertIsDisplayed()
        compose.onNodeWithTag("photos:is-cover:p1").assertIsDisplayed()
        compose.onNodeWithTag("photos:cover:p2").assertIsDisplayed()
        compose.onNodeWithTag("photos:cover:p1").assertDoesNotExist()
    }

    @Test
    fun `pictures picked go to the server and join the others`() {
        val bikes = bikes()
        start(bikes)
        openPhotos()

        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        assertThat(picker.launches).isEqualTo(1)
        assertThat(bikes.uploaded).hasSize(2)
        compose.onNodeWithText("Фото: 5 из 12").assertIsDisplayed()
        compose.onNodeWithTag("photos:photo:ph-new-1").assertExists()
        compose.onNodeWithTag("photos:photo:ph-new-2").assertExists()
        compose.onNodeWithTag("photos:pending:1").assertDoesNotExist()
    }

    @Test
    fun `a refused picture stays with the reason and can only be dropped`() {
        val bikes = bikes()
        bikes.writeError =
            DataError.Rejected(415, "unsupported_media_type", "Нужен JPEG, PNG или WebP.")
        picker.picked = listOf(Uri.parse("content://media/a"))
        start(bikes)
        openPhotos()

        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("photos:failure:1").assertIsDisplayed()
        compose.onNodeWithText("Нужен JPEG, PNG или WebP.").assertIsDisplayed()
        compose.onNodeWithTag("photos:retry:1").assertDoesNotExist()
        compose.onNodeWithTag("photos:cancel:1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("photos:pending:1").assertDoesNotExist()
    }

    @Test
    fun `a lost connection can be tried again`() {
        val bikes = bikes()
        bikes.writeError = DataError.Offline(java.io.IOException("down"))
        picker.picked = listOf(Uri.parse("content://media/a"))
        start(bikes)
        openPhotos()
        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("photos:retry:1").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(bikes.uploaded).hasSize(1)
        compose.onNodeWithTag("photos:pending:1").assertDoesNotExist()
        compose.onNodeWithText("Фото: 4 из 12").assertIsDisplayed()
    }

    @Test
    fun `a file that is not a picture is told so before any upload`() {
        val bikes = bikes()
        picker.picked = listOf(Uri.parse("content://media/text"))
        start(
            bikes,
            FakePhotoFiles(mapOf("content://media/text" to PhotoImportException.Reason.Unreadable)),
        )
        openPhotos()

        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("photos:failure:1").assertIsDisplayed()
        assertThat(bikes.uploaded).isEmpty()
    }

    @Test
    fun `a photo is made the cover with its button`() {
        val bikes = bikes()
        start(bikes)
        openPhotos()

        compose.onNodeWithTag("photos:cover:p3").performScrollTo().performClick()
        compose.waitForIdle()

        assertThat(bikes.covers).containsExactly("p3")
        compose.onNodeWithTag("photos:is-cover:p3").assertExists()
        compose.onNodeWithTag("photos:cover:p1").assertExists()
    }

    @Test
    fun `a photo is removed after the person confirms, and not before`() {
        val bikes = bikes()
        start(bikes)
        openPhotos()

        compose.onNodeWithTag("photos:delete:p2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Удалить фото?").assertIsDisplayed()
        compose.onNodeWithTag("photos:delete-cancel").performClick()
        compose.waitForIdle()
        assertThat(bikes.removedPhotos).isEmpty()

        compose.onNodeWithTag("photos:delete:p2").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("photos:delete-confirm").performClick()
        compose.waitForIdle()

        assertThat(bikes.removedPhotos).containsExactly("p2")
        compose.onNodeWithTag("photos:photo:p2").assertDoesNotExist()
        compose.onNodeWithText("Фото: 2 из 12").assertIsDisplayed()
    }

    @Test
    fun `a bike with twelve photos has no room for another`() {
        val bikes = bikes()
        bikes.details =
            mapOf(
                "b-own" to
                    own.copy(
                        photos =
                            (1..12).map {
                                ru.colabike.core.model.Photo(
                                    "q$it",
                                    "https://example.test/q$it.jpg",
                                )
                            }
                    )
            )
        start(bikes)
        openPhotos()

        compose.onNodeWithTag("photos:add").assertIsNotEnabled()
        compose.onNodeWithText("Фото: 12 из 12").assertIsDisplayed()
    }

    @Test
    fun `the new photos are on the bike's page when the person goes back`() {
        val bikes = bikes()
        start(bikes)
        openPhotos()
        compose.onNodeWithTag("photos:add").performClick()
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Назад").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag("bike:photos").assertExists()
        assertThat(bikes.detailCalls).isAtLeast(2)
    }
}
