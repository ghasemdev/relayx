package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.data.remote.RelayServerClient
import com.parsomash.relayx.data.remote.dto.IngestMessageRequestDto
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.util.AppDispatchers
import com.parsomash.relayx.util.RelayLogger
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

@Factory
class DispatchOutboxUseCase(
    private val outboxDao: OutboxMessageDao,
    private val preferencesRepository: PreferencesRepository,
    private val client: RelayServerClient = RelayServerClient(),
    private val dispatchers: AppDispatchers = AppDispatchers()
) {

    suspend operator fun invoke(): Boolean = withContext(dispatchers.io) {
        val config = preferencesRepository.getConfig()
        if (!config.forwardingEnabled) {
            RelayLogger.d("Dispatch", "Forwarding disabled; skipping queue dispatch")
            return@withContext true
        }

        val pendingEntities = outboxDao.getPendingMessages(limit = 25)
        if (pendingEntities.isEmpty()) {
            RelayLogger.d("Dispatch", "Outbox queue is empty")
            return@withContext true
        }

        RelayLogger.i("Dispatch", "Processing ${pendingEntities.size} pending outbox messages")
        var allSuccess = true

        for (entity in pendingEntities) {
            val now = System.currentTimeMillis()
            outboxDao.updateDeliveryState(
                id = entity.id,
                status = DeliveryStatus.SENDING.name,
                retryCount = entity.retryCount,
                lastAttemptAt = now,
                errorMessage = null
            )

            val payload = IngestMessageRequestDto(
                messageId = entity.id,
                deviceId = config.deviceId,
                sender = entity.sender,
                body = entity.transformedBody ?: entity.rawBody,
                receivedAt = entity.receivedAt,
                metadata = mapOf(
                    "retry_count" to entity.retryCount.toString(),
                    "device_id" to config.deviceId
                )
            )

            val result = client.ingestMessage(
                baseUrl = config.baseUrl,
                bearerToken = config.bearerToken,
                payload = payload
            )

            result.fold(
                onSuccess = {
                    outboxDao.updateDeliveryState(
                        id = entity.id,
                        status = DeliveryStatus.DELIVERED.name,
                        retryCount = entity.retryCount,
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = null
                    )
                    RelayLogger.i("Dispatch", "Message ${entity.id} marked DELIVERED")
                },
                onFailure = { error ->
                    allSuccess = false
                    val newRetryCount = entity.retryCount + 1
                    val errorMsg = error.localizedMessage ?: "Unknown transmission error"
                    outboxDao.updateDeliveryState(
                        id = entity.id,
                        status = DeliveryStatus.FAILED.name,
                        retryCount = newRetryCount,
                        lastAttemptAt = System.currentTimeMillis(),
                        errorMessage = errorMsg
                    )
                    RelayLogger.w("Dispatch", "Message ${entity.id} failed delivery (attempt $newRetryCount): $errorMsg")
                }
            )
        }

        allSuccess
    }
}
