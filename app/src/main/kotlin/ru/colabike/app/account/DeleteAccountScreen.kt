package ru.colabike.app.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.HaloTone
import ru.colabike.core.designsystem.component.IconHalo
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.AccountDeletionRepository

@Composable
fun DeleteAccountRoute(
    deletion: AccountDeletionRepository,
    auth: AuthActions,
    onBack: () -> Unit,
) {
    val viewModel = viewModel { DeleteAccountViewModel(deletion, auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The browser is opened from the screen: it needs an Activity, which a ViewModel must not hold.
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DeleteAccountEvent.OpenProvider -> auth.startYandexReauth(context)
            }
        }
    }
    DeleteAccountScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onConfirmationChange = viewModel::onConfirmationChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onDelete = viewModel::delete,
    )
}

/**
 * The last step before an account is deleted: what goes, the word to type, and how the person
 * proves it is them. Everything is on one screen, so that nothing is hidden behind a dialog that
 * could be confirmed by a stray tap.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onConfirmationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.delete_account_title), onBack = onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                DeleteAccountUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is DeleteAccountUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                is DeleteAccountUiState.Unavailable ->
                    Content {
                        Text(state.reason.resolve(), style = MaterialTheme.typography.bodyLarge)
                    }
                is DeleteAccountUiState.Ready ->
                    Content {
                        Form(
                            state,
                            onConfirmationChange,
                            onPasswordChange,
                            onTogglePassword,
                            onDelete,
                        )
                    }
            }
        }
    }
}

@Composable
private fun Content(body: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.s, bottom = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = FormWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ColaCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(
                    Modifier.padding(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    IconHalo(ColaIcons.Info, tone = HaloTone.Secondary)
                    Text(
                        stringResource(R.string.delete_account_heading),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        stringResource(R.string.delete_account_what),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            body()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Form(
    state: DeleteAccountUiState.Ready,
    onConfirmationChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onDelete: () -> Unit,
) {
    val yandex = state.method == AccountDeletion.Method.Yandex
    Text(
        stringResource(
            if (yandex) R.string.delete_account_how_yandex else R.string.delete_account_how_password
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.confirmation,
        onValueChange = onConfirmationChange,
        label = { Text(stringResource(R.string.delete_account_word_label)) },
        supportingText = { Text(stringResource(R.string.delete_account_word_hint)) },
        singleLine = true,
        enabled = !state.busy,
        isError = state.error != null,
        shape = colaFieldShape,
        colors = colaTextFieldColors(),
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = if (yandex) ImeAction.Done else ImeAction.Next,
            ),
        keyboardActions = KeyboardActions(onDone = { onDelete() }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!yandex) {
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.delete_account_password)) },
            leadingIcon = { Icon(painterResource(ColaIcons.Lock), contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = onTogglePassword) {
                    Icon(
                        painterResource(
                            if (state.passwordVisible) ColaIcons.VisibilityOff
                            else ColaIcons.Visibility
                        ),
                        contentDescription =
                            stringResource(
                                if (state.passwordVisible) R.string.login_hide_password
                                else R.string.login_show_password
                            ),
                    )
                }
            },
            singleLine = true,
            enabled = !state.busy,
            isError = state.error != null,
            shape = colaFieldShape,
            colors = colaTextFieldColors(),
            visualTransformation =
                if (state.passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDelete() }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
    }
    // What is happening, announced as it changes.
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        state.error?.let {
            Text(
                it.resolve(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (yandex && state.providerConfirmed) {
            Text(
                stringResource(R.string.delete_account_provider_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (yandex && state.waitingForProvider) {
            Text(
                stringResource(R.string.delete_account_provider_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Button(
        onClick = onDelete,
        enabled = state.canSubmit,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch + 4.dp),
    ) {
        Text(
            stringResource(
                when {
                    state.busy -> R.string.delete_account_deleting
                    yandex && !state.providerConfirmed -> R.string.delete_account_action_yandex
                    else -> R.string.delete_account_action
                }
            )
        )
    }
}

private val FormWidth = 560.dp
