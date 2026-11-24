package com.ztube.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_channels")
data class FeedChannelEntity(
    @PrimaryKey val channelUrl: String,
    val lastFetchedAt: Long,
)
