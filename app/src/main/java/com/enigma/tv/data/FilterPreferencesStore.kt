package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.filterPreferencesDataStore by preferencesDataStore("enigma_filter_settings")

data class FilterSettings(
    val profanityMode: String = "DISABLED", // "DISABLED", "BLEEP"
    val profanitySensitivity: String = "MEDIUM", // "LOW" (Severe), "MEDIUM" (Moderate+), "HIGH" (All)
    val sceneMode: String = "DISABLED", // "DISABLED", "BLUR", "SKIP"
    val sceneSensitivity: String = "MODERATE", // "MODERATE", "STRICT"
    val captionOffsetMs: Long = 0L // -10000ms to +10000ms
)

class FilterPreferencesStore(private val context: Context) {
    private val profanityModeKey = stringPreferencesKey("profanity_mode")
    private val profanitySensitivityKey = stringPreferencesKey("profanity_sensitivity")
    private val sceneModeKey = stringPreferencesKey("scene_mode")
    private val sceneSensitivityKey = stringPreferencesKey("scene_sensitivity")
    private val captionOffsetKey = longPreferencesKey("caption_offset_ms")

    val settingsFlow: Flow<FilterSettings> = context.filterPreferencesDataStore.data.map { prefs ->
        FilterSettings(
            profanityMode = prefs[profanityModeKey] ?: "DISABLED",
            profanitySensitivity = prefs[profanitySensitivityKey] ?: "MEDIUM",
            sceneMode = prefs[sceneModeKey] ?: "DISABLED",
            sceneSensitivity = prefs[sceneSensitivityKey] ?: "MODERATE",
            captionOffsetMs = prefs[captionOffsetKey] ?: 0L
        )
    }

    suspend fun getSettings(): FilterSettings {
        return settingsFlow.first()
    }

    suspend fun setProfanityMode(mode: String) {
        context.filterPreferencesDataStore.edit { prefs ->
            prefs[profanityModeKey] = mode
        }
    }

    suspend fun setProfanitySensitivity(sensitivity: String) {
        context.filterPreferencesDataStore.edit { prefs ->
            prefs[profanitySensitivityKey] = sensitivity
        }
    }

    suspend fun setSceneMode(mode: String) {
        context.filterPreferencesDataStore.edit { prefs ->
            prefs[sceneModeKey] = mode
        }
    }

    suspend fun setSceneSensitivity(sensitivity: String) {
        context.filterPreferencesDataStore.edit { prefs ->
            prefs[sceneSensitivityKey] = sensitivity
        }
    }

    suspend fun setCaptionOffsetMs(offsetMs: Long) {
        context.filterPreferencesDataStore.edit { prefs ->
            prefs[captionOffsetKey] = offsetMs
        }
    }
}
