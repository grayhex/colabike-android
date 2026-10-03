package ru.colabike.app.bikes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import java.text.NumberFormat
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.bikeSubtitle
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeComponent
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository

@Composable
fun BikeDetailRoute(
    repository: BikesRepository,
    id: BikeId,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    val viewModel = viewModel { BikeDetailViewModel(repository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    BikeDetailScreen(state, showBack = showBack, onBack = onBack, onRetry = viewModel::load)
}

/** One bike: the photo first, then what it is, who rides it, the facts and the build. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BikeDetailScreen(
    state: BikeDetailUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val title = (state as? BikeDetailUiState.Loaded)?.bike?.summary?.name.orEmpty()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painterResource(ColaIcons.ArrowBack),
                                contentDescription = stringResource(R.string.bike_back),
                            )
                        }
                    }
                },
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikeDetailUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikeDetailUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                is BikeDetailUiState.Loaded -> BikeContent(state.bike)
            }
        }
    }
}

@Composable
private fun BikeContent(bike: BikeDetail) {
    val summary = bike.summary
    val locale = LocalConfiguration.current.locales[0]
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
            val photo = bike.photos.firstOrNull() ?: summary.cover
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        painterResource(ColaIcons.Bike),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp).align(Alignment.Center),
                    )
                }
            }
            Column(
                Modifier.padding(Spacing.screen),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    summary.name,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                bikeSubtitle(summary.brand, summary.model, summary.year)
                    .takeIf { it.isNotBlank() }
                    ?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                if (!summary.isPublic) {
                    Text(
                        stringResource(R.string.bike_private),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            summary.author?.let { UserRow(it) }
            if (bike.description.isNotBlank()) {
                Section(stringResource(R.string.bike_description)) {
                    Text(bike.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            val facts =
                listOfNotNull(
                    bike.color
                        .takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.bike_color) to it },
                    bike.size
                        .takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.bike_size) to it },
                    bike.weightKg?.let {
                        stringResource(R.string.bike_weight) to
                            stringResource(
                                R.string.bike_weight_value,
                                NumberFormat.getNumberInstance(locale).format(it),
                            )
                    },
                    bike.mileageKm
                        .takeIf { it > 0 }
                        ?.let {
                            stringResource(R.string.bike_mileage) to
                                stringResource(R.string.bike_mileage_value, it)
                        },
                )
            if (facts.isNotEmpty()) {
                Section(stringResource(R.string.bike_specs)) {
                    facts.forEachIndexed { index, (label, value) ->
                        if (index > 0) HorizontalDivider()
                        Fact(label, value)
                    }
                }
            }
            bike.components.groupBy(BikeComponent::section).forEach { (section, components) ->
                Section(
                    stringResource(
                        when (section) {
                            "build" -> R.string.bike_build
                            "accessories" -> R.string.bike_accessories
                            else -> R.string.bike_other_components
                        }
                    )
                ) {
                    components.forEachIndexed { index, component ->
                        if (index > 0) HorizontalDivider()
                        Fact(component.category, component.name)
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
    }
}
