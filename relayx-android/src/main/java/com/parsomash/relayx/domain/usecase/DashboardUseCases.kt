package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.GatewayStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Factory

@Factory
class GetGatewayStatsUseCase(
    private val dao: OutboxMessageDao
) {
    operator fun invoke(): Flow<GatewayStats> {
        return combine(
            dao.observeTotalCount(),
            dao.observeForwardedCount(),
            dao.observeFailedCount(),
            dao.observeLastMessageTimestamp()
        ) { total, forwarded, failed, lastTime ->
            GatewayStats(
                totalReceived = total,
                totalForwarded = forwarded,
                totalFiltered = 0,
                totalFailed = failed,
                lastMessageTimestamp = lastTime
            )
        }
    }
}

@Factory
class ToggleForwardingUseCase(
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferencesRepository.setForwardingEnabled(enabled)
    }
}
