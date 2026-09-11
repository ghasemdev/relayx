package com.parsomash.relayx.data.remote

import com.parsomash.relayx.data.remote.dto.IngestMessageRequestDto
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayServerClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: RelayServerClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        client = RelayServerClient()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun testCheckHealthSuccess() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"ok","uptime_seconds":120,"database":"connected","version":"0.1.0"}""")
        )

        val baseUrl = mockServer.url("").toString().removeSuffix("/")
        val result = client.checkHealth(baseUrl)

        assertTrue(result.isSuccess)
        val (health, latency) = result.getOrThrow()
        assertEquals("ok", health.status)
        assertEquals("connected", health.database)
        assertEquals("0.1.0", health.version)
        assertEquals(120L, health.uptimeSeconds)
        assertTrue(latency >= 0)

        val recordedRequest = mockServer.takeRequest()
        assertEquals("/api/v1/health", recordedRequest.path)
        assertEquals("GET", recordedRequest.method)
    }

    @Test
    fun testCheckHealthHttpError() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val baseUrl = mockServer.url("").toString().removeSuffix("/")
        val result = client.checkHealth(baseUrl)

        assertTrue(result.isFailure)
    }

    @Test
    fun testIngestMessageSuccessWithBearerToken() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"messageId":"msg-abc-123","status":"RECEIVED"}""")
        )

        val baseUrl = mockServer.url("").toString().removeSuffix("/")
        val payload = IngestMessageRequestDto(
            messageId = "msg-abc-123",
            deviceId = "pixel-test-01",
            sender = "+15550001",
            body = "Verification code: 123456",
            receivedAt = 1700000000000L,
            metadata = mapOf("source" to "sms")
        )

        val result = client.ingestMessage(
            baseUrl = baseUrl,
            bearerToken = "secret-token-token",
            payload = payload
        )

        assertTrue(result.isSuccess)
        val response = result.getOrThrow()
        assertEquals("msg-abc-123", response.messageId)
        assertEquals("RECEIVED", response.status)

        val recorded = mockServer.takeRequest()
        assertEquals("/api/v1/messages", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer secret-token-token", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("msg-abc-123"))
    }

    @Test
    fun testIngestMessageUnauthorized() = runBlocking {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error":"unauthorized"}""")
        )

        val baseUrl = mockServer.url("").toString().removeSuffix("/")
        val payload = IngestMessageRequestDto(
            messageId = "msg-fail",
            deviceId = "pixel-test-01",
            sender = "BANK",
            body = "Code 9999",
            receivedAt = 1700000000000L
        )

        val result = client.ingestMessage(
            baseUrl = baseUrl,
            bearerToken = "invalid-token",
            payload = payload
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("401") == true)
    }
}
