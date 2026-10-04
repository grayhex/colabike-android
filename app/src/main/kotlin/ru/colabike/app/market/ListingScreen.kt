package ru.colabike.app.market

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.colabike.app.R
import ru.colabike.app.auth.AuthActions
import ru.colabike.app.bikes.BikeGallery
import ru.colabike.app.links.LocalLinkOpener
import ru.colabike.app.links.SiteLinks
import ru.colabike.app.ui.LocalSignInRequest
import ru.colabike.app.ui.resolve
import ru.colabike.core.auth.AuthState
import ru.colabike.core.designsystem.component.ColaCard
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaListItem
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.Fact
import ru.colabike.core.designsystem.component.ListItemAction
import ru.colabike.core.designsystem.component.ListingCard
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.PillBadge
import ru.colabike.core.designsystem.component.UserRow
import ru.colabike.core.designsystem.component.listingPrice
import ru.colabike.core.designsystem.component.listingPriceIsSum
import ru.colabike.core.designsystem.component.listingTypeLabel
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.BikeId
import ru.colabike.core.model.Listing
import ru.colabike.core.model.ListingCondition
import ru.colabike.core.model.ListingId
import ru.colabike.core.model.ListingStatus
import ru.colabike.core.model.MarketRepository

/** Where a listing's page leads. */
class ListingActions(
    val onBack: () -> Unit,
    /** Another listing of the same seller. */
    val onOpenListing: (ListingId) -> Unit = {},
    /** The seller's page; [ref] is the person's UUID. */
    val onOpenSeller: (ref: String) -> Unit = {},
    /** All the listings of one seller; [username] is the filter the market list takes. */
    val onOpenSellerListings: (username: String) -> Unit = {},
    val onOpenComponent: (modelId: String) -> Unit = {},
    val onOpenBike: (BikeId) -> Unit = {},
)

@Composable
fun ListingRoute(
    repository: MarketRepository,
    auth: AuthActions,
    links: SiteLinks,
    id: ListingId,
    actions: ListingActions,
) {
    val viewModel = viewModel(key = "listing:${id.value}") { ListingViewModel(repository, id) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val authState by auth.state.collectAsStateWithLifecycle()
    val signIn = LocalSignInRequest.current
    val opener = LocalLinkOpener.current
    val signedIn = authState is AuthState.SignedIn
    ListingScreen(
        state = state,
        actions = actions,
        onRetry = viewModel::load,
        // A closed listing answers a guest "not found" exactly as a missing one does.
        onSignIn = if (signedIn) null else signIn,
        // A guest is asked to sign in first; nothing is saved or asked for them afterwards.
        onToggleSaved = if (signedIn) viewModel::toggleSaved else signIn,
        onShowContact = if (signedIn) viewModel::showContact else signIn,
        onHideContact = viewModel::hideContact,
        signedIn = signedIn,
        // The page on the site, only if the path is a path of this site.
        onOpenSite = { path -> links.pageFromPath(path)?.let(opener::open) },
        onOpenLink = opener::open,
    )
}

/**
 * One listing: the pictures, what is offered and on what terms, who offers it and how to reach
 * them. The contact is not part of the page: it appears after the viewer asks for it.
 */
@Composable
fun ListingScreen(
    state: ListingUiState,
    actions: ListingActions,
    onRetry: () -> Unit = {},
    onSignIn: (() -> Unit)? = null,
    onToggleSaved: () -> Unit = {},
    onShowContact: () -> Unit = {},
    onHideContact: () -> Unit = {},
    signedIn: Boolean = true,
    onOpenSite: (path: String) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(title = stringResource(R.string.listing_title), onBack = actions.onBack)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                ListingUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is ListingUiState.Failed ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (state.notFound) {
                            NotFound(onSignIn)
                        } else {
                            ErrorState(state.message.resolve(), onRetry = onRetry)
                        }
                    }
                is ListingUiState.Loaded ->
                    Content(
                        state = state,
                        actions = actions,
                        signedIn = signedIn,
                        onToggleSaved = onToggleSaved,
                        onShowContact = onShowContact,
                        onHideContact = onHideContact,
                        onOpenSite = onOpenSite,
                        onOpenLink = onOpenLink,
                    )
            }
        }
    }
}

/**
 * Gone, hidden or someone's draft: the same words, and for a guest the thought that it may be
 * theirs.
 */
