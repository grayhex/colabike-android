package ru.colabike.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.AppDependencies
import ru.colabike.app.config.LocalFeatures
import ru.colabike.app.login.LoginRoute
import ru.colabike.core.auth.AuthState

/**
 * What a screen calls when something needs a signed-in person: the app shows sign-in over the
 * guest's place and, when sign-in succeeds, the guest is where they were, now as a member.
 */
val LocalSignInRequest = staticCompositionLocalOf { {} }

private const val SHELL_STATE = "app"

/**
 * Root: the session decides between sign-in and the app; nothing else is global state.
 *
 * - Without a session the app asks to sign in, with the choice to look around as a guest. A guest
 *   sees what is public, and anything that needs an account opens sign-in over their place
 *   ([LocalSignInRequest]); closing it brings them back.
 * - The shell keeps its navigation while sign-in is on screen and when a guest signs in; it starts
 *   afresh for the next account after a sign-out.
 */
@Composable
fun ColaBikeApp(dependencies: AppDependencies) {
    val auth by dependencies.auth.state.collectAsStateWithLifecycle()
    val config by dependencies.appConfig.state.collectAsStateWithLifecycle()
    val guest by dependencies.settings.browsingAsGuest.collectAsStateWithLifecycle()
    val sessionStores = viewModel { SessionStores() }
    val shellState = rememberSaveableStateHolder()
    var signInOpen by rememberSaveable { mutableStateOf(false) }

    val signedIn = auth is AuthState.SignedIn
    // A person who has signed in is no longer "a guest": a later sign-out asks again.
    LaunchedEffect(signedIn) {
        if (signedIn) {
            signInOpen = false
            dependencies.settings.setBrowsingAsGuest(false)
        }
    }

    // Coming back to the app asks the server for the config again, at most once in a while.
    LifecycleResumeEffect(Unit) {
        dependencies.appConfig.refreshIfStale()
        onPauseOrDispose {}
    }

    CompositionLocalProvider(LocalFeatures provides config.features) {
        ColaBikeContent(
            dependencies = dependencies,
            auth = auth,
            configLoaded = config.loaded,
            guest = guest,
            signedIn = signedIn,
            signInOpen = signInOpen,
            onSignInOpen = { signInOpen = it },
            sessionStores = sessionStores,
            shellState = shellState,
        )
    }
}

@Composable
private fun ColaBikeContent(
    dependencies: AppDependencies,
    auth: AuthState,
    configLoaded: Boolean,
    guest: Boolean,
    signedIn: Boolean,
    signInOpen: Boolean,
    onSignInOpen: (Boolean) -> Unit,
    sessionStores: SessionStores,
    shellState: SaveableStateHolder,
) {
    when {
        // What the device kept is read in a moment; the screens that depend on it wait for it,
        // the network is never waited for.
        !configLoaded || auth is AuthState.Restoring ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        auth is AuthState.SignedOut && !guest -> {
            // The previous account's place in the app (a screen, a scroll) does not wait for the
            // next.
            LaunchedEffect(Unit) {
                sessionStores.end()
                shellState.removeState(SHELL_STATE)
            }
            LoginRoute(
                dependencies.auth,
                dependencies.links,
                onBrowseAsGuest = { dependencies.settings.setBrowsingAsGuest(true) },
            )
        }
        signInOpen && !signedIn -> {
            BackHandler { onSignInOpen(false) }
            LoginRoute(
                dependencies.auth,
                dependencies.links,
                onClose = { onSignInOpen(false) },
            )
        }
        else ->
            shellState.SaveableStateProvider(SHELL_STATE) {
                SessionScope(if (signedIn) Viewer.Member else Viewer.Guest, sessionStores) {
                    CompositionLocalProvider(
                        LocalSignInRequest provides remember { { onSignInOpen(true) } }
                    ) {
                        AppShell(dependencies)
                    }
                }
            }
    }
}
