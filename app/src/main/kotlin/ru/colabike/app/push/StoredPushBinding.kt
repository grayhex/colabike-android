package ru.colabike.app.push

import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.UUID

/**
 * What the phone remembers of its registration at the server (cola#342): the account and the
 * generation of the binding, what was sent last (as a fingerprint, never the address), and the
 * random id of this install. The address itself is not kept here: the provider holds it, and the
 * server keeps it sealed. Nothing here is secret; nothing is worth restoring, and backups are off.
 *
 * It is also the [PushBinding] the handler reads for every message: no record, or a record for
 * another generation, and nothing is shown.
 */
class StoredPushBinding(private val preferences: SharedPreferences) : PushBinding {
    /** A registration the server accepted. */
    data class Record(
        val accountId: String,
        val generation: Int,
        val projectId: String,
        /** A short digest of the address that was sent, so that "the same address" is known. */
        val fingerprint: String,
        val syncedAtMillis: Long,
    )

    /** The random id of this install; made once and kept through sign-outs. */
    fun installationId(): String {
        preferences.getString(KEY_INSTALLATION, null)?.let { existing ->
            if (INSTALLATION.matches(existing)) return existing
        }
        val fresh = UUID.randomUUID().toString()
        preferences.edit { putString(KEY_INSTALLATION, fresh) }
        return fresh
    }

    fun record(): Record? {
        val account = preferences.getString(KEY_ACCOUNT, null) ?: return null
        val generation = preferences.getInt(KEY_GENERATION, 0).takeIf { it >= 1 } ?: return null
        return Record(
            accountId = account,
            generation = generation,
            projectId = preferences.getString(KEY_PROJECT, null).orEmpty(),
            fingerprint = preferences.getString(KEY_FINGERPRINT, null).orEmpty(),
            syncedAtMillis = preferences.getLong(KEY_SYNCED, 0L),
        )
    }

    fun save(record: Record) = preferences.edit {
        putString(KEY_ACCOUNT, record.accountId)
        putInt(KEY_GENERATION, record.generation)
        putString(KEY_PROJECT, record.projectId)
        putString(KEY_FINGERPRINT, record.fingerprint)
        putLong(KEY_SYNCED, record.syncedAtMillis)
    }

    /** Forgets the registration (not the install): sign-out, withdrawn consent, a provider gone. */
    fun clear() = preferences.edit {
        remove(KEY_ACCOUNT)
            .remove(KEY_GENERATION)
            .remove(KEY_PROJECT)
            .remove(KEY_FINGERPRINT)
            .remove(KEY_SYNCED)
    }

    override fun current(): PushBound? = record()?.let { PushBound(it.accountId, it.generation) }

    private companion object {
        const val KEY_INSTALLATION = "installation_id"
        const val KEY_ACCOUNT = "account_id"
        const val KEY_GENERATION = "generation"
        const val KEY_PROJECT = "project_id"
        const val KEY_FINGERPRINT = "fingerprint"
        const val KEY_SYNCED = "synced_at"
        val INSTALLATION = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}
