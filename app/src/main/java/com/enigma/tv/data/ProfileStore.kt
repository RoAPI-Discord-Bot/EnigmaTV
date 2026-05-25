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
    val name: String
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
        val profile = ViewerProfile(id = UUID.randomUUID().toString(), name = trimmed)
        context.profileDataStore.edit { prefs ->
            val current = readProfiles(prefs[profilesKey]).toMutableList()
            current.add(profile)
            prefs[profilesKey] = gson.toJson(current.take(6))
            prefs[activeKey] = profile.id
        }
        return profile
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
