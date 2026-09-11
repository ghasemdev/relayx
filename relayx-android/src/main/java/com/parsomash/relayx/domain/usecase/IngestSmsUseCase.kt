package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.QueuedMessage
import com.parsomash.relayx.util.AppDispatchers
import com.parsomash.relayx.util.RelayLogger
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.koin.core.annotation.Factory

@OptIn(ExperimentalUuidApi::class)
@Factory
class IngestSmsUseCase(
    private val outboxDao: OutboxMessageDao,
    private val preferencesRepository: PreferencesRepository,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {

    suspend operator fun invoke(sender: String, body: String): QueuedMessage? = withContext(dispatchers.io) {
        val config = preferencesRepository.getConfig()
        if (!config.forwardingEnabled) {
            RelayLogger.d("Ingest", "Ignored SMS from $sender: forwarding is disabled")
            return@withContext null
        }

        val messageId = Uuid.random().toString()
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

        entity.toDomain()
    }
}
