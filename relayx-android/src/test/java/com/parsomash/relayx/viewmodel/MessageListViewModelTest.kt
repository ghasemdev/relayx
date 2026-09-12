package com.parsomash.relayx.viewmodel

import com.parsomash.relayx.data.local.FakeOutboxMessageDao
import com.parsomash.relayx.data.local.OutboxMessageEntity
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.MessageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var dao: FakeOutboxMessageDao
    private lateinit var viewModel: MessageListViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        dao = FakeOutboxMessageDao()
        viewModel = MessageListViewModel(dao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialFilterSetting() = runTest {
        viewModel.setInitialFilter("FAILED")
        assertEquals(MessageFilter.FAILED, viewModel.selectedFilter.value)

        viewModel.setInitialFilter("FORWARDED")
        assertEquals(MessageFilter.FORWARDED, viewModel.selectedFilter.value)

        viewModel.setInitialFilter("INVALID_STRING")
        assertEquals(MessageFilter.ALL, viewModel.selectedFilter.value)
    }

    @Test
    fun testFilterTabSelectionFiltersMessages() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        dao.insert(OutboxMessageEntity("1", "BANK", "1", receivedAt = 1000L, status = "DELIVERED"))
        dao.insert(OutboxMessageEntity("2", "TWILIO", "2", receivedAt = 2000L, status = "FAILED"))
        dao.insert(OutboxMessageEntity("3", "GOOGLE", "3", receivedAt = 3000L, status = "FILTERED"))
        dao.insert(OutboxMessageEntity("4", "AMAZON", "4", receivedAt = 4000L, status = "PENDING"))

        advanceUntilIdle()

        // Default ALL
        var state = viewModel.uiState.value
        assertEquals(4, state.messages.size)

        // Select FAILED
        viewModel.selectFilter(MessageFilter.FAILED)
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("2", state.messages[0].id)
        assertEquals(DeliveryStatus.FAILED, state.messages[0].status)

        // Select FORWARDED (DELIVERED)
        viewModel.selectFilter(MessageFilter.FORWARDED)
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("1", state.messages[0].id)
        assertEquals(DeliveryStatus.DELIVERED, state.messages[0].status)

        // Select FILTERED
        viewModel.selectFilter(MessageFilter.FILTERED)
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("3", state.messages[0].id)
        assertEquals(DeliveryStatus.FILTERED, state.messages[0].status)
    }

    @Test
    fun testRealTimeSearchFiltering() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        dao.insert(
            OutboxMessageEntity(
                "uuid-aaa-111",
                "BANK_AUTH",
                "Code 1",
                receivedAt = 1000L,
                status = "DELIVERED"
            )
        )
        dao.insert(
            OutboxMessageEntity(
                "uuid-bbb-222",
                "TWILIO_ALERT",
                "Code 2",
                receivedAt = 2000L,
                status = "DELIVERED"
            )
        )
        dao.insert(
            OutboxMessageEntity(
                "uuid-ccc-333",
                "UBER_RIDE",
                "Code 3",
                receivedAt = 3000L,
                status = "DELIVERED"
            )
        )

        advanceUntilIdle()

        // Search by sender
        viewModel.onSearchQueryChanged("twilio")
        advanceUntilIdle()
        var state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("TWILIO_ALERT", state.messages[0].sender)

        // Search by UUID substring
        viewModel.onSearchQueryChanged("ccc-333")
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(1, state.messages.size)
        assertEquals("UBER_RIDE", state.messages[0].sender)

        // Clear search
        viewModel.clearSearch()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertEquals(3, state.messages.size)
    }

    @Test
    fun testSelectMessageAndRetry() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        dao.insert(
            OutboxMessageEntity(
                "failed-msg",
                "BANK",
                "Body",
                receivedAt = 1000L,
                status = "FAILED",
                retryCount = 1
            )
        )
        advanceUntilIdle()

        val item = viewModel.uiState.value.messages.first()
        viewModel.selectMessage(item)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertNotNull(state.selectedMessage)
        assertEquals("failed-msg", state.selectedMessage?.id)
        assertTrue(state.selectedMessage?.canRetry == true)

        var dispatchTriggered = false
        viewModel.retryMessage("failed-msg") {
            dispatchTriggered = true
        }
        advanceUntilIdle()

        assertTrue(dispatchTriggered)
        val updatedInDao = dao.getById("failed-msg")
        assertEquals("PENDING", updatedInDao?.status)

        viewModel.dismissMessageDetail()
        advanceUntilIdle()
        state = viewModel.uiState.value
        assertNull(state.selectedMessage)
    }
}
