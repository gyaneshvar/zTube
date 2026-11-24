package com.ztube.data.account

import android.content.Context
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ztube.data.db.PlaylistDao
import com.ztube.data.db.SubscriptionDao
import com.ztube.data.db.ZTubeDatabase
import com.ztube.data.prefs.SettingsRepository
import com.ztube.data.prefs.YoutubeAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInResult {
    data class Done(val sync: SyncResult) : SignInResult
    data class DifferentAccount(val oldEmail: String, val newEmail: String) : SignInResult
    data class Failed(val message: String) : SignInResult
}

@Singleton
class YoutubeAccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: GoogleAuth,
    private val api: YoutubeDataApi,
    private val sync: YoutubeSync,
    private val db: ZTubeDatabase,
    private val subscriptionDao: SubscriptionDao,
    private val playlistDao: PlaylistDao,
    private val settings: SettingsRepository,
) {

    val account: Flow<YoutubeAccount?> = settings.youtubeAccount

    suspend fun onInteractiveTokenReceived(token: String): SignInResult {
        return try {
            val email = api.userEmail(token)
            val stored = settings.youtubeAccount.first()?.email
            if (stored != null && !stored.equals(email, ignoreCase = true)) {
                return SignInResult.DifferentAccount(stored, email)
            }
            val channel = api.channel(token)
            settings.setYoutubeAccount(email, channel?.id, channel?.title, channel?.avatarUrl)
            schedule()
            SignInResult.Done(sync.sync(token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: YoutubeQuotaException) {
            SignInResult.Failed(SyncResult.QuotaExceeded.message())
        } catch (e: YoutubeUnauthorizedException) {
            SignInResult.Failed("Sign-in failed, try again")
        } catch (e: IOException) {
            SignInResult.Failed("Couldn't reach Google, try again later")
        } catch (e: Exception) {
            SignInResult.Failed("Sign-in failed, try again")
        }
    }

    suspend fun syncNow(): SyncResult = sync.sync()

    suspend fun signOut(keepItems: Boolean) = withContext(NonCancellable) {
        sync.mutex.withLock {
            WorkManager.getInstance(context).cancelUniqueWork(YoutubeSyncWorker.WORK_NAME)
            val email = settings.youtubeAccount.first()?.email
            db.withTransaction {
                if (keepItems) {
                    subscriptionDao.deleteMuted()
                    subscriptionDao.keepAllLocally()
                    playlistDao.detachYoutubePlaylists()
                } else {
                    subscriptionDao.deleteYoutubeOnly()
                    subscriptionDao.clearAllFromYoutube()
                    playlistDao.deleteYoutubePlaylists()
                }
            }
            if (email != null) auth.revoke(email)
            settings.clearYoutubeAccount()
        }
    }

    suspend fun scheduleIfSignedIn() {
        if (settings.youtubeAccount.first() != null) schedule()
    }

    private fun schedule() {
        val request = PeriodicWorkRequestBuilder<YoutubeSyncWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(YoutubeSyncWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
