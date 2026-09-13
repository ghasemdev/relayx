package com.parsomash.relayx.data.local

import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleDaoTest {

    private lateinit var dao: FakeRuleDao
    private lateinit var repository: RuleRepositoryImpl

    @Before
    fun setup() {
        dao = FakeRuleDao()
        val testDispatchers = AppDispatchers(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined
        )
        repository = RuleRepositoryImpl(dao, testDispatchers)
    }

    @Test
    fun testInsertAndOrderRulesByPriorityAscending() = runTest {
        val rule1 = Rule(
            id = "rule-3",
            name = "Low Priority Rule",
            priority = 200,
            enabled = true
        )
        val rule2 = Rule(
            id = "rule-1",
            name = "High Priority Rule",
            priority = 10,
            enabled = true
        )
        val rule3 = Rule(
            id = "rule-2",
            name = "Medium Priority Rule",
            priority = 50,
            enabled = true
        )

        repository.saveRule(rule1)
        repository.saveRule(rule2)
        repository.saveRule(rule3)

        val rules = repository.getAllRules().first()
        assertEquals(3, rules.size)
        assertEquals("rule-1", rules[0].id)
        assertEquals("rule-2", rules[1].id)
        assertEquals("rule-3", rules[2].id)
    }

    @Test
    fun testActiveRulesFilterOutDisabledRules() = runTest {
        val active = Rule(
            id = "active-1",
            name = "Active Rule",
            priority = 10,
            enabled = true
        )
        val disabled = Rule(
            id = "disabled-1",
            name = "Disabled Rule",
            priority = 5,
            enabled = false
        )

        repository.saveRule(active)
        repository.saveRule(disabled)

        val activeList = repository.getActiveRules().first()
        assertEquals(1, activeList.size)
        assertEquals("active-1", activeList[0].id)

        val activeDirect = repository.getActiveRulesDirect()
        assertEquals(1, activeDirect.size)
        assertEquals("active-1", activeDirect[0].id)

        val activeCount = repository.getActiveRulesCount().first()
        assertEquals(1, activeCount)
    }

    @Test
    fun testToggleRuleState() = runTest {
        val rule = Rule(
            id = "rule-toggle",
            name = "Toggle Me",
            priority = 10,
            enabled = true
        )
        repository.saveRule(rule)

        assertEquals(1, repository.getActiveRulesCount().first())

        repository.toggleRule("rule-toggle", false)
        val fetched = repository.getRuleById("rule-toggle")
        assertNotNull(fetched)
        assertFalse(fetched!!.enabled)
        assertEquals(0, repository.getActiveRulesCount().first())

        repository.toggleRule("rule-toggle", true)
        assertTrue(repository.getRuleById("rule-toggle")!!.enabled)
        assertEquals(1, repository.getActiveRulesCount().first())
    }

    @Test
    fun testDeleteRule() = runTest {
        val rule = Rule(
            id = "rule-del",
            name = "Delete Me",
            priority = 10,
            enabled = true
        )
        repository.saveRule(rule)
        assertNotNull(repository.getRuleById("rule-del"))

        repository.deleteRule("rule-del")
        assertNull(repository.getRuleById("rule-del"))
        assertEquals(0, repository.getAllRules().first().size)
    }

    @Test
    fun testReorderRules() = runTest {
        val rule1 = Rule(id = "r1", name = "R1", priority = 10, enabled = true)
        val rule2 = Rule(id = "r2", name = "R2", priority = 20, enabled = true)
        val rule3 = Rule(id = "r3", name = "R3", priority = 30, enabled = true)

        repository.saveRule(rule1)
        repository.saveRule(rule2)
        repository.saveRule(rule3)

        // Reverse order: r3, r2, r1
        repository.reorderRules(listOf("r3", "r2", "r1"))

        val ordered = repository.getAllRules().first()
        assertEquals("r3", ordered[0].id)
        assertEquals(10, ordered[0].priority)
        assertEquals("r2", ordered[1].id)
        assertEquals(20, ordered[1].priority)
        assertEquals("r1", ordered[2].id)
        assertEquals(30, ordered[2].priority)
    }

    @Test
    fun testSeedDefaultRulesIfEmpty() = runTest {
        assertEquals(0, dao.countAll())

        repository.seedDefaultRulesIfEmpty()
        assertEquals(1, dao.countAll())

        val rules = repository.getAllRules().first()
        assertEquals(1, rules.size)
        val starter = rules[0]
        assertEquals("Default 2FA & Verification Code", starter.name)
        assertEquals(RuleAction.FORWARD_TRANSFORMED, starter.action)
        assertEquals(SenderMatchType.ANY, starter.senderMatchType)
        assertTrue(starter.enabled)

        // Calling it again should not re-seed
        repository.seedDefaultRulesIfEmpty()
        assertEquals(1, dao.countAll())
    }
}
