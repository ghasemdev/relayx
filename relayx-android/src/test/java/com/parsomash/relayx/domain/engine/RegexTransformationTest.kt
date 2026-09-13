package com.parsomash.relayx.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexTransformationTest {

    @Test
    fun `extracts 6-digit verification code using capture group`() {
        val pattern = "(?i)code is (\\d{6})"
        val text = "Your verification code is 492019. Valid for 5 minutes."

        val extracted = RegexValidator.extract(pattern, text, 1)
        assertEquals("492019", extracted)
    }

    @Test
    fun `extracts full match when no capture group specified`() {
        val pattern = "\\b\\d{6}\\b"
        val text = "Your code 123456 has expired."

        val extracted = RegexValidator.extract(pattern, text, 1)
        assertEquals("123456", extracted)
    }

    @Test
    fun `returns null when pattern does not match text`() {
        val pattern = "\\b\\d{6}\\b"
        val text = "Welcome to our newsletter, Jane!"

        val extracted = RegexValidator.extract(pattern, text)
        assertNull(extracted)
    }

    @Test
    fun `handles invalid pattern gracefully without crashing`() {
        val invalidPattern = "[0-9"
        val text = "Test 1234"

        val error = RegexValidator.validatePattern(invalidPattern)
        assertTrue(error != null)

        val extracted = RegexValidator.extract(invalidPattern, text)
        assertNull(extracted)
    }

    @Test
    fun `matches checks case-insensitive patterns`() {
        val pattern = "(?i)urgent"
        assertTrue(RegexValidator.matches(pattern, "URGENT notification"))
        assertFalse(RegexValidator.matches(pattern, "Normal notification"))
    }
}
