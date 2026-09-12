package com.parsomash.relayx.viewmodel

import com.parsomash.relayx.data.local.FakeOutboxMessageDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.data.local.PreferencesRepository
import com.parsomash.relayx.domain.usecase.GetGatewayStatsUseCase
import com.parsomash.relayx.domain.usecase.ToggleForwardingUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeOutboxMessageDao
    private lateinit var prefsRepo: PreferencesRepository
    private lateinit var viewModel: DashboardViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeOutboxMessageDao()

        val testFile = tmpFolder.newFile("test_dashboard_prefs.preferences_pb")
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
            produceFile = { testFile }
        )
        prefsRepo = PreferencesRepository(dataStore)

        val statsUseCase = GetGatewayStatsUseCase(dao)
        val toggleUseCase = ToggleForwardingUseCase(prefsRepo)

        viewModel = DashboardViewModel(
            getGatewayStatsUseCase = statsUseCase,
            toggleForwardingUseCase = toggleUseCase,
            preferencesRepository = prefsRepo,
            outboxMessageDao = dao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testObservesLatestMessageAndOpensDetailSheet() = runTest {
        dao.insert(
            OutboxMessageEntity(
                id = "latest-msg-001",
                sender = "CHASE_BANK",
                rawBody = "Verification code: 123456",
                receivedAt = 50000L,
                status = "FAILED",
                errorMessage = "Network timeout"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.latestMessage)
        assertEquals("latest-msg-001", state.latestMessage?.id)
        assertEquals("CHASE_BANK", state.latestMessage?.sender)
        assertFalse(state.isShowingDetailSheet)

        viewModel.showLatestMessageDetail()
        assertTrue(viewModel.uiState.value.isShowingDetailSheet)

        var scheduled = false
        viewModel.retryMessage("latest-msg-001") {
            scheduled = true
        }
        advanceUntilIdle()

        assertTrue(scheduled)
        val resetInDb = dao.getById("latest-msg-001")
        assertEquals("PENDING", resetInDb?.status)

        viewModel.dismissDetailSheet()
        assertFalse(viewModel.uiState.value.isShowingDetailSheet)
    }
}
