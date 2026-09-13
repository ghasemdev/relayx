package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.engine.RuleEngine
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.QueuedMessage
import com.parsomash.relayx.domain.repository.RuleRepository
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
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine,
    private val dispatchers: AppDispatchers = AppDispatchers()
) {

    suspend operator fun invoke(sender: String, body: String): QueuedMessage? = withContext(dispatchers.io) {
        val config = preferencesRepository.getConfig()
        if (!config.forwardingEnabled) {
            RelayLogger.d("Ingest", "Ignored SMS from $sender: forwarding is disabled")
            return@withContext null
        }

        // Seed default rules if database is empty
        ruleRepository.seedDefaultRulesIfEmpty()

        val activeRules = ruleRepository.getActiveRulesDirect()
        val evalResult = ruleEngine.evaluate(sender, body, activeRules)

        val messageId = Uuid.random().toString()
        val receivedTime = System.currentTimeMillis()

        val finalStatus = if (evalResult.isDropped) {
            DeliveryStatus.FILTERED
        } else {
            DeliveryStatus.PENDING
        }

        val entity = OutboxMessageEntity(
            id = messageId,
            sender = sender,
            rawBody = body,
            transformedBody = evalResult.transformedBody,
            receivedAt = receivedTime,
            status = finalStatus.name,
            retryCount = 0,
            lastAttemptAt = null,
            errorMessage = if (evalResult.isDropped) evalResult.executionLog else null
        )

        outboxDao.insert(entity)
        if (evalResult.isDropped) {
            RelayLogger.i("Ingest", "SMS dropped by pre-filter: ID $messageId from $sender (${evalResult.executionLog})")
        } else {
            RelayLogger.i("Ingest", "Durable outbox entry saved: ID $messageId from $sender (Action: ${evalResult.action})")
        }

        entity.toDomain()
    }
}
