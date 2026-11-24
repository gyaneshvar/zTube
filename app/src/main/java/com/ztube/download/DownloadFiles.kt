package com.ztube.download

import android.content.Context
import android.net.Uri
import com.ztube.data.db.DownloadEntity
import java.io.File

object DownloadStatus {
    const val QUEUED = "QUEUED"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

fun downloadsDir(context: Context): File =
    context.getExternalFilesDir("downloads") ?: File(context.filesDir, "downloads")

fun DownloadEntity.directory(): File? =
    (videoPath ?: audioPath).takeIf { it.isNotEmpty() }?.let { File(it).parentFile }

fun DownloadEntity.thumbnailModel(): String? {
    val local = directory()?.let { File(it, "thumb.jpg") }
    return if (local != null && local.exists()) Uri.fromFile(local).toString() else thumbnailUrl
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format(bytes / 1_000)
}
