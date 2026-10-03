package ru.colabike.app.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 keys. Serializable, so the back stack survives process death; arguments are ids,
 * never objects (a screen loads what it shows).
 */
@Serializable
sealed interface Destination : NavKey {
    @Serializable data object Bikes : Destination

    @Serializable data class Bike(val id: String) : Destination

    @Serializable data object Profile : Destination
}

/** Top-level tabs: a tab owns the whole back stack; switching tabs resets it. */
fun MutableList<NavKey>.selectTab(tab: Destination) {
    if (firstOrNull() == tab && size == 1) return
    clear()
    add(tab)
}

/** Opening a bike replaces the bike already shown, so two panes never stack details. */
fun MutableList<NavKey>.openBike(bike: Destination.Bike) {
    if (lastOrNull() is Destination.Bike) removeAt(lastIndex)
    add(bike)
}
