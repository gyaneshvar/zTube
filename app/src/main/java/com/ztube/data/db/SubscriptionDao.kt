package com.ztube.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    @Query("SELECT * FROM subscriptions WHERE muted = 0 ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelUrl = :channelUrl AND muted = 0)")
    fun isSubscribed(channelUrl: String): Flow<Boolean>

    @Query("SELECT * FROM subscriptions")
    suspend fun allRows(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE channelUrl = :channelUrl")
    suspend fun find(channelUrl: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(subscription: SubscriptionEntity): Long

    @Query(
        "UPDATE subscriptions SET addedLocally = 1, muted = 0, name = :name, avatarUrl = COALESCE(:avatarUrl, avatarUrl) " +
            "WHERE channelUrl = :channelUrl",
    )
    suspend fun markSubscribed(channelUrl: String, name: String, avatarUrl: String?)

    @Query("UPDATE subscriptions SET muted = 1, addedLocally = 0 WHERE channelUrl = :channelUrl")
    suspend fun mute(channelUrl: String)

    @Query("DELETE FROM subscriptions WHERE channelUrl = :channelUrl")
    suspend fun delete(channelUrl: String)

    @Query(
        "UPDATE subscriptions SET fromYoutube = 1, name = :name, avatarUrl = COALESCE(:avatarUrl, avatarUrl) " +
            "WHERE channelUrl = :channelUrl",
    )
    suspend fun markFromYoutube(channelUrl: String, name: String, avatarUrl: String?)

    @Query("UPDATE subscriptions SET fromYoutube = 0 WHERE channelUrl = :channelUrl")
    suspend fun clearFromYoutube(channelUrl: String)

    @Query("DELETE FROM subscriptions WHERE muted = 1")
    suspend fun deleteMuted()

    @Query("UPDATE subscriptions SET fromYoutube = 0, addedLocally = 1")
    suspend fun keepAllLocally()

    @Query("DELETE FROM subscriptions WHERE fromYoutube = 1 AND addedLocally = 0")
    suspend fun deleteYoutubeOnly()

    @Query("UPDATE subscriptions SET fromYoutube = 0")
    suspend fun clearAllFromYoutube()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restore(subscription: SubscriptionEntity)

    @Transaction
    suspend fun subscribe(subscription: SubscriptionEntity) {
        insertIgnore(subscription)
        markSubscribed(subscription.channelUrl, subscription.name, subscription.avatarUrl)
    }

    @Transaction
    suspend fun importAll(subscriptions: List<SubscriptionEntity>): Int {
        var inserted = 0
        subscriptions.forEach {
            if (insertIgnore(it) != -1L) inserted++
            markSubscribed(it.channelUrl, it.name, it.avatarUrl)
        }
        return inserted
    }

    @Transaction
    suspend fun unsubscribe(channelUrl: String): SubscriptionEntity? {
        val row = find(channelUrl) ?: return null
        if (row.fromYoutube) mute(channelUrl) else delete(channelUrl)
        return row
    }
}
