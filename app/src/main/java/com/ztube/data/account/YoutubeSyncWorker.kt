package com.ztube.data.account

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.ztube.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class YoutubeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: YoutubeSync,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (settings.youtubeAccount.first() == null) return Result.success()
        return when (sync.sync()) {
            is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        const val WORK_NAME = "youtube-sync"
        const val WORK_NAME_NOW = "youtube-sync-now"
    }
}
