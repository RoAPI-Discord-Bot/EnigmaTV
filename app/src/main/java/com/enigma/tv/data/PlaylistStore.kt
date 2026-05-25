package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.playlistDataStore by preferencesDataStore("enigma_playlists")

class PlaylistStore(private val context: Context) {
    private val gson = Gson()
    private val legacyKey = stringPreferencesKey("playlists")

    private fun key(profileId: String) = stringPreferencesKey("playlists_$profileId")

    fun watch(profileId: String): Flow<List<Playlist>> = context.playlistDataStore.data.map { prefs ->
        readList(prefs[key(profileId)] ?: prefs[legacyKey])
    }

    suspend fun replaceAll(profileId: String, items: List<Playlist>) {
        context.playlistDataStore.edit { prefs ->
            prefs[key(profileId)] = gson.toJson(items)
        }
    }

    suspend fun createPlaylist(profileId: String, name: String): Playlist {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name.trim())
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)] ?: prefs[legacyKey]).toMutableList()
            current.add(0, playlist)
            prefs[key(profileId)] = gson.toJson(current)
        }
        return playlist
    }

    suspend fun deletePlaylist(profileId: String, playlistId: String) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)]).filter { it.id != playlistId }
            prefs[key(profileId)] = gson.toJson(current)
        }
    }

    suspend fun addItem(profileId: String, playlistId: String, item: FavoriteItem) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)]).map { pl ->
                if (pl.id != playlistId) pl
                else {
                    val items = pl.items.filter { it.id != item.id || it.type != item.type }
                    pl.copy(items = listOf(item) + items)
                }
            }
            prefs[key(profileId)] = gson.toJson(current)
        }
    }

    suspend fun removeItem(profileId: String, playlistId: String, item: FavoriteItem) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key(profileId)]).map { pl ->
                if (pl.id != playlistId) pl
                else pl.copy(items = pl.items.filter { it.id != item.id || it.type != item.type })
            }
            prefs[key(profileId)] = gson.toJson(current)
        }
    }

    private fun readList(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
