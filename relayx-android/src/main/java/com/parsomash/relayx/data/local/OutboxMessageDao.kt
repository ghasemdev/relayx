package com.parsomash.relayx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: OutboxMessageEntity): Long

    @Update
    suspend fun update(message: OutboxMessageEntity): Int

    @Query("SELECT * FROM outbox_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): OutboxMessageEntity?

    @Query("SELECT * FROM outbox_messages WHERE status IN ('PENDING', 'FAILED') ORDER BY received_at ASC LIMIT :limit")
    suspend fun getPendingMessages(limit: Int = 50): List<OutboxMessageEntity>

    @Query("UPDATE outbox_messages SET status = :status, retry_count = :retryCount, last_attempt_at = :lastAttemptAt, error_message = :errorMessage WHERE id = :id")
    suspend fun updateDeliveryState(
        id: String,
        status: String,
        retryCount: Int,
        lastAttemptAt: Long?,
        errorMessage: String?
    ): Int

    @Query("SELECT COUNT(1) FROM outbox_messages")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(1) FROM outbox_messages WHERE status = 'DELIVERED'")
    fun observeForwardedCount(): Flow<Int>

    @Query("SELECT COUNT(1) FROM outbox_messages WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("SELECT COUNT(1) FROM outbox_messages WHERE status = 'FILTERED'")
    fun observeFilteredCount(): Flow<Int>

    @Query("SELECT MAX(received_at) FROM outbox_messages")
    fun observeLastMessageTimestamp(): Flow<Long?>

    @Query("SELECT * FROM outbox_messages ORDER BY received_at DESC")
    fun observeAllMessages(): Flow<List<OutboxMessageEntity>>

    @Query("SELECT * FROM outbox_messages ORDER BY received_at DESC LIMIT 1")
    fun observeLatestMessage(): Flow<OutboxMessageEntity?>

    @Query("UPDATE outbox_messages SET status = 'PENDING', error_message = null, last_attempt_at = :now WHERE id = :id")
    suspend fun resetForRetry(id: String, now: Long): Int
}
