package ru.colabike.app

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.colabike.app.navigation.Destination
import ru.colabike.app.navigation.NavigationState
import ru.colabike.app.navigation.Navigator
import ru.colabike.app.navigation.TopLevel
import ru.colabike.app.navigation.requiredFeature
import ru.colabike.core.model.Feature
import ru.colabike.core.model.FeatureAvailability

/** Where links go, and what a function that the server has switched off does to them. */
class NavigatorFeaturesTest {
    private val bikes = Destination.Bikes
    private val rides = Destination.Rides
    private val messages = Destination.Messages
    private val profile = Destination.Profile

    private fun stateOf(vararg sections: NavKey) =
        NavigationState(
            startRoute = bikes,
            topLevelRoute = mutableStateOf<NavKey>(bikes),
            backStacks = sections.associateWith { mutableListOf(it) },
        )

    private var features = FeatureAvailability.AllOn
    private var unavailable = 0
    private val state = stateOf(bikes, rides, messages, profile)
    private val navigator =
        Navigator(state, features = { features }, onUnavailable = { unavailable++ })

    @Test
    fun `a journal entry, a listing and a catalog model open over Bikes from any section`() {
        navigator.select(profile)

        navigator.go(Destination.Journal("j1"))
        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack).containsExactly(bikes, Destination.Journal("j1")).inOrder()

