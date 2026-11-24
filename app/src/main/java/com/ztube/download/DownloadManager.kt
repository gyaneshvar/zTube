package com.ztube.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ztube.data.db.DownloadDao
import com.ztube.data.db.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DownloadDao,
) {

    suspend fun start(download: DownloadEntity, height: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(download.videoUrl))
        val stale = dao.get(download.videoUrl)
        withContext(Dispatchers.IO) { stale?.directory()?.deleteRecursively() }
        dao.upsert(download.copy(status = DownloadStatus.QUEUED, downloadedBytes = 0))
        enqueue(download.videoUrl, height)
    }

    suspend fun retry(download: DownloadEntity) {
        dao.updateStatus(download.videoUrl, DownloadStatus.QUEUED)
        val height = download.quality.removeSuffix("p").toIntOrNull()
            ?: if (download.quality == DownloadStreams.AUDIO_ONLY_LABEL) 0 else DEFAULT_HEIGHT
        enqueue(download.videoUrl, height)
    }

    suspend fun delete(videoUrl: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(videoUrl))
        val download = dao.get(videoUrl)
        dao.delete(videoUrl)
        withContext(Dispatchers.IO) { download?.directory()?.deleteRecursively() }
    }

    private fun enqueue(videoUrl: String, height: Int) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_URL to videoUrl, DownloadWorker.KEY_HEIGHT to height))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(workName(videoUrl), ExistingWorkPolicy.KEEP, request)
    }

    private companion object {
        const val DEFAULT_HEIGHT = 720
    }

    private fun workName(videoUrl: String) = "download:$videoUrl"
}
