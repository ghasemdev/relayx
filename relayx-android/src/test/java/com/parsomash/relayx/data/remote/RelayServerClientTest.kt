package com.parsomash.relayx.data.remote

import com.parsomash.relayx.data.remote.dto.IngestMessageRequestDto
import com.parsomash.relayx.util.AppDispatchers
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RelayServerClientTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = AppDispatchers(
        io = testDispatcher,
        default = testDispatcher,
        main = testDispatcher
    )

    @Test
    fun testCheckHealthSuccess() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/v1/health", request.url.encodedPath)
            assertEquals("GET", request.method.value)
            respond(
                content = """{"status":"ok","uptime_seconds":120,"database":"connected","version":"0.1.0"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = RelayServerClient(
            httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            dispatchers = appDispatchers
        )

        val result = client.checkHealth("http://127.0.0.1:8080")
        assertTrue(result.isSuccess)
        val (health, latency) = result.getOrThrow()
        assertEquals("ok", health.status)
        assertEquals("connected", health.database)
        assertEquals("0.1.0", health.version)
        assertEquals(120L, health.uptimeSeconds)
        assertTrue(latency >= 0)
    }

    @Test
    fun testCheckHealthHttpError() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = RelayServerClient(
            httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            dispatchers = appDispatchers
        )

        val result = client.checkHealth("http://127.0.0.1:8080")
        assertTrue(result.isFailure)
    }

    @Test
    fun testIngestMessageSuccessWithBearerToken() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/api/v1/messages", request.url.encodedPath)
            assertEquals("POST", request.method.value)
            assertEquals("Bearer secret-token-token", request.headers[HttpHeaders.Authorization])
            respond(
                content = """{"messageId":"msg-abc-123","status":"RECEIVED"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = RelayServerClient(
            httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            dispatchers = appDispatchers
        )

        val payload = IngestMessageRequestDto(
            messageId = "msg-abc-123",
            deviceId = "pixel-test-01",
            sender = "+15550001",
            body = "Verification code: 123456",
            receivedAt = 1700000000000L,
            metadata = mapOf("source" to "sms")
        )

        val result = client.ingestMessage(
            baseUrl = "http://127.0.0.1:8080",
            bearerToken = "secret-token-token",
            payload = payload
        )

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("msg-abc-123", response.messageId)
        assertEquals("RECEIVED", response.status)
    }

    @Test
    fun testIngestMessageUnauthorized() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"error":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = RelayServerClient(
            httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            dispatchers = appDispatchers
        )

        val payload = IngestMessageRequestDto(
            messageId = "msg-fail",
            deviceId = "pixel-test-01",
            sender = "BANK",
            body = "Code 9999",
            receivedAt = 1700000000000L
        )

        val result = client.ingestMessage(
            baseUrl = "http://127.0.0.1:8080",
            bearerToken = "invalid-token",
            payload = payload
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
