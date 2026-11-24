package com.ztube.data.feed

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FeedRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val feed: FeedRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        feed.refresh(force = false, allowFallback = false)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "feed-refresh"
    }
}
