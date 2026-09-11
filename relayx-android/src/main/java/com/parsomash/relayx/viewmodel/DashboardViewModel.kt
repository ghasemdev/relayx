package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.GatewayStats
import com.parsomash.relayx.domain.usecase.GetGatewayStatsUseCase
import com.parsomash.relayx.domain.usecase.ToggleForwardingUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardUiState(
    val forwardingEnabled: Boolean = false,
    val stats: GatewayStats = GatewayStats(),
    val serverHost: String = "10.0.2.2",
    val serverPort: Int = 8080,
    val activeRulesCount: Int = 1, // Default passthrough rule in Phase 2
    val hasSmsPermission: Boolean = false
)

class DashboardViewModel(
    private val getGatewayStatsUseCase: GetGatewayStatsUseCase,
    private val toggleForwardingUseCase: ToggleForwardingUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        preferencesRepository.configFlow
            .onEach { config ->
                _uiState.update {
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
                _uiState.update { it.copy(stats = stats) }
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
        _uiState.update { it.copy(hasSmsPermission = hasPermission) }
    }
}
