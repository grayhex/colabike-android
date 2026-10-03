package ru.colabike.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer
import androidx.savedstate.compose.serialization.serializers.MutableStateSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Navigation state: one back stack per top-level section and the section that is on screen. Both
 * survive configuration changes and process death. Modelled on the Navigation 3 "multiple back
 * stacks" recipe; changed only through [Navigator].
 *
 * - The app exits through the start section: its entries are always in the list shown by
 *   NavDisplay, so Back from any other section returns there first.
 * - Leaving a section keeps its stack, its scroll positions (SaveableStateHolder) and its
 *   ViewModels, so Bike → another tab → Bike finds the same bike, scrolled where it was.
 */
class NavigationState(
    val startRoute: NavKey,
    topLevelRoute: MutableState<NavKey>,
    val backStacks: Map<NavKey, MutableList<NavKey>>,
) {
    /** The section on screen. */
    var topLevelRoute: NavKey by topLevelRoute

    /** The stack of the section on screen; a section that vanished from the app falls back. */
    val currentStack: MutableList<NavKey>
        get() = backStacks[topLevelRoute] ?: backStacks.getValue(startRoute)

    /**
     * The entries NavDisplay shows: the start section's, then the current section's. Every section
     * has its own state and ViewModel decorators, so leaving one does not drop what it holds.
     */
    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated = backStacks.mapValues { (_, stack) ->
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                        rememberViewModelStoreNavEntryDecorator<NavKey>(),
                    ),
                entryProvider = entryProvider,
            )
        }
        val inUse =
            if (topLevelRoute == startRoute || topLevelRoute !in backStacks) listOf(startRoute)
            else listOf(startRoute, topLevelRoute)
        return inUse.flatMap { decorated[it].orEmpty() }
    }
}

@Composable
fun rememberNavigationState(startRoute: NavKey, topLevelRoutes: Set<NavKey>): NavigationState {
    val topLevelRoute =
        rememberSerializable(
            startRoute,
            topLevelRoutes,
            serializer = MutableStateSerializer(NavKeySerializer()),
        ) {
            mutableStateOf(startRoute)
        }
    val backStacks = topLevelRoutes.associateWith { key -> rememberNavBackStack(key) }
    return remember(startRoute, topLevelRoutes) {
        NavigationState(startRoute, topLevelRoute, backStacks)
    }
}

/**
 * What the shell does with taps and Back:
 * - a tap on another section switches to it, leaving the stack it had;
 * - a tap on the current section goes to its root if something is open above it, and otherwise asks
 *   the root screen to scroll to the top ([reselects]);
 * - Back pops the current stack; at the root of a section other than the start it returns to the
 *   start section; at the start root it is not handled and the system leaves the app.
 */
class Navigator(private val state: NavigationState) {
    private val reselectEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)

    fun select(section: NavKey) {
        if (section !in state.backStacks) return
        if (section != state.topLevelRoute) {
            state.topLevelRoute = section
            return
        }
        val stack = state.currentStack
        if (stack.size > 1) {
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            reselectEvents.tryEmit(section)
        }
    }

    /** A screen above the current one (profile → devices): pushed, and Back pops it. */
    fun open(destination: Destination) {
        val stack = state.currentStack
        if (stack.lastOrNull() != destination) stack.add(destination)
    }

    /** Opening a bike replaces the bike already shown, so two panes never stack details. */
    fun openBike(id: String) {
        val stack = state.currentStack
        if (stack.lastOrNull() is Destination.Bike) stack.removeAt(stack.lastIndex)
        stack.add(Destination.Bike(id))
    }

    /**
     * Goes where a link points: the section that owns the destination, then the destination on its
     * stack (a bike replaces the bike already shown). Back from it works as for any opened screen.
     */
    fun go(destination: Destination) {
        when (destination) {
            is Destination.Bike -> {
                if (TopLevel.Bikes.root !in state.backStacks) return
                state.topLevelRoute = TopLevel.Bikes.root
                openBike(destination.id)
            }
            else -> Unit
        }
    }

    /** True when Back was used here; false when it belongs to the system. */
    fun back(): Boolean {
        val stack = state.currentStack
        return when {
            stack.size > 1 -> {
                stack.removeAt(stack.lastIndex)
                true
            }
            state.topLevelRoute != state.startRoute -> {
                state.topLevelRoute = state.startRoute
                true
            }
            else -> false
        }
    }

    /** One event each time [section] is tapped while it is already on screen at its root. */
    fun reselects(section: NavKey): Flow<Unit> =
        reselectEvents.asSharedFlow().filter { it == section }.map {}
}
