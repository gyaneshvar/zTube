package com.ztube.data.extractor

data class VideoItem(
    val url: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedText: String?,
    val uploadedAtMillis: Long? = null,
)
