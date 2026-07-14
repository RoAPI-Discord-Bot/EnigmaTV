package com.enigma.tv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("enigma_session")

class UserSessionStore(private val context: Context) {
    private val onboardingKey = booleanPreferencesKey("onboarding_complete")

    val onboardingComplete: Flow<Boolean> = context.sessionDataStore.data.map {
        it[onboardingKey] ?: false
    }

    suspend fun isOnboardingComplete(): Boolean = onboardingComplete.first()

    suspend fun setOnboardingComplete() {
        context.sessionDataStore.edit { it[onboardingKey] = true }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
