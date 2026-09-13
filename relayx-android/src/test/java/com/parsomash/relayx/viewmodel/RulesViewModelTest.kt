package com.parsomash.relayx.viewmodel

import com.parsomash.relayx.data.local.FakeRuleDao
import com.parsomash.relayx.data.local.RuleRepositoryImpl
import com.parsomash.relayx.domain.engine.RuleEngineImpl
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.domain.usecase.DeleteRuleUseCase
import com.parsomash.relayx.domain.usecase.GetActiveRulesCountUseCase
import com.parsomash.relayx.domain.usecase.GetRulesUseCase
import com.parsomash.relayx.domain.usecase.ReorderRulesUseCase
import com.parsomash.relayx.domain.usecase.SaveRuleUseCase
import com.parsomash.relayx.domain.usecase.TestRulesUseCase
import com.parsomash.relayx.domain.usecase.ToggleRuleUseCase
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RulesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeRuleDao
    private lateinit var repository: RuleRepositoryImpl
    private lateinit var engine: RuleEngineImpl
    private lateinit var viewModel: RulesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeRuleDao()
        val dispatchers = AppDispatchers(
            main = testDispatcher,
            io = testDispatcher,
            default = testDispatcher
        )
        repository = RuleRepositoryImpl(dao, dispatchers)
        engine = RuleEngineImpl()

        viewModel = RulesViewModel(
            getRulesUseCase = GetRulesUseCase(repository),
            getActiveRulesCountUseCase = GetActiveRulesCountUseCase(repository),
            saveRuleUseCase = SaveRuleUseCase(repository, engine),
            toggleRuleUseCase = ToggleRuleUseCase(repository),
            deleteRuleUseCase = DeleteRuleUseCase(repository),
            reorderRulesUseCase = ReorderRulesUseCase(repository),
            testRulesUseCase = TestRulesUseCase(engine, repository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndRuleAddition() = runTest {
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.rules.size)
        assertEquals(0, viewModel.uiState.value.activeCount)

        val rule = Rule(
            id = "rule-1",
            name = "Bank Rule",
            senderPattern = "BANK",
            senderMatchType = SenderMatchType.EXACT,
            action = RuleAction.FORWARD_RAW,
            priority = 10,
            enabled = true
        )

        viewModel.onEvent(RulesUiEvent.SaveRule(rule))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rules.size)
        assertEquals(1, state.activeCount)
        assertEquals("rule-1", state.rules[0].id)
    }

    @Test
    fun testToggleRuleAndActiveCount() = runTest {
        val rule = Rule(
            id = "rule-toggle",
            name = "Toggle Test",
            priority = 10,
            enabled = true
        )
        viewModel.onEvent(RulesUiEvent.SaveRule(rule))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.activeCount)

        viewModel.onEvent(RulesUiEvent.ToggleRule("rule-toggle", false))
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.activeCount)
        assertFalse(viewModel.uiState.value.rules[0].enabled)
    }

    @Test
    fun testDeleteRule() = runTest {
        val rule = Rule(id = "r-del", name = "Delete Me", priority = 1, enabled = true)
        viewModel.onEvent(RulesUiEvent.SaveRule(rule))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.rules.size)

        viewModel.onEvent(RulesUiEvent.DeleteRule("r-del"))
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.rules.size)
    }

    @Test
    fun testSaveRuleWithInvalidRegexSetsErrorMessage() = runTest {
        val invalidRule = Rule(
            id = "r-invalid",
            name = "Broken Regex",
            contentPattern = "[a-z", // Unclosed bracket
            action = RuleAction.FORWARD_RAW,
            priority = 10,
            enabled = true
        )

        viewModel.onEvent(RulesUiEvent.SaveRule(invalidRule))
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Invalid content pattern regex"))

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun testInteractiveSandboxEvaluation() = runTest {
        val rule = Rule(
            id = "r-otp",
            name = "OTP Rule",
            senderPattern = "AUTH",
            senderMatchType = SenderMatchType.EXACT,
            contentPattern = "code:\\s*(\\d+)",
            action = RuleAction.FORWARD_TRANSFORMED,
            transformPattern = "code:\\s*(\\d+)",
            priority = 1,
            enabled = true
        )
        viewModel.onEvent(RulesUiEvent.SaveRule(rule))
        advanceUntilIdle()

        viewModel.onEvent(
            RulesUiEvent.TestInputChanged(
                sender = "AUTH",
                body = "Your code: 998877"
            )
        )
        viewModel.onEvent(RulesUiEvent.RunTest)
        advanceUntilIdle()

        val result = viewModel.uiState.value.testResult
        assertNotNull(result)
        assertFalse(result!!.isDropped)
        assertEquals(RuleAction.FORWARD_TRANSFORMED, result.action)
        assertEquals("998877", result.transformedBody)
        assertEquals("r-otp", result.matchedRule?.id)

        // Clear test
        viewModel.onEvent(RulesUiEvent.ClearTest)
        assertEquals("", viewModel.uiState.value.testSender)
        assertEquals("", viewModel.uiState.value.testBody)
        assertNull(viewModel.uiState.value.testResult)
    }
}
