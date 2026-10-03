package ru.colabike.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AccountRepository

@Composable
fun ProfileRoute(account: AccountRepository, auth: AuthActions) {
    val viewModel = viewModel { ProfileViewModel(account, auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, onRetry = viewModel::load, onSignOut = viewModel::signOut)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(state: ProfileUiState, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.profile_title),
                        modifier = Modifier.semantics { heading() },
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ProfileUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is ProfileUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                is ProfileUiState.Loaded ->
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        val account = state.account
                        Avatar(account.displayName, account.avatarUrl, size = 96.dp)
                        Text(
                            account.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            stringResource(
                                ru.colabike.core.designsystem.R.string.cola_username,
                                account.username,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (account.location.isNotBlank())
                            Text(account.location, style = MaterialTheme.typography.bodyMedium)
                        if (account.bio.isNotBlank()) {
                            Text(
                                account.bio,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 560.dp),
                            )
                        }
                        if (!account.emailVerified) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.widthIn(max = 560.dp),
                            ) {
                                Text(
                                    stringResource(R.string.profile_unverified),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(Spacing.m),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = !state.signingOut,
                            modifier =
                                Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Icon(
                                painterResource(ColaIcons.Logout),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(R.string.profile_sign_out),
                                modifier = Modifier.padding(start = Spacing.s),
                            )
                        }
                    }
            }
        }
    }
}
