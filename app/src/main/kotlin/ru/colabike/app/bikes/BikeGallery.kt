package ru.colabike.app.bikes

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import coil3.network.HttpException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.theme.ColaBikeTheme
import ru.colabike.core.designsystem.theme.ColaMotion
import ru.colabike.core.designsystem.theme.LocalReducedMotion
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Photo

/**
 * The size asked of the server for the full-screen picture (`?width=`). The server serves a closed
 * set of widths, 160, 320, 640 and 1280 (cola `lib/media-sizes.ts`), and answers any other with 400
 * "wrong photo size": 1600 was exactly that, and every full-screen photo came out as "Photo
 * unavailable". 1280 is the largest of the set; a photo narrower than that comes as it is.
 */
private const val FULLSCREEN_WIDTH = "1280"

/** The size for a small picture in a list of a bike's photos (one of the server's own widths). */
private const val THUMBNAIL_WIDTH = "320"

/** The same picture at the size for a full screen; an address that is not a URL stays as it is. */
fun Photo.fullscreenUrl(): String = sized(FULLSCREEN_WIDTH)

/** The same picture small, for a row of a list. */
fun Photo.thumbnailUrl(): String = sized(THUMBNAIL_WIDTH)

private fun Photo.sized(width: String): String =
    url.toHttpUrlOrNull()?.newBuilder()?.setQueryParameter("width", width)?.build()?.toString()
        ?: url

/**
 * The photos of a bike as pages of a pager with "2 / 5" on the corner; a tap opens the full-screen
 * viewer at that photo. A bike without photos shows the quiet "no photo" tile and opens nothing.
 */
@Composable
fun BikeGallery(photos: List<Photo>, aspect: Float, modifier: Modifier = Modifier) {
    val frame = MaterialTheme.shapes.extraLarge
    val shape =
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(frame)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, frame)
    if (photos.isEmpty()) {
        BikePhoto(null, shape)
        return
    }
    val pager = rememberPagerState { photos.size }
    var viewerAt by rememberSaveable { mutableStateOf<Int?>(null) }
    // The photo the viewer was left on is the one the page shows when it closes.
    var lastViewed by rememberSaveable { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    Box(shape) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val description = stringResource(R.string.cola_photo_n_of_m, page + 1, photos.size)
            val action = stringResource(R.string.bike_photo_open)
            // The whole bike, wheels and handlebar: a photo is not cropped to fill the frame.
            BikePhoto(
                photos[page].url,
                Modifier.fillMaxSize()
                    .semantics { contentDescription = description }
                    .clickable(role = Role.Button, onClickLabel = action) { viewerAt = page },
                contentScale = ContentScale.Fit,
            )
        }
        if (photos.size > 1)
            PageCounter(pager.currentPage + 1, photos.size, Modifier.align(Alignment.BottomEnd))
    }
    viewerAt?.let { from ->
        PhotoViewer(
            photos,
            from,
            onPage = { lastViewed = it },
            onDismiss = {
                viewerAt = null
                scope.launch { pager.scrollToPage(lastViewed.coerceIn(photos.indices)) }
            },
        )
    }
}

@Composable
private fun PageCounter(page: Int, count: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.cola_photo_n_of_m_short, page, count)
    Surface(
        modifier = modifier.padding(Spacing.m),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            modifier =
                Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).semantics {
                    contentDescription = label
                },
        )
    }
}

/**
 * The photos at full screen: swipe between them, pinch or double-tap to zoom, the close button or
 * Back to leave. Paging is held while a photo is zoomed so that a drag moves the picture.
 */
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    startAt: Int,
    onDismiss: () -> Unit,
    onPage: (Int) -> Unit = {},
) {
    val pager = rememberPagerState(initialPage = startAt.coerceIn(photos.indices)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    val reportPage by rememberUpdatedState(onPage)
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }
            .collect {
                zoomed = false
                reportPage(it)
            }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        ColaBikeTheme(darkTheme = true) {
            Box(
                Modifier.fillMaxSize()
                    .testTag("gallery:viewer")
                    .background(MaterialTheme.colorScheme.scrim)
            ) {
                HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page
                    ->
                    ZoomablePhoto(
                        photos[page],
                        active = pager.currentPage == page,
                        onZoomed = { if (pager.currentPage == page) zoomed = it },
                    )
                }
                ViewerBar(pager, photos.size, onDismiss)
            }
        }
    }
}

