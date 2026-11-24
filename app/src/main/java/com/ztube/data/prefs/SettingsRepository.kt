package com.ztube.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

data class YoutubeAccount(
    val email: String,
    val channelId: String?,
    val channelTitle: String?,
    val channelAvatar: String?,
    val lastSyncedAt: Long?,
    val lastSyncAttemptAt: Long?,
    val needsReauth: Boolean,
)

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

    val youtubeAccount: Flow<YoutubeAccount?> = context.dataStore.data.map { prefs ->
        prefs[ACCOUNT_EMAIL]?.let {
            YoutubeAccount(
                email = it,
                channelId = prefs[CHANNEL_ID],
                channelTitle = prefs[CHANNEL_TITLE],
                channelAvatar = prefs[CHANNEL_AVATAR],
                lastSyncedAt = prefs[LAST_SYNCED_AT],
                lastSyncAttemptAt = prefs[LAST_SYNC_ATTEMPT_AT],
                needsReauth = prefs[NEEDS_REAUTH] ?: false,
            )
        }
    }

    suspend fun setYoutubeAccount(email: String, channelId: String?, channelTitle: String?, channelAvatar: String?) {
        context.dataStore.edit { prefs ->
            prefs[ACCOUNT_EMAIL] = email
            setOrRemove(prefs, CHANNEL_ID, channelId)
            setOrRemove(prefs, CHANNEL_TITLE, channelTitle)
            setOrRemove(prefs, CHANNEL_AVATAR, channelAvatar)
            prefs[NEEDS_REAUTH] = false
        }
    }

    suspend fun setNeedsReauth(needsReauth: Boolean) {
        context.dataStore.edit { it[NEEDS_REAUTH] = needsReauth }
    }

    suspend fun setSynced(at: Long) {
        context.dataStore.edit {
            it[LAST_SYNCED_AT] = at
            it[NEEDS_REAUTH] = false
        }
    }

    suspend fun setSyncAttempted(at: Long) {
        context.dataStore.edit { it[LAST_SYNC_ATTEMPT_AT] = at }
    }

    suspend fun clearYoutubeAccount() {
        context.dataStore.edit {
            it.remove(ACCOUNT_EMAIL)
            it.remove(CHANNEL_ID)
            it.remove(CHANNEL_TITLE)
            it.remove(CHANNEL_AVATAR)
            it.remove(LAST_SYNCED_AT)
            it.remove(LAST_SYNC_ATTEMPT_AT)
            it.remove(NEEDS_REAUTH)
        }
    }

    suspend fun signInPromptVersion(): Long = context.dataStore.data.first()[SIGN_IN_PROMPT_VERSION] ?: 0L

    suspend fun setSignInPromptVersion(version: Long) {
        context.dataStore.edit { it[SIGN_IN_PROMPT_VERSION] = version }
    }

    suspend fun updateLastCheckAt(): Long = context.dataStore.data.first()[UPDATE_LAST_CHECK_AT] ?: 0L

    suspend fun setUpdateLastCheckAt(at: Long) {
        context.dataStore.edit { it[UPDATE_LAST_CHECK_AT] = at }
    }

    suspend fun updateSnoozedVersion(): String? = context.dataStore.data.first()[UPDATE_SNOOZED_VERSION]

    suspend fun updateSnoozedUntil(): Long = context.dataStore.data.first()[UPDATE_SNOOZED_UNTIL] ?: 0L

    suspend fun setUpdateSnoozed(version: String, until: Long) {
        context.dataStore.edit {
            it[UPDATE_SNOOZED_VERSION] = version
            it[UPDATE_SNOOZED_UNTIL] = until
        }
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

    private fun setOrRemove(prefs: MutablePreferences, key: Preferences.Key<String>, value: String?) {
        if (value != null) prefs[key] = value else prefs.remove(key)
    }

    private companion object {
        val SPEED = floatPreferencesKey("playback_speed")
        val QUALITY = intPreferencesKey("quality")
        val SPONSOR_BLOCK = booleanPreferencesKey("sponsor_block")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val CHANNEL_ID = stringPreferencesKey("youtube_channel_id")
        val CHANNEL_TITLE = stringPreferencesKey("youtube_channel_title")
        val CHANNEL_AVATAR = stringPreferencesKey("youtube_channel_avatar")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val LAST_SYNC_ATTEMPT_AT = longPreferencesKey("youtube_last_sync_attempt_at")
        val NEEDS_REAUTH = booleanPreferencesKey("needs_reauth")
        val SIGN_IN_PROMPT_VERSION = longPreferencesKey("sign_in_prompt_version")
        val UPDATE_LAST_CHECK_AT = longPreferencesKey("update_last_check_at")
        val UPDATE_SNOOZED_VERSION = stringPreferencesKey("update_snoozed_version")
        val UPDATE_SNOOZED_UNTIL = longPreferencesKey("update_snoozed_until")
        const val MAX_RECENT = 15
    }
}
