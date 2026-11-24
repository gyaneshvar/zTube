package com.ztube.data.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ZTubeDatabase =
        Room.databaseBuilder(context, ZTubeDatabase::class.java, "ztube.db")
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun subscriptionDao(db: ZTubeDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun historyDao(db: ZTubeDatabase): HistoryDao = db.historyDao()

    @Provides
    fun playlistDao(db: ZTubeDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun downloadDao(db: ZTubeDatabase): DownloadDao = db.downloadDao()
}
