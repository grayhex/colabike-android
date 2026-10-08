package ru.colabike.app

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.colabike.app.intents.IntentEditorRoute
import ru.colabike.app.nearby.CoarseFix
import ru.colabike.app.nearby.CoarseLocation
import ru.colabike.app.nearby.CoarseResult
import ru.colabike.core.designsystem.theme.ColaBikeTheme

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru-w360dp-h800dp-xhdpi")
class IntentAreaLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val restoration = StateRestorationTester(compose)
    private val permission = PendingPermission()

    private class PendingPermission : ActivityResultRegistry(), ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry
            get() = this

        var request: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            request = requestCode
        }

        fun grant() {
            dispatchResult(checkNotNull(request), true)
        }
    }

    private fun start(location: CoarseLocation) {
        val dependencies =
            object : AppDependencies by FakeDependencies() {
                override val coarseLocation = location
            }
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides permission) {
                ColaBikeTheme { IntentEditorRoute(dependencies, null, {}, {}, {}) }
            }
        }
        compose
            .onNodeWithTag("intent-editor:area")
            .performScrollTo()
            .performTextInput("Исходная область")
        compose.onNodeWithTag("intent-editor:choose-area").performScrollTo().performClick()
        compose.onNodeWithTag("intent-area:label").performScrollTo().performTextReplacement("Парк")
    }

    @Test
    fun `permission result remains valid after composition recreation with retained ViewModel`() {
        val location = FakeCoarseLocation(granted = false)
        start(location)
        compose.onNodeWithTag("intent-area:locate").performScrollTo().performClick()
        assertThat(permission.request).isNotNull()
        assertThat(location.reads).isEqualTo(0)

        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { permission.grant() }

        compose.onNodeWithTag("intent-area:confirm").assertIsEnabled()
        assertThat(location.reads).isEqualTo(1)
    }

    @Test
    fun `pending location continues across composition recreation with retained ViewModel`() {
        val result = CompletableDeferred<CoarseResult>()
        var reads = 0
        val location =
            object : CoarseLocation {
                override fun granted() = true

                override suspend fun current(): CoarseResult {
                    reads++
                    return result.await()
                }
            }
        start(location)
        compose.onNodeWithTag("intent-area:locate").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { result.complete(CoarseResult.Located(CoarseFix(37.61, 55.75))) }

        compose.onNodeWithTag("intent-area:confirm").assertIsEnabled()
        assertThat(reads).isEqualTo(1)
    }

    @Test
    fun `predictive back cancellation retains draft and completion returns only to form`() {
        start(FakeCoarseLocation())
        compose.onNodeWithTag("intent-area:coordinates").performScrollTo().performClick()
        compose.onNodeWithTag("intent-area:latitude").performScrollTo().performTextInput("55.73")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("intent-area:latitude").performScrollTo().assertTextContains("55.73")
        val dispatcher = compose.activity.onBackPressedDispatcher
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 200f, 0.5f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithTag("intent-area:picker").assertIsDisplayed()
        compose.onNodeWithTag("intent-area:label").performScrollTo().assertTextContains("Парк")
        compose.runOnIdle {
            dispatcher.dispatchOnBackStarted(
                BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)
            )
            dispatcher.dispatchOnBackProgressed(
                BackEventCompat(100f, 200f, 0.8f, BackEventCompat.EDGE_LEFT)
            )
        }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.onNodeWithTag("intent-editor").assertIsDisplayed()
        compose.onNodeWithTag("intent-area:picker").assertDoesNotExist()
        compose
            .onNodeWithTag("intent-editor:area")
            .performScrollTo()
            .assertTextContains("Исходная область")
    }
}
