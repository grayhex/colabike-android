package ru.colabike.app.bikes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.LocalSharer
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LikeButton
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.StatTile
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.bikeSubtitle
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeDetail
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.BikesRepository
import ru.colabike.core.model.CommentCountChange

@Composable
fun BikeDetailRoute(
    repository: BikesRepository,
    auth: AuthActions,
    links: SiteLinks,
    id: BikeId,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenAuthor: (ref: String) -> Unit = {},
    onOpenJournal: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenComments: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenRides: (id: BikeId, name: String) -> Unit = { _, _ -> },
    commentChanges: Flow<CommentCountChange> = emptyFlow(),
) {
    val viewModel = viewModel { BikeDetailViewModel(repository, id, commentChanges) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val sharer = LocalSharer.current
    val opener = LocalLinkOpener.current
    val signedIn = authState is AuthState.SignedIn
    BikeDetailScreen(
        state,
        showBack = showBack,
        onBack = onBack,
        onRetry = viewModel::load,
        // A private bike answers a guest "not found" exactly as a missing one does.
        onSignIn = if (signedIn) null else signIn,
        // A guest is asked to sign in first; the like is not given for them afterwards.
        onToggleLike = if (signedIn) viewModel::toggleLike else signIn,
        onShare = { title -> sharer.share(title, links.bike(id.value)) },
        onOpenLink = opener::open,
        onOpenAuthor = onOpenAuthor,
        onOpenJournal = onOpenJournal,
        onOpenComments = onOpenComments,
        onOpenRides = onOpenRides,
    )
}

/**
 * One bike: the photos, what it is, who rides it, the like and the share, the passport (the facts
 * the owner gave, and the price only if the owner shows it), the description and the build in the
 * owner's groups. Nothing the contract does not give is shown.
 */
@Composable
fun BikeDetailScreen(
    state: BikeDetailUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: (() -> Unit)? = null,
    onToggleLike: () -> Unit = {},
    onShare: (title: String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenAuthor: (ref: String) -> Unit = {},
    onOpenJournal: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenComments: (id: BikeId, name: String) -> Unit = { _, _ -> },
    onOpenRides: (id: BikeId, name: String) -> Unit = { _, _ -> },
) {
    val loaded = (state as? BikeDetailUiState.Loaded)?.bike
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = loaded?.summary?.name ?: stringResource(R.string.bike_title),
                subtitle =
                    loaded
                        ?.let {
                            bikeSubtitle(
                                it.summary.brand,
                                "${it.summary.model} ${it.trim}".trim(),
                                it.summary.year,
                            )
                        }
                        ?.ifBlank { null },
                titleMaxLines = 4,
                onBack = if (showBack) onBack else null,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                BikeDetailUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is BikeDetailUiState.Failed ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        ErrorState(state.message.resolve(), onRetry = onRetry)
                        if (state.notFound && onSignIn != null) {
                            Text(
                                stringResource(R.string.bike_private_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = Spacing.xl),
                            )
                            Button(
                                onClick = onSignIn,
                                modifier =
                                    Modifier.padding(Spacing.l).heightIn(min = Spacing.touch),
                            ) {
                                Text(stringResource(R.string.profile_sign_in))
                            }
                        }
                    }
                is BikeDetailUiState.Loaded ->
                    BikeContent(
                        state,
                        onToggleLike,
                        onShare,
                        onOpenLink,
                        onOpenAuthor,
                        onOpenJournal,
                        onOpenComments,
                        onOpenRides,
                    )
            }
        }
    }
}

