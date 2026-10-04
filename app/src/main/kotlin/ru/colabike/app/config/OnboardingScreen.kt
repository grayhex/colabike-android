package ru.colabike.app.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.launch
import ru.colabike.app.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.OnboardingItem

/**
 * The introduction the server asks for: its pages in order, with a picture where the device has it.
 * The server gives the words and pictures; the layout is the app's own. Skipping and finishing are
 * the same for the person (they have seen this revision), so both end in [onDone]. A missing
 * picture leaves the page as text, and long text and a big font scroll.
 */
@Composable
fun OnboardingScreen(
    items: List<OnboardingItem>,
    imageOf: (String) -> File?,
    onDone: () -> Unit,
) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == items.lastIndex
    // Back goes to the page before; from the first page it leaves the app, as anywhere else.
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding")
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.s)) {
            if (!last) {
                TextButton(
                    onClick = onDone,
                    modifier = Modifier.align(Alignment.CenterEnd).testTag("onboarding:skip"),
                ) {
                    Text(stringResource(R.string.onboarding_skip))
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Page(items[page], imageOf)
        }
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            if (items.size > 1) {
                val description =
                    stringResource(R.string.onboarding_page, pager.currentPage + 1, items.size)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier =
                        Modifier.semantics {
                            contentDescription = description
                            liveRegion = LiveRegionMode.Polite
                        },
                ) {
                    repeat(items.size) { index ->
                        Box(
                            Modifier.size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pager.currentPage)
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }
            }
            Button(
                onClick = {
                    if (last) onDone()
                    else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                },
                modifier =
                    Modifier.widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touch)
                        .testTag("onboarding:next"),
            ) {
                Text(
                    stringResource(if (last) R.string.onboarding_done else R.string.onboarding_next)
                )
            }
        }
    }
}

@Composable
private fun Page(item: OnboardingItem, imageOf: (String) -> File?) {
    val image = item.imageUrl?.let(imageOf)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            if (image != null) {
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier.fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(MaterialTheme.shapes.extraLarge),
                )
            }
            Text(
                item.title,
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() },
            )
            item.body?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
