package ru.colabike.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.colabike.app.ui.ColaBikeApp
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.PreviewData
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.CommentThread
import ru.colabike.core.model.toDraft

/**
 * Long texts on the page of a bike (issue #56). They need the real text engine to be measured, so
 * they are here and not with the rest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class LongTextsTest {
    @get:Rule val compose = createComposeRule()

    private fun start(dependencies: FakeDependencies) {
        compose.setContent { ColaBikeTheme { ColaBikeApp(dependencies) } }
        compose.waitForIdle()
    }

    private val long = (1..60).joinToString(" ") { "слово$it" }

    @Test
    fun `a long comment is shut to a few lines and goes on with the word more`() {
        val comments =
            FakeComments(threads = listOf(CommentThread(commentOf("r1", body = long), emptyList())))
        start(
            FakeDependencies(
                comments = comments,
                auth = FakeAuth(AuthState.SignedIn(account)),
            )
        )
        compose.onNodeWithContentDescription("Велосипед 1", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Ещё").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ещё").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Свернуть").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ещё").assertDoesNotExist()
    }

    @Test
    fun `a long note of a component is shut to two lines and opened in place`() {
        val own =
            PreviewData.bikeDetail
                .fromDraft(
                    ru.colabike.core.model.BikeId("b-own"),
                    PreviewData.bikeDetail.toDraft().copy(name = "Мой трейл"),
                    "\"v1\"",
                )
                .let {
                    it.copy(
                        components = listOf(BikeComponent("c1", "build", "Вилка", "Axon", long))
                    )
                }
        val bikes =
            FakeBikes(mapOf(null to ru.colabike.core.model.Page(listOf(own.summary), null))).also {
                it.details = mapOf("b-own" to own)
            }
        start(FakeDependencies(bikes = bikes, auth = FakeAuth(AuthState.SignedIn(account))))
        compose.onNodeWithContentDescription("Мой трейл", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Комплектация").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Подробнее").performScrollTo().performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Свернуть").performScrollTo().assertIsDisplayed()
    }
}
