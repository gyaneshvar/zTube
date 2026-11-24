package com.ztube.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val playbackSpeed: Flow<Float> = context.dataStore.data.map { it[SPEED] ?: 1f }
    val quality: Flow<Int> = context.dataStore.data.map { it[QUALITY] ?: 720 }
    val sponsorBlockEnabled: Flow<Boolean> = context.dataStore.data.map { it[SPONSOR_BLOCK] ?: true }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.dataStore.edit { it[SPEED] = speed }
    }

    suspend fun setQuality(height: Int) {
        context.dataStore.edit { it[QUALITY] = height }
    }

    suspend fun setSponsorBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SPONSOR_BLOCK] = enabled }
    }

    private companion object {
        val SPEED = floatPreferencesKey("playback_speed")
        val QUALITY = intPreferencesKey("quality")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
    }
}
