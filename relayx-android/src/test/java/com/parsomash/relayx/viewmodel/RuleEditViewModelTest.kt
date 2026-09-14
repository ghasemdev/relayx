package com.parsomash.relayx.viewmodel

import android.content.Context
import com.parsomash.relayx.data.local.FakeOutboxMessageDao
import com.parsomash.relayx.data.local.FakeRuleDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.data.local.RuleRepositoryImpl
import com.parsomash.relayx.domain.engine.RuleEngineImpl
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.domain.usecase.GetRuleByIdUseCase
import com.parsomash.relayx.domain.usecase.GetSmsSendersUseCase
import com.parsomash.relayx.domain.usecase.SaveRuleUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEditViewModelTest {

  private val testDispatcher = StandardTestDispatcher()
  private lateinit var ruleDao: FakeRuleDao
  private lateinit var outboxDao: FakeOutboxMessageDao
  private lateinit var repository: RuleRepositoryImpl
  private lateinit var engine: RuleEngineImpl
  private lateinit var getRuleByIdUseCase: GetRuleByIdUseCase
  private lateinit var saveRuleUseCase: SaveRuleUseCase
  private lateinit var getSmsSendersUseCase: GetSmsSendersUseCase
  private lateinit var viewModel: RuleEditViewModel

  @Before
  fun setup() {
    Dispatchers.setMain(testDispatcher)
    val dispatchers = AppDispatchers(
      main = testDispatcher,
      io = testDispatcher,
      default = testDispatcher
    )
    ruleDao = FakeRuleDao()
    outboxDao = FakeOutboxMessageDao()
    repository = RuleRepositoryImpl(ruleDao, dispatchers)
    engine = RuleEngineImpl()
    getRuleByIdUseCase = GetRuleByIdUseCase(repository)
    saveRuleUseCase = SaveRuleUseCase(repository, engine)
    getSmsSendersUseCase = GetSmsSendersUseCase(context = null, outboxDao = outboxDao, dispatchers = dispatchers)

    viewModel = RuleEditViewModel(
      getRuleByIdUseCase = getRuleByIdUseCase,
      saveRuleUseCase = saveRuleUseCase,
      getSmsSendersUseCase = getSmsSendersUseCase
    )
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun testLoadRuleForNewRule() = runTest {
    viewModel.loadRule(null)
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertFalse(state.isEditing)
    assertEquals("", state.name)
    assertEquals(SenderMatchType.EXACT, state.senderMatchType)
  }

  @Test
  fun testLoadExistingRule() = runTest {
    val existing = Rule(
      id = "rule-123",
      name = "Bank OTP",
      senderPattern = "BANK",
      senderMatchType = SenderMatchType.EXACT,
      contentPattern = "\\d{6}",
      action = RuleAction.FORWARD_RAW,
      priority = 10
    )
    repository.saveRule(existing)

    viewModel.loadRule("rule-123")
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertTrue(state.isEditing)
    assertEquals("Bank OTP", state.name)
    assertEquals("BANK", state.senderPattern)
    assertEquals(10, state.priority)
    assertTrue(state.isFormValid)
  }

  @Test
  fun testSelectSenderFromSms() = runTest {
    outboxDao.insert(
      OutboxMessageEntity(
        id = "msg-1",
        sender = "982000123",
        rawBody = "Code: 1234",
        receivedAt = System.currentTimeMillis(),
        status = "DELIVERED"
      )
    )

    viewModel.loadRule(null)
    advanceUntilIdle()

    assertEquals(listOf("982000123"), viewModel.uiState.value.availableSenders.map { it.address })
    assertEquals("Code: 1234", viewModel.uiState.value.availableSenders.first().snippet)

    viewModel.onSenderSelected("982000123")
    assertEquals("982000123", viewModel.uiState.value.senderPattern)
  }

  @Test
  fun testSenderSearchQueryFiltersList() = runTest {
    outboxDao.insert(
      OutboxMessageEntity(
        id = "msg-1",
        sender = "BANK-XYZ",
        rawBody = "Body 1",
        receivedAt = 1000L,
        status = "DELIVERED"
      )
    )
    outboxDao.insert(
      OutboxMessageEntity(
        id = "msg-2",
        sender = "GOOGLE",
        rawBody = "Body 2",
        receivedAt = 2000L,
        status = "DELIVERED"
      )
    )

    viewModel.loadSenders()
    advanceUntilIdle()

    assertEquals(2, viewModel.uiState.value.availableSenders.size)

    viewModel.onSenderSearchQueryChanged("bank")
    assertEquals(listOf("BANK-XYZ"), viewModel.uiState.value.filteredSenders.map { it.address })

    viewModel.onSenderSearchQueryChanged("goo")
    assertEquals(listOf("GOOGLE"), viewModel.uiState.value.filteredSenders.map { it.address })

    // Also verify searching by message snippet
    viewModel.onSenderSearchQueryChanged("Body 1")
    assertEquals(listOf("BANK-XYZ"), viewModel.uiState.value.filteredSenders.map { it.address })
  }

  @Test
  fun testSaveRuleSuccess() = runTest {
    viewModel.loadRule(null)
    advanceUntilIdle()

    viewModel.onNameChanged("Alert Rule")
    viewModel.onSenderMatchTypeChanged(SenderMatchType.ANY)

    var saved = false
    viewModel.saveRule { saved = true }
    advanceUntilIdle()

    assertTrue(saved)
    assertTrue(viewModel.uiState.value.isSaved)
    val savedRules = repository.getActiveRulesDirect()
    assertTrue(savedRules.any { it.name == "Alert Rule" })
  }
}
