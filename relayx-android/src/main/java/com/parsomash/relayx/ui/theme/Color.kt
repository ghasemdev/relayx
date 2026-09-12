package com.parsomash.relayx.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Delivery Status Colors
val StatusDelivered = Color(0xFF10B981)
val StatusDeliveredContainer = Color(0xFFD1FAE5)
val StatusDeliveredText = Color(0xFF065F46)

val StatusPending = Color(0xFFF59E0B)
val StatusPendingContainer = Color(0xFFFEF3C7)
val StatusPendingText = Color(0xFF92400E)

val StatusFailed = Color(0xFFEF4444)
val StatusFailedContainer = Color(0xFFFEE2E2)
val StatusFailedText = Color(0xFF991B1B)

val StatusFiltered = Color(0xFF8B5CF6)
val StatusFilteredContainer = Color(0xFFEDE9FE)
val StatusFilteredText = Color(0xFF5B21B6)

fun deliveryStatusColor(status: String): Color {
    return when (status.uppercase()) {
        "DELIVERED", "FORWARDED" -> StatusDelivered
        "FAILED" -> StatusFailed
        "FILTERED" -> StatusFiltered
        else -> StatusPending
    }
}

fun deliveryStatusContainerColor(status: String): Color {
    return when (status.uppercase()) {
        "DELIVERED", "FORWARDED" -> StatusDeliveredContainer
        "FAILED" -> StatusFailedContainer
        "FILTERED" -> StatusFilteredContainer
        else -> StatusPendingContainer
    }
}

fun deliveryStatusTextColor(status: String): Color {
    return when (status.uppercase()) {
        "DELIVERED", "FORWARDED" -> StatusDeliveredText
        "FAILED" -> StatusFailedText
        "FILTERED" -> StatusFilteredText
        else -> StatusPendingText
    }
}