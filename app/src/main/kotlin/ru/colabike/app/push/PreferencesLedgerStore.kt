package ru.colabike.app.push

import android.content.SharedPreferences
import androidx.core.content.edit

/** The ledger in SharedPreferences (no backup; ids and times only, no text of any notification). */
class PreferencesLedgerStore(private val preferences: SharedPreferences) : LedgerStore {
    override fun read(): String? = preferences.getString(KEY, null)

    override fun write(value: String) = preferences.edit { putString(KEY, value) }

    private companion object {
        const val KEY = "ledger"
    }
}
