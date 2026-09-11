package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.QueuedMessage
import com.parsomash.relayx.util.RelayLogger
import java.util.UUID

class IngestSmsUseCase(
    private val outboxDao: OutboxMessageDao,
    private val preferencesRepository: PreferencesRepository
) {

    suspend operator fun invoke(sender: String, body: String): QueuedMessage? {
        val config = preferencesRepository.getConfig()
        if (!config.forwardingEnabled) {
            RelayLogger.d("Ingest", "Ignored SMS from $sender: forwarding is disabled")
            return null
        }

        val messageId = UUID.randomUUID().toString()
        val receivedTime = System.currentTimeMillis()

        val entity = OutboxMessageEntity(
            id = messageId,
            sender = sender,
            rawBody = body,
            transformedBody = null,
            receivedAt = receivedTime,
            status = DeliveryStatus.PENDING.name,
            retryCount = 0,
            lastAttemptAt = null,
            errorMessage = null
        )

        outboxDao.insert(entity)
        RelayLogger.i("Ingest", "Durable outbox entry saved: ID $messageId from $sender (${body.length} chars)")

        return entity.toDomain()
    }
}
