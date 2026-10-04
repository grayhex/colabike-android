package ru.colabike.app.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.delay
import ru.colabike.app.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AppConfig
import ru.colabike.core.model.ConfigAssets
import ru.colabike.core.model.LaunchFill

/**
 * What the launch screen is for this run. It is decided once, from what the device already holds:
 * the screen is shown only with its picture on the device, so nothing is ever waited for and a
 * picture that was not downloaded (a first start, a damaged file) is simply no launch screen.
 */
sealed interface LaunchPlan {
    data object None : LaunchPlan

    data class Show(val image: File, val fill: LaunchFill, val title: String?) : LaunchPlan
}

fun launchPlanOf(config: AppConfig, assets: ConfigAssets): LaunchPlan {
    val launch = config.launch
    if (!launch.enabled) return LaunchPlan.None
    val file = launch.imageUrl?.let(assets::fileOf) ?: return LaunchPlan.None
    return LaunchPlan.Show(file, launch.fill, launch.title)
}

/** How long, at most, the launch screen covers the start while the real work of it goes on. */
const val LAUNCH_MAX_MS = 1_500L

/**
 * The launch screen after the system's splash: the server's picture over the page, for as long as
 * the start itself takes ([startDone] says it is over) and never longer than [LAUNCH_MAX_MS]. No
 * minimum time is added for the sake of the picture. It is not part of the app's content: it
 * answers no touch and TalkBack hears only the name.
 */
@Composable
fun LaunchOverlay(plan: LaunchPlan, startDone: Boolean) {
    if (plan !is LaunchPlan.Show) return
    // Once over, over for this run (a turn of the screen does not bring it back).
    var over by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LAUNCH_MAX_MS)
        over = true
    }
    LaunchedEffect(startDone) { if (startDone) over = true }
    // It is there from the first frame it is composed in (a fade-in would show the page first).
    AnimatedVisibility(
        visible = !over,
        enter = EnterTransition.None,
        exit = fadeOut(tween(FADE_MS)),
    ) {
        LaunchFrame(plan)
    }
}

/** The launch screen itself: the picture over the page, the title at the bottom. */
@Composable
fun LaunchFrame(plan: LaunchPlan.Show) {
    val name = stringResource(R.string.app_name)
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures {} }
            .testTag("launch")
            .clearAndSetSemantics { contentDescription = name }
    ) {
        AsyncImage(
            model = plan.image,
            contentDescription = null,
            contentScale = if (plan.fill == LaunchFill.Fit) ContentScale.Fit else ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        plan.title?.let { title ->
            Surface(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            )
                        )
                        .padding(Spacing.xl),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.l),
                )
            }
        }
    }
}

private const val FADE_MS = 150
