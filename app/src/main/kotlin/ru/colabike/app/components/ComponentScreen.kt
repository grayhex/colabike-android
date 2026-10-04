package ru.colabike.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.colabike.app.R
import ru.colabike.app.bikes.BikeGallery
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Eyebrow
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.ComponentId
import ru.colabike.core.model.ComponentPhoto
import ru.colabike.core.model.ComponentsRepository
import ru.colabike.core.model.Photo

@Composable
fun ComponentRoute(
    repository: ComponentsRepository,
    links: SiteLinks,
    id: ComponentId,
    onBack: () -> Unit,
    onOpenComments: (id: ComponentId, name: String) -> Unit = { _, _ -> },
) {
    val viewModel = viewModel(key = "component:${id.value}") { ComponentViewModel(repository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val opener = LocalLinkOpener.current
    ComponentScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        // The page of the model on the site, only if its path is a path of this site.
        onOpenSite = { path -> links.pageFromPath(path)?.let(opener::open) },
        onOpenLink = opener::open,
        onOpenComments = onOpenComments,
    )
}

/**
 * One model of the catalog: photos with their authors or sources and licences, what it is, on how
 * many public bikes it stands, its description and the discussion. Nothing a person's own build
 * holds (dates, prices, owners) is here, because the catalog does not give it.
 */
@Composable
fun ComponentScreen(
    state: ComponentUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit = {},
    onOpenSite: (path: String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenComments: (id: ComponentId, name: String) -> Unit = { _, _ -> },
) {
    val loaded = state as? ComponentUiState.Loaded
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = loaded?.model?.name ?: stringResource(R.string.component_title),
                subtitle = loaded?.model?.brand?.ifBlank { null },
                titleMaxLines = 4,
                onBack = onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ComponentUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is ComponentUiState.Failed ->
                    if (state.notFound) {
                        EmptyState(
                            title = stringResource(R.string.component_not_found_title),
                            message = stringResource(R.string.component_not_found),
                            icon = ColaIcons.Build,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorState(
                            state.message.resolve(),
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                is ComponentUiState.Loaded ->
                    ModelContent(state, onOpenSite, onOpenLink, onOpenComments)
            }
        }
    }
}

@Composable
private fun ModelContent(
    state: ComponentUiState.Loaded,
    onOpenSite: (path: String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenComments: (id: ComponentId, name: String) -> Unit,
) {
    val model = state.model
    // The gallery when it came; until then, or if there is none, the cover the card has.
    val photos =
        state.photos
            .orEmpty()
            .map { Photo(it.id, it.url) }
            .ifEmpty {
                listOfNotNull(model.coverUrl?.let { Photo("cover", it) })
            }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("component:page"),
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
                BikeGallery(photos, aspect = 4f / 3f)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (model.category.isNotBlank()) Eyebrow(model.category)
                    Text(
                        pluralStringResource(
                            ru.colabike.core.designsystem.R.plurals.cola_component_builds,
                            model.builds,
                            model.builds,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (model.archived) Archived()
            }
            if (model.description.isNotBlank()) {
                Section(stringResource(R.string.component_description)) {
                    Text(model.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            val credited = state.photos.orEmpty().filter { it.hasCredit() }
            if (credited.isNotEmpty()) {
                Section(stringResource(R.string.component_photos)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        credited.forEach { PhotoCredit(it, onOpenLink) }
                    }
                }
            }
            ColaListItem(
                title = stringResource(R.string.comments_open),
                supporting = stringResource(R.string.comments_open_hint),
                icon = ColaIcons.Comment,
                onClick = { onOpenComments(model.id, model.name) },
                modifier = Modifier.testTag("component:comments"),
            )
            if (model.path.isNotBlank()) {
                ColaListItem(
                    title = stringResource(R.string.component_open_site),
                    supporting = stringResource(R.string.component_open_site_hint),
                    icon = ColaIcons.Info,
                    action = ListItemAction.External,
                    onClick = { onOpenSite(model.path) },
                )
            }
        }
    }
}

@Composable
private fun Archived() {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PillBadge(stringResource(R.string.component_archived_title))
            Text(
                stringResource(R.string.component_archived),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A photo that says whose it is or where it is from has a line in the credits. */
private fun ComponentPhoto.hasCredit() = source != null || author != null || caption.isNotBlank()

/**
 * The credit of one photo as the server gave it: the caption, and either the person who uploaded it
 * or the outside source with its creator and licence, whose links open in the browser.
 */
@Composable
private fun PhotoCredit(photo: ComponentPhoto, onOpenLink: (String) -> Unit) {
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (photo.caption.isNotBlank()) {
                Text(photo.caption, style = MaterialTheme.typography.titleSmall)
            }
            val source = photo.source
            if (source != null) {
                Text(
                    source.credit.ifBlank { source.creator },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(
                        R.string.component_photo_source,
                        source.provider,
                        source.license,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                source.url?.let { url ->
                    TextButton(onClick = { onOpenLink(url) }) {
                        Text(stringResource(R.string.component_photo_source_link, source.provider))
                    }
                }
                source.licenseUrl?.let { url ->
                    TextButton(onClick = { onOpenLink(url) }) {
                        Text(stringResource(R.string.component_photo_license_link, source.license))
                    }
                }
            } else {
                photo.author?.let {
                    Text(
                        stringResource(R.string.component_photo_author, it.displayName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
