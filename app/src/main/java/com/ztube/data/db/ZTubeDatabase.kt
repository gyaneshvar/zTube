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
        FeedVideoEntity::class,
        FeedChannelEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class ZTubeDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun historyDao(): HistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun downloadDao(): DownloadDao
    abstract fun feedDao(): FeedDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN fromYoutube INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN addedLocally INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE subscriptions ADD COLUMN muted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playlists ADD COLUMN youtubePlaylistId TEXT")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_videos` (`videoUrl` TEXT NOT NULL, `channelUrl` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, `uploader` TEXT NOT NULL, `uploaderUrl` TEXT NOT NULL, `thumbnailUrl` TEXT, " +
                "`uploaderAvatarUrl` TEXT, `durationSeconds` INTEGER NOT NULL, `viewCount` INTEGER NOT NULL, " +
                "`uploadedText` TEXT, `uploadedAtMillis` INTEGER, PRIMARY KEY(`videoUrl`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_feed_videos_channelUrl` ON `feed_videos` (`channelUrl`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `feed_channels` (`channelUrl` TEXT NOT NULL, `lastFetchedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`channelUrl`))",
        )
    }
}
