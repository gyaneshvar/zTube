package com.ztube.ui.components

import android.text.format.DateUtils
import com.ztube.data.extractor.VideoItem
import java.util.Locale

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatCount(count: Long): String = when {
    count >= 1_000_000_000 -> compact(count / 1_000_000_000.0, "B")
    count >= 1_000_000 -> compact(count / 1_000_000.0, "M")
    count >= 1_000 -> compact(count / 1_000.0, "K")
    else -> count.toString()
}

private fun compact(value: Double, suffix: String): String {
    val text = if (value >= 100) String.format(Locale.US, "%.0f", value) else String.format(Locale.US, "%.1f", value)
    return text.removeSuffix(".0") + suffix
}

fun videoMeta(video: VideoItem, withUploader: Boolean = true): String = listOfNotNull(
    video.uploader.takeIf { withUploader && it.isNotEmpty() },
    video.viewCount.takeIf { it >= 0 }?.let { "${formatCount(it)} views" },
    video.uploadedText?.takeIf { it.isNotEmpty() },
).joinToString(" · ")

fun formatTimeAgo(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
