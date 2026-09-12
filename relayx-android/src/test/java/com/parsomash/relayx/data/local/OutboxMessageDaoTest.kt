package com.parsomash.relayx.data.local

import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.MessageFilter
import com.parsomash.relayx.domain.model.toDetail
import com.parsomash.relayx.domain.model.toListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OutboxMessageDaoTest {

    private lateinit var dao: FakeOutboxMessageDao

    @Before
    fun setup() {
        dao = FakeOutboxMessageDao()
    }

    @Test
    fun testObserveAllMessagesAndLatestMessageOrder() = runTest {
        val msg1 = OutboxMessageEntity(
            id = "msg-1",
            sender = "BANK",
            rawBody = "Code 1111",
            receivedAt = 1000L,
            status = "DELIVERED"
        )
        val msg2 = OutboxMessageEntity(
            id = "msg-2",
            sender = "TWILIO",
            rawBody = "Code 2222",
            receivedAt = 2000L,
            status = "FAILED",
            errorMessage = "HTTP 500"
        )

        dao.insert(msg1)
        dao.insert(msg2)

        val allMessages = dao.observeAllMessages().first()
        assertEquals(2, allMessages.size)
        assertEquals("msg-2", allMessages[0].id)
        assertEquals("msg-1", allMessages[1].id)

        val latest = dao.observeLatestMessage().first()
        assertNotNull(latest)
        assertEquals("msg-2", latest?.id)
    }

    @Test
    fun testResetForRetryUpdatesStatusAndClearsError() = runTest {
        val failedMsg = OutboxMessageEntity(
            id = "failed-1",
            sender = "GOOGLE",
            rawBody = "G-123456",
            receivedAt = 5000L,
            status = "FAILED",
            retryCount = 2,
            errorMessage = "Timeout"
        )
        dao.insert(failedMsg)

        val now = 6000L
        val affected = dao.resetForRetry("failed-1", now)
        assertEquals(1, affected)

        val updated = dao.getById("failed-1")
        assertNotNull(updated)
        assertEquals("PENDING", updated?.status)
        assertNull(updated?.errorMessage)
        assertEquals(now, updated?.lastAttemptAt)
        assertEquals(2, updated?.retryCount)
    }

    @Test
    fun testObserveFilteredCount() = runTest {
        dao.insert(
            OutboxMessageEntity(
                id = "1",
                sender = "A",
                rawBody = "1",
                receivedAt = 1,
                status = "FILTERED"
            )
        )
        dao.insert(
            OutboxMessageEntity(
                id = "2",
                sender = "B",
                rawBody = "2",
                receivedAt = 2,
                status = "DELIVERED"
            )
        )
        dao.insert(
            OutboxMessageEntity(
                id = "3",
                sender = "C",
                rawBody = "3",
                receivedAt = 3,
                status = "FILTERED"
            )
        )

        val filteredCount = dao.observeFilteredCount().first()
        assertEquals(2, filteredCount)
    }

    @Test
    fun testMessageFilterFromString() {
        assertEquals(MessageFilter.ALL, MessageFilter.fromString("ALL"))
        assertEquals(MessageFilter.PENDING, MessageFilter.fromString("pending"))
        assertEquals(MessageFilter.FORWARDED, MessageFilter.fromString("Forwarded"))
        assertEquals(MessageFilter.FAILED, MessageFilter.fromString("FAILED"))
        assertEquals(MessageFilter.FILTERED, MessageFilter.fromString("filtered"))
        assertEquals(MessageFilter.ALL, MessageFilter.fromString("unknown_value"))
    }

    @Test
    fun testToListItemAndToDetailMappers() {
        val entity = OutboxMessageEntity(
            id = "uuid-1234",
            sender = "+15551234567",
            rawBody = "Your OTP is 987654",
            transformedBody = "987654",
            receivedAt = 1700000000000L,
            status = "FAILED",
            retryCount = 3,
            errorMessage = "Gateway unreachable"
        )

        val listItem = entity.toListItem(now = 1700000060000L)
        assertEquals("uuid-1234", listItem.id)
        assertEquals("+15551234567", listItem.sender)
        assertEquals(DeliveryStatus.FAILED, listItem.status)
        assertTrue(listItem.isFailed)
        assertEquals(3, listItem.retryCount)

        val detail = entity.toDetail()
        assertEquals("uuid-1234", detail.id)
        assertEquals("+15551234567", detail.sender)
        assertEquals("Your OTP is 987654", detail.rawBody)
        assertEquals("987654", detail.transformedBody)
        assertEquals(DeliveryStatus.FAILED, detail.status)
        assertTrue(detail.canRetry)
        assertEquals("Gateway unreachable", detail.errorMessage)
    }
}
