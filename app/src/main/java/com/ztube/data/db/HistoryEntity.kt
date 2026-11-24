package com.ztube.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val positionMs: Long,
    val watchedAt: Long,
)