        navigator.go(Destination.Listing("l1"))
        navigator.go(Destination.Component("c1"))
        assertThat(state.currentStack)
            .containsExactly(
                bikes,
                Destination.Journal("j1"),
                Destination.Listing("l1"),
                Destination.Component("c1"),
            )
            .inOrder()
        // What was open in the profile stays where it was.
        assertThat(state.backStacks.getValue(profile)).containsExactly(profile)
    }

    @Test
    fun `a discussion opens in the section of its object, the devices in Profile, the inbox where the person is`() {
        navigator.go(Destination.Comments("bike", "b1", "", "c1"))
        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack.last())
            .isEqualTo(Destination.Comments("bike", "b1", "", "c1"))

        navigator.go(Destination.Comments("ride", "r1", "", "c2"))
        assertThat(state.topLevelRoute).isEqualTo(rides)
        assertThat(state.currentStack.last())
            .isEqualTo(Destination.Comments("ride", "r1", "", "c2"))

        navigator.go(Destination.Devices)
        assertThat(state.topLevelRoute).isEqualTo(profile)
        assertThat(state.currentStack).containsExactly(profile, Destination.Devices).inOrder()

        navigator.select(messages)
        navigator.go(Destination.Notifications)
        assertThat(state.topLevelRoute).isEqualTo(messages)
        assertThat(state.currentStack)
            .containsExactly(messages, Destination.Notifications)
            .inOrder()
    }

    @Test
    fun `a discussion under a ride is not opened when rides are off`() {
        features = featuresOff(Feature.Rides)

        navigator.go(Destination.Comments("ride", "r1", "", "c2"))

        assertThat(unavailable).isEqualTo(1)
        assertThat(state.topLevelRoute).isEqualTo(bikes)
    }

    @Test
    fun `a ride opens in Rides, a conversation in Messages`() {
        navigator.go(Destination.Ride("r1"))
        assertThat(state.topLevelRoute).isEqualTo(rides)
        assertThat(state.currentStack).containsExactly(rides, Destination.Ride("r1")).inOrder()

        navigator.go(Destination.Conversation("messaging:dm-1"))
        assertThat(state.topLevelRoute).isEqualTo(messages)
        assertThat(state.currentStack)
            .containsExactly(messages, Destination.Conversation("messaging:dm-1"))
            .inOrder()
        assertThat(unavailable).isEqualTo(0)
    }

    @Test
    fun `a section that the app does not show falls back to Bikes, where a ride opens as well`() {
        val state = stateOf(bikes, profile)
        val navigator = Navigator(state)

        navigator.go(Destination.Ride("r1"))

        assertThat(state.topLevelRoute).isEqualTo(bikes)
        assertThat(state.currentStack).containsExactly(bikes, Destination.Ride("r1")).inOrder()
    }

    @Test
    fun `a function that is off is not opened by a link, and the person is told once`() {
        features = FeatureAvailability(mapOf("rides" to false, "market" to false))
        navigator.select(profile)

        navigator.go(Destination.Ride("r1"))
        navigator.go(Destination.Listing("l1"))

        assertThat(unavailable).isEqualTo(2)
        // Nowhere: not even the section changed.
        assertThat(state.topLevelRoute).isEqualTo(profile)
        assertThat(state.backStacks.getValue(rides)).containsExactly(rides)
        assertThat(state.backStacks.getValue(bikes)).containsExactly(bikes)
    }

    @Test
    fun `a function that is off is not opened from a row either, the others are`() {
        features = FeatureAvailability(mapOf("componentCatalog" to false, "chat" to false))

        navigator.open(Destination.Component("c1"))
        navigator.open(Destination.Components)
        navigator.open(Destination.Comments("component", "c1", "Кассета"))
        navigator.open(Destination.Conversation("messaging:x"))
        assertThat(unavailable).isEqualTo(4)
        assertThat(state.currentStack).containsExactly(bikes)

        navigator.open(Destination.Comments("bike", "b1", "Велосипед"))
        navigator.open(Destination.Market())
        assertThat(unavailable).isEqualTo(4)
        assertThat(state.currentStack)
            .containsExactly(
                bikes,
                Destination.Comments("bike", "b1", "Велосипед"),
                Destination.Market(),
            )
            .inOrder()
    }

    @Test
    fun `the flags are read when a screen is opened, not when the shell was made`() {
        navigator.open(Destination.Listing("l1"))
        assertThat(unavailable).isEqualTo(0)

        features = FeatureAvailability(mapOf("market" to false))
        navigator.open(Destination.Listing("l2"))

        assertThat(unavailable).isEqualTo(1)
        assertThat(state.currentStack).containsExactly(bikes, Destination.Listing("l1")).inOrder()
    }

    @Test
    fun `every screen of a switchable function says which, and the rest say none`() {
        assertThat(Destination.Rides.requiredFeature()).isEqualTo(Feature.Rides)
        assertThat(Destination.BikeRides("b", "n").requiredFeature()).isEqualTo(Feature.Rides)
        assertThat(Destination.Messages.requiredFeature()).isEqualTo(Feature.Chat)
        assertThat(Destination.NewConversation.requiredFeature()).isEqualTo(Feature.Chat)
        assertThat(Destination.SavedMarket.requiredFeature()).isEqualTo(Feature.Market)
        assertThat(Destination.Components.requiredFeature()).isEqualTo(Feature.ComponentCatalog)
        assertThat(Destination.Comments("ride", "r", "t").requiredFeature())
            .isEqualTo(Feature.Rides)
        listOf(
                Destination.Feed,
                Destination.Bikes,
                Destination.Profile,
                Destination.Bike("b"),
                Destination.Journal("j"),
                Destination.Comments("bike", "b", "t"),
                Destination.Person("p"),
                Destination.Devices,
                Destination.About,
            )
            .forEach { assertThat(it.requiredFeature()).isNull() }
    }

    @Test
    fun `the tabs follow the flags, and Bikes, the start, is always there`() {
        assertThat(TopLevel.shown(FeatureAvailability.AllOn)).isEqualTo(TopLevel.shown)
        assertThat(TopLevel.shown(FeatureAvailability(mapOf("rides" to false))))
            .containsExactly(TopLevel.Feed, TopLevel.Bikes, TopLevel.Market, TopLevel.Profile)
            .inOrder()
        assertThat(TopLevel.shown(FeatureAvailability(mapOf("rides" to false, "market" to false))))
            .containsExactly(TopLevel.Feed, TopLevel.Bikes, TopLevel.Profile)
            .inOrder()
        // Flags that are not about a tab change none: the chats are a button beside the bell, not
        // a tab, and their flag takes the button away, not a place in the bar.
        assertThat(TopLevel.shown(FeatureAvailability(mapOf("chat" to false))))
            .isEqualTo(TopLevel.shown)
    }
}
