package ru.colabike.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.app.ui.UiText
import ru.colabike.app.ui.resolve
import ru.colabike.app.ui.toUiText
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.skeleton
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikeSummary
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.DataError
import ru.colabike.core.model.PeopleRepository

@Immutable
internal data class ProfileBikesState(
    val bikes: List<BikeSummary> = emptyList(),
    val loading: Boolean = true,
    val error: UiText? = null,
)

/** Session-owned preview of the public bikes on this rider's profile. */
internal class ProfileBikesModel(
    private val people: PeopleRepository,
    private val repository: BikesRepository,
    private val ref: String,
) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileBikesState())
    val state: StateFlow<ProfileBikesState> = mutable
    private var request: Job? = null

    init {
        refresh()
        viewModelScope.launch { repository.changes.collect { refresh() } }
    }

    fun refresh() {
        request?.cancel()
        request = viewModelScope.launch {
            mutable.value = mutable.value.copy(loading = true, error = null)
            try {
                val bikes = people.bikesOf(ref, limit = 2).items.take(2)
                coroutineContext.ensureActive()
                mutable.value = ProfileBikesState(bikes, loading = false)
            } catch (error: DataError) {
                coroutineContext.ensureActive()
                mutable.value = mutable.value.copy(loading = false, error = error.toUiText())
            }
        }
    }
}

@Composable
internal fun ProfileBikesRoute(
    people: PeopleRepository,
    bikes: BikesRepository,
    ref: String,
    onOpenBike: (BikeId) -> Unit,
    onOpenProfile: () -> Unit,
) {
    val model = viewModel(key = "profile-bikes:$ref") { ProfileBikesModel(people, bikes, ref) }
    val state by model.state.collectAsStateWithLifecycle()
    ProfileBikes(state, model::refresh, onOpenBike, onOpenProfile)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileBikes(
    state: ProfileBikesState,
    onRetry: () -> Unit,
    onOpenBike: (BikeId) -> Unit,
    onOpenProfile: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.profile_bikes),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tileWidth =
                if (LocalDensity.current.fontScale <= 1.3f && maxWidth.value >= 328)
                    (maxWidth - Spacing.m) / 2
                else maxWidth
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                if (state.loading && state.bikes.isEmpty()) {
                    repeat(2) {
                        Box(Modifier.width(tileWidth).aspectRatio(1.5f).skeleton())
                    }
                }
                state.bikes.forEach { bike ->
                    ColaCard(
                        modifier = Modifier.width(tileWidth).fillMaxRowHeight(),
                        onClick = { onOpenBike(bike.id) },
                    ) {
                        BikePhoto(
                            bike.cover?.url,
                            Modifier.fillMaxWidth().aspectRatio(2.0f),
                            ContentScale.Fit,
                        )
                        Text(
                            bike.name,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(Spacing.m),
                        )
                    }
                }
            }
        }
        if (!state.loading && state.bikes.isEmpty() && state.error == null) {
            Text(
                stringResource(R.string.profile_bikes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.error != null) {
            Text(
                stringResource(R.string.profile_bikes_failed) + "\n" + state.error.resolve(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(ru.colabike.core.designsystem.R.string.cola_retry))
            }
        }
        TextButton(onClick = onOpenProfile, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(R.string.profile_public))
        }
    }
}
