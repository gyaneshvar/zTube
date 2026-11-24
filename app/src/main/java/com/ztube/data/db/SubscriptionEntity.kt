package com.ztube.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelUrl: String,
    val name: String,
    val avatarUrl: String?,
    val subscribedAt: Long,
)
