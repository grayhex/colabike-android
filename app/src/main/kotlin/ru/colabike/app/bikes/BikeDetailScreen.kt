package ru.colabike.app.bikes

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.StatTile
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

/** One bike: its name and what it is, the photo, who rides it, the facts and the build. */
@Composable
fun BikeDetailScreen(
    state: BikeDetailUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val summary = (state as? BikeDetailUiState.Loaded)?.bike?.summary
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = summary?.name ?: stringResource(R.string.bike_title),
                subtitle =
                    summary?.let { bikeSubtitle(it.brand, it.model, it.year) }?.ifBlank { null },
                titleMaxLines = 4,
                onBack = if (showBack) onBack else null,
            )
        },
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

/** From this pane width the photo is wide (16:9) so it does not push the facts off the screen. */
private val WidePane = 600.dp

@Composable
private fun BikeContent(bike: BikeDetail) =
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val summary = bike.summary
        val locale = LocalConfiguration.current.locales[0]
        val photoAspect = if (maxWidth >= WidePane) 16f / 9f else 4f / 3f
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.screen)
                    .padding(top = Spacing.s, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.section),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    val frame = MaterialTheme.shapes.extraLarge
                    BikePhoto(
                        (bike.photos.firstOrNull() ?: summary.cover)?.url,
                        Modifier.fillMaxWidth()
                            .aspectRatio(photoAspect)
                            .clip(frame)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, frame),
                    )
                    if (summary.isFormer || !summary.isPublic) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            if (summary.isFormer) {
                                PillBadge(
                                    stringResource(
                                        ru.colabike.core.designsystem.R.string.cola_former_bike
                                    )
                                )
                            }
                            if (!summary.isPublic) {
                                PillBadge(
                                    stringResource(R.string.bike_private),
                                    icon = ColaIcons.Lock,
                                )
                            }
                        }
                    }
                    summary.author?.let { UserRow(it) }
                }
                if (bike.description.isNotBlank()) {
                    Section(stringResource(R.string.bike_description)) {
                        Text(bike.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val facts =
                    listOfNotNull(
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
                        bike.size
                            .takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.bike_size) to it },
                        bike.color
                            .takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.bike_color) to it },
                    )
                if (facts.isNotEmpty()) {
                    Section(stringResource(R.string.bike_specs)) { Facts(facts) }
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
                        ColaCard(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(horizontal = Spacing.card, vertical = Spacing.s)
                            ) {
                                components.forEachIndexed { index, component ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                    Component(component.category, component.name)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** Two tiles to a row; one when the system font is large and a tile no longer fits half a row. */
@Composable
private fun Facts(facts: List<Pair<String, String>>) {
    val columns = if (LocalDensity.current.fontScale > LargeFont) 1 else 2
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        facts.chunked(columns).forEach { row ->
            Row(
                Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                row.forEach { (label, value) ->
                    StatTile(label, value, Modifier.weight(1f).fillMaxHeight())
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** From this font scale on, stat tiles stack. */
private const val LargeFont = 1.3f

@Composable
private fun Component(category: String, name: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Eyebrow(category, maxLines = 3, modifier = Modifier.weight(0.4f))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}
