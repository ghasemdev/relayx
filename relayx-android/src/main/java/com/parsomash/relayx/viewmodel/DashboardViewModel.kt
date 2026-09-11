package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.GatewayStats
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
    val hasSmsPermission: Boolean = false
)

@KoinViewModel
class DashboardViewModel(
    private val getGatewayStatsUseCase: GetGatewayStatsUseCase,
    private val toggleForwardingUseCase: ToggleForwardingUseCase,
    private val preferencesRepository: PreferencesRepository
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
}
