package ru.colabike.app.rides.map

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.roundToInt
import ru.colabike.app.R
import ru.colabike.core.designsystem.theme.Spacing
import ru.colabike.core.model.RideRoute

internal val LocalMapSnapshots = staticCompositionLocalOf<MapSnapshots?> { null }

/** A static basemap and public route: scrolling the page never pans a map by accident. */
@Composable
internal fun MapLibrePreview(route: RideRoute, styleOverride: String?, modifier: Modifier) {
    val shared = LocalMapSnapshots.current
    val own = remember(shared) { shared ?: MapSnapshots() }
    DisposableEffect(own) {
        onDispose { if (shared == null) own.clear() }
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current.density
    val scheme = MaterialTheme.colorScheme
    val style = BasemapStyle.choose(styleOverride, scheme.background.luminance() < 0.5f)
    BoxWithConstraints(modifier) {
        val width = (maxWidth.value * density).roundToInt().coerceIn(64, 1024)
        val height = (width * maxHeight.value / maxWidth.value).roundToInt().coerceIn(64, 1024)
        val key =
            SnapshotKey(
                route,
                style,
                SnapshotLook(
                    scheme.primary.toArgb(),
                    scheme.surfaceContainer.toArgb(),
                    scheme.surfaceContainerLow.toArgb(),
                ),
                width,
                height,
                width / maxWidth.value,
            )
        var bitmap by remember(key, own) { mutableStateOf<Bitmap?>(null) }
        var failed by remember(key, own) { mutableStateOf(false) }
        LaunchedEffect(key, own, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                bitmap = own.image(context, key)
                failed = bitmap == null
            }
        }
        val image = bitmap
        if (image != null) {
            Image(
                image.asImageBitmap(),
                contentDescription = stringResource(R.string.route_map_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            // The geometry is truthful while tiles are loading or unavailable. Name it a sketch,
            // and keep its height so that the title and metrics never jump after a response.
            RouteSketch(route, Modifier.fillMaxSize())
            Text(
                stringResource(
                    if (failed) R.string.route_preview_offline else R.string.route_preview_loading
                ),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .padding(Spacing.s)
                        .background(scheme.surface, MaterialTheme.shapes.small)
                        .padding(Spacing.xs),
            )
        }
    }
}