@Composable
private fun NotFound(onSignIn: (() -> Unit)?) {
    Column(
        Modifier.padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            stringResource(R.string.listing_not_found_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.listing_not_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onSignIn != null) {
            Text(
                stringResource(R.string.listing_private_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onSignIn, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.profile_sign_in))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Content(
    state: ListingUiState.Loaded,
    actions: ListingActions,
    signedIn: Boolean,
    onToggleSaved: () -> Unit,
    onShowContact: () -> Unit,
    onHideContact: () -> Unit,
    onOpenSite: (path: String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val listing = state.listing
    val brief = listing.brief
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("listing:page"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = ContentWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.s, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (listing.photos.isNotEmpty()) BikeGallery(listing.photos, aspect = 4f / 3f)
            Notice(listing)
            val kind = listingTypeLabel(brief.type)
            val condition = listing.condition?.let { conditionLabel(it) }
            if (kind != null || condition != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    kind?.let { PillBadge(it) }
                    condition?.let { PillBadge(it) }
                }
            }
            Text(
                brief.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                listingPrice(brief.type, brief.price, brief.currency),
                style =
                    if (listingPriceIsSum(brief.type, brief.price)) ColaTheme.textStyles.numeral
                    else MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("listing:price"),
            )
            if (brief.location.isNotBlank()) Fact(ColaIcons.Location, brief.location)
            if (state.canSave) SaveButton(state, onToggleSaved)
            state.saveError?.let {
                Text(
                    it.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (listing.description.isNotBlank()) {
                Section(stringResource(R.string.listing_description)) {
                    Text(listing.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Related(listing, actions, onOpenSite)
            Section(stringResource(R.string.listing_seller)) {
                UserRow(
                    brief.author,
                    onClick = { actions.onOpenSeller(brief.author.id.value) },
                )
                ContactSection(state, signedIn, onShowContact, onHideContact, onOpenLink)
            }
            Others(state, actions)
            if (listing.path.isNotBlank()) {
                ColaListItem(
                    title = stringResource(R.string.listing_open_site),
                    supporting = stringResource(R.string.listing_open_site_hint),
                    icon = ColaIcons.Info,
                    action = ListItemAction.External,
                    onClick = { onOpenSite(listing.path) },
                    modifier = Modifier.testTag("listing:site"),
                )
            }
        }
    }
}

/** Why a listing that was opened by its id is not on the market. */
@Composable
private fun Notice(listing: Listing) {
    val (title, body) =
        when {
            listing.status == ListingStatus.Draft ->
                R.string.listing_draft_title to R.string.listing_draft
            listing.status == ListingStatus.Sold ->
                R.string.listing_sold_title to R.string.listing_sold
            listing.expired -> R.string.listing_expired_title to R.string.listing_expired
            else -> return
        }
    ColaCard(Modifier.fillMaxWidth().testTag("listing:notice")) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            PillBadge(stringResource(title))
            Text(
                stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun conditionLabel(condition: ListingCondition): String =
    stringResource(
        when (condition) {
            ListingCondition.New -> R.string.market_condition_new
            ListingCondition.Used -> R.string.market_condition_used
        }
    )

/** The catalog model and the seller's bike the listing is tied to, each a way on. */
@Composable
private fun Related(
    listing: Listing,
    actions: ListingActions,
    onOpenSite: (path: String) -> Unit,
) {
    if (listing.componentModel == null && listing.bikeModel == null && listing.linkedBike == null) {
        return
    }
    Section(stringResource(R.string.listing_related)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            listing.componentModel?.let { model ->
                ColaListItem(
                    title = model.name,
                    supporting = stringResource(R.string.listing_component_model_hint),
                    icon = ColaIcons.Build,
                    onClick = { actions.onOpenComponent(model.id) },
                    modifier = Modifier.testTag("listing:component"),
                )
            }
            listing.bikeModel?.let { model ->
                ColaListItem(
                    title = model.name,
                    supporting = stringResource(R.string.listing_bike_model_hint),
                    icon = ColaIcons.Bike,
                    action = ListItemAction.External,
                    onClick = { onOpenSite(model.path) },
                    modifier = Modifier.testTag("listing:bike_model"),
                )
            }
            listing.linkedBike?.let { bike ->
                ColaListItem(
                    title = bike.name,
                    supporting = stringResource(R.string.listing_linked_bike_hint),
                    icon = ColaIcons.Bike,
                    onClick = { actions.onOpenBike(bike.id) },
                    modifier = Modifier.testTag("listing:bike"),
                )
            }
        }
    }
}

/**
 * The contact of the seller: said in words what it takes, asked for by a tap and never before, and
 * shown as text that the reader copies or, if it is a plain `https` address, opens. Nothing else is
 * done with it.
 */
@Composable
private fun ContactSection(
    state: ListingUiState.Loaded,
    signedIn: Boolean,
    onShow: () -> Unit,
    onHide: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val listing = state.listing
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            stringResource(R.string.listing_contact),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        when {
            listing.isOwner -> Note(R.string.listing_contact_owner)
            !listing.onMarket -> Note(R.string.listing_contact_off)
            !listing.hasContact -> Note(R.string.listing_contact_none)
            else ->
                when (val contact = state.contact) {
                    is ContactState.Shown -> ShownContact(contact.text, onHide, onOpenLink)
                    ContactState.Loading ->
                        Text(
                            stringResource(R.string.listing_contact_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    else -> {
                        Note(
                            if (signedIn) R.string.listing_contact_hint
                            else R.string.listing_contact_sign_in
                        )
                        (contact as? ContactState.Failed)?.let {
                            Text(
                                it.message.resolve(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier =
                                    Modifier.testTag("listing:contact_error").semantics {
                                        liveRegion = LiveRegionMode.Polite
                                    },
                            )
                        }
                        Button(
                            onClick = onShow,
                            modifier =
                                Modifier.fillMaxWidth()
                                    .heightIn(min = Spacing.touch)
                                    .testTag("listing:contact_show"),
                        ) {
                            Text(
                                stringResource(
                                    if (signedIn) R.string.listing_contact_show
                                    else R.string.profile_sign_in
                                )
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ShownContact(text: String, onHide: () -> Unit, onOpenLink: (String) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    // A whole-string address with `https` is a link the reader can open; nothing else is.
    val link = text.trim().takeIf { it.toHttpUrlOrNull()?.scheme == "https" }
    ColaCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.testTag("listing:contact"),
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(sensitiveClip(text)))
                            copied = true
                        }
                    },
                    modifier =
                        Modifier.heightIn(min = Spacing.touch).testTag("listing:contact_copy"),
                ) {
                    Text(stringResource(R.string.listing_contact_copy))
                }
                if (link != null) {
                    OutlinedButton(
                        onClick = { onOpenLink(link) },
                        modifier =
                            Modifier.heightIn(min = Spacing.touch).testTag("listing:contact_link"),
                    ) {
                        Text(stringResource(R.string.listing_contact_open_link))
                    }
                }
                TextButton(
                    onClick = onHide,
                    modifier =
                        Modifier.heightIn(min = Spacing.touch).testTag("listing:contact_hide"),
                ) {
                    Text(stringResource(R.string.listing_contact_hide))
                }
            }
            if (copied) {
                Text(
                    stringResource(R.string.listing_contact_copied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * The copy of a contact is marked sensitive, so the system keeps it out of its clipboard preview.
 */
private fun sensitiveClip(text: String): ClipData =
    ClipData.newPlainText("", text).apply {
        description.extras = PersistableBundle().apply { putBoolean(SENSITIVE_EXTRA, true) }
    }

// ClipDescription.EXTRA_IS_SENSITIVE is API 33; the key is ignored by older systems.
private const val SENSITIVE_EXTRA = "android.content.extra.IS_SENSITIVE"

@Composable
private fun SaveButton(state: ListingUiState.Loaded, onToggle: () -> Unit) {
    val saved = state.saved
    val description =
        stringResource(
            if (saved) R.string.listing_unsave_description else R.string.listing_save_description
        )
    val content: @Composable () -> Unit = {
        Icon(
            painterResource(if (saved) ColaIcons.BookmarkFilled else ColaIcons.Bookmark),
            contentDescription = null,
        )
        Text(
            stringResource(if (saved) R.string.listing_saved else R.string.listing_save),
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
    val modifier =
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).testTag("listing:save").semantics {
            contentDescription = description
        }
    Row(Modifier.fillMaxWidth()) {
        if (saved) {
            FilledTonalButton(
                onClick = onToggle,
                enabled = !state.saving,
                modifier = modifier,
                content = { content() },
            )
        } else {
            OutlinedButton(
                onClick = onToggle,
                enabled = !state.saving,
                modifier = modifier,
                content = { content() },
            )
        }
    }
}

/** Up to four more of the seller's listings that are on the market, and the way to all of them. */
@Composable
private fun Others(state: ListingUiState.Loaded, actions: ListingActions) {
    val others = state.others ?: return
    if (others.items.isEmpty()) return
    val seller = state.listing.brief.author
    Section(stringResource(R.string.listing_others)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            others.items.forEach { other ->
                ListingCard(
                    other,
                    onClick = { actions.onOpenListing(ListingId(other.id)) },
                    modifier = Modifier.testTag("listing:other:${other.id}"),
                )
            }
            if (others.total > 0) {
                ColaListItem(
                    title = stringResource(R.string.listing_others_all, others.total),
                    supporting = stringResource(R.string.listing_others_all_hint),
                    icon = ColaIcons.Tag,
                    onClick = { actions.onOpenSellerListings(seller.username) },
                    modifier = Modifier.testTag("listing:others_all"),
                )
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

private val ContentWidth = 720.dp
