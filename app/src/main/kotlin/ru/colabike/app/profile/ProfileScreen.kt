package ru.colabike.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.Avatar
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AccountRepository

@Composable
fun ProfileRoute(account: AccountRepository, auth: AuthActions) {
    val viewModel = viewModel { ProfileViewModel(account, auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileScreen(state, onRetry = viewModel::load, onSignOut = viewModel::signOut)
}

@Composable
fun ProfileScreen(state: ProfileUiState, onRetry: () -> Unit, onSignOut: () -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ColaTopBar(title = stringResource(R.string.profile_title)) },
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
                            .padding(horizontal = Spacing.screen)
                            .padding(top = Spacing.l, bottom = Spacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        val account = state.account
                        Avatar(account.displayName, account.avatarUrl, size = 96.dp)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                account.displayName,
                                style = MaterialTheme.typography.displayMedium,
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
                        }
                        if (account.location.isNotBlank()) {
                            PillBadge(account.location, icon = ColaIcons.Location)
                        }
                        if (account.bio.isNotBlank()) {
                            Text(
                                account.bio,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.widthIn(max = 560.dp),
                            )
                        }
                        if (!account.emailVerified) {
                            ColaCard(
                                Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Row(
                                    Modifier.padding(Spacing.l),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.l),
                                ) {
                                    IconHalo(ColaIcons.MailUnread)
                                    Text(
                                        stringResource(R.string.profile_unverified),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = !state.signingOut,
                            modifier =
                                Modifier.widthIn(max = 420.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = Spacing.touch),
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
