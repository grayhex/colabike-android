package ru.colabike.app.push

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Where a tap on a push leads, in ids: the app asks the server for the name, the state and the
 * right to see it. The same fields as the typed target of the inbox, without names and addresses.
 * [type] is an open set; [id] is null for a target without an object (the server's own).
 */
data class PushTarget(
    val type: String,
    val id: String?,
    val commentId: String?,
    val occurrenceAt: Instant?,
    val agreementRevision: Int?,
)

/**
 * What a transport carries to the phone, as the backend's contract
 * (`docs/contracts/notifications/v1/payload.json`, format 1) defines it. The message is data only:
 * the one notification there is gets built here. Nothing in it opens anything or names a closed
 * object: for an event about something closed [neutral] is true and the text is general.
 *
 * The text is not logged and not printed ([toString] says only which delivery this is).
 */
data class PushEnvelope(
    /** One delivery: a repeat of the same one is not shown twice. */
    val deliveryId: String,
    /** The notification on the server: the key of "read" and its line in the inbox. */
    val eventId: String,
    /**
     * The phone's binding to the account at the time of sending; not the current one: not shown.
     */
    val bindingGeneration: Int,
    /** The key of the category as the server wrote it; one the app does not know is "general". */
    val category: String,
    val type: String,
    val createdAt: Instant,
    /** After this the notification is useless (a past date, a closed set): it is not shown. */
    val expiresAt: Instant,
    val neutral: Boolean,
    val title: String,
    val body: String?,
    /** The stack: news of the same object replaces the earlier, different rides stay apart. */
    val group: String,
    val target: PushTarget,
) {
    override fun toString() = "PushEnvelope(delivery=$deliveryId, event=$eventId, type=$type)"
}

/** Why a message is not shown. A dropped message leaves nothing behind in the tray. */
enum class DropReason {
    /**
     * Not JSON, a field missing or wrong, a text over its limit, no target: not ours or damaged.
     */
    Malformed,

    /** A format version this app does not know: neither shown nor read further. */
    UnsupportedVersion,

    /** Longer than the transport allows. */
    TooLarge,

    /** Its time has passed. */
    Expired,

    /** The binding is not the current one (sign-out, another account, a reinstall), or none. */
    OtherBinding,
}

sealed interface ParsedPush {
    data class Valid(val envelope: PushEnvelope) : ParsedPush

    data class Dropped(val reason: DropReason) : ParsedPush
}

/**
 * Reads and checks a message before anything is shown. Strict about what is shown (every field of
 * format 1, with the limits of the contract), tolerant about what is added: an extra field of the
 * same version is skipped, a category or a target type the app does not know is kept as words.
 */
object PushEnvelopes {
    /** The transport's limit for the whole message; the envelope itself takes 3072 at most. */
    const val MAX_BYTES = 4096

    private const val VERSION = 1
    private val UUID =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    /**
     * [binding] is the generation the phone holds now (null: none, nothing may be shown), [now]
     * decides expiry.
     */
    fun parse(raw: String, now: Instant, binding: Int?): ParsedPush {
        if (raw.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return dropped(DropReason.TooLarge)
        val root =
            try {
                Json.parseToJsonElement(raw) as? JsonObject
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } ?: return dropped(DropReason.Malformed)

        // The version first: a format that is not this one is not read any further.
        val version = root.int("v") ?: return dropped(DropReason.Malformed)
        if (version != VERSION) return dropped(DropReason.UnsupportedVersion)

        val envelope = envelope(root) ?: return dropped(DropReason.Malformed)
        if (!now.isBefore(envelope.expiresAt)) return dropped(DropReason.Expired)
        if (binding == null || binding != envelope.bindingGeneration) {
            return dropped(DropReason.OtherBinding)
        }
        return ParsedPush.Valid(envelope)
    }

    private fun dropped(reason: DropReason) = ParsedPush.Dropped(reason)

    private fun envelope(root: JsonObject): PushEnvelope? {
        val target = target(root["target"] as? JsonObject ?: return null) ?: return null
        val title = root.string("title")?.takeIf { it.length in 1..80 } ?: return null
        val body =
            when (val value = root["body"]) {
                null,
                is kotlinx.serialization.json.JsonNull -> null
                else -> (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            }
        if (body != null && body.length > 160) return null
        return PushEnvelope(
            deliveryId = root.uuid("deliveryId") ?: return null,
            eventId = root.uuid("eventId") ?: return null,
            bindingGeneration = root.int("bindingGeneration")?.takeIf { it >= 1 } ?: return null,
            category = root.string("category")?.takeIf { it.length in 1..40 } ?: return null,
            type = root.string("type")?.takeIf { it.length in 1..40 } ?: return null,
            createdAt = root.instant("createdAt") ?: return null,
            expiresAt = root.instant("expiresAt") ?: return null,
            neutral = root.bool("neutral") ?: return null,
            title = title,
            body = body,
            group = root.string("group")?.takeIf { it.length in 1..80 } ?: return null,
            target = target,
        )
    }

    private fun target(json: JsonObject): PushTarget? {
        val type = json.string("type")?.takeIf { it.length in 1..20 } ?: return null
        // The ids and the date may be absent or null; present, they must be what they say.
        fun nullableUuid(key: String): Result<String?> =
            when (val value = json[key]) {
                null,
                is kotlinx.serialization.json.JsonNull -> Result.success(null)
                else -> json.uuid(key)?.let { Result.success(it) } ?: Result.failure(Failure)
            }
        val id =
            nullableUuid("id").getOrElse {
                return null
            }
        val commentId =
            nullableUuid("commentId").getOrElse {
                return null
            }
        val occurrenceAt =
            when (json["occurrenceAt"]) {
                null,
                is kotlinx.serialization.json.JsonNull -> null
                else -> json.instant("occurrenceAt") ?: return null
            }
        val revision =
            when (json["agreementRevision"]) {
                null,
                is kotlinx.serialization.json.JsonNull -> null
                else -> json.int("agreementRevision")?.takeIf { it >= 1 } ?: return null
            }
        return PushTarget(type, id, commentId, occurrenceAt, revision)
    }

    private object Failure : Exception()

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    private fun JsonObject.uuid(key: String): String? =
        string(key)?.takeIf { UUID.matches(it) }?.lowercase()

    private fun JsonObject.instant(key: String): Instant? =
        string(key)?.let {
            try {
                Instant.parse(it)
            } catch (_: DateTimeParseException) {
                null
            }
        }
}
