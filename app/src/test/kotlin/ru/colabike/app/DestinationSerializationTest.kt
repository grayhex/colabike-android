package ru.colabike.app

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import ru.colabike.app.navigation.Destination

/**
 * The back stack is saved as JSON and read back after the process died. A destination that cannot
 * make the trip would crash the app on return, and one added without a test here would be found by
 * a person, not by CI.
 */
class DestinationSerializationTest {
    private val json = Json

    /** One of each, with values a screen really carries (a title with quotes and Cyrillic). */
    private val samples: List<Destination> =
        listOf(
            Destination.Feed,
            Destination.Bikes,
            Destination.Components,
            Destination.Component("c0000000-0000-4000-8000-000000000001"),
            Destination.Market(),
            Destination.Market("test-rider"),
            Destination.Listing("a0000000-0000-4000-8000-000000000001"),
            Destination.SavedMarket,
            Destination.Bike("6e7f8091-a2b3-4c4d-9e5f-60718293a4b5"),
            Destination.Rides,
            Destination.Ride("b2000000-0000-4000-8000-000000000002"),
            Destination.BikeRides("b1", "Городской «Трэвел»"),
            Destination.Messages,
            Destination.Conversation("colabike:dm-1"),
            Destination.NewConversation,
            Destination.Notifications,
            Destination.Profile,
            Destination.Person("test-rider"),
            Destination.People("u1", following = true),
            Destination.Journal("j1"),
            Destination.BikeJournal("b1", "Старый \"шоссейник\""),
            Destination.Comments("bike", "b1", "Городской Трэвел", focus = "c1"),
            Destination.Comments("ride", "r1", "Выезд", focus = null),
            Destination.SavedJournal,
            Destination.Search(people = true),
            Destination.Devices,
            Destination.DeleteAccount,
            Destination.NotificationSettings,
            Destination.Participation("b2000000-0000-4000-8000-0000000000b2"),
            Destination.Participation(
                "b2000000-0000-4000-8000-0000000000b2",
                "2026-10-10T07:00:00Z",
            ),
            Destination.Intents,
            Destination.Intent("b3000000-0000-4000-8000-000000000001"),
            Destination.IntentEditor(),
            Destination.IntentEditor("b3000000-0000-4000-8000-000000000001"),
            Destination.NearbySettings,
            Destination.NearbyOffers,
            Destination.About,
            Destination.Licenses,
            Destination.Blocked,
            Destination.BikeEditor(),
            Destination.BikeEditor("6f1c2b9e-3a1d-4f2e-9a6b-0c8d7e5f4a31"),
        )

    @Test
    fun `every destination comes back from the saved form unchanged`() {
        samples.forEach { destination ->
            val saved = json.encodeToString(Destination.serializer(), destination)

            assertThat(json.decodeFromString(Destination.serializer(), saved))
                .isEqualTo(destination)
        }
    }

    @Test
    fun `no destination exists without a sample here`() {
        // The sealed serializer lists its subclasses by their serial names.
        val declared =
            Destination.serializer().descriptor.getElementDescriptor(1).elementNames.toSet()
        val sampled =
            samples
                .map {
                    json
                        .encodeToJsonElement(Destination.serializer(), it)
                        .jsonObject
                        .getValue("type")
                        .jsonPrimitive
                        .content
                }
                .toSet()

        assertThat(sampled).containsExactlyElementsIn(declared)
    }

    @Test
    fun `a saved form that a newer build wrote for a destination this build does not know fails to decode, not to a wrong screen`() {
        val saved = """{"type":"ru.colabike.app.navigation.Destination.Galaxy"}"""

        val result = runCatching { json.decodeFromString(Destination.serializer(), saved) }

        assertThat(result.isFailure).isTrue()
    }
}
