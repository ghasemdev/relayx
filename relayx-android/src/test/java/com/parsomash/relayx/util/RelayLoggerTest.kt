package com.parsomash.relayx.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RelayLoggerTest {

    private val capturedLogs = mutableListOf<CapturedLog>()

    data class CapturedLog(
        val priority: String,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    @Before
    fun setUp() {
        capturedLogs.clear()
        RelayLogger.testSink = { priority, tag, message, throwable ->
            capturedLogs.add(CapturedLog(priority, tag, message, throwable))
        }
    }

    @After
    fun tearDown() {
        RelayLogger.testSink = null
        capturedLogs.clear()
    }

    @Test
    fun testSanitizeMasksOtp() {
        val messageWithOtp4 = "Your verification code is 1234. Do not share."
        val sanitized4 = RelayLogger.sanitize(messageWithOtp4)
        assertEquals("Your verification code is [REDACTED]. Do not share.", sanitized4)
        assertFalse(sanitized4.contains("1234"))

        val messageWithOtp6 = "RelayX: 654321 is your login code"
        val sanitized6 = RelayLogger.sanitize(messageWithOtp6)
        assertEquals("RelayX: [REDACTED] is your login code", sanitized6)
        assertFalse(sanitized6.contains("654321"))

        val messageWithOtp8 = "Security code: 87654321"
        val sanitized8 = RelayLogger.sanitize(messageWithOtp8)
        assertEquals("Security code: [REDACTED]", sanitized8)
        assertFalse(sanitized8.contains("87654321"))
    }

    @Test
    fun testSanitizeMasksBearerToken() {
        val authHeader = "Authorization: Bearer my-secret-jwt-token-9988"
        val sanitized = RelayLogger.sanitize(authHeader)
        assertTrue(sanitized.contains("Bearer [REDACTED]"))
        assertFalse(sanitized.contains("my-secret-jwt-token-9988"))
    }

    @Test
    fun testSanitizePreservesSafeMetadata() {
        val safeText = "Intercepted SMS from +15551234567 (120 bytes, 1 segments)"
        val sanitized = RelayLogger.sanitize(safeText)
        assertTrue(sanitized.contains("+15551234567"))
        assertTrue(sanitized.contains("120 bytes"))
    }

    @Test
    fun testSanitizeHandlesNull() {
        assertEquals("", RelayLogger.sanitize(null))
    }

    @Test
    fun testLoggerRoutesSanitizedLogsToSink() {
        RelayLogger.d("Worker", "Processing message with code 4567")
        RelayLogger.i("Server", "Authenticated with Bearer secret-abc")
        RelayLogger.w("Dispatch", "Failed for code 987654")
        RelayLogger.e("Ingest", "Crash with code 112233", RuntimeException("Boom"))

        assertEquals(4, capturedLogs.size)

        assertEquals("DEBUG", capturedLogs[0].priority)
        assertEquals("RelayX:Worker", capturedLogs[0].tag)
        assertEquals("Processing message with code [REDACTED]", capturedLogs[0].message)

        assertEquals("INFO", capturedLogs[1].priority)
        assertEquals("RelayX:Server", capturedLogs[1].tag)
        assertEquals("Authenticated with Bearer [REDACTED]", capturedLogs[1].message)

        assertEquals("WARN", capturedLogs[2].priority)
        assertEquals("RelayX:Dispatch", capturedLogs[2].tag)
        assertEquals("Failed for code [REDACTED]", capturedLogs[2].message)

        assertEquals("ERROR", capturedLogs[3].priority)
        assertEquals("RelayX:Ingest", capturedLogs[3].tag)
        assertEquals("Crash with code [REDACTED]", capturedLogs[3].message)
        assertEquals("Boom", capturedLogs[3].throwable?.message)
    }
}
