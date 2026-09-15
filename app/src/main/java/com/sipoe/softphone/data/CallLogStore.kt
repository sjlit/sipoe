package com.sipoe.softphone.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.callLogDataStore by preferencesDataStore(name = "sipoe_call_log")

class CallLogStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    val flow: Flow<List<CallLogEntry>> = context.callLogDataStore.data.map { prefs ->
        decode(prefs[Keys.ENTRIES])
    }

    suspend fun add(entry: CallLogEntry) {
        context.callLogDataStore.edit { prefs ->
            val updated = (listOf(entry) + decode(prefs[Keys.ENTRIES])).take(MAX_ENTRIES)
            prefs[Keys.ENTRIES] = json.encodeToString(updated)
        }
    }

    suspend fun remove(id: Long) {
        context.callLogDataStore.edit { prefs ->
            val updated = decode(prefs[Keys.ENTRIES]).filterNot { it.id == id }
            prefs[Keys.ENTRIES] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        context.callLogDataStore.edit { prefs ->
            prefs.remove(Keys.ENTRIES)
        }
    }

    private fun decode(raw: String?): List<CallLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<CallLogEntry>>(raw) }.getOrDefault(emptyList())
    }

    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
    }

    companion object {
        const val MAX_ENTRIES = 50
    }
}
