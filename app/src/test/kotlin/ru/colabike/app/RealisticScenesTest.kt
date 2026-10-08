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
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.AppShell
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeRef
import ru.colabike.core.model.FeedItem
import ru.colabike.core.model.NotificationCount
import ru.colabike.core.model.Page
import ru.colabike.core.model.Person
import ru.colabike.core.model.Photo
import ru.colabike.core.model.UserId

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

/** Repeatable acceptance content, also used for before/after comparisons. */
fun realisticDependencies(): FakeDependencies {
    val author = Person(UserId("visual-author"), "sasha.rides", "Александра Соколова", null)
    val blue =
        PreviewData.bike.copy(
            id = BikeId("visual-blue"),
            name = "Ocean Blue",
            brand = "Mikamaro",
            model = "Ocean Blue",
            year = 2024,
            cover = Photo("blue", "https://example.test/gravel-blue.jpg"),
            author = author,
            likes = 128,
            comments = 12,
            liked = false,
        )
    val dark =
        blue.copy(
            id = BikeId("visual-dark"),
            name = "Гравий, лес и длинная дорога домой",
            brand = "ARC8",
            model = "Eero",
            cover = Photo("dark", "https://example.test/gravel-dark.jpg"),
            likes = 9,
        )
    val green =
        blue.copy(
            id = BikeId("visual-green"),
            name = "По делам и за хлебом",
            brand = "Bianchi",
            model = "City",
            cover = Photo("green", "https://example.test/bike-green.jpg"),
            likes = 0,
        )
    val bike =
        PreviewData.bikeDetail.copy(
            summary = blue,
            description =
                "В будни — вдоль набережной. В выходные — за город, на гравий и тихие лесные дороги.",
            photos =
                listOf(blue.cover!!, Photo("portrait", "https://example.test/bike-portrait.jpg")),
            trim = "",
            manufacturerUrl = null,
            components = emptyList(),
        )
    val ride =
        rideDetail(0).let {
            it.copy(
                summary =
                    it.summary.copy(
                        title = "Вдоль реки к утреннему кофе",
                        author = author,
                    ),
                route = VisualRoutes.load("river"),
                description =
                    "Набережная, тихие улицы и остановка у любимой кофейни. Отличное начало воскресенья.",
            )
        }
    val viewer =
        account.copy(
            name = "Михаил Орлов",
            username = "misha.velo",
            bio = "Гравий, небольшие города и кофе в дороге. Ищу компанию на воскресенье.",
            emailVerified = true,
        )
    val journal =
        PreviewData.journal.copy(
            title = "Выходные выше облаков",
            kind = "story",
            excerpt =
                "Подъём к озеру, прохладный ветер и дорога, ради которой стоит проснуться пораньше.",
            bike = BikeRef(blue.id, blue.name),
            author = author,
        )
    val now = Instant.parse("2026-10-03T14:00:00Z")
    return FakeDependencies(
        bikes =
            FakeBikes(mapOf(null to Page(listOf(blue, dark, green), null))).apply {
                details = mapOf(blue.id.value to bike)
            },
        rides =
            FakeRides(
                completedPages = mapOf(null to Page(listOf(ride.summary), null)),
                details = mapOf(ride.summary.id.value to ride),
            ),
        feed =
            FakeFeed(
                mapOf(
                    null to
                        Page(
                            listOf(
                                FeedItem.Ride(ride.summary, now),
                                FeedItem.Journal(journal, now),
                                FeedItem.Bike(blue, now),
                            ),
                            null,
                        )
                )
            ),
        intents =
            FakeIntents(
                community =
                    listOf(
                        sampleIntent(1).copy(author = author),
                        sampleIntent(2, area = "Лосиный остров")
                            .copy(
                                author =
                                    author.copy(id = UserId("visual-igor"), name = "Игорь Лебедев")
                            ),
                    )
            ),
        journal = FakeJournal(pages = mapOf(null to Page(listOf(journal), null))),
        auth = FakeAuth(AuthState.SignedIn(viewer)),
        account = FakeAccount { viewer },
        notifications = FakeNotifications(unread = NotificationCount(128, capped = true)),
    )
}
