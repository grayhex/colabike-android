package ru.colabike.app.market

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.DataError
import ru.colabike.core.model.Listing
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.MarketRepository
import ru.colabike.core.model.SellerListings

/**
 * The seller's contact, as far as this screen has got with it. It exists only here: it is asked for
 * by an explicit tap, held in this state and nowhere else (not the repository, not a saved state,
 * not a log), and gone with the screen.
 */
@Immutable
sealed interface ContactState {
    /** Not asked for. */
    data object Hidden : ContactState

    data object Loading : ContactState

    /** The contact as the author wrote it. It is text: nothing in it is acted on by itself. */
    class Shown(val text: String) : ContactState {
        // The text is the author's private word to a signed-in reader: out of every log line.
        override fun toString() = "Shown"
    }

    /** The server's refusal in words: an unconfirmed e-mail, a limit, a listing that is gone. */
    data class Failed(val message: UiText) : ContactState
}

@Immutable
sealed interface ListingUiState {
    data object Loading : ListingUiState

    data class Loaded(
        val listing: Listing,
        /** Saved by the viewer; the bookmark shows the result at once and the server confirms. */
        val saved: Boolean,
        val saving: Boolean = false,
        /** The server refused the last change and the bookmark went back; say so. */
        val saveError: UiText? = null,
        /** The seller's other listings; null until they come, or if they cannot. */
        val others: SellerListings? = null,
        val contact: ContactState = ContactState.Hidden,
    ) : ListingUiState {
        /** Only a listing on the market can be saved (the API says 404 to the rest). */
        val canSave: Boolean
            get() = listing.onMarket

        /** The contact is for a reader other than the author, of a listing on the market. */
        val canAskContact: Boolean
            get() = listing.onMarket && listing.hasContact && !listing.isOwner
    }

    /**
     * [notFound]: hidden, deleted, someone's draft, a blocked seller or an id that is no listing.
     */
    data class Failed(val message: UiText, val notFound: Boolean = false) : ListingUiState
}

class ListingViewModel(private val repository: MarketRepository, private val id: ListingId) :
    ViewModel() {
    private val mutable = MutableStateFlow<ListingUiState>(ListingUiState.Loading)
    val state: StateFlow<ListingUiState> = mutable.asStateFlow()

    init {
        load()
        // Saved or un-saved elsewhere (the list of the saved ones), shown here at once.
        viewModelScope.launch {
            repository.savedChanges.collect { change ->
                if (change.id == id) update { it.copy(saved = change.saved) }
            }
        }
    }

    fun load() {
        mutable.value = ListingUiState.Loading
        viewModelScope.launch {
            val listing =
                try {
                    repository.listing(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataError) {
                    mutable.value =
                        ListingUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                    return@launch
                }
            mutable.value = ListingUiState.Loaded(listing, saved = listing.saved)
            val others =
                try {
                    repository.sellerOthers(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataError) {
                    // The page does not fail for the neighbours; the section is left out.
                    null
                }
            update { it.copy(others = others) }
        }
    }

    /**
     * Saves or un-saves at once and lets the server have the last word: its answer sets the
     * bookmark, a refusal puts the old one back and says why.
     */
    fun toggleSaved() {
        val loaded = state.value as? ListingUiState.Loaded ?: return
        if (loaded.saving || !loaded.canSave) return
        val before = loaded.saved
        val target = !before
        mutable.value = loaded.copy(saved = target, saving = true, saveError = null)
        viewModelScope.launch {
            try {
                val answer = repository.setSaved(id, target)
                update { it.copy(saved = answer, saving = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: DataError) {
                update { it.copy(saved = before, saving = false, saveError = e.toUiText()) }
            }
        }
    }

    /** Asks the server for the contact. Only an explicit tap calls this, never a page load. */
    fun showContact() {
        val loaded = state.value as? ListingUiState.Loaded ?: return
        if (!loaded.canAskContact || loaded.contact == ContactState.Loading) return
        if (loaded.contact is ContactState.Shown) return
        update { it.copy(contact = ContactState.Loading) }
        viewModelScope.launch {
            val next =
                try {
                    ContactState.Shown(repository.contact(id))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataError) {
                    ContactState.Failed(e.contactText())
                }
            update { it.copy(contact = next) }
        }
    }

    /** Takes the contact off the screen; the next time it is asked for again. */
    fun hideContact() = update { it.copy(contact = ContactState.Hidden) }

    private fun update(transform: (ListingUiState.Loaded) -> ListingUiState.Loaded) {
        mutable.update { if (it is ListingUiState.Loaded) transform(it) else it }
    }
}

/**
 * Why the contact was not given, in words about the contact (the common wording of an unconfirmed
 * e-mail is about comments). A limit and the rest keep the one wording every screen has.
 */
private fun DataError.contactText(): UiText =
    when {
        this is DataError.Rejected && code == "email_verification_required" ->
            UiText.Res(R.string.listing_contact_unverified)
        this is DataError.NotFound -> UiText.Res(R.string.listing_contact_gone)
        this is DataError.SignedOut -> UiText.Res(R.string.listing_contact_signed_out)
        else -> toUiText()
    }
