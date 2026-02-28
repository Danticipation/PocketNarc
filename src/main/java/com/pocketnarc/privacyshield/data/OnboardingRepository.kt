package com.pocketnarc.privacyshield.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

class OnboardingRepository(private val context: Context) {
    private val onboardingCompleteKey = booleanPreferencesKey("onboarding_complete")

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[onboardingCompleteKey] ?: false
    }

    suspend fun setOnboardingComplete() {
        context.dataStore.edit { preferences ->
            preferences[onboardingCompleteKey] = true
        }
    }
}
