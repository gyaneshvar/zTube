package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HistoryDao {

    @Query("SELECT * FROM history ORDER BY watchedAt DESC")
    abstract fun all(): Flow<List<HistoryEntity>>

    @Query("SELECT positionMs FROM history WHERE videoUrl = :videoUrl")
    abstract suspend fun position(videoUrl: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIgnore(entity: HistoryEntity): Long

    @Query(
        "UPDATE history SET title = :title, uploader = :uploader, thumbnailUrl = :thumbnailUrl, " +
            "durationSeconds = :durationSeconds, watchedAt = :watchedAt WHERE videoUrl = :videoUrl",
    )
    protected abstract suspend fun touch(
        videoUrl: String,
        title: String,
        uploader: String,
        thumbnailUrl: String?,
        durationSeconds: Long,
        watchedAt: Long,
    )

    @Transaction
    open suspend fun record(entity: HistoryEntity) {
        if (insertIgnore(entity) == -1L) {
            touch(
                entity.videoUrl,
                entity.title,
                entity.uploader,
                entity.thumbnailUrl,
                entity.durationSeconds,
                entity.watchedAt,
            )
        }
    }

    @Query("UPDATE history SET positionMs = :positionMs WHERE videoUrl = :videoUrl")
    abstract suspend fun updatePosition(videoUrl: String, positionMs: Long)

    @Query("DELETE FROM history WHERE videoUrl = :videoUrl")
    abstract suspend fun delete(videoUrl: String)

    @Query("DELETE FROM history")
    abstract suspend fun clear()
}
