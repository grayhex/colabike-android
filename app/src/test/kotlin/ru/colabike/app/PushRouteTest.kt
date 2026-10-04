package ru.colabike.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.colabike.app.navigation.Destination
import ru.colabike.app.push.PushTap
import ru.colabike.app.push.PushTarget
import ru.colabike.app.push.destination

/** Where a tap on a notification leads, and what is believed of the Intent that carries it. */
@RunWith(RobolectricTestRunner::class)
class PushRouteTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val event = "00000000-0000-4000-9000-000000000066"
    private val id = "00000000-0000-4000-8000-00000000000a"
    private val comment = "00000000-0000-4000-8000-000000000014"

    private fun target(type: String, id: String? = this.id, comment: String? = null) =
        PushTarget(type, id, comment, null, null)

    @Test
    fun `an object opens its own screen, a comment opens the discussion at it`() {
        assertThat(target("bike").destination("like")).isEqualTo(Destination.Bike(id))
        assertThat(target("ride").destination("ride_changed")).isEqualTo(Destination.Ride(id))
        assertThat(target("journal").destination("journal_like")).isEqualTo(Destination.Journal(id))
        assertThat(target("component").destination("component_like"))
            .isEqualTo(Destination.Component(id))
        assertThat(target("profile").destination("follow")).isEqualTo(Destination.Person(id))
        assertThat(target("market").destination("market_expiring"))
            .isEqualTo(Destination.Listing(id))

        assertThat(target("bike", comment = comment).destination("reply"))
            .isEqualTo(Destination.Comments("bike", id, "", comment))
        assertThat(target("ride", comment = comment).destination("ride_reply"))
            .isEqualTo(Destination.Comments("ride", id, "", comment))
        assertThat(target("component", comment = comment).destination("component_reply"))
            .isEqualTo(Destination.Comments("component", id, "", comment))
    }

    @Test
    fun `a sign-in used again opens the devices, anything else unknown opens the inbox`() {
        assertThat(target("account").destination("session_reuse")).isEqualTo(Destination.Devices)
        assertThat(target("account").destination("something")).isEqualTo(Destination.Notifications)
        assertThat(target("galaxy").destination("new_kind")).isEqualTo(Destination.Notifications)
        assertThat(target("bike-week").destination("bike_week"))
            .isEqualTo(Destination.Notifications)
        assertThat(target("bike", id = null).destination("like"))
            .isEqualTo(Destination.Notifications)
    }

    @Test
    fun `a tap goes through the Intent and comes back whole`() {
        val tap =
            PushTap(
                event,
                "ride_changed",
                PushTarget(
                    "ride",
                    id,
                    null,
                    Instant.parse("2026-10-10T07:00:00Z"),
                    3,
                ),
            )

        val intent = tap.intent(context)

        assertThat(intent.component?.className)
            .isEqualTo("ru.colabike.app.push.NotificationTapActivity")
        assertThat(intent.data).isNull()
        assertThat(PushTap.from(intent)).isEqualTo(tap)
    }

    @Test
    fun `an Intent that is not whole or not ours is not a tap`() {
        val good = PushTap(event, "reply", target("bike", comment = comment)).intent(context)
        assertThat(PushTap.from(good)).isNotNull()

        assertThat(PushTap.from(null)).isNull()
        assertThat(PushTap.from(Intent(Intent.ACTION_VIEW))).isNull()
        // Another action, however well filled in.
        assertThat(PushTap.from(Intent(good).setAction("com.evil.OPEN"))).isNull()
        // Ids that are not ids, a missing notification, a target type of the wrong size.
        assertThat(PushTap.from(Intent(good).putExtra("event", "../me"))).isNull()
        assertThat(PushTap.from(Intent(good).apply { removeExtra("event") })).isNull()
        assertThat(PushTap.from(Intent(good).putExtra("target_id", "../../etc/passwd"))).isNull()
        assertThat(PushTap.from(Intent(good).putExtra("comment", "javascript:alert(1)"))).isNull()
        assertThat(PushTap.from(Intent(good).putExtra("target_type", "x".repeat(21)))).isNull()
        assertThat(PushTap.from(Intent(good).putExtra("occurrence", "yesterday"))).isNull()
    }
}