@Composable
private fun ViewerBar(pager: PagerState, count: Int, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.88f))
            .safeDrawingPadding()
            .padding(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss, modifier = Modifier.size(Spacing.touch)) {
            Icon(
                painterResource(ColaIcons.Close),
                contentDescription =
                    stringResource(ru.colabike.core.designsystem.R.string.cola_close),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(R.string.cola_photo_n_of_m, pager.currentPage + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
            textAlign = TextAlign.Center,
        )
    }
}

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f

@Composable
private fun ZoomablePhoto(photo: Photo, active: Boolean, onZoomed: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var attempt by remember { mutableIntStateOf(0) }
    var photoAvailable by remember(photo.url) { mutableStateOf(false) }
    val reportZoom by rememberUpdatedState(onZoomed)
    var gesturing by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val shownScale by
        animateFloatAsState(
            scale,
            animationSpec = if (reduced || gesturing) snap() else ColaMotion.effects(),
            label = "photo zoom",
        )
    val latestScale by rememberUpdatedState(shownScale)
    // Leaving the page resets it after composition, without writing to a parent during layout.
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            reportZoom(false)
        }
    }
    fun toggleZoom() {
        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
        offsetX = 0f
        offsetY = 0f
        reportZoom(scale > 1f)
    }
    Box(
        Modifier.fillMaxSize()
            .testTag("gallery:photo:${photo.id}")
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { toggleZoom() })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    gesturing = true
                    scale = latestScale
                    var transforming = scale > 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) transforming = true
                        if (transforming) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val factor = next / scale
                            val limitX = size.width * (next - 1f) / 2f
                            val limitY = size.height * (next - 1f) / 2f
                            if (centroid.x.isFinite() && centroid.y.isFinite()) {
                                offsetX =
                                    (offsetX * factor +
                                            (size.width / 2f - centroid.x) * (factor - 1f) +
                                            pan.x)
                                        .coerceIn(-limitX, limitX)
                                offsetY =
                                    (offsetY * factor +
                                            (size.height / 2f - centroid.y) * (factor - 1f) +
                                            pan.y)
                                        .coerceIn(-limitY, limitY)
                            }
                            scale = next
                            reportZoom(scale > 1f)
                            event.changes.forEach { it.consume() }
                        }
                        // A single finger at 1x belongs to HorizontalPager and stays unconsumed.
                    } while (event.changes.any { it.pressed })
                    gesturing = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // A new key is a new request: a failed one is not kept, so "try again" really asks again.
        key(attempt) {
            SubcomposeAsyncImage(
                model = photo.fullscreenUrl(),
                onSuccess = { photoAvailable = true },
                onError = { photoAvailable = false },
                onLoading = { photoAvailable = false },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = {},
                error = { PhotoFailure(it.result.throwable, onRetry = { attempt++ }) },
                modifier =
                    Modifier.fillMaxSize()
                        .graphicsLayer(
                            scaleX = shownScale,
                            scaleY = shownScale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
            )
        }
        if (active && photoAvailable) {
            TextButton(
                onClick = { toggleZoom() },
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(Spacing.m)
                        .heightIn(min = Spacing.touch)
                        .testTag("gallery:zoom")
                        .background(MaterialTheme.colorScheme.surfaceContainer, PillShape),
            ) {
                Text(stringResource(if (scale > 1f) R.string.photo_fit else R.string.photo_zoom))
            }
        }
    }
}

/** The photo is not there (the server says 404), as against the server not being reached. */
internal fun Throwable.isPhotoMissing(): Boolean = this is HttpException && response.code == 404

/**
 * Why a full-screen photo did not come: a photo that is gone says so and offers nothing; a failed
 * transfer says so and offers to try again.
 */
@Composable
private fun PhotoFailure(cause: Throwable, onRetry: () -> Unit) {
    val missing = cause.isPhotoMissing()
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl).semantics {
            liveRegion = LiveRegionMode.Polite
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(
                if (missing) ru.colabike.core.designsystem.R.string.cola_photo_unavailable
                else R.string.bike_photo_failed
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (!missing) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = Spacing.touch)) {
                Text(
                    stringResource(ru.colabike.core.designsystem.R.string.cola_retry),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
