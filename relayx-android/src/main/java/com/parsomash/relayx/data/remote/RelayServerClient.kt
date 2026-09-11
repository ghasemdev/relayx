package com.parsomash.relayx.data.remote

import com.parsomash.relayx.data.remote.dto.HealthResponseDto
import com.parsomash.relayx.data.remote.dto.IngestMessageRequestDto
import com.parsomash.relayx.data.remote.dto.IngestMessageResponseDto
import com.parsomash.relayx.util.RelayLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class RelayServerClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(baseUrl: String): Result<Pair<HealthResponseDto, Long>> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/health"
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        val startTime = System.currentTimeMillis()
        try {
            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - startTime
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Server returned HTTP ${response.code}: ${response.message}")
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body"))
                val health = json.decodeFromString<HealthResponseDto>(body)
                Result.success(Pair(health, latency))
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
    ): Result<IngestMessageResponseDto> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/api/v1/messages"
        val jsonPayload = json.encodeToString(IngestMessageRequestDto.serializer(), payload)
        val body = jsonPayload.toRequestBody(jsonMediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")

        if (bearerToken.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val result = if (responseBody.isNotBlank()) {
                        json.decodeFromString<IngestMessageResponseDto>(responseBody)
                    } else {
                        IngestMessageResponseDto(status = "RECEIVED")
                    }
                    RelayLogger.i("Client", "Message ${payload.messageId} ingested successfully (HTTP ${response.code})")
                    Result.success(result)
                } else if (response.code == 401) {
                    RelayLogger.e("Client", "Ingestion rejected: Unauthorized Bearer Token")
                    Result.failure(IOException("HTTP 401 Unauthorized: Invalid device token"))
                } else {
                    RelayLogger.e("Client", "Ingestion failed with HTTP ${response.code}")
                    Result.failure(IOException("HTTP ${response.code}: $responseBody"))
                }
            }
        } catch (e: Exception) {
            RelayLogger.w("Client", "Ingest message network exception for ID ${payload.messageId}", e)
            Result.failure(e)
        }
    }
}
