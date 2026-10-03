package ru.colabike.app.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BrandMark
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.colaFieldShape
import ru.colabike.core.designsystem.component.colaTextFieldColors
import ru.colabike.core.designsystem.theme.ColaCanvas
import ru.colabike.core.designsystem.theme.Spacing

@Composable
fun LoginRoute(auth: AuthActions) {
    val viewModel = viewModel { LoginViewModel(auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LoginScreen(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTogglePassword = viewModel::togglePasswordVisibility,
        onSubmit = viewModel::submit,
        onYandex = { auth.startYandex(context) },
    )
}

/**
 * Sign-in: one centred column at most 420 dp wide on any window, fields with autofill types, the
 * keyboard's Done signs in, errors are announced.
 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onSubmit: () -> Unit,
    onYandex: () -> Unit,
) {
    ColaCanvas(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandMark(size = 72.dp)
                Text(
                    stringResource(R.string.login_title),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.login_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(R.string.login_email)) },
                    leadingIcon = {
                        Icon(painterResource(ColaIcons.Mail), contentDescription = null)
                    },
                    singleLine = true,
                    enabled = !state.busy,
                    isError = state.error != null,
                    shape = colaFieldShape,
                    colors = colaTextFieldColors(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    modifier =
                        Modifier.fillMaxWidth().semantics {
                            contentType = ContentType.EmailAddress + ContentType.Username
                        },
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    label = { Text(stringResource(R.string.login_password)) },
                    leadingIcon = {
                        Icon(painterResource(ColaIcons.Lock), contentDescription = null)
                    },
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
                        KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    modifier =
                        Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
                )
                state.error?.let {
                    Text(
                        it.resolve(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                            Modifier.fillMaxWidth().semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch + Spacing.xs),
                ) {
                    if (state.busy)
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    else Text(stringResource(R.string.login_submit))
                }
                if (state.yandexEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.login_or),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(Modifier.weight(1f))
                    }
                    OutlinedButton(
                        onClick = onYandex,
                        enabled = !state.busy,
                        modifier =
                            Modifier.fillMaxWidth().heightIn(min = Spacing.touch + Spacing.xs),
                    ) {
                        Text(stringResource(R.string.login_yandex))
                    }
                }
            }
        }
    }
}
