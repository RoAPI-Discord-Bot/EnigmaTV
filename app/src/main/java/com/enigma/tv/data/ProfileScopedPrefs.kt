package com.enigma.tv.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Per-profile library keys. Legacy global keys are only read for the [default] profile
 * so Kid/Adult profiles do not share the same favorites or continue watching.
 */
object ProfileScopedPrefs {
    const val DEFAULT_PROFILE_ID = "default"

    fun jsonForProfile(
        prefs: Preferences,
        profileId: String,
        profileKey: Preferences.Key<String>,
        legacyKey: Preferences.Key<String>? = null
    ): String? {
        prefs[profileKey]?.let { return it }
        if (profileId == DEFAULT_PROFILE_ID && legacyKey != null) {
            return prefs[legacyKey]
        }
        return null
    }
}
