package com.ztube.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    val autoplay: Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY] ?: true }
    val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_SEARCHES]?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun setAutoplay(enabled: Boolean) {
        context.dataStore.edit { it[AUTOPLAY] = enabled }
    }

    suspend fun addRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES]?.split('\n').orEmpty()
            val updated = (listOf(query) + current.filter { it != query && it.isNotEmpty() }).take(MAX_RECENT)
            prefs[RECENT_SEARCHES] = updated.joinToString("\n")
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_SEARCHES]?.split('\n').orEmpty()
            prefs[RECENT_SEARCHES] = current.filter { it != query }.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { it.remove(RECENT_SEARCHES) }
    }

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
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        const val MAX_RECENT = 15
    }
}
