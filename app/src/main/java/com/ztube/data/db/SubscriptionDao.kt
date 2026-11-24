package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelUrl = :channelUrl)")
    fun isSubscribed(channelUrl: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>): List<Long>

    @Query("DELETE FROM subscriptions WHERE channelUrl = :channelUrl")
    suspend fun delete(channelUrl: String)
}
