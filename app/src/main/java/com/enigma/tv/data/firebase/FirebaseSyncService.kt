package com.enigma.tv.data.firebase

import com.enigma.tv.data.ContinueWatchingEntry
import com.enigma.tv.data.FavoriteItem
import com.enigma.tv.data.Playlist
import com.enigma.tv.data.UserProfile
import com.enigma.tv.data.ViewerProfile
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
        const val SYNC_TIMEOUT_MS = 15_000L
    }

    private fun accountRef() = auth.currentUser?.uid?.let { uid ->
        db.reference.child("users").child(uid)
    }

    private fun profileRef(profileId: String) = accountRef()?.child("profiles")?.child(profileId)

    suspend fun pushAccountMeta(activeProfileId: String, profiles: List<ViewerProfile>) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            accountRef()?.updateChildren(
                mapOf(
                    "activeProfileId" to activeProfileId,
                    "profileList" to gson.toJson(profiles),
                    "updatedAt" to System.currentTimeMillis()
                )
            )?.await()
        }
    }

    suspend fun pushProfileData(
        profileId: String,
        displayName: String,
        email: String,
        favorites: List<FavoriteItem>,
        continueWatching: List<ContinueWatchingEntry>,
        playlists: List<Playlist>
    ) = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            val ref = profileRef(profileId) ?: return@withTimeout
            ref.updateChildren(
                mapOf(
                    "profile" to mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    "favorites" to gson.toJson(favorites),
                    "continueWatching" to gson.toJson(continueWatching),
                    "playlists" to gson.toJson(playlists)
                )
            ).await()
        }
    }

    suspend fun pullAccount(): AccountCloudData? = runCatching {
        withTimeout(SYNC_TIMEOUT_MS) {
            val ref = accountRef() ?: return@withTimeout null
            val snap = ref.get().await()
            if (!snap.exists()) return@withTimeout null

            val activeProfileId = snap.child("activeProfileId").getValue(String::class.java) ?: "default"
            val profiles = parseViewerProfiles(snap.child("profileList").getValue(String::class.java))
                .ifEmpty { listOf(ViewerProfile("default", "Main")) }

            val profileData = mutableMapOf<String, CloudData>()
            val profilesNode = snap.child("profiles")
            for (profile in profiles) {
                val node = profilesNode.child(profile.id)
                if (!node.exists()) continue
                val profileMap = node.child("profile").value as? Map<*, *>
                profileData[profile.id] = CloudData(
                    profile = UserProfile(
                        uid = auth.currentUser?.uid ?: "",
                        email = profileMap?.get("email")?.toString() ?: auth.currentUser?.email.orEmpty(),
                        displayName = profileMap?.get("displayName")?.toString() ?: profile.name
                    ),
                    favorites = parseFavorites(node.child("favorites").getValue(String::class.java)),
                    continueWatching = parseContinue(node.child("continueWatching").getValue(String::class.java)),
                    playlists = parsePlaylists(node.child("playlists").getValue(String::class.java))
                )
            }

            AccountCloudData(
                activeProfileId = activeProfileId,
                profiles = profiles,
                profileData = profileData
            )
        }
    }.getOrNull()

    private fun parseViewerProfiles(json: String?): List<ViewerProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ViewerProfile>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
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

data class AccountCloudData(
    val activeProfileId: String,
    val profiles: List<ViewerProfile>,
    val profileData: Map<String, CloudData>
)
