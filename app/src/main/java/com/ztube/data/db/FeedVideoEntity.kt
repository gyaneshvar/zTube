package com.ztube.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ztube.data.extractor.VideoItem

@Entity(tableName = "feed_videos", indices = [Index("channelUrl")])
data class FeedVideoEntity(
    @PrimaryKey val videoUrl: String,
    val channelUrl: String,
    val title: String,
    val uploader: String,
    val uploaderUrl: String,
    val thumbnailUrl: String?,
    val uploaderAvatarUrl: String?,
    val durationSeconds: Long,
    val viewCount: Long,
    val uploadedText: String?,
    val uploadedAtMillis: Long?,
)

fun VideoItem.toFeedEntity(channelUrl: String) = FeedVideoEntity(
    videoUrl = url,
    channelUrl = channelUrl,
    title = title,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    uploaderAvatarUrl = uploaderAvatarUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadedText = uploadedText,
    uploadedAtMillis = uploadedAtMillis,
)

fun FeedVideoEntity.toVideoItem() = VideoItem(
    url = videoUrl,
    title = title,
    uploader = uploader,
    uploaderUrl = uploaderUrl,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    uploadedText = uploadedText,
    uploadedAtMillis = uploadedAtMillis,
    uploaderAvatarUrl = uploaderAvatarUrl,
)
