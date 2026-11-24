package com.ztube.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long,
    @ColumnInfo(defaultValue = "0") val fromYoutube: Boolean = false,
    @ColumnInfo(defaultValue = "1") val addedLocally: Boolean = true,
    @ColumnInfo(defaultValue = "0") val muted: Boolean = false,
)
