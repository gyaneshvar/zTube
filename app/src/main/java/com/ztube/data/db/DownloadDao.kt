package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE videoUrl = :videoUrl")
    fun download(videoUrl: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloads WHERE videoUrl = :videoUrl")
    suspend fun get(videoUrl: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :downloadedBytes, status = :status WHERE videoUrl = :videoUrl")
    suspend fun updateProgress(videoUrl: String, downloadedBytes: Long, status: String)

    @Query("UPDATE downloads SET status = :status WHERE videoUrl = :videoUrl")
    suspend fun updateStatus(videoUrl: String, status: String)

    @Query(
        "UPDATE downloads SET videoPath = :videoPath, audioPath = :audioPath, totalBytes = :totalBytes, " +
            "status = :status WHERE videoUrl = :videoUrl",
    )
    suspend fun updateFiles(videoUrl: String, videoPath: String?, audioPath: String, totalBytes: Long, status: String)

    @Query("DELETE FROM downloads WHERE videoUrl = :videoUrl")
    suspend fun delete(videoUrl: String)
}
