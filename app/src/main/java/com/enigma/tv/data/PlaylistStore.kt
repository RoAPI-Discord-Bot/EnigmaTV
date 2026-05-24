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
    private val key = stringPreferencesKey("playlists")

    val playlists: Flow<List<Playlist>> = context.playlistDataStore.data.map { prefs ->
        readList(prefs[key])
    }

    suspend fun replaceAll(items: List<Playlist>) {
        context.playlistDataStore.edit { prefs ->
            prefs[key] = gson.toJson(items)
        }
    }

    suspend fun createPlaylist(name: String): Playlist {
        val playlist = Playlist(id = UUID.randomUUID().toString(), name = name.trim())
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key]).toMutableList()
            current.add(0, playlist)
            prefs[key] = gson.toJson(current)
        }
        return playlist
    }

    suspend fun deletePlaylist(playlistId: String) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key]).filter { it.id != playlistId }
            prefs[key] = gson.toJson(current)
        }
    }

    suspend fun addItem(playlistId: String, item: FavoriteItem) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key]).map { pl ->
                if (pl.id != playlistId) pl
                else {
                    val items = pl.items.filter { it.id != item.id || it.type != item.type }
                    pl.copy(items = listOf(item) + items)
                }
            }
            prefs[key] = gson.toJson(current)
        }
    }

    suspend fun removeItem(playlistId: String, item: FavoriteItem) {
        context.playlistDataStore.edit { prefs ->
            val current = readList(prefs[key]).map { pl ->
                if (pl.id != playlistId) pl
                else pl.copy(items = pl.items.filter { it.id != item.id || it.type != item.type })
            }
            prefs[key] = gson.toJson(current)
        }
    }

    private fun readList(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