/** From this pane width the photo is wide (16:9) so it does not push the facts off the screen. */
private val WidePane = 600.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BikeContent(
    state: BikeDetailUiState.Loaded,
    onToggleLike: () -> Unit,
    onShare: (title: String) -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenAuthor: (ref: String) -> Unit,
    onOpenJournal: (id: BikeId, name: String) -> Unit,
    onOpenComments: (id: BikeId, name: String) -> Unit,
    onOpenRides: (id: BikeId, name: String) -> Unit,
) =
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bike = state.bike
        val summary = bike.summary
        val locale = LocalConfiguration.current.locales[0]
        val photoAspect = if (maxWidth >= WidePane) 16f / 9f else 4f / 3f
        val photos = bike.photos.ifEmpty { listOfNotNull(summary.cover) }
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
                    BikeGallery(photos, photoAspect)
                    val badges = BikeLabels.badges(summary.classification)
                    if (badges.isNotEmpty() || summary.isFormer || !summary.isPublic) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                            verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        ) {
                            badges.forEach { PillBadge(it) }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        // One's own and private bikes cannot be liked: the count shows, no switch.
                        val likeable = !summary.isOwner && summary.isPublic
                        LikeButton(
                            liked = summary.liked,
                            count = summary.likes,
                            onToggle = onToggleLike.takeIf { likeable },
                            busy = state.liking,
                        )
                        if (summary.isPublic) {
                            val title = summary.name
                            OutlinedButton(
                                onClick = { onShare(title) },
                                modifier = Modifier.heightIn(min = Spacing.touch),
                            ) {
                                Icon(
                                    painterResource(ColaIcons.Share),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    stringResource(
                                        ru.colabike.core.designsystem.R.string.cola_share
                                    ),
                                    modifier = Modifier.padding(start = Spacing.s),
                                )
                            }
                        }
                    }
                    state.likeError?.let {
                        Text(
                            it.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    summary.author?.let { author ->
                        UserRow(author, onClick = { onOpenAuthor(author.id.value) })
                    }
                }
                if (bike.description.isNotBlank()) {
                    Section(stringResource(R.string.bike_description)) {
                        Text(bike.description, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                val facts = passport(bike, locale)
                // The price arrives only when the owner shows it (or it is the owner's own). It
                // gets a whole row: "85 000 ₽" does not fit half of one without breaking.
                val price = bike.priceRub
                if (facts.isNotEmpty() || price != null) {
                    Section(stringResource(R.string.bike_specs)) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            if (facts.isNotEmpty()) Facts(facts)
                            if (price != null) {
                                StatTile(
                                    stringResource(R.string.bike_price),
                                    priceText(price, locale),
                                    Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
                orderComponents(bike.components, bike.groupOrder).forEach { (section, components) ->
                    Section(
                        stringResource(
                            when (section) {
                                "build" -> R.string.bike_build
                                "accessories" -> R.string.bike_accessories
                                else -> R.string.bike_other_components
                            }
                        )
                    ) {
                        ComponentsCard(components, locale, onOpenLink)
                    }
                }
                // Only a public bike has a discussion (the API answers 404 for the rest).
                if (summary.isPublic) {
                    ColaListItem(
                        title = stringResource(R.string.comments_open),
                        supporting =
                            pluralStringResource(
                                ru.colabike.core.designsystem.R.plurals.cola_comments,
                                summary.comments,
                                summary.comments,
                            ),
                        icon = ColaIcons.Comment,
                        onClick = { onOpenComments(summary.id, summary.name) },
                    )
                }
                // Only a public bike has rides to show (the API answers 404 for the rest).
                if (summary.isPublic) {
                    ColaListItem(
                        title = stringResource(R.string.bike_rides),
                        supporting = stringResource(R.string.bike_rides_hint),
                        icon = ColaIcons.Route,
                        onClick = { onOpenRides(summary.id, summary.name) },
                    )
                }
                ColaListItem(
                    title = stringResource(R.string.bike_journal),
                    supporting = stringResource(R.string.bike_journal_hint),
                    icon = ColaIcons.Journal,
                    onClick = { onOpenJournal(summary.id, summary.name) },
                )
                bike.manufacturerUrl?.let { url ->
                    ColaListItem(
                        title = stringResource(R.string.bike_manufacturer),
                        supporting = stringResource(R.string.bike_manufacturer_hint),
                        icon = ColaIcons.Info,
                        action = ListItemAction.External,
                        onClick = { onOpenLink(url) },
                    )
                }
            }
        }
    }

/** The facts the owner gave, as label and value; what is empty is not here. */
@Composable
private fun passport(bike: BikeDetail, locale: Locale): List<Pair<String, String>> =
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
        bike.size.takeIf { it.isNotBlank() }?.let { stringResource(R.string.bike_size) to it },
        bike.color.takeIf { it.isNotBlank() }?.let { stringResource(R.string.bike_color) to it },
    )

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
