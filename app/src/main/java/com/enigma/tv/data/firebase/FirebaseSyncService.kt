package com.enigma.tv.data.firebase

import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.tasks.await

class FirebaseSyncService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance("https://enigmatv-default-rtdb.firebaseio.com")
    private val gson = Gson()

    private fun userRef() = auth.currentUser?.uid?.let { db.reference.child("users").child(it) }

    suspend fun saveProfile(displayName: String, email: String) {
        val ref = userRef() ?: return
        ref.child("profile").setValue(
            mapOf(
                "displayName" to displayName,
                "email" to email,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
    }

    suspend fun pushFavorites(items: List<FavoriteItem>) {
        val ref = userRef() ?: return
        ref.child("favorites").setValue(gson.toJson(items)).await()
    }

    suspend fun pushContinueWatching(items: List<ContinueWatchingEntry>) {
        val ref = userRef() ?: return
        ref.child("continueWatching").setValue(gson.toJson(items)).await()
    }

    suspend fun pushPlaylists(items: List<Playlist>) {
        val ref = userRef() ?: return
        ref.child("playlists").setValue(gson.toJson(items)).await()
    }

    suspend fun pullAll(): CloudData? {
        val ref = userRef() ?: return null
        val snap = ref.get().await()
        if (!snap.exists()) return null
        val profileMap = snap.child("profile").value as? Map<*, *>
        return CloudData(
            profile = UserProfile(
                uid = auth.currentUser?.uid ?: "",
                email = profileMap?.get("email")?.toString() ?: auth.currentUser?.email.orEmpty(),
                displayName = profileMap?.get("displayName")?.toString()
                    ?: auth.currentUser?.displayName.orEmpty()
            ),
            favorites = parseFavorites(snap.child("favorites").getValue(String::class.java)),
            continueWatching = parseContinue(snap.child("continueWatching").getValue(String::class.java)),
            playlists = parsePlaylists(snap.child("playlists").getValue(String::class.java))
        )
    }

    private fun parseFavorites(json: String?): List<FavoriteItem> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<FavoriteItem>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun parseContinue(json: String?): List<ContinueWatchingEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ContinueWatchingEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun parsePlaylists(json: String?): List<Playlist> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}

data class CloudData(
    val profile: UserProfile,
    val favorites: List<FavoriteItem>,
    val continueWatching: List<ContinueWatchingEntry>,
    val playlists: List<Playlist>
)
