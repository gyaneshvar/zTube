package com.ztube.data.account

import androidx.room.withTransaction
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.ztube.data.db.PlaylistDao
import com.ztube.data.db.PlaylistEntity
import com.ztube.data.db.PlaylistVideoEntity
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.SubscriptionEntity
import com.ztube.data.db.ZTubeDatabase
import com.ztube.data.prefs.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncResult {
    data class Synced(val subscriptions: Int, val playlists: Int) : SyncResult
    data object NeedsReauth : SyncResult
    data object QuotaExceeded : SyncResult
    data class Failed(val message: String) : SyncResult
}

fun SyncResult.message(): String = when (this) {
    is SyncResult.Synced -> "Synced $subscriptions channels, $playlists playlists"
    SyncResult.NeedsReauth -> "Sign in again to keep syncing"
    SyncResult.QuotaExceeded -> "YouTube quota reached, try tomorrow"
    is SyncResult.Failed -> message
}

private class NeedsReauthException : Exception()

private class FetchedPlaylist(val id: String, val title: String, val videos: List<YoutubeVideo>?)

private class Fetched(
    val subscriptions: List<YoutubeSubscription>,
    val complete: Boolean,
    val playlists: List<FetchedPlaylist>,
)

@Singleton
class YoutubeSync @Inject constructor(
    private val auth: GoogleAuth,
    private val api: YoutubeDataApi,
    private val db: ZTubeDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val settings: SettingsRepository,
) {

    val mutex = Mutex()

    suspend fun sync(initialToken: String? = null): SyncResult = mutex.withLock { run(initialToken) }

    private suspend fun run(initialToken: String?): SyncResult {
        val email = settings.youtubeAccount.first()?.email ?: return SyncResult.Failed("Not signed in")
        return try {
            val token = initialToken ?: silentToken(email)
            val fetched = try {
                fetch(token)
            } catch (e: YoutubeUnauthorizedException) {
                auth.clearToken(token)
                fetch(silentToken(email))
            }
            apply(fetched)
            settings.setSynced(System.currentTimeMillis())
            SyncResult.Synced(fetched.subscriptions.size, fetched.playlists.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NeedsReauthException) {
            needsReauth()
        } catch (e: YoutubeUnauthorizedException) {
            needsReauth()
        } catch (e: YoutubeQuotaException) {
            SyncResult.QuotaExceeded
        } catch (e: YoutubeApiException) {
            if (e.code == 403) needsReauth() else SyncResult.Failed("Couldn't reach YouTube, try again later")
        } catch (e: IOException) {
            SyncResult.Failed("Couldn't reach YouTube, try again later")
        } catch (e: Exception) {
            SyncResult.Failed("Sync failed")
        }
    }

    private suspend fun needsReauth(): SyncResult {
        settings.setNeedsReauth(true)
        return SyncResult.NeedsReauth
    }

    private suspend fun silentToken(email: String): String =
        when (val result = auth.authorize(email)) {
            is AuthResult.Token -> result.token
            is AuthResult.NeedsResolution -> throw NeedsReauthException()
            is AuthResult.Failure -> throw failure(result.error)
            AuthResult.Cancelled -> throw NeedsReauthException()
        }

    private fun failure(error: Exception): Exception {
        if (error !is ApiException) return IOException(error)
        return when (error.statusCode) {
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT, CommonStatusCodes.INTERNAL_ERROR -> IOException(error)
            else -> NeedsReauthException()
        }
    }

    private suspend fun fetch(token: String): Fetched {
        val subscriptions = api.subscriptions(token)
        val playlists = api.playlists(token)
        val items = playlists.map { it.id to api.playlistItems(token, it.id) }
        val videos = api.videos(token, items.flatMap { (_, list) -> list.orEmpty().map { it.videoId } }.distinct())
            .associateBy { it.id }
        return Fetched(
            subscriptions.items,
            subscriptions.items.size >= subscriptions.totalResults,
            playlists.mapIndexed { index, playlist ->
                val list = items[index].second
                FetchedPlaylist(
                    playlist.id,
                    playlist.title,
                    list?.mapNotNull { videos[it.videoId] }?.distinctBy { it.id },
                )
            },
        )
    }

    private suspend fun apply(fetched: Fetched) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val existing = subscriptionDao.allRows().associateBy { it.channelUrl }
            val fetchedUrls = fetched.subscriptions.map { channelUrl(it.channelId) }.toSet()
            fetched.subscriptions.forEach {
                val url = channelUrl(it.channelId)
                if (existing.containsKey(url)) {
                    subscriptionDao.markFromYoutube(url, it.title, it.avatarUrl)
                } else {
                    subscriptionDao.insertIgnore(
                        SubscriptionEntity(url, it.title, it.avatarUrl, now, fromYoutube = true, addedLocally = false),
                    )
                }
            }
            if (fetched.complete) {
                existing.values.filter { it.fromYoutube && it.channelUrl !in fetchedUrls }.forEach {
                    if (it.addedLocally) subscriptionDao.clearFromYoutube(it.channelUrl) else subscriptionDao.delete(it.channelUrl)
                }
            }

            val existingPlaylists = playlistDao.youtubePlaylists().associateBy { it.youtubePlaylistId }
            fetched.playlists.forEach { playlist ->
                val current = existingPlaylists[playlist.id]
                val id = when {
                    current == null -> playlistDao.insert(PlaylistEntity(name = playlist.title, createdAt = now, youtubePlaylistId = playlist.id))
                    else -> current.id.also { if (current.name != playlist.title) playlistDao.rename(it, playlist.title) }
                }
                if (playlist.videos != null) {
                    playlistDao.clearVideos(id)
                    playlist.videos.forEachIndexed { index, video ->
                        playlistDao.insertVideo(
                            PlaylistVideoEntity(id, videoUrl(video.id), video.title, video.uploader, video.thumbnailUrl, video.durationSeconds, index),
                        )
                    }
                }
            }
            val fetchedIds = fetched.playlists.map { it.id }.toSet()
            existingPlaylists.values.filter { it.youtubePlaylistId !in fetchedIds }.forEach { playlistDao.delete(it.id) }
        }
    }

    private fun channelUrl(id: String) = "https://www.youtube.com/channel/$id"

    private fun videoUrl(id: String) = "https://www.youtube.com/watch?v=$id"
}
