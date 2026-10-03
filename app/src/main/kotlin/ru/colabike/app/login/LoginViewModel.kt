package ru.colabike.app.login

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.YandexFailure
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.model.DataError

@Immutable
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val busy: Boolean = false,
    val error: UiText? = null,
    val yandexEnabled: Boolean = false,
) {
    override fun toString() = "LoginUiState(busy=$busy, error=$error)" // never print the password
}

class LoginViewModel(private val auth: AuthActions) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState(yandexEnabled = auth.yandexEnabled))
    val state: StateFlow<LoginUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            auth.yandexFailures.collect { failure ->
                mutableState.update { it.copy(busy = false, error = failure.toUiText()) }
            }
        }
        // Whatever way the session started (password, or Yandex ID while a password was typed),
        // the secret goes: this ViewModel outlives the signed-in app and shows again after
        // sign-out.
        viewModelScope.launch {
            auth.state.collect { state ->
                if (state is AuthState.SignedIn) {
                    mutableState.update {
                        it.copy(password = "", passwordVisible = false, busy = false, error = null)
                    }
                }
            }
        }
    }

    fun onEmailChange(value: String) = mutableState.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = mutableState.update {
        it.copy(password = value, error = null)
    }

    fun togglePasswordVisibility() = mutableState.update {
        it.copy(passwordVisible = !it.passwordVisible)
    }

    fun submit() {
        val current = state.value
        if (current.busy) return
        if (current.email.isBlank() || current.password.isEmpty()) {
            mutableState.update { it.copy(error = UiText.Res(R.string.login_empty_fields)) }
            return
        }
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.signIn(current.email, current.password)
                // The root switches to the app; the password does not outlive the sign-in.
                mutableState.update { it.copy(password = "", busy = false) }
            } catch (e: DataError) {
                mutableState.update { it.copy(busy = false, error = e.toUiText()) }
            }
        }
    }
}

private fun YandexFailure.toUiText(): UiText =
    when (this) {
        is YandexFailure.Exchange -> error.toUiText()
        is YandexFailure.Flow ->
            UiText.Res(
                when (reason) {
                    YandexSignIn.Reason.Cancelled -> R.string.login_yandex_cancelled
                    YandexSignIn.Reason.Blocked -> R.string.login_yandex_blocked
                    YandexSignIn.Reason.RegistrationClosed ->
                        R.string.login_yandex_registration_closed
                    YandexSignIn.Reason.EmailExists -> R.string.login_yandex_email_exists
                    YandexSignIn.Reason.RateLimited -> R.string.error_rate_limited_soon
                    YandexSignIn.Reason.ProviderError,
                    YandexSignIn.Reason.NoPendingSignIn,
                    YandexSignIn.Reason.Unknown -> R.string.login_yandex_failed
                }
            )
    }
