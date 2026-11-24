package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class FeedChannelState(
    val channelUrl: String,
    val lastFetchedAt: Long?,
    val newestUpload: Long?,
)

@Dao
abstract class FeedDao {

    @Query(
        "SELECT f.* FROM feed_videos f INNER JOIN subscriptions s ON s.channelUrl = f.channelUrl " +
            "WHERE s.muted = 0 ORDER BY f.uploadedAtMillis IS NULL, f.uploadedAtMillis DESC LIMIT 300",
    )
    abstract fun feed(): Flow<List<FeedVideoEntity>>

    @Query(
        "SELECT s.channelUrl AS channelUrl, c.lastFetchedAt AS lastFetchedAt, " +
            "(SELECT MAX(v.uploadedAtMillis) FROM feed_videos v WHERE v.channelUrl = s.channelUrl) AS newestUpload " +
            "FROM subscriptions s LEFT JOIN feed_channels c ON c.channelUrl = s.channelUrl WHERE s.muted = 0",
    )
    abstract suspend fun channelStates(): List<FeedChannelState>

    @Query("DELETE FROM feed_videos WHERE channelUrl = :channelUrl")
    protected abstract suspend fun deleteVideos(channelUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertVideos(videos: List<FeedVideoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun upsertChannel(channel: FeedChannelEntity)

    @Query("DELETE FROM feed_videos WHERE channelUrl NOT IN (SELECT channelUrl FROM subscriptions WHERE muted = 0)")
    protected abstract suspend fun deleteUnsubscribedVideos()

    @Query("DELETE FROM feed_channels WHERE channelUrl NOT IN (SELECT channelUrl FROM subscriptions WHERE muted = 0)")
    protected abstract suspend fun deleteUnsubscribedChannels()

    @Transaction
    open suspend fun replaceChannel(channelUrl: String, videos: List<FeedVideoEntity>, fetchedAt: Long) {
        deleteVideos(channelUrl)
        insertVideos(videos)
        upsertChannel(FeedChannelEntity(channelUrl, fetchedAt))
    }

    @Transaction
    open suspend fun pruneUnsubscribed() {
        deleteUnsubscribedVideos()
        deleteUnsubscribedChannels()
    }
}
