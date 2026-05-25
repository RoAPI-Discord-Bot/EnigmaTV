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

    suspend fun addProfile(name: String): ViewerProfile {
        val trimmed = name.trim().ifBlank { "Profile" }
        var created = ViewerProfile(id = "", name = trimmed, avatarIndex = 0)
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).toMutableList()
            created = ViewerProfile(
                id = UUID.randomUUID().toString(),
                name = trimmed,
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
                    name = name?.trim()?.takeIf { it.isNotBlank() } ?: p.name,
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
        val trimmed = name.trim().ifBlank { return }
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).map {
                if (it.id == id) it.copy(name = trimmed) else it
            }
            prefs[profilesKey] = gson.toJson(current)
        }
    }

    suspend fun importFromCloud(profiles: List<ViewerProfile>, activeId: String?) {
        if (profiles.isEmpty()) return
        val local = readProfiles(context.profileDataStore.data.first()[profilesKey])
        val merged = profiles.map { remote ->
            val localP = local.find { it.id == remote.id }
            when {
                localP == null -> remote
                !remote.avatarBase64.isNullOrBlank() -> remote
                !localP.avatarBase64.isNullOrBlank() -> remote.copy(
                    avatarBase64 = localP.avatarBase64,
                    avatarUri = localP.avatarUri ?: remote.avatarUri
                )
                else -> remote.copy(
                    name = remote.name.ifBlank { localP.name },
                    avatarUri = remote.avatarUri?.takeIf { it.isNotBlank() }
                        ?: localP.avatarUri,
                    avatarBase64 = remote.avatarBase64?.takeIf { it.isNotBlank() }
                        ?: localP.avatarBase64
                )
            }
        }
        context.profileDataStore.edit { prefs ->
            prefs[profilesKey] = gson.toJson(merged.take(6))
            val active = activeId?.takeIf { id -> merged.any { it.id == id } } ?: merged.first().id
            prefs[activeKey] = active
        }
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
