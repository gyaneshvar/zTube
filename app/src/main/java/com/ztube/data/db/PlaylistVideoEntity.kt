package com.ztube.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoUrl: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long,
    val position: Int,
)
