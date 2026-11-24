package com.ztube.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ztube.data.db.DownloadDao
import com.ztube.data.extractor.best
import com.ztube.data.extractor.YoutubeRepository
import com.ztube.playback.youtubeUserAgent
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: DownloadDao,
    private val repository: YoutubeRepository,
) : CoroutineWorker(context, params) {

    private val url = inputData.getString(KEY_URL).orEmpty()
    private var title = ""
    private var lastReport = 0L

    override suspend fun doWork(): Result {
        val entity = dao.get(url) ?: return Result.failure()
        title = entity.title
        return try {
            dao.updateStatus(url, DownloadStatus.RUNNING)
            download(inputData.getInt(KEY_HEIGHT, 0))
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.updateStatus(url, DownloadStatus.QUEUED) }
            throw e
        } catch (e: Exception) {
            dao.updateStatus(url, DownloadStatus.FAILED)
            Result.failure()
        }
    }

    private suspend fun download(height: Int) {
        showProgress(0)
        val info = repository.streamInfo(url, fresh = true)
        val selection = DownloadStreams.select(info, height) ?: throw IOException("No stream available")
        val dir = File(downloadsDir(applicationContext), info.id).apply { mkdirs() }
        val parts = listOfNotNull(
            selection.video?.let { File(dir, "video.mp4") to it.content },
            selection.audio?.let { File(dir, "audio.m4a") to it.content },
        )

        val lengths = parts.map { (_, source) -> contentLength(source) }
        val total = lengths.sum()
        dao.updateFiles(
            url,
            selection.video?.let { File(dir, "video.mp4").path },
            selection.audio?.let { File(dir, "audio.m4a").path }.orEmpty(),
            total,
            DownloadStatus.RUNNING,
        )

        var downloaded = parts.mapIndexed { index, (file, _) ->
            if (file.length() >= lengths[index]) file.delete()
            file.length()
        }.sum()
        parts.forEachIndexed { index, (file, source) ->
            fetch(source, file, lengths[index]) { bytes ->
                downloaded += bytes
                report(downloaded, total)
            }
        }

        info.thumbnails.best()?.let { thumbnail ->
            try {
                fetchWhole(thumbnail, File(dir, "thumb.jpg"))
            } catch (e: IOException) {
                File(dir, "thumb.jpg").delete()
            }
        }
        dao.updateProgress(url, total, DownloadStatus.DONE)
    }

    private suspend fun report(downloaded: Long, total: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastReport < REPORT_INTERVAL_MS) return
        lastReport = now
        dao.updateProgress(url, downloaded, DownloadStatus.RUNNING)
        showProgress(if (total > 0) (downloaded * 100 / total).toInt() else 0)
    }

    private suspend fun showProgress(percent: Int) {
        try {
            setForeground(foregroundInfo(percent))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
        }
    }

    private suspend fun contentLength(source: String): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(source).header("Range", "bytes=0-0").build()
        client.newCall(request).execute().use { response ->
            val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            if (response.code != 206 || total == null) throw IOException("Range not supported (HTTP ${response.code})")
            total
        }
    }

    private suspend fun fetch(source: String, file: File, total: Long, onBytes: suspend (Long) -> Unit) {
        while (file.length() < total) {
            val start = file.length()
            val end = minOf(start + CHUNK_BYTES, total) - 1
            val request = Request.Builder().url(source).header("Range", "bytes=$start-$end").build()
            withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { response ->
                    if (response.code != 206) throw IOException("HTTP ${response.code}")
                    val buffer = ByteArray(BUFFER_BYTES)
                    FileOutputStream(file, true).use { out ->
                        val input = response.body.byteStream()
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            onBytes(read.toLong())
                        }
                    }
                }
            }
            if (file.length() == start) throw IOException("No progress")
        }
    }

    private suspend fun fetchWhole(source: String, file: File) = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(source).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            file.outputStream().use { response.body.byteStream().copyTo(it) }
        }
    }

    private fun foregroundInfo(percent: Int): ForegroundInfo {
        val manager = NotificationManagerCompat.from(applicationContext)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Downloads")
                .build(),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        val id = url.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_HEIGHT = "height"

        private const val CHANNEL_ID = "downloads"
        private const val CHUNK_BYTES = 10L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val REPORT_INTERVAL_MS = 1_000L

        private val client = OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                chain.proceed(request.newBuilder().header("User-Agent", youtubeUserAgent(request.url.toString())).build())
            }
            .build()
    }
}
