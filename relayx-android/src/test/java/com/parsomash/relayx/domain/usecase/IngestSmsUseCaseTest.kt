package com.parsomash.relayx.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.parsomash.relayx.data.local.FakeOutboxMessageDao
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.engine.RuleEngine
import com.parsomash.relayx.domain.engine.RuleEngineImpl
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.domain.repository.RuleRepository
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule as JunitRule

@OptIn(ExperimentalCoroutinesApi::class)
class IngestSmsUseCaseTest {

    @get:JunitRule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val outboxDao = FakeOutboxMessageDao()
    private val ruleEngine: RuleEngine = RuleEngineImpl()
    private val appDispatchers = AppDispatchers(
        main = testDispatcher,
        io = testDispatcher,
        default = testDispatcher
    )

    private lateinit var preferencesRepository: PreferencesRepository

    private val fakeRuleRepo = object : RuleRepository {
        var rules = mutableListOf<Rule>()
        override fun getAllRules(): Flow<List<Rule>> = MutableStateFlow(rules)
        override fun getActiveRules(): Flow<List<Rule>> =
            MutableStateFlow(rules.filter { it.enabled })

        override suspend fun getActiveRulesDirect(): List<Rule> = rules.filter { it.enabled }
        override fun getActiveRulesCount(): Flow<Int> = MutableStateFlow(rules.count { it.enabled })
        override suspend fun getRuleById(id: String): Rule? = rules.find { it.id == id }
        override suspend fun saveRule(rule: Rule) {
            rules.removeAll { it.id == rule.id }
            rules.add(rule)
        }

        override suspend fun toggleRule(id: String, enabled: Boolean) {
            val idx = rules.indexOfFirst { it.id == id }
            if (idx != -1) rules[idx] = rules[idx].copy(enabled = enabled)
        }

        override suspend fun deleteRule(id: String) {
            rules.removeAll { it.id == id }
        }

        override suspend fun updateRulePriority(id: String, priority: Int) {}
        override suspend fun reorderRules(orderedIds: List<String>) {}
        override suspend fun seedDefaultRulesIfEmpty() {}
    }

    private lateinit var useCase: IngestSmsUseCase

    @Before
    fun setUp() {
        val testFile = tmpFolder.newFile("test_preferences.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        preferencesRepository = PreferencesRepository(dataStore)

        useCase = IngestSmsUseCase(
            outboxDao = outboxDao,
            preferencesRepository = preferencesRepository,
            ruleRepository = fakeRuleRepo,
            ruleEngine = ruleEngine,
            dispatchers = appDispatchers
        )
    }

    @Test
    fun `when forwarding disabled returns null and does not insert`() = runTest(testDispatcher) {
        preferencesRepository.setForwardingEnabled(false)

        val result = useCase("SENDER", "Body")

        assertNull(result)
        assertEquals(0, outboxDao.messages.value.size)
    }

    @Test
    fun `when rule matches FORWARD_RAW message is queued as PENDING`() = runTest(testDispatcher) {
        preferencesRepository.setForwardingEnabled(true)
        val allowRule = Rule(
            id = "r1",
            name = "Allow Bank",
            senderPattern = "MYBANK",
            senderMatchType = SenderMatchType.EXACT,
            action = RuleAction.FORWARD_RAW,
            priority = 10
        )
        fakeRuleRepo.rules = mutableListOf(allowRule)

        val result = useCase("MYBANK", "Account alert")

        assertNotNull(result)
        assertEquals(DeliveryStatus.PENDING, result?.status)
        assertEquals(1, outboxDao.messages.value.size)
        val saved = outboxDao.messages.value.values.first()
        assertEquals("MYBANK", saved.sender)
        assertEquals("PENDING", saved.status)
        assertNull(saved.transformedBody)
    }

    @Test
    fun `when no rule matches message is saved as FILTERED`() = runTest(testDispatcher) {
        preferencesRepository.setForwardingEnabled(true)
        fakeRuleRepo.rules = mutableListOf()

        val result = useCase("STRANGER", "Personal chat")

        assertNotNull(result)
        assertEquals(DeliveryStatus.FILTERED, result?.status)
        assertEquals(1, outboxDao.messages.value.size)
        val saved = outboxDao.messages.value.values.first()
        assertEquals("FILTERED", saved.status)
    }

    @Test
    fun `when rule matches FORWARD_TRANSFORMED sets transformedBody and PENDING status`() =
        runTest(testDispatcher) {
            preferencesRepository.setForwardingEnabled(true)
            val otpRule = Rule(
                id = "r1",
                name = "Extract OTP",
                senderPattern = "",
                senderMatchType = SenderMatchType.ANY,
                contentPattern = "(?i)code: (\\d{6})",
                action = RuleAction.FORWARD_TRANSFORMED,
                transformPattern = "(?i)code: (\\d{6})",
                priority = 10
            )
            fakeRuleRepo.rules = mutableListOf(otpRule)

            val result = useCase("AUTH_SERVICE", "Your login code: 958210")

            assertNotNull(result)
            assertEquals(DeliveryStatus.PENDING, result?.status)
            assertEquals(1, outboxDao.messages.value.size)
            val saved = outboxDao.messages.value.values.first()
            assertEquals("958210", saved.transformedBody)
            assertEquals("PENDING", saved.status)
        }
}
