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
import java.util.UUID

private val Context.profileDataStore by preferencesDataStore("enigma_profiles")

data class ViewerProfile(
    val id: String,
    val name: String,
    val avatarIndex: Int = 0,
    val avatarUri: String? = null,
    /** JPEG base64 for Firebase sync and cross-device restore */
    val avatarBase64: String? = null
)

class ProfileStore(private val context: Context) {
    private val gson = Gson()
    private val profilesKey = stringPreferencesKey("profiles_json")
    private val activeKey = stringPreferencesKey("active_profile_id")

    val profiles: Flow<List<ViewerProfile>> = context.profileDataStore.data.map { prefs ->
        readProfiles(prefs[profilesKey]).ifEmpty { listOf(defaultProfile()) }
    }

    val activeProfileId: Flow<String> = context.profileDataStore.data.map { prefs ->
        prefs[activeKey] ?: defaultProfile().id
    }

    suspend fun ensureDefaultProfile(): ViewerProfile {
        val prefs = context.profileDataStore.data.first()
        val list = readProfiles(prefs[profilesKey])
        if (list.isNotEmpty()) {
            val active = prefs[activeKey] ?: list.first().id
            if (list.any { it.id == active }) return list.first { it.id == active }
            setActive(list.first().id)
            return list.first()
        }
        val profile = defaultProfile()
        context.profileDataStore.edit {
            it[profilesKey] = gson.toJson(listOf(profile))
            it[activeKey] = profile.id
        }
        return profile
    }

    suspend fun setActive(id: String) {
        context.profileDataStore.edit { it[activeKey] = id }
    }

    private fun formatProfileName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return "Profile"
        return trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    suspend fun addProfile(name: String): ViewerProfile {
        val formatted = formatProfileName(name)
        var created = ViewerProfile(id = "", name = formatted, avatarIndex = 0)
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).toMutableList()
            created = ViewerProfile(
                id = UUID.randomUUID().toString(),
                name = formatted,
                avatarIndex = current.size % ProfileConstants.AVATAR_PRESET_COUNT
            )
            current.add(created)
            prefs[profilesKey] = gson.toJson(current.take(6))
            prefs[activeKey] = created.id
        }
        return created
    }

    suspend fun updateProfile(id: String, name: String? = null, avatarIndex: Int? = null, avatarUri: String? = null) {
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).map { p ->
                if (p.id != id) p
                else p.copy(
                    name = name?.let { formatProfileName(it) } ?: p.name,
                    avatarIndex = avatarIndex ?: p.avatarIndex,
                    avatarUri = avatarUri ?: p.avatarUri
                )
            }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun setProfileAvatarData(id: String, avatarUri: String?, avatarBase64: String?) {
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).map { p ->
                if (p.id == id) {
                    p.copy(
                        avatarUri = avatarUri,
                        avatarBase64 = avatarBase64
                    )
                } else p
            }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun setProfileAvatarIndex(id: String, index: Int) {
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).map { p ->
                if (p.id == id) {
                    p.copy(
                        avatarIndex = index.mod(ProfileConstants.AVATAR_PRESET_COUNT),
                        avatarUri = null,
                        avatarBase64 = null
                    )
                } else p
            }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun renameProfile(id: String, name: String) {
        val formatted = formatProfileName(name)
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).map {
                if (it.id == id) it.copy(name = formatted) else it
            }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    /**
     * Merge cloud profiles into local — never delete local-only profiles.
     */
    suspend fun importFromCloud(cloudProfiles: List<ViewerProfile>, activeId: String?) {
        val prefs = context.profileDataStore.data.first()
        val local = readProfiles(prefs[profilesKey])
        if (cloudProfiles.isEmpty() && local.isNotEmpty()) return

        val remoteById = cloudProfiles.associateBy { it.id }
        val merged = mutableListOf<ViewerProfile>()

        for (localP in local) {
            merged.add(mergeProfileEntry(localP, remoteById[localP.id]))
        }
        for (remote in cloudProfiles) {
            if (merged.none { it.id == remote.id }) {
                merged.add(remote)
            }
        }

        val final = if (merged.isEmpty()) listOf(defaultProfile()) else merged.take(6)

        context.profileDataStore.edit { store ->
            store[profilesKey] = gson.toJson(final)
            // Cloud activeProfileId is the source of truth.
            // Only fall back to the current local active if cloud didn't specify one.
            val active = activeId?.takeIf { id -> final.any { it.id == id } }
                ?: store[activeKey]?.takeIf { id -> final.any { it.id == id } }
                ?: final.first().id
            store[activeKey] = active
        }
    }

    private fun mergeProfileEntry(local: ViewerProfile, remote: ViewerProfile?): ViewerProfile {
        if (remote == null) return local
        // Cloud is always the source of truth for name and avatarIndex.
        // Only inherit local avatar data if cloud has none (locally uploaded but not yet pushed).
        val resolvedAvatarBase64 = remote.avatarBase64?.takeIf { it.isNotBlank() }
            ?: local.avatarBase64?.takeIf { it.isNotBlank() }
        val resolvedAvatarUri = remote.avatarUri?.takeIf { it.isNotBlank() }
            ?: local.avatarUri?.takeIf { it.isNotBlank() }
        return remote.copy(
            name = remote.name.ifBlank { local.name }.ifBlank { "Profile" },
            avatarUri = resolvedAvatarUri,
            avatarBase64 = resolvedAvatarBase64,
            avatarIndex = remote.avatarIndex.takeIf { it != 0 } ?: local.avatarIndex
        )
    }

    suspend fun snapshot(): Pair<List<ViewerProfile>, String> {
        val prefs = context.profileDataStore.data.first()
        val list = readProfiles(prefs[profilesKey]).ifEmpty { listOf(defaultProfile()) }
        val active = prefs[activeKey] ?: list.first().id
        return list to active
    }

    suspend fun removeProfile(id: String) {
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).filter { it.id != id }
            val safe = if (current.isEmpty()) listOf(defaultProfile()) else current
            prefs[profilesKey] = gson.toJson(safe)
            val active = prefs[activeKey]
            if (active == id || active == null) {
                prefs[activeKey] = safe.first().id
            }
        }
    }

    private fun readProfiles(json: String?): List<ViewerProfile> {
        if (json.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<ViewerProfile>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun defaultProfile() = ViewerProfile(id = "default", name = "Main")
}
