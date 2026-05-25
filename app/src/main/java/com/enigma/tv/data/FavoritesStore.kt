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

private val Context.favoritesDataStore by preferencesDataStore("enigma_favorites")

class FavoritesStore(private val context: Context) {
    private val gson = Gson()
    private val legacyKey = stringPreferencesKey("favorites")

    private fun key(profileId: String) = stringPreferencesKey("favorites_$profileId")

    fun watch(profileId: String): Flow<List<FavoriteItem>> = context.favoritesDataStore.data.map { prefs ->
        readList(prefs[key(profileId)] ?: prefs[legacyKey])
    }

    suspend fun readOnce(profileId: String): List<FavoriteItem> {
        val prefs = context.favoritesDataStore.data.first()
        return readList(prefs[key(profileId)] ?: prefs[legacyKey])
    }

    suspend fun replaceAll(profileId: String, items: List<FavoriteItem>) {
        context.favoritesDataStore.edit { prefs ->
            prefs[key(profileId)] = gson.toJson(items.take(50))
        }
    }

    suspend fun toggle(profileId: String, item: FavoriteItem): Boolean {
        var added = false
        context.favoritesDataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)] ?: prefs[legacyKey]).toMutableList()
            val idx = current.indexOfFirst { it.id == item.id && it.type == item.type }
            if (idx >= 0) {
                current.removeAt(idx)
                added = false
            } else {
                current.add(0, item)
                added = true
            }
            prefs[key(profileId)] = gson.toJson(current.take(50))
        }
        return added
    }

    private fun readList(json: String?): List<FavoriteItem> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
