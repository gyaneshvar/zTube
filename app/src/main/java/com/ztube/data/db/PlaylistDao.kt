package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val videoCount: Int,
    val youtubePlaylistId: String?,
)

@Dao
interface PlaylistDao {

    @Query(
        "SELECT p.id AS id, p.name AS name, COUNT(v.videoUrl) AS videoCount, " +
            "p.youtubePlaylistId AS youtubePlaylistId FROM playlists p " +
            "LEFT JOIN playlist_videos v ON v.playlistId = p.id " +
            "GROUP BY p.id ORDER BY p.createdAt DESC",
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position")
    fun videos(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlists WHERE youtubePlaylistId IS NOT NULL")
    suspend fun youtubePlaylists(): List<PlaylistEntity>

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearVideos(playlistId: Long)

    @Query("DELETE FROM playlists WHERE youtubePlaylistId IS NOT NULL")
    suspend fun deleteYoutubePlaylists()

    @Query("UPDATE playlists SET youtubePlaylistId = NULL")
    suspend fun detachYoutubePlaylists()

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertVideo(video: PlaylistVideoEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoUrl = :videoUrl")
    suspend fun removeVideo(playlistId: Long, videoUrl: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
