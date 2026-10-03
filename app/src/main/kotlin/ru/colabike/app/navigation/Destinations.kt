package ru.colabike.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 keys. Serializable, so the back stacks survive process death; arguments are ids,
 * never objects or secrets (a screen loads what it shows). A top-level section is the first key of
 * its own stack.
 */
@Serializable
sealed interface Destination : NavKey {
    @Serializable data object Feed : Destination

    @Serializable data object Bikes : Destination

    @Serializable data class Bike(val id: String) : Destination

    @Serializable data object Rides : Destination

    @Serializable data object Messages : Destination

    @Serializable data object Profile : Destination
}
