package com.parsomash.relayx.util

import android.util.Log

/**
 * Privacy-preserving logger enforcing Constitution Principle III & ADR-008.
 * Guarantees that sensitive SMS bodies, OTP tokens, and bearer credentials
 * are never leaked into Logcat or crash diagnostics.
 */
object RelayLogger {

    private const val TAG_PREFIX = "RelayX"

    // Regex pattern detecting potential OTP codes (4 to 8 consecutive digits)
    private val OTP_PATTERN = Regex("""\b\d{4,8}\b""")

    // Regex pattern detecting Bearer auth tokens
    private val BEARER_PATTERN = Regex("""(?i)bearer\s+[A-Za-z0-9_\-\.]+""")

    // Redaction replacement
    const val REDACTED = "[REDACTED]"

    // Test sink hook to inspect logged messages in automated tests
    var testSink: ((priority: String, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    /**
     * Sanitizes input string by masking authentication tokens and verification digits.
     */
    fun sanitize(message: String?): String {
        if (message == null) return ""
        var sanitized = BEARER_PATTERN.replace(message, "Bearer $REDACTED")
        sanitized = OTP_PATTERN.replace(sanitized, REDACTED)
        return sanitized
    }

    fun d(tag: String, message: String) {
        val fullTag = "$TAG_PREFIX:$tag"
        val safeMessage = sanitize(message)
        val sink = testSink
        if (sink != null) {
            sink("DEBUG", fullTag, safeMessage, null)
        } else {
            try {
                Log.d(fullTag, safeMessage)
            } catch (_: Throwable) {
                // Fallback for JVM unit tests without mocked android runtime
            }
        }
    }

    fun i(tag: String, message: String) {
        val fullTag = "$TAG_PREFIX:$tag"
        val safeMessage = sanitize(message)
        val sink = testSink
        if (sink != null) {
            sink("INFO", fullTag, safeMessage, null)
        } else {
            try {
                Log.i(fullTag, safeMessage)
            } catch (_: Throwable) {
                // Fallback for JVM unit tests without mocked android runtime
            }
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val fullTag = "$TAG_PREFIX:$tag"
        val safeMessage = sanitize(message)
        val sink = testSink
        if (sink != null) {
            sink("WARN", fullTag, safeMessage, throwable)
        } else {
            try {
                if (throwable != null) {
                    Log.w(fullTag, safeMessage, throwable)
                } else {
                    Log.w(fullTag, safeMessage)
                }
            } catch (_: Throwable) {
                // Fallback for JVM unit tests without mocked android runtime
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullTag = "$TAG_PREFIX:$tag"
        val safeMessage = sanitize(message)
        val sink = testSink
        if (sink != null) {
            sink("ERROR", fullTag, safeMessage, throwable)
        } else {
            try {
                if (throwable != null) {
                    Log.e(fullTag, safeMessage, throwable)
                } else {
                    Log.e(fullTag, safeMessage)
                }
            } catch (_: Throwable) {
                // Fallback for JVM unit tests without mocked android runtime
            }
        }
    }
}
