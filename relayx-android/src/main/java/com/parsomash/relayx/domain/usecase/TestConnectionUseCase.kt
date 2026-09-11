package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.data.remote.RelayServerClient
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Factory

sealed interface ConnectionTestResult {
    data class Success(val latencyMs: Long, val version: String, val database: String) : ConnectionTestResult
    data class Failure(val errorMessage: String) : ConnectionTestResult
}

@Factory
class TestConnectionUseCase(
    private val client: RelayServerClient = RelayServerClient(),
    private val dispatchers: AppDispatchers = AppDispatchers()
) {
    suspend operator fun invoke(host: String, port: Int, useHttps: Boolean): ConnectionTestResult = withContext(dispatchers.io) {
        if (host.isBlank()) {
            return@withContext ConnectionTestResult.Failure("Server host cannot be empty")
        }
        if (port !in 1..65535) {
            return@withContext ConnectionTestResult.Failure("Port must be between 1 and 65535")
        }

        val scheme = if (useHttps) "https" else "http"
        val baseUrl = "$scheme://$host:$port"

        val result = client.checkHealth(baseUrl)
        result.fold(
            onSuccess = { (health, latency) ->
                ConnectionTestResult.Success(
                    latencyMs = latency,
                    version = health.version,
                    database = health.database
                )
            },
            onFailure = { error ->
                val message = error.localizedMessage ?: "Connection timed out or host unreachable"
                ConnectionTestResult.Failure(message)
            }
        )
    }
}
