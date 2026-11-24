package com.ztube.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val videoPath: String?,
    val audioPath: String,
    val quality: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val createdAt: Long,
)
