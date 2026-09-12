package com.parsomash.relayx.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeOutboxMessageDao : OutboxMessageDao {
    val messages = MutableStateFlow<Map<String, OutboxMessageEntity>>(emptyMap())

    override suspend fun insert(message: OutboxMessageEntity): Long {
        messages.value += (message.id to message)
        return 1L
    }

    override suspend fun update(message: OutboxMessageEntity): Int {
        messages.value += (message.id to message)
        return 1
    }

    override suspend fun getById(id: String): OutboxMessageEntity? {
        return messages.value[id]
    }

    override suspend fun getPendingMessages(limit: Int): List<OutboxMessageEntity> {
        return messages.value.values
            .filter { it.status in listOf("PENDING", "FAILED") }
            .sortedBy { it.receivedAt }
            .take(limit)
    }

    override suspend fun updateDeliveryState(
        id: String,
        status: String,
        retryCount: Int,
        lastAttemptAt: Long?,
        errorMessage: String?
    ): Int {
        val existing = messages.value[id] ?: return 0
        messages.value += (id to existing.copy(
            status = status,
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
            errorMessage = errorMessage
        ))
        return 1
    }

    override fun observeTotalCount(): Flow<Int> {
        return messages.map { it.size }
    }

    override fun observeForwardedCount(): Flow<Int> {
        return messages.map { map -> map.values.count { it.status == "DELIVERED" } }
    }

    override fun observeFailedCount(): Flow<Int> {
        return messages.map { map -> map.values.count { it.status == "FAILED" } }
    }

    override fun observeFilteredCount(): Flow<Int> {
        return messages.map { map -> map.values.count { it.status == "FILTERED" } }
    }

    override fun observeLastMessageTimestamp(): Flow<Long?> {
        return messages.map { map -> map.values.maxOfOrNull { it.receivedAt } }
    }

    override fun observeAllMessages(): Flow<List<OutboxMessageEntity>> {
        return messages.map { map -> map.values.sortedByDescending { it.receivedAt } }
    }

    override fun observeLatestMessage(): Flow<OutboxMessageEntity?> {
        return messages.map { map -> map.values.maxByOrNull { it.receivedAt } }
    }

    override suspend fun resetForRetry(id: String, now: Long): Int {
        val existing = messages.value[id] ?: return 0
        messages.value += (id to existing.copy(
            status = "PENDING",
            errorMessage = null,
            lastAttemptAt = now
        ))
        return 1
    }
}
