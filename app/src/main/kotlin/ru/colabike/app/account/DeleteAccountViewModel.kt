package ru.colabike.app.account

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.auth.YandexReauth
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.auth.YandexSignIn
import ru.colabike.core.model.AccountDeletion
import ru.colabike.core.model.AccountDeletionRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.DeletionProof

/** The word the person types: the server asks for it too (cola docs/modules/api-v1.md). */
const val DELETE_WORD = "УДАЛИТЬ"

@Immutable
sealed interface DeleteAccountUiState {
    data object Loading : DeleteAccountUiState

    data class Failed(val message: UiText) : DeleteAccountUiState

    /** The account cannot be deleted from here; [reason] says why and what to do. */
    data class Unavailable(val reason: UiText) : DeleteAccountUiState

    data class Ready(
        val method: AccountDeletion.Method,
        val confirmation: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        /** The browser was opened for the confirmation and has not brought it back. */
        val waitingForProvider: Boolean = false,
        /** The provider confirmed the person; what is left is to say [DELETE_WORD]. */
        val providerConfirmed: Boolean = false,
        /** The request is on its way: nothing can be changed or sent again. */
        val busy: Boolean = false,
        val error: UiText? = null,
    ) : DeleteAccountUiState {
        val wordTyped: Boolean
            get() = confirmation.trim().equals(DELETE_WORD, ignoreCase = true)

        /** Enough has been given to send; a provider confirmation may still have to be fetched. */
        val canSubmit: Boolean
            get() =
                !busy &&
                    wordTyped &&
                    (method != AccountDeletion.Method.Password || password.isNotEmpty())

        override fun toString() =
            "Ready(method=$method, busy=$busy, error=$error)" // never print the password
    }
}

/** What the screen has to do besides drawing. */
sealed interface DeleteAccountEvent {
    /** Open the browser for the confirmation with the provider. */
    data object OpenProvider : DeleteAccountEvent
}

/**
 * Deleting one's own account (cola #354, docs/adr/0021). The person types the word and proves who
 * they are: with the password, or, for an account made through Yandex ID, by signing in with Yandex
 * again. The proof lives in memory for one request. On success the server ended every session, and
 * the app clears what it kept of the person as at sign-out.
 */
class DeleteAccountViewModel(
    private val deletion: AccountDeletionRepository,
    private val auth: AuthActions,
) : ViewModel() {
    private val mutableState = MutableStateFlow<DeleteAccountUiState>(DeleteAccountUiState.Loading)
    val state: StateFlow<DeleteAccountUiState> = mutableState.asStateFlow()

    private val mutableEvents = Channel<DeleteAccountEvent>(Channel.BUFFERED)
    val events: Flow<DeleteAccountEvent> = mutableEvents.receiveAsFlow()

    // The confirmation of the provider: a secret, so not in the state.
    private var provider: DeletionProof.Provider? = null

    init {
        viewModelScope.launch {
            auth.yandexReauth.collect { result ->
                when (result) {
                    is YandexReauth.Proof -> {
                        provider = DeletionProof.Provider(result.code, result.verifier)
                        updateReady {
                            it.copy(
                                waitingForProvider = false,
                                providerConfirmed = true,
                                error = null,
                            )
                        }
                        // The word was typed before the browser: nothing more to ask.
                        val ready = state.value as? DeleteAccountUiState.Ready
                        if (ready != null && ready.canSubmit) send(ready)
                    }
                    is YandexReauth.Failed ->
                        updateReady {
                            it.copy(waitingForProvider = false, error = result.reason.toUiText())
                        }
                }
            }
        }
        load()
    }

    fun load() {
        mutableState.value = DeleteAccountUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    val terms = deletion.deletion()
                    val method = terms.method
                    when {
                        terms.allowed && method != null -> DeleteAccountUiState.Ready(method)
                        else ->
                            DeleteAccountUiState.Unavailable(
                                UiText.Res(
                                    when (terms.reason) {
                                        AccountDeletion.Reason.Admin ->
                                            R.string.delete_account_admin
                                        AccountDeletion.Reason.NoMethod ->
                                            R.string.delete_account_no_method
                                        AccountDeletion.Reason.Other,
                                        null -> R.string.delete_account_unavailable
                                    }
                                )
                            )
                    }
                } catch (e: DataError) {
                    DeleteAccountUiState.Failed(e.toUiText())
                }
        }
    }

    fun onConfirmationChange(text: String) = updateReady {
        if (it.busy) it else it.copy(confirmation = text, error = null)
    }

    fun onPasswordChange(text: String) = updateReady {
        if (it.busy) it else it.copy(password = text, error = null)
    }

    fun togglePasswordVisibility() = updateReady { it.copy(passwordVisible = !it.passwordVisible) }

    /** The button: sends what is ready, or asks the provider first. */
    fun delete() {
        val ready = state.value as? DeleteAccountUiState.Ready ?: return
        if (!ready.canSubmit) return
        if (ready.method == AccountDeletion.Method.Yandex && provider == null) {
            updateReady { it.copy(waitingForProvider = true, error = null) }
            mutableEvents.trySend(DeleteAccountEvent.OpenProvider)
            return
        }
        send(ready)
    }

    private fun send(ready: DeleteAccountUiState.Ready) {
        val proof: DeletionProof =
            when (ready.method) {
                AccountDeletion.Method.Password -> DeletionProof.Password(ready.password)
                AccountDeletion.Method.Yandex -> provider ?: return
            }
        updateReady { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                deletion.delete(proof)
                // Gone: forget the proof and let the app go back to a guest's.
                provider = null
                auth.accountDeleted()
            } catch (e: DataError) {
                // The code of the provider is spent by any attempt; a password stays to be fixed.
                if (ready.method == AccountDeletion.Method.Yandex) provider = null
                updateReady {
                    it.copy(
                        busy = false,
                        providerConfirmed = provider != null,
                        error = refusal(ready.method, e),
                    )
                }
            }
        }
    }

    private fun refusal(method: AccountDeletion.Method, error: DataError): UiText =
        if (error is DataError.Rejected && error.code == "invalid_credentials")
            UiText.Res(
                if (method == AccountDeletion.Method.Password)
                    R.string.delete_account_wrong_password
                else R.string.delete_account_wrong_provider
            )
        else error.toUiText()

    private fun YandexSignIn.Reason.toUiText(): UiText =
        UiText.Res(
            when (this) {
                YandexSignIn.Reason.Cancelled -> R.string.delete_account_provider_cancelled
                YandexSignIn.Reason.RateLimited -> R.string.error_rate_limited_soon
                else -> R.string.delete_account_provider_failed
            }
        )

    private fun updateReady(transform: (DeleteAccountUiState.Ready) -> DeleteAccountUiState.Ready) {
        mutableState.update { if (it is DeleteAccountUiState.Ready) transform(it) else it }
    }
}
