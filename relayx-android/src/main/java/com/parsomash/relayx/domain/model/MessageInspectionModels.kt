package com.parsomash.relayx.domain.model

import com.parsomash.relayx.data.local.OutboxMessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MessageListItem(
    val id: String,
    val sender: String,
    val formattedTime: String,
    val relativeTime: String,
    val status: DeliveryStatus,
    val retryCount: Int,
    val isFailed: Boolean = status == DeliveryStatus.FAILED
)

data class MessageDetail(
    val id: String,
    val sender: String,
    val rawBody: String,
    val transformedBody: String? = null,
    val receivedAt: Long,
    val formattedReceivedAt: String,
    val status: DeliveryStatus,
    val retryCount: Int,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = status == DeliveryStatus.FAILED || status == DeliveryStatus.PENDING
)

fun OutboxMessageEntity.toListItem(now: Long = System.currentTimeMillis()): MessageListItem {
    val deliveryStatus = parseDeliveryStatus(status)
    return MessageListItem(
        id = id,
        sender = sender,
        formattedTime = formatTime(receivedAt),
        relativeTime = formatRelativeTime(receivedAt, now),
        status = deliveryStatus,
        retryCount = retryCount,
        isFailed = deliveryStatus == DeliveryStatus.FAILED
    )
}

fun OutboxMessageEntity.toDetail(): MessageDetail {
    val deliveryStatus = parseDeliveryStatus(status)
    return MessageDetail(
        id = id,
        sender = sender,
        rawBody = rawBody,
        transformedBody = transformedBody,
        receivedAt = receivedAt,
        formattedReceivedAt = formatDateTime(receivedAt),
        status = deliveryStatus,
        retryCount = retryCount,
        lastAttemptAt = lastAttemptAt,
        errorMessage = errorMessage,
        canRetry = deliveryStatus == DeliveryStatus.FAILED || deliveryStatus == DeliveryStatus.PENDING
    )
}

private fun parseDeliveryStatus(status: String): DeliveryStatus {
    return try {
        DeliveryStatus.valueOf(status.uppercase())
    } catch (_: Exception) {
        DeliveryStatus.PENDING
    }
}

fun formatTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

fun formatDateTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

fun formatRelativeTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    if (epochMillis <= 0L) return ""
    val diff = (now - epochMillis).coerceAtLeast(0L)
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 30 -> "Just now"
        seconds < 60 -> "${seconds}s ago"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
