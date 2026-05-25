package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("enigma_tv_prefs")

class ContinueWatchingStore(private val context: Context) {
    private val gson = Gson()
    private val legacyKey = stringPreferencesKey("enigma_continue_watching")
    private val legacyCinetvKey = stringPreferencesKey("cinetv_cw")

    private fun key(profileId: String) = stringPreferencesKey("continue_$profileId")

    fun watch(profileId: String): Flow<List<ContinueWatchingEntry>> = context.dataStore.data.map { prefs ->
        val json = prefs[key(profileId)] ?: prefs[legacyKey] ?: prefs[legacyCinetvKey] ?: "[]"
        readList(json)
    }

    suspend fun readOnce(profileId: String): List<ContinueWatchingEntry> {
        val prefs = context.dataStore.data.first()
        return readList(prefs[key(profileId)] ?: prefs[legacyKey] ?: prefs[legacyCinetvKey])
    }

    suspend fun replaceAll(profileId: String, items: List<ContinueWatchingEntry>) {
        context.dataStore.edit { prefs ->
            prefs[key(profileId)] = gson.toJson(items.take(12))
        }
    }

    suspend fun addOrUpdate(profileId: String, entry: ContinueWatchingEntry) {
        context.dataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)] ?: prefs[legacyKey] ?: prefs[legacyCinetvKey])
            val updated = (listOf(entry) + current.filter {
                it.id != entry.id || it.type != entry.type
            }).take(12)
            prefs[key(profileId)] = gson.toJson(updated)
        }
    }

    suspend fun updateProgress(profileId: String, id: Int, type: ContentType, season: Int, episode: Int) {
        context.dataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)] ?: prefs[legacyKey]).toMutableList()
            val idx = current.indexOfFirst { it.id == id && it.type == type }
            if (idx >= 0) {
                current[idx] = current[idx].copy(season = season, episode = episode)
                prefs[key(profileId)] = gson.toJson(current)
            }
        }
    }

    private fun readList(json: String?): List<ContinueWatchingEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ContinueWatchingEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
