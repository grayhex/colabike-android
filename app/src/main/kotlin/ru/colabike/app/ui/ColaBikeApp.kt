package ru.colabike.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.AppDependencies
import ru.colabike.app.login.LoginRoute
import ru.colabike.core.auth.AuthState

/** Root: the session decides between sign-in and the app; nothing else is global state. */
@Composable
fun ColaBikeApp(dependencies: AppDependencies) {
    val auth by dependencies.auth.state.collectAsStateWithLifecycle()
    val sessionStores = viewModel { SessionStores() }
    when (auth) {
        AuthState.Restoring ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        AuthState.SignedOut -> {
            LaunchedEffect(Unit) { sessionStores.end() }
            LoginRoute(dependencies.auth)
        }
        is AuthState.SignedIn -> SessionScope(sessionStores) { AppShell(dependencies) }
    }
}
