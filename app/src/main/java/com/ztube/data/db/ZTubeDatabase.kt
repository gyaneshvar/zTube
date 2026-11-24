package com.ztube.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubscriptionEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ZTubeDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN fromYoutube INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN addedLocally INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN youtubePlaylistId TEXT")
    }
}
