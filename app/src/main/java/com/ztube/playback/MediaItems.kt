package com.ztube.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

const val EXTRA_FRESH = "ztube.fresh"

fun videoMediaItem(
    url: String,
    title: String,
    uploader: String,
    thumbnail: String?,
    durationSeconds: Long,
    quality: Int,
    fresh: Boolean = false,
): MediaItem {
    val extras = Bundle().apply {
        putInt(EXTRA_QUALITY, quality)
        putBoolean(EXTRA_FRESH, fresh)
    }
    return MediaItem.Builder()
        .setMediaId(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(uploader)
                .setArtworkUri(thumbnail?.toUri())
                .setDurationMs(durationSeconds.takeIf { it > 0 }?.times(1000))
                .build(),
        )
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
        .build()
}
