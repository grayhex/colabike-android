package ru.colabike.app.nearby

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.app.R
import ru.colabike.app.ui.resolve
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.ColaTopBar
import ru.colabike.core.designsystem.component.EmptyState
import ru.colabike.core.designsystem.component.ErrorState
import ru.colabike.core.designsystem.component.LoadingState
import ru.colabike.core.designsystem.component.RideCard
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.NearbyOffer
import ru.colabike.core.model.NearbyOffersState
import ru.colabike.core.model.NearbyReason
import ru.colabike.core.model.RideId

/** What the offers screen can ask for. */
data class NearbyOffersActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenRide: (RideId) -> Unit = {},
    /** Where the person fixes what the list is missing: the area, the switch. */
    val onOpenSettings: () -> Unit = {},
)

private val ContentWidth = 640.dp

/**
 * The public rides on now in the person's area. A card names why it is here (the area, the date of
 * the person's own intention) and never a place or a distance: the server has none to give.
 */
@Composable
fun NearbyOffersScreen(state: NearbyOffersUiState, actions: NearbyOffersActions) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ColaTopBar(
                title = stringResource(R.string.nearby_offers_title),
                onBack = actions.onBack,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                NearbyOffersUiState.Loading -> LoadingState(Modifier.fillMaxSize())
                is NearbyOffersUiState.Failed ->
                    ErrorState(
                        state.message.resolve(),
                        onRetry = actions.onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                is NearbyOffersUiState.Loaded -> Loaded(state, actions)
            }
        }
    }
}

@Composable
private fun Loaded(state: NearbyOffersUiState.Loaded, actions: NearbyOffersActions) {
    val offers = state.offers
    when {
        offers.state == NearbyOffersState.Ready && offers.items.isNotEmpty() ->
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh) {
                LazyColumn(
                    Modifier.fillMaxSize().testTag("nearby-offers:list"),
                    contentPadding = PaddingValues(Spacing.screen),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    state.refreshError?.let { error ->
                        item {
                            Text(
                                error.resolve(),
                                style =
                                    androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                modifier =
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            TextButton(onClick = actions.onRefresh) {
                                Text(
                                    stringResource(
                                        ru.colabike.core.designsystem.R.string.cola_retry
                                    )
                                )
                            }
                        }
                    }
                    items(offers.items, key = { it.ride.id.value }) { offer ->
                        OfferCard(offer, actions)
                    }
                }
            }
        else -> {
            val (title, message, action) = emptyText(offers.state)
            EmptyState(
                title = stringResource(title),
                message = stringResource(message),
                icon = ColaIcons.Location,
                actionLabel = action?.let { stringResource(it) },
                onAction = if (action != null) actions.onOpenSettings else null,
                secondaryLabel = stringResource(ru.colabike.core.designsystem.R.string.cola_retry),
                onSecondary = actions.onRefresh,
                modifier = Modifier.fillMaxSize().testTag("nearby-offers:empty"),
            )
        }
    }
}

@Composable
private fun OfferCard(offer: NearbyOffer, actions: NearbyOffersActions) {
    val badges =
        offer.reasons.map {
            stringResource(
                when (it) {
                    NearbyReason.Nearby -> R.string.notifications_reason_nearby
                    NearbyReason.Intent -> R.string.notifications_reason_intent
                }
            )
        }
    RideCard(
        offer.ride,
        onClick = { actions.onOpenRide(offer.ride.id) },
        badges = badges,
        modifier =
            Modifier.widthIn(max = ContentWidth).testTag("nearby-offer:${offer.ride.id.value}"),
    )
}

/** What an empty list says: why it is empty and, where the person can mend it, the way. */
private fun emptyText(state: NearbyOffersState): Triple<Int, Int, Int?> =
    when (state) {
        NearbyOffersState.Ready ->
            Triple(R.string.nearby_offers_none_title, R.string.nearby_offers_none, null)
        NearbyOffersState.Off ->
            Triple(
                R.string.nearby_offers_off_title,
                R.string.nearby_offers_off,
                R.string.nearby_offers_configure,
            )
        NearbyOffersState.NoArea ->
            Triple(
                R.string.nearby_offers_no_area_title,
                R.string.nearby_offers_no_area,
                R.string.nearby_offers_configure,
            )
        NearbyOffersState.Expired ->
            Triple(
                R.string.nearby_offers_expired_title,
                R.string.nearby_offers_expired,
                R.string.nearby_offers_configure,
            )
        NearbyOffersState.Unavailable ->
            Triple(
                R.string.nearby_offers_unavailable_title,
                R.string.nearby_offers_unavailable,
                null,
            )
    }
