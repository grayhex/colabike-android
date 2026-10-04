package ru.colabike.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.ColaTheme
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.JournalStatus
import ru.colabike.core.model.JournalSummary
import ru.colabike.core.model.ListingBrief

/** The words for the kinds of journal entry the API has; a kind that is not known has none. */
@Composable
fun journalKindLabel(kind: String): String? =
    when (kind) {
        "build" -> stringResource(R.string.cola_journal_kind_build)
        "service" -> stringResource(R.string.cola_journal_kind_service)
        "review" -> stringResource(R.string.cola_journal_kind_review)
        "question" -> stringResource(R.string.cola_journal_kind_question)
        "story" -> stringResource(R.string.cola_journal_kind_story)
        else -> null
    }

/**
 * A journal entry in a list: kind and (for the owner) draft, the title in the serif voice, the
 * start of the text, then the bike, the day and the mileage, and at the bottom the author and the
 * counts. TalkBack reads one sentence and offers one action.
 */
@Composable
fun JournalCard(entry: JournalSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val kind = journalKindLabel(entry.kind)
    val draft =
        if (entry.status == JournalStatus.Draft) stringResource(R.string.cola_journal_draft)
        else null
    val day = entry.eventDate?.let { date(it, locale) }
    val mileage =
        entry.mileageKm?.let { stringResource(R.string.cola_distance_km, integer(it, locale)) }
    val likes = pluralStringResource(R.plurals.cola_likes, entry.likes, entry.likes)
    val comments = pluralStringResource(R.plurals.cola_comments, entry.comments, entry.comments)
    val author = stringResource(R.string.cola_by_author, entry.author.displayName)
    val description =
        listOfNotNull(
                kind,
                draft,
                entry.title,
                entry.bike.name,
                day,
                mileage,
                entry.excerpt.ifBlank { null },
                author,
                likes,
                comments,
            )
            .joinToString(", ")
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openLabel) {
                    onClick()
                    true
                }
            },
    ) {
        Column(
            Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (kind != null || draft != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    kind?.let { PillBadge(it, icon = ColaIcons.Journal) }
                    draft?.let { PillBadge(it) }
                }
            }
            Text(
                entry.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.excerpt.isNotBlank()) {
                Text(
                    entry.excerpt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Fact(ColaIcons.Bike, entry.bike.name)
                day?.let { Fact(ColaIcons.Calendar, it) }
                mileage?.let { Fact(ColaIcons.Route, it) }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Avatar(entry.author.displayName, entry.author.avatarUrl, size = 24.dp)
                Text(
                    entry.author.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Counter(
                    if (entry.liked) ColaIcons.LikeFilled else ColaIcons.Like,
                    entry.likes,
                    liked = entry.liked,
                )
                Counter(ColaIcons.Comment, entry.comments)
            }
        }
    }
}

/**
 * A market listing in a list or the feed: a small picture, what is offered, the price and the
 * place. Without [onClick] it only tells and says no "open".
 */
@Composable
fun ListingCard(
    listing: ListingBrief,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val type = listingTypeLabel(listing.type)
    val price = listingPrice(listing.type, listing.price, listing.currency)
    val description =
        listOfNotNull(
                type,
                listing.title,
                price,
                listing.location.ifBlank { null },
                stringResource(R.string.cola_by_author, listing.author.displayName),
            )
            .joinToString(", ")
    val openLabel = stringResource(R.string.cola_open)
    ColaCard(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        modifier =
            modifier.fillMaxWidth().clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = openLabel) {
                        onClick()
                        true
                    }
                }
            },
    ) {
        // At a large font the text needs the whole width; the picture is the first thing to go.
        val bigFont = LocalDensity.current.fontScale > BigFontScale
        Row(
            Modifier.padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.Top,
        ) {
            if (!bigFont) {
                Thumbnail(listing.cover?.url, Modifier.size(ThumbnailSize))
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                type?.let { Eyebrow(it) }
                Text(
                    listing.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // Only a number is set as a numeral; "by agreement" is words and may wrap.
                Text(
                    price,
                    style =
                        if (listingPriceIsSum(listing.type, listing.price))
                            ColaTheme.textStyles.numeral
                        else MaterialTheme.typography.titleMedium,
                )
                if (listing.location.isNotBlank()) Fact(ColaIcons.Location, listing.location)
            }
        }
    }
}

/** The kind of a listing in words ("Продаётся"); null for one a later server version adds. */
@Composable
fun listingTypeLabel(type: String): String? =
    when (type) {
        "sale" -> stringResource(R.string.cola_listing_sale)
        "wanted" -> stringResource(R.string.cola_listing_wanted)
        "exchange" -> stringResource(R.string.cola_listing_exchange)
        "free" -> stringResource(R.string.cola_listing_free)
        else -> null
    }

/**
 * A price as a person reads it: the sum in its own currency, "free" for a gift, "by agreement" for
 * no price. Nothing is converted between currencies.
 */
@Composable
fun listingPrice(type: String, price: Double?, currency: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return when {
        type == "free" -> stringResource(R.string.cola_price_free)
        price == null -> stringResource(R.string.cola_price_by_agreement)
        else ->
            stringResource(R.string.cola_price_value, amount(price, locale), currencySign(currency))
    }
}

/** Only a sum is set as a numeral; "free" and "by agreement" are words and may wrap. */
fun listingPriceIsSum(type: String, price: Double?): Boolean = price != null && type != "free"

private val ThumbnailSize = 80.dp
private const val BigFontScale = 1.3f

/** The listing's picture; without one, a quiet tile with no words (too small for them). */
@Composable
private fun Thumbnail(url: String?, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.small
    if (url != null) {
        BikePhoto(url, modifier.clip(shape))
    } else {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(ColaIcons.Image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

private fun currencySign(code: String): String =
    when (code) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        else -> code
    }

/** A small icon with a line of quiet text: where, when, how far. */
@Composable
fun Fact(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
