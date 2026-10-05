package ru.colabike.core.network

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Test
import ru.colabike.core.model.CircleMode
import ru.colabike.core.model.DataError
import ru.colabike.core.model.MuteKind
import ru.colabike.core.model.MuteRef
import ru.colabike.core.model.NotificationCategory
import ru.colabike.core.model.NotificationSettingsChange

/**
 * The settings against the examples the backend publishes (pinned, see SOURCE.txt): what a response
 * reads as, and what a change puts on the wire.
 */
class NotificationSettingsRepositoryTest {
    private val site = TestServer()
    private val settings =
        NetworkNotificationSettingsRepository(site.api.personal, site.media, Dispatchers.Unconfined)

    @After fun close() = site.close()

    private fun case(name: String): JsonObject =
        Json.parseToJsonElement(
                requireNotNull(
                        javaClass.getResource("/contracts/notifications/v1/preferences.json")
                    ) {
                        "no contract file"
                    }
                    .readText()
            )
            .jsonObject["cases"]!!
            .jsonArray
            .map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == name }

    private fun body(name: String): String =
        case(name)["response"]!!.jsonObject["body"]!!.toString()

    @Test
    fun `a new account has nothing on and nothing chosen`() = runTest {
        site.json(200, body("defaults_push_not_connected"))

        val loaded = settings.settings()

        assertThat(site.server.takeRequest().url.encodedPath)
            .isEqualTo("/api/v1/me/notification-settings")
        assertThat(loaded.channels.emailEnabled).isFalse()
        assertThat(loaded.channels.emailVerified).isTrue()
        // The server cannot carry push yet: the app must not offer a switch that cannot work.
        assertThat(loaded.channels.pushAvailable).isFalse()
        assertThat(loaded.reminders).isTrue()
        assertThat(loaded.timeZone).isNull()
        assertThat(loaded.quietHours.enabled).isFalse()
        assertThat(loaded.quietHours.from).isEqualTo(LocalTime.of(22, 0))
        assertThat(loaded.quietHours.to).isEqualTo(LocalTime.of(7, 0))
        assertThat(loaded.quietHours.allowCancellations).isFalse()
        assertThat(loaded.pausedUntil).isNull()
        assertThat(loaded.circleMode).isEqualTo(CircleMode.Friends)
        assertThat(loaded.circleMembers).isEmpty()
        assertThat(loaded.considering).isFalse()
        assertThat(loaded.mutes).isEmpty()
        assertThat(loaded.updatedAt).isNull()
        val keys = loaded.categories.map { it.category }
        assertThat(keys)
            .containsExactly(
                NotificationCategory.Rides,
                NotificationCategory.Discussions,
                NotificationCategory.Market,
                NotificationCategory.Plans,
                NotificationCategory.Intents,
                NotificationCategory.Chat,
                NotificationCategory.Nearby,
            )
            .inOrder()
        val market = loaded.categories.first { it.category == NotificationCategory.Market }
        assertThat(market.email.supported).isTrue()
        assertThat(market.push.supported).isFalse()
        assertThat(
                loaded.categories.first { it.category == NotificationCategory.Plans }.push.enabled
            )
            .isTrue()
    }

    @Test
    fun `quiet hours, a pause, a circle and mutes read as the examples say`() = runTest {
        site.json(200, body("quiet_hours"))
        val quiet = settings.settings()
        assertThat(quiet.timeZone).isEqualTo(ZoneId.of("Europe/Moscow"))
        assertThat(quiet.quietHours.enabled).isTrue()
        assertThat(quiet.quietHours.from).isEqualTo(LocalTime.of(23, 0))
        assertThat(quiet.quietHours.to).isEqualTo(LocalTime.of(7, 30))
        assertThat(quiet.updatedAt).isNotNull()

        site.json(200, body("pause"))
        assertThat(settings.settings().pausedUntil)
            .isEqualTo(Instant.parse("2026-10-10T18:00:00.000Z"))

        site.json(200, body("circle_selected"))
        val circle = settings.settings()
        assertThat(circle.circleMode).isEqualTo(CircleMode.Selected)
        assertThat(circle.circleMembers.map { it.username }).containsExactly("boris")
        assertThat(circle.considering).isTrue()

        site.json(200, body("mute_author_and_ride"))
        val muted = settings.settings()
        assertThat(muted.mutes.map { it.kind })
            .containsExactly(MuteKind.Author, MuteKind.Ride)
            .inOrder()
        assertThat(muted.mutes.map { it.label }).containsExactly("Борис", "Вечерний круг").inOrder()
    }

    @Test
    fun `a mode, a mute kind and a category from the future are kept, not dropped`() = runTest {
        val wire =
            Json.parseToJsonElement(body("circle_selected")).jsonObject.toMutableMap().apply {
                put(
                    "circle",
                    Json.parseToJsonElement("""{"mode":"neighbours","members":[]}"""),
                )
                put(
                    "mutes",
                    Json.parseToJsonElement(
                        """[{"kind":"album","id":"00000000-0000-4000-8000-000000000009","label":null}]"""
                    ),
                )
                put(
                    "categories",
                    JsonArray(
                        Json.parseToJsonElement(body("circle_selected"))
                            .jsonObject["categories"]!!
                            .jsonArray +
                            Json.parseToJsonElement(
                                """{"key":"vouchers","label":"Скидки","email":{"supported":false,"enabled":false},"push":{"supported":true,"enabled":false}}"""
                            )
                    ),
                )
            }
        site.json(200, JsonObject(wire).toString())

        val loaded = settings.settings()

        assertThat(loaded.circleMode).isEqualTo(CircleMode.Unknown)
        assertThat(loaded.mutes.single().kind).isEqualTo(MuteKind.Unknown)
        assertThat(loaded.mutes.single().label).isNull()
        val extra = loaded.categories.last()
        assertThat(extra.category).isEqualTo(NotificationCategory.Other)
        assertThat(extra.key).isEqualTo("vouchers")
        assertThat(extra.label).isEqualTo("Скидки")
    }

    @Test
    fun `a change names only what it changes, and an empty one asks for nothing but the settings`() =
        runTest {
            site.json(200, body("quiet_hours"))
            settings.change(
                NotificationSettingsChange(
                    timeZone = ZoneId.of("Europe/Moscow"),
                    quietEnabled = true,
                    quietFrom = LocalTime.of(23, 0),
                    quietTo = LocalTime.of(7, 30),
                )
            )
            val request = site.server.takeRequest()
            assertThat(request.method).isEqualTo("PATCH")
            assertThat(request.url.encodedPath).isEqualTo("/api/v1/me/notification-settings")
            val sent = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            assertThat(sent.keys).containsExactly("timeZone", "quietHours")
            assertThat(sent["timeZone"]!!.jsonPrimitive.content).isEqualTo("Europe/Moscow")
            val quiet = sent["quietHours"]!!.jsonObject
            assertThat(quiet.keys).containsExactly("enabled", "from", "to")
            assertThat(quiet["from"]!!.jsonPrimitive.content).isEqualTo("23:00")
            assertThat(quiet["to"]!!.jsonPrimitive.content).isEqualTo("07:30")

            // Nothing to change: only a read.
            site.json(200, body("defaults_push_not_connected"))
            settings.change(NotificationSettingsChange())
            assertThat(site.server.takeRequest().method).isEqualTo("GET")
        }

    @Test
    fun `lifting a pause is a word, never a null, which the generated client would leave out`() =
        runTest {
            site.json(200, body("pause_lifted"))
            settings.change(NotificationSettingsChange(resume = true))
            val lifted = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
            assertThat(lifted.keys).containsExactly("resume")
            assertThat(lifted["resume"]!!.jsonPrimitive.content).isEqualTo("true")

            site.json(200, body("pause"))
            settings.change(
                NotificationSettingsChange(pauseUntil = Instant.parse("2026-10-10T18:00:00Z"))
            )
            val paused = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject
            assertThat(paused.keys).containsExactly("pausedUntil")
            assertThat(Instant.parse(paused["pausedUntil"]!!.jsonPrimitive.content))
                .isEqualTo(Instant.parse("2026-10-10T18:00:00Z"))
        }

    @Test
    fun `the circle, the mutes and the switches go as the contract spells them`() = runTest {
        site.json(200, body("circle_selected"))
        settings.change(
            NotificationSettingsChange(
                circleMode = CircleMode.Selected,
                circleAdd = listOf("00000000-0000-4000-8000-000000000003", "not-a-uuid"),
                considering = true,
                muteAdd = listOf(MuteRef(MuteKind.Author, "00000000-0000-4000-8000-000000000003")),
                muteRemove =
                    listOf(MuteRef(MuteKind.Unknown, "00000000-0000-4000-8000-000000000004")),
                pushEnabled = true,
                categoryPush = mapOf("rides" to false, "vouchers" to true),
                categoryEmail = mapOf("market" to true),
                reminders = false,
            )
        )
        val sent = Json.parseToJsonElement(site.server.takeRequest().body!!.utf8()).jsonObject

        assertThat(sent["circle"]!!.jsonObject["mode"]!!.jsonPrimitive.content)
            .isEqualTo("selected")
        // An id that is no id, a mute of a kind this app does not know and a category it cannot
        // name are not sent: the server would refuse the request whole.
        assertThat(sent["circle"]!!.jsonObject["add"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("00000000-0000-4000-8000-000000000003")
        assertThat(sent["mutes"]!!.jsonObject.keys).containsExactly("add")
        assertThat(sent["considering"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(sent["reminders"]!!.jsonPrimitive.content).isEqualTo("false")
        assertThat(
                sent["channels"]!!
                    .jsonObject["push"]!!
                    .jsonObject["enabled"]!!
                    .jsonPrimitive
                    .content
            )
            .isEqualTo("true")
        assertThat(sent["channels"]!!.jsonObject.keys).containsExactly("push")
        val categories = sent["categories"]!!.jsonArray.map { it.jsonObject }
        assertThat(categories.map { it["key"]!!.jsonPrimitive.content })
            .containsExactly("market", "rides")
            .inOrder()
        assertThat(categories.first { it["key"]!!.jsonPrimitive.content == "rides" }.keys)
            .containsExactly("key", "push")
    }

    @Test
    fun `a refusal is kept with its code, and nothing is claimed as saved`() = runTest {
        for (name in listOf("email_not_verified", "quiet_hours_need_a_zone")) {
            val response = case(name)["response"]!!.jsonObject
            val status = response["status"]!!.jsonPrimitive.content.toInt()
            site.json(status, response["body"]!!.toString())

            val failure = runCatching {
                settings.change(NotificationSettingsChange(pushEnabled = true))
            }
                .exceptionOrNull()

            assertThat(failure).isInstanceOf(DataError.Rejected::class.java)
            val rejected = failure as DataError.Rejected
            val expected = response["body"]!!.jsonObject["error"]!!.jsonObject
            assertThat(rejected.code).isEqualTo(expected["code"]!!.jsonPrimitive.content)
            assertThat(rejected.status).isEqualTo(status)
        }
    }

    @Test
    fun `a channel the server cannot carry is a server failure, which the app avoids by not asking`() =
        runTest {
            val response = case("push_not_connected")["response"]!!.jsonObject
            site.json(503, response["body"]!!.toString())

            val failure = runCatching {
                settings.change(NotificationSettingsChange(pushEnabled = true))
            }
                .exceptionOrNull()

            assertThat(failure).isInstanceOf(DataError.Server::class.java)
            assertThat((failure as DataError.Server).status).isEqualTo(503)
        }
}
