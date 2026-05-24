package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("enigma_tv_prefs")

class ContinueWatchingStore(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("enigma_continue_watching")
    private val legacyKey = stringPreferencesKey("cinetv_cw")

    val entries: Flow<List<ContinueWatchingEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[key] ?: prefs[legacyKey] ?: "[]"
        val type = object : TypeToken<List<ContinueWatchingEntry>>() {}.type
        gson.fromJson<List<ContinueWatchingEntry>>(json, type) ?: emptyList()
    }

    suspend fun replaceAll(items: List<ContinueWatchingEntry>) {
        context.dataStore.edit { prefs ->
            prefs[key] = gson.toJson(items.take(12))
            prefs.remove(legacyKey)
        }
    }

    suspend fun addOrUpdate(entry: ContinueWatchingEntry) {
        context.dataStore.edit { prefs ->
            val current = readList(prefs[key] ?: prefs[legacyKey])
            val updated = (listOf(entry) + current.filter {
                it.id != entry.id || it.type != entry.type
            }).take(12)
            prefs[key] = gson.toJson(updated)
            prefs.remove(legacyKey)
        }
    }

    suspend fun updateProgress(id: Int, type: ContentType, season: Int, episode: Int) {
        context.dataStore.edit { prefs ->
            val current = readList(prefs[key] ?: prefs[legacyKey]).toMutableList()
            val idx = current.indexOfFirst { it.id == id && it.type == type }
            if (idx >= 0) {
                current[idx] = current[idx].copy(season = season, episode = episode)
                prefs[key] = gson.toJson(current)
                prefs.remove(legacyKey)
            }
        }
    }

    private fun readList(json: String?): List<ContinueWatchingEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ContinueWatchingEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
