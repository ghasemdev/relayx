package com.parsomash.relayx.data.remote

import com.parsomash.relayx.data.remote.dto.HealthResponseDto
import com.parsomash.relayx.data.remote.dto.IngestMessageRequestDto
import com.parsomash.relayx.data.remote.dto.IngestMessageResponseDto
import com.parsomash.relayx.util.AppDispatchers
import com.parsomash.relayx.util.RelayLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import java.io.IOException

@Single
class RelayServerClient(
    private val httpClient: HttpClient = createDefaultHttpClient(),
    private val dispatchers: AppDispatchers = AppDispatchers()
) {

    suspend fun checkHealth(baseUrl: String): Result<Pair<HealthResponseDto, Long>> = withContext(dispatchers.io) {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val url = "$cleanBaseUrl${ApiConstants.PATH_HEALTH}"
        val startTime = System.currentTimeMillis()
        try {
            val response = httpClient.get(url) {
                header(ApiConstants.HEADER_ACCEPT, ApiConstants.CONTENT_TYPE_JSON)
            }
            val latency = System.currentTimeMillis() - startTime
            if (response.status.value in 200..299) {
                val health = response.body<HealthResponseDto>()
                Result.success(Pair(health, latency))
            } else {
                Result.failure(
                    IOException("Server returned HTTP ${response.status.value}: ${response.status.description}")
                )
            }
        } catch (e: Exception) {
            RelayLogger.w("Client", "Health check failed for $url", e)
            Result.failure(e)
        }
    }

    suspend fun ingestMessage(
        baseUrl: String,
        bearerToken: String,
        payload: IngestMessageRequestDto
    ): Result<IngestMessageResponseDto> = withContext(dispatchers.io) {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        val url = "$cleanBaseUrl${ApiConstants.PATH_MESSAGES}"
        try {
            val response = httpClient.post(url) {
                header(ApiConstants.HEADER_ACCEPT, ApiConstants.CONTENT_TYPE_JSON)
                contentType(ContentType.Application.Json)
                if (bearerToken.isNotBlank()) {
                    header(
                        ApiConstants.HEADER_AUTHORIZATION,
                        "${ApiConstants.HEADER_BEARER_PREFIX}$bearerToken"
                    )
                }
                setBody(payload)
            }

            if (response.status.value in 200..299) {
                val result = response.body<IngestMessageResponseDto>()
                RelayLogger.i(
                    "Client",
                    "Message ${payload.messageId} ingested successfully (HTTP ${response.status.value})"
                )
                Result.success(result)
            } else if (response.status == HttpStatusCode.Unauthorized) {
                RelayLogger.e("Client", "Ingestion rejected: Unauthorized Bearer Token")
                Result.failure(IOException("HTTP 401 Unauthorized: Invalid device token"))
            } else {
                val responseBody = response.bodyAsText()
                RelayLogger.e("Client", "Ingestion failed with HTTP ${response.status.value}")
                Result.failure(IOException("HTTP ${response.status.value}: $responseBody"))
            }
        } catch (e: Exception) {
            RelayLogger.w("Client", "Ingest message network exception for ID ${payload.messageId}", e)
            Result.failure(e)
        }
    }

    companion object {
        fun createDefaultHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 3000
                socketTimeoutMillis = 5000
            }
        }
    }
}
