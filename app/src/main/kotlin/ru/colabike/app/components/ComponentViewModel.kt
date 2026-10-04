package ru.colabike.app.components

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.toUiText
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentModel
import ru.colabike.core.model.ComponentPhoto
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.DataError

@Immutable
sealed interface ComponentUiState {
    data object Loading : ComponentUiState

    /**
     * [model] is the canonical one: when the model asked for was merged into another, this is the
     * other, and its id is the one for the photos and the discussion. [photos] is null until they
     * arrive and empty when there are none or they could not be read (the page does not fail for
     * that).
     */
    data class Loaded(val model: ComponentModel, val photos: List<ComponentPhoto>? = null) :
        ComponentUiState

    /** [notFound]: an unpublished, unknown or deleted model (or a link that is not a model). */
    data class Failed(val message: UiText, val notFound: Boolean = false) : ComponentUiState
}

/**
 * One model's page: the card first, the gallery after it. A failure of the gallery leaves the page
 * as it is; a failure of the card is the page's failure.
 */
class ComponentViewModel(
    private val repository: ComponentsRepository,
    private val id: ComponentId,
) : ViewModel() {
    private val mutable = MutableStateFlow<ComponentUiState>(ComponentUiState.Loading)
    val state: StateFlow<ComponentUiState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = ComponentUiState.Loading
        viewModelScope.launch {
            val model =
                try {
                    repository.model(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataError) {
                    mutable.value =
                        ComponentUiState.Failed(e.toUiText(), notFound = e is DataError.NotFound)
                    return@launch
                }
            mutable.value = ComponentUiState.Loaded(model)
            val photos =
                try {
                    repository.photos(model.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DataError) {
                    emptyList()
                }
            mutable.value = ComponentUiState.Loaded(model, photos)
        }
    }
}
