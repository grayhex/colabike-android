package ru.colabike.app.bikes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import ru.colabike.app.R
import ru.colabike.core.designsystem.component.BikePhoto
import ru.colabike.core.designsystem.component.ColaIcons
import ru.colabike.core.designsystem.component.PhotoTile
import ru.colabike.core.designsystem.theme.PillShape
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.Photo

/** The size asked of the server for the full-screen picture (`?width=`, cola api-v1). */
private const val FULLSCREEN_WIDTH = "1600"

/** The same picture at the size for a full screen; an address that is not a URL stays as it is. */
fun Photo.fullscreenUrl(): String =
    url.toHttpUrlOrNull()
        ?.newBuilder()
        ?.setQueryParameter("width", FULLSCREEN_WIDTH)
        ?.build()
        ?.toString() ?: url

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
    Box(shape) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val description = stringResource(R.string.cola_photo_n_of_m, page + 1, photos.size)
            val action = stringResource(R.string.bike_photo_open)
            BikePhoto(
                photos[page].url,
                Modifier.fillMaxSize()
                    .semantics { contentDescription = description }
                    .clickable(role = Role.Button, onClickLabel = action) { viewerAt = page },
            )
        }
        if (photos.size > 1)
            PageCounter(pager.currentPage + 1, photos.size, Modifier.align(Alignment.BottomEnd))
    }
    viewerAt?.let { PhotoViewer(photos, it, onDismiss = { viewerAt = null }) }
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
fun PhotoViewer(photos: List<Photo>, startAt: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = startAt.coerceIn(photos.indices)) { photos.size }
    var zoomed by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                ZoomablePhoto(
                    photos[page],
                    active = pager.currentPage == page,
                    onZoomed = { zoomed = it },
                )
            }
            ViewerBar(pager, photos.size, onDismiss)
        }
    }
}

@Composable
private fun ViewerBar(pager: PagerState, count: Int, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxWidth().safeDrawingPadding().padding(Spacing.s)) {
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                painterResource(ColaIcons.Close),
                contentDescription =
                    stringResource(ru.colabike.core.designsystem.R.string.cola_close),
                tint = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
        Text(
            stringResource(R.string.cola_photo_n_of_m, pager.currentPage + 1, count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
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
    // Leaving the page puts the photo back, so it is never found zoomed on return.
    if (!active && scale != 1f) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        onZoomed(false)
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else DOUBLE_TAP_ZOOM
                        offsetX = 0f
                        offsetY = 0f
                        onZoomed(scale > 1f)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    onZoomed(scale > 1f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = photo.fullscreenUrl(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            loading = {},
            error = {
                PhotoTile(
                    stringResource(ru.colabike.core.designsystem.R.string.cola_photo_unavailable)
                )
            },
            modifier =
                Modifier.fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY,
                    ),
        )
    }
}
