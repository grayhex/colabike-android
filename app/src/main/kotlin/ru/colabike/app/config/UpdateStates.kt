package ru.colabike.app.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.colabike.app.AppDependencies
import ru.colabike.core.model.UpdateState
import ru.colabike.core.model.updateState

/**
 * What the server's version policy says of this build. It is worked out again when the config
 * changes and each time the app is brought back, because a verdict of "hard" is believed for a day
 * after the server last confirmed it, and that day runs out while the app is closed.
 */
@Composable
fun rememberUpdateState(dependencies: AppDependencies): UpdateState {
    val config by dependencies.appConfig.state.collectAsStateWithLifecycle()
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumes++
        onPauseOrDispose {}
    }
    return remember(config.config.compatibility, config.validatedAt, resumes) {
        config.config.compatibility.updateState(
            dependencies.versionCode,
            config.validatedAt,
            dependencies.clock.instant(),
        )
    }
}
