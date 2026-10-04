package ru.colabike.app.push

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Where the ledger keeps its text between starts. */
interface LedgerStore {
    fun read(): String?

    fun write(value: String)
}

/** What the ledger says of a message that is about to be shown. */
enum class Admission {
    /** Not seen before: show it. */
    New,

    /** The same delivery or the same event again: a repeat does not ring a second time. */
    Duplicate,

    /** Older than what is already shown for the same stack: the newer one stays. */
    Stale,
}

/**
 * The short memory of what was delivered, so a repeated request or a second push of the same event
 * does not show twice and an old message that arrives late does not replace a newer one. Bounded:
 * the last [capacity] deliveries, events and stacks, oldest forgotten first. It holds ids and times
 * only, never text. A sign-out [clear]s it.
 */
class DeliveryLedger(private val store: LedgerStore, private val capacity: Int = 100) {
    private val deliveries = LinkedHashSet<String>()
    private val events = LinkedHashSet<String>()
    private val groups = LinkedHashMap<String, Long>()

    init {
        read()
    }

    @Synchronized
    fun admit(envelope: PushEnvelope): Admission {
        val repeat = envelope.deliveryId in deliveries || envelope.eventId in events
        remember(deliveries, envelope.deliveryId)
        if (repeat) {
            save()
            return Admission.Duplicate
        }
        remember(events, envelope.eventId)
        val shown = groups[envelope.group]
        val at = envelope.createdAt.toEpochMilli()
        val admission =
            if (shown != null && at < shown) {
                Admission.Stale
            } else {
                groups.remove(envelope.group)
                groups[envelope.group] = at
                while (groups.size > capacity) groups.remove(groups.keys.first())
                Admission.New
            }
        save()
        return admission
    }

    @Synchronized
    fun clear() {
        deliveries.clear()
        events.clear()
        groups.clear()
        save()
    }

    private fun remember(set: LinkedHashSet<String>, id: String) {
        set.remove(id)
        set.add(id)
        while (set.size > capacity) set.remove(set.first())
    }

    private fun save() {
        val json = buildJsonObject {
            put("d", JsonArray(deliveries.map(::JsonPrimitive)))
            put("e", JsonArray(events.map(::JsonPrimitive)))
            put(
                "g",
                JsonObject(groups.mapValues { JsonPrimitive(it.value) }),
            )
        }
        store.write(json.toString())
    }

    /** What was kept, if it is whole; anything else is a fresh start, never a crash. */
    private fun read() {
        val text = store.read() ?: return
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            json["d"]?.jsonArray?.forEach { deliveries.add(it.jsonPrimitive.content) }
            json["e"]?.jsonArray?.forEach { events.add(it.jsonPrimitive.content) }
            json["g"]?.jsonObject?.forEach { (group, at) -> groups[group] = at.jsonPrimitive.long }
        } catch (_: Exception) {
            deliveries.clear()
            events.clear()
            groups.clear()
        }
    }
}
