package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.liveFavoritesStore by preferencesDataStore("enigma_live_favorites")
private val Context.liveRecentsStore by preferencesDataStore("enigma_live_recents")

class LiveChannelFavoritesStore(private val context: Context) {
    private val gson = Gson()
    private val favKey = stringPreferencesKey("favorite_channel_ids")
    private val recentKey = stringPreferencesKey("recent_channel_ids")

    val favoriteIds: Flow<Set<String>> = context.liveFavoritesStore.data.map { prefs ->
        readIds(prefs[favKey])
    }

    val recentIds: Flow<List<String>> = context.liveRecentsStore.data.map { prefs ->
        readIds(prefs[recentKey]).toList()
    }

    suspend fun toggleFavorite(channelId: String): Boolean {
        var added = false
        context.liveFavoritesStore.edit { prefs ->
            val current = readIds(prefs[favKey]).toMutableSet()
            if (current.contains(channelId)) {
                current.remove(channelId)
                added = false
            } else {
                current.add(channelId)
                added = true
            }
            prefs[favKey] = gson.toJson(current.toList())
        }
        return added
    }

    suspend fun addRecent(channelId: String) {
        context.liveRecentsStore.edit { prefs ->
            val current = readIds(prefs[recentKey]).toMutableList()
            current.remove(channelId)
            current.add(0, channelId)
            prefs[recentKey] = gson.toJson(current.take(12))
        }
    }

    private fun readIds(json: String?): Set<String> {
        if (json.isNullOrBlank()) return emptySet()
        val type = object : TypeToken<List<String>>() {}.type
        return (gson.fromJson<List<String>>(json, type) ?: emptyList()).toSet()
    }
}
