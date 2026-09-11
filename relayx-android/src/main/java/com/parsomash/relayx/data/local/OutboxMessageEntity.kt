package com.parsomash.relayx.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.QueuedMessage

@Entity(
    tableName = "outbox_messages",
    indices = [
        Index(value = ["status", "received_at"], name = "idx_outbox_status_received")
    ]
)
data class OutboxMessageEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "raw_body")
    val rawBody: String,

    @ColumnInfo(name = "transformed_body")
    val transformedBody: String? = null,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    fun toDomain(): QueuedMessage {
        return QueuedMessage(
            id = id,
            sender = sender,
            rawBody = rawBody,
            transformedBody = transformedBody,
            receivedAt = receivedAt,
            status = try {
                DeliveryStatus.valueOf(status)
            } catch (_: Exception) {
                DeliveryStatus.PENDING
            },
            retryCount = retryCount,
            lastAttemptAt = lastAttemptAt,
            errorMessage = errorMessage
        )
    }

    companion object {
        fun fromDomain(domain: QueuedMessage): OutboxMessageEntity {
            return OutboxMessageEntity(
                id = domain.id,
                sender = domain.sender,
                rawBody = domain.rawBody,
                transformedBody = domain.transformedBody,
                receivedAt = domain.receivedAt,
                status = domain.status.name,
                retryCount = domain.retryCount,
                lastAttemptAt = domain.lastAttemptAt,
                errorMessage = domain.errorMessage
            )
        }
    }
}
