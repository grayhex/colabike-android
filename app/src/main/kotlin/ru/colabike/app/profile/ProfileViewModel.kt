package ru.colabike.app.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.auth.AuthState
import ru.colabike.core.model.Account
import ru.colabike.core.model.AccountRepository
import ru.colabike.core.model.DataError

@Immutable
sealed interface ProfileUiState {
    /** Nobody is signed in: the profile is an invitation, not an account. */
    data object Guest : ProfileUiState

    data object Loading : ProfileUiState

    data class Loaded(val account: Account, val signingOut: Boolean = false) : ProfileUiState

    data class Failed(val message: UiText) : ProfileUiState
}

/**
 * The profile of whoever is looking. The ViewModel lives in the viewer's own store (SessionScope),
 * so it is created for a guest or for a member and never changes between them.
 */
class ProfileViewModel(private val account: AccountRepository, private val auth: AuthActions) :
    ViewModel() {
    private val mutableState =
        MutableStateFlow<ProfileUiState>(
            if (auth.state.value is AuthState.SignedIn) ProfileUiState.Loading
            else ProfileUiState.Guest
        )
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    init {
        if (state.value == ProfileUiState.Loading) load()
    }

    fun load() {
        mutableState.value = ProfileUiState.Loading
        viewModelScope.launch {
            mutableState.value =
                try {
                    ProfileUiState.Loaded(account.me())
                } catch (e: DataError) {
                    ProfileUiState.Failed(e.toUiText())
                }
        }
    }

    fun signOut() {
        (state.value as? ProfileUiState.Loaded)?.let {
            mutableState.value = it.copy(signingOut = true)
        }
        viewModelScope.launch { auth.signOut() }
    }
}
