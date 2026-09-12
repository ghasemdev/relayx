package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.GatewayStats
import com.parsomash.relayx.domain.model.MessageDetail
import com.parsomash.relayx.domain.model.toDetail
import com.parsomash.relayx.domain.usecase.GetGatewayStatsUseCase
import com.parsomash.relayx.domain.usecase.ToggleForwardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class DashboardUiState(
    val forwardingEnabled: Boolean = false,
    val stats: GatewayStats = GatewayStats(),
    val serverHost: String = "10.0.2.2",
    val serverPort: Int = 8080,
    val activeRulesCount: Int = 1, // Default passthrough rule in Phase 2
    val hasSmsPermission: Boolean = false,
    val latestMessage: MessageDetail? = null,
    val isShowingDetailSheet: Boolean = false,
    val isRetrying: Boolean = false
)

@KoinViewModel
class DashboardViewModel(
    private val toggleForwardingUseCase: ToggleForwardingUseCase,
    private val outboxMessageDao: OutboxMessageDao,
    getGatewayStatsUseCase: GetGatewayStatsUseCase,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState>
        field = MutableStateFlow(DashboardUiState())

    init {
        preferencesRepository.configFlow
            .onEach { config ->
                uiState.update {
                    it.copy(
                        forwardingEnabled = config.forwardingEnabled,
                        serverHost = config.serverHost,
                        serverPort = config.serverPort
                    )
                }
            }
            .launchIn(viewModelScope)

        getGatewayStatsUseCase()
            .onEach { stats ->
                uiState.update { it.copy(stats = stats) }
            }
            .launchIn(viewModelScope)

        outboxMessageDao.observeLatestMessage()
            .onEach { entity ->
                uiState.update { it.copy(latestMessage = entity?.toDetail()) }
            }
            .launchIn(viewModelScope)
    }

    fun toggleForwarding(enabled: Boolean, onEnabled: (() -> Unit)? = null) {
        viewModelScope.launch {
            toggleForwardingUseCase(enabled)
            if (enabled) {
                onEnabled?.invoke()
            }
        }
    }

    fun updatePermissionState(hasPermission: Boolean) {
        uiState.update { it.copy(hasSmsPermission = hasPermission) }
    }

    fun showLatestMessageDetail() {
        if (uiState.value.latestMessage != null) {
            uiState.update { it.copy(isShowingDetailSheet = true) }
        }
    }

    fun dismissDetailSheet() {
        uiState.update { it.copy(isShowingDetailSheet = false) }
    }

    fun retryMessage(messageId: String, onScheduleDispatch: (() -> Unit)? = null) {
        viewModelScope.launch {
            uiState.update { it.copy(isRetrying = true) }
            try {
                val now = System.currentTimeMillis()
                outboxMessageDao.resetForRetry(messageId, now)
                onScheduleDispatch?.invoke()
            } finally {
                uiState.update { it.copy(isRetrying = false) }
            }
        }
    }
}
