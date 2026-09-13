package com.parsomash.relayx.domain.engine

import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleEngineTest {

    private lateinit var engine: RuleEngine

    @Before
    fun setUp() {
        engine = RuleEngineImpl()
    }

    @Test
    fun `empty rules list yields default DROP`() {
        val result = engine.evaluate("CHASE", "Your OTP is 123456", emptyList())
        assertTrue(result.isDropped)
        assertEquals(RuleAction.DROP, result.action)
        assertNull(result.matchedRule)
    }

    @Test
    fun `exact sender match with FORWARD_RAW forwards message intact`() {
        val rule = Rule(
            id = "r1",
            name = "Chase Allow",
            senderPattern = "CHASE",
            senderMatchType = SenderMatchType.EXACT,
            action = RuleAction.FORWARD_RAW,
            priority = 10
        )

        val result = engine.evaluate("chase", "Hello account holder", listOf(rule))
        assertFalse(result.isDropped)
        assertEquals(RuleAction.FORWARD_RAW, result.action)
        assertNull(result.transformedBody)
        assertEquals("r1", result.matchedRule?.id)
    }

    @Test
    fun `prefix sender match with DROP drops matching message`() {
        val rule = Rule(
            id = "r1",
            name = "Drop 800 Numbers",
            senderPattern = "+1800",
            senderMatchType = SenderMatchType.PREFIX,
            action = RuleAction.DROP,
            priority = 10
        )

        val result = engine.evaluate("+18005550199", "Spam marketing message", listOf(rule))
        assertTrue(result.isDropped)
        assertEquals(RuleAction.DROP, result.action)
        assertEquals("r1", result.matchedRule?.id)
    }

    @Test
    fun `contains sender match matches substring`() {
        val rule = Rule(
            id = "r1",
            name = "Bank Sender",
            senderPattern = "BANK",
            senderMatchType = SenderMatchType.CONTAINS,
            action = RuleAction.FORWARD_RAW,
            priority = 10
        )

        val result = engine.evaluate("MY_BANK_ALERT", "Statement ready", listOf(rule))
        assertFalse(result.isDropped)
        assertEquals(RuleAction.FORWARD_RAW, result.action)
    }

    @Test
    fun `regex sender match correctly identifies pattern`() {
        val rule = Rule(
            id = "r1",
            name = "Shortcodes",
            senderPattern = "^[0-9]{5,6}$",
            senderMatchType = SenderMatchType.REGEX,
            action = RuleAction.FORWARD_RAW,
            priority = 10
        )

        val matchResult = engine.evaluate("22446", "Security Alert", listOf(rule))
        assertFalse(matchResult.isDropped)

        val noMatchResult = engine.evaluate("+1234567890", "Normal phone", listOf(rule))
        assertTrue(noMatchResult.isDropped)
    }

    @Test
    fun `forward transformed extracts regex capture group`() {
        val rule = Rule(
            id = "r1",
            name = "Extract OTP",
            senderPattern = "",
            senderMatchType = SenderMatchType.ANY,
            contentPattern = "(?i)code is (\\d{6})",
            action = RuleAction.FORWARD_TRANSFORMED,
            transformPattern = "(?i)code is (\\d{6})",
            priority = 10
        )

        val result = engine.evaluate("SERVICE", "Your verification code is 849201. Do not share.", listOf(rule))
        assertFalse(result.isDropped)
        assertEquals(RuleAction.FORWARD_TRANSFORMED, result.action)
        assertEquals("849201", result.transformedBody)
    }

    @Test
    fun `deterministic priority order - lower number wins`() {
        val lowPriorityAllow = Rule(
            id = "r2",
            name = "Allow All",
            senderPattern = "",
            senderMatchType = SenderMatchType.ANY,
            action = RuleAction.FORWARD_RAW,
            priority = 100
        )

        val highPriorityDrop = Rule(
            id = "r1",
            name = "Drop Specific Bank",
            senderPattern = "BADBANK",
            senderMatchType = SenderMatchType.EXACT,
            action = RuleAction.DROP,
            priority = 10
        )

        val result = engine.evaluate("BADBANK", "Transaction alert", listOf(lowPriorityAllow, highPriorityDrop))
        assertTrue(result.isDropped)
        assertEquals("r1", result.matchedRule?.id)
    }

    @Test
    fun `disabled rules are skipped`() {
        val disabledRule = Rule(
            id = "r1",
            name = "Disabled Allow",
            senderPattern = "TEST",
            senderMatchType = SenderMatchType.EXACT,
            action = RuleAction.FORWARD_RAW,
            priority = 10,
            enabled = false
        )

        val result = engine.evaluate("TEST", "Message body", listOf(disabledRule))
        assertTrue(result.isDropped)
        assertNull(result.matchedRule)
    }
}
