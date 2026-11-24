package com.ztube.data.account

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class YoutubeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: YoutubeSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        when (sync.sync()) {
            is SyncResult.Failed -> Result.retry()
            else -> Result.success()
        }

    companion object {
        const val WORK_NAME = "youtube-sync"
    }
}
