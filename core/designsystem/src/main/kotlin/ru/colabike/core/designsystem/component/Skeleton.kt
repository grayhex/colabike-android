package ru.colabike.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.colabike.core.designsystem.R
import ru.colabike.core.designsystem.theme.LocalReducedMotion
import ru.colabike.core.designsystem.theme.Spacing

/**
 * A placeholder block in the theme's small shape, or [shape]; it pulses unless the system removes
 * animations.
 */
fun Modifier.skeleton(shape: Shape? = null): Modifier = composed {
    val pulse =
        if (LocalReducedMotion.current) 1f
        else
            rememberInfiniteTransition(label = "skeleton")
                .animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec =
                        infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
                    label = "pulse",
                )
                .value
    alpha(pulse)
        .background(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            shape ?: MaterialTheme.shapes.small,
        )
}

/** The shape of a [BikeCard] while it loads. */
@Composable
fun BikeCardSkeleton(modifier: Modifier = Modifier) {
    ColaCard(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}) {
        Box(Modifier.fillMaxWidth().aspectRatio(BikePhotoAspect).skeleton(RectangleShape))
        Column(
            Modifier.padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Box(Modifier.fillMaxWidth(0.7f).height(18.dp).skeleton())
            Box(Modifier.fillMaxWidth(0.45f).height(14.dp).skeleton())
        }
    }
}

/** Announces loading once for a whole group of skeletons instead of every block. */
@Composable
fun SkeletonGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val label = stringResource(R.string.cola_loading)
    Box(modifier.semantics { contentDescription = label }) { content() }
}
