package com.ztube.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        DownloadEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ZTubeDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}
