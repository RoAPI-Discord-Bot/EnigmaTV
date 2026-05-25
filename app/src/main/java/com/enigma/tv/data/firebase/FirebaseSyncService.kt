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
import kotlinx.coroutines.withTimeout

class FirebaseSyncService {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance("https://enigmatv-default-rtdb.firebaseio.com")
    private val gson = Gson()

    private companion object {
        const val SYNC_TIMEOUT_MS = 8_000L
    }

    private fun userRef(profileId: String) = auth.currentUser?.uid?.let { uid ->
        db.reference.child("users").child(uid).child("profiles").child(profileId)
    }

    suspend fun saveProfile(displayName: String, email: String, profileId: String) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            val ref = userRef(profileId) ?: return@withTimeout
            ref.child("profile").setValue(
                mapOf(
                    "displayName" to displayName,
                    "email" to email,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
        }
    }

    suspend fun pushFavorites(profileId: String, items: List<FavoriteItem>) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            userRef(profileId)?.child("favorites")?.setValue(gson.toJson(items))?.await()
        }
    }

    suspend fun pushContinueWatching(profileId: String, items: List<ContinueWatchingEntry>) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            userRef(profileId)?.child("continueWatching")?.setValue(gson.toJson(items))?.await()
        }
    }

    suspend fun pushPlaylists(profileId: String, items: List<Playlist>) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            userRef(profileId)?.child("playlists")?.setValue(gson.toJson(items))?.await()
        }
    }

    suspend fun pullProfile(profileId: String): CloudData? = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            val ref = userRef(profileId) ?: return@withTimeout null
            val snap = ref.get().await()
            if (!snap.exists()) return@withTimeout null
            val profileMap = snap.child("profile").value as? Map<*, *>
            CloudData(
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
    }.getOrNull()

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
