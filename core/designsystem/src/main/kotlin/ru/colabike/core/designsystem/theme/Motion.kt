package ru.colabike.core.designsystem.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion tokens: bouncy springs for movement ("spatial"), critically damped
 * ones for colour and opacity ("effects"). Stable material3 1.4 keeps MotionScheme and
 * MaterialExpressiveTheme internal, so components and transitions take their springs from here;
 * when material3 1.5 is stable the theme switches to MaterialExpressiveTheme
 * (docs/adr/0001-platform-baseline.md). System "remove animations" scales these to zero.
 */
object ColaMotion {
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    fun <T> fastSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    fun <T> slowSpatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)

    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    fun <T> fastEffects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 3800f)
}
