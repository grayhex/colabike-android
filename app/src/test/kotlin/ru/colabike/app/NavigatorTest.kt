package ru.colabike.app

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.NavigationState
import ru.colabike.app.navigation.Navigator
import ru.colabike.app.navigation.TopLevel

/** What the shell does with taps and Back, without a screen. */
class NavigatorTest {
    private val bikes = Destination.Bikes
    private val profile = Destination.Profile

    private val state =
        NavigationState(
            startRoute = bikes,
            topLevelRoute = mutableStateOf<NavKey>(bikes),
            backStacks =
                mapOf(
                    bikes to mutableListOf<NavKey>(bikes),
                    profile to mutableListOf<NavKey>(profile),
                ),
        )
    private val navigator = Navigator(state)

    @Test
    fun `leaving a section keeps what is open in it`() {
        navigator.openBike("b1")
        navigator.select(profile)
        navigator.select(bikes)

        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack).containsExactly(bikes, Destination.Bike("b1")).inOrder()
    }

    @Test
    fun `opening a bike replaces the bike already open`() {
        navigator.openBike("b1")
        navigator.openBike("b2")

        assertThat(state.currentStack).containsExactly(bikes, Destination.Bike("b2")).inOrder()
    }

    @Test
    fun `tapping the current section with a bike open returns to its root`() {
        navigator.openBike("b1")
        navigator.select(bikes)

        assertThat(state.currentStack).containsExactly(bikes)
    }

    @Test
    fun `to the root drops every screen opened above it and stays in the section`() {
        navigator.openBike("b1")
        navigator.open(Destination.BikeParts("b1"))
        navigator.open(Destination.BikePart("b1"))

        navigator.toRoot()

        assertThat(state.currentStack).containsExactly(bikes)
        assertThat(state.topLevelRoute).isEqualTo(bikes)
        // At the root already, it does nothing (and the screen is not told to scroll).
        navigator.toRoot()
        assertThat(state.currentStack).containsExactly(bikes)
    }

    @Test
    fun `tapping the current section at its root asks the screen to scroll to the top`() = runTest {
        navigator.reselects(bikes).test {
            navigator.select(bikes)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reselect is heard only by its own section`() = runTest {
        navigator.reselects(bikes).test {
            navigator.select(profile)
            navigator.select(profile)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching sections is not a reselect`() = runTest {
        navigator.reselects(profile).test {
            navigator.select(profile)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back pops, then goes to the start section, then belongs to the system`() {
        navigator.select(profile)
        assertThat(navigator.back()).isTrue()
        assertThat(state.topLevelRoute).isEqualTo(bikes)

        navigator.openBike("b1")
        assertThat(navigator.back()).isTrue()
        assertThat(state.currentStack).containsExactly(bikes)

        assertThat(navigator.back()).isFalse()
        assertThat(state.topLevelRoute).isEqualTo(bikes)
    }

    @Test
    fun `back from another section leaves the start section as it was`() {
        navigator.openBike("b1")
        navigator.select(profile)
        navigator.back()

        assertThat(state.currentStack).containsExactly(bikes, Destination.Bike("b1")).inOrder()
    }

    @Test
    fun `a section that is not part of the app cannot be selected`() {
        navigator.select(Destination.Messages)

        assertThat(state.topLevelRoute).isEqualTo(bikes)
    }

    @Test
    fun `only sections with a screen are shown, and the app starts on one of them`() {
        // Enabling a section is part of the slice that builds it: update this list together with
        // the flag in TopLevel.
        assertThat(TopLevel.shown)
            .containsExactly(
                TopLevel.Feed,
                TopLevel.Bikes,
                TopLevel.Rides,
                TopLevel.Messages,
                TopLevel.Profile,
            )
            .inOrder()
        assertThat(TopLevel.shown).contains(TopLevel.start)
    }

    @Test
    fun `a link to a bike goes to the bikes section from any other`() {
        navigator.select(profile)
        navigator.open(Destination.Devices)

        navigator.go(Destination.Bike("b9"))

        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack).containsExactly(bikes, Destination.Bike("b9")).inOrder()
        // What was open in the profile stays where it was.
        assertThat(state.backStacks.getValue(profile))
            .containsExactly(profile, Destination.Devices)
            .inOrder()
    }

    @Test
    fun `a link replaces the bike on screen and Back closes it`() {
        navigator.openBike("b1")

        navigator.go(Destination.Bike("b2"))
        assertThat(state.currentStack).containsExactly(bikes, Destination.Bike("b2")).inOrder()

        assertThat(navigator.back()).isTrue()
        assertThat(state.currentStack).containsExactly(bikes)
    }

    @Test
    fun `a destination with no place in the shell is ignored`() {
        navigator.go(Destination.Profile)

        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack).containsExactly(bikes)
    }

    @Test
    fun `Back from a screen opened in the profile returns to the profile root`() {
        navigator.select(profile)
        navigator.open(Destination.About)
        navigator.open(Destination.Licenses)

        assertThat(navigator.back()).isTrue()
        assertThat(state.currentStack).containsExactly(profile, Destination.About).inOrder()
        assertThat(navigator.back()).isTrue()
        assertThat(state.currentStack).containsExactly(profile)
    }

    @Test
    fun `a link to a person goes to the bikes section with the person on top`() {
        navigator.select(profile)

        navigator.go(Destination.Person("test-rider"))

        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack)
            .containsExactly(bikes, Destination.Person("test-rider"))
            .inOrder()
        assertThat(navigator.back()).isTrue()
        assertThat(state.currentStack).containsExactly(bikes)
    }

    @Test
    fun `a bike opened from a person is pushed over the person`() {
        navigator.open(Destination.Person("u1"))

        navigator.openBike("b3")

        assertThat(state.currentStack)
            .containsExactly(bikes, Destination.Person("u1"), Destination.Bike("b3"))
            .inOrder()
    }
}
