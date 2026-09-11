package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.model.GatewayConfig
import com.parsomash.relayx.domain.usecase.ConnectionTestResult
import com.parsomash.relayx.domain.usecase.TestConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val host: String = "10.0.2.2",
    val port: String = "8080",
    val useHttps: Boolean = false,
    val deviceId: String = "",
    val bearerToken: String = "",
    val isTesting: Boolean = false,
    val testResult: ConnectionTestResult? = null,
    val saveMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val testConnectionUseCase: TestConnectionUseCase = TestConnectionUseCase()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = preferencesRepository.configFlow.first()
            _uiState.update {
                it.copy(
                    host = config.serverHost,
                    port = config.serverPort.toString(),
                    useHttps = config.useHttps,
                    deviceId = config.deviceId,
                    bearerToken = config.bearerToken
                )
            }
        }
    }

    fun onHostChanged(host: String) {
        _uiState.update { it.copy(host = host, saveMessage = null) }
    }

    fun onPortChanged(port: String) {
        _uiState.update { it.copy(port = port.filter { char -> char.isDigit() }, saveMessage = null) }
    }

    fun onUseHttpsChanged(useHttps: Boolean) {
        _uiState.update { it.copy(useHttps = useHttps, saveMessage = null) }
    }

    fun onDeviceIdChanged(deviceId: String) {
        _uiState.update { it.copy(deviceId = deviceId, saveMessage = null) }
    }

    fun onBearerTokenChanged(token: String) {
        _uiState.update { it.copy(bearerToken = token, saveMessage = null) }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value
            val portInt = state.port.toIntOrNull() ?: 8080
            val config = GatewayConfig(
                serverHost = state.host.trim(),
                serverPort = portInt,
                useHttps = state.useHttps,
                deviceId = state.deviceId.trim(),
                bearerToken = state.bearerToken.trim(),
                forwardingEnabled = preferencesRepository.getConfig().forwardingEnabled
            )
            preferencesRepository.updateConfig(config)
            _uiState.update { it.copy(saveMessage = "Settings saved successfully") }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            val portInt = state.port.toIntOrNull() ?: 8080
            _uiState.update { it.copy(isTesting = true, testResult = null, saveMessage = null) }
            val result = testConnectionUseCase(state.host.trim(), portInt, state.useHttps)
            _uiState.update { it.copy(isTesting = false, testResult = result) }
        }
    }
}
