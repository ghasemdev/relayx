package com.parsomash.relayx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// Delivery Status Colors (Light)
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

// Delivery Status Colors (Dark)
val StatusDeliveredContainerDark = Color(0xFF064E3B)
val StatusDeliveredTextDark = Color(0xFFA7F3D0)

val StatusPendingContainerDark = Color(0xFF78350F)
val StatusPendingTextDark = Color(0xFFFDE68A)

val StatusFailedContainerDark = Color(0xFF7F1D1D)
val StatusFailedTextDark = Color(0xFFFECACA)

val StatusFilteredContainerDark = Color(0xFF4C1D95)
val StatusFilteredTextDark = Color(0xFFDDD6FE)

// Rule Action Colors (Light)
val ActionForwardRaw = Color(0xFF10B981)
val ActionForwardRawContainer = Color(0xFFD1FAE5)
val ActionForwardRawText = Color(0xFF065F46)

val ActionForwardTransformed = Color(0xFF0284C7)
val ActionForwardTransformedContainer = Color(0xFFE0F2FE)
val ActionForwardTransformedText = Color(0xFF0369A1)

val ActionDrop = Color(0xFFEF4444)
val ActionDropContainer = Color(0xFFFEE2E2)
val ActionDropText = Color(0xFF991B1B)

// Rule Action Colors (Dark)
val ActionForwardTransformedContainerDark = Color(0xFF0C4A6E)
val ActionForwardTransformedTextDark = Color(0xFFBAE6FD)

val isDarkThemeActive: Boolean
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.luminance() < 0.5f

fun deliveryStatusColor(status: String): Color {
    return when (status.uppercase()) {
        "DELIVERED", "FORWARDED" -> StatusDelivered
        "FAILED" -> StatusFailed
        "FILTERED" -> StatusFiltered
        else -> StatusPending
    }
}

fun deliveryStatusContainerColor(status: String, isDark: Boolean = false): Color {
    return if (isDark) {
        when (status.uppercase()) {
            "DELIVERED", "FORWARDED" -> StatusDeliveredContainerDark
            "FAILED" -> StatusFailedContainerDark
            "FILTERED" -> StatusFilteredContainerDark
            else -> StatusPendingContainerDark
        }
    } else {
        when (status.uppercase()) {
            "DELIVERED", "FORWARDED" -> StatusDeliveredContainer
            "FAILED" -> StatusFailedContainer
            "FILTERED" -> StatusFilteredContainer
            else -> StatusPendingContainer
        }
    }
}

fun deliveryStatusTextColor(status: String, isDark: Boolean = false): Color {
    return if (isDark) {
        when (status.uppercase()) {
            "DELIVERED", "FORWARDED" -> StatusDeliveredTextDark
            "FAILED" -> StatusFailedTextDark
            "FILTERED" -> StatusFilteredTextDark
            else -> StatusPendingTextDark
        }
    } else {
        when (status.uppercase()) {
            "DELIVERED", "FORWARDED" -> StatusDeliveredText
            "FAILED" -> StatusFailedText
            "FILTERED" -> StatusFilteredText
            else -> StatusPendingText
        }
    }
}

fun ruleActionContainerColor(action: String, isDark: Boolean = false): Color {
    return if (isDark) {
        when (action.uppercase()) {
            "FORWARD_RAW" -> StatusDeliveredContainerDark
            "FORWARD_TRANSFORMED" -> ActionForwardTransformedContainerDark
            "DROP" -> StatusFailedContainerDark
            else -> StatusPendingContainerDark
        }
    } else {
        when (action.uppercase()) {
            "FORWARD_RAW" -> ActionForwardRawContainer
            "FORWARD_TRANSFORMED" -> ActionForwardTransformedContainer
            "DROP" -> ActionDropContainer
            else -> StatusPendingContainer
        }
    }
}

fun ruleActionTextColor(action: String, isDark: Boolean = false): Color {
    return if (isDark) {
        when (action.uppercase()) {
            "FORWARD_RAW" -> StatusDeliveredTextDark
            "FORWARD_TRANSFORMED" -> ActionForwardTransformedTextDark
            "DROP" -> StatusFailedTextDark
            else -> StatusPendingTextDark
        }
    } else {
        when (action.uppercase()) {
            "FORWARD_RAW" -> ActionForwardRawText
            "FORWARD_TRANSFORMED" -> ActionForwardTransformedText
            "DROP" -> ActionDropText
            else -> StatusPendingText
        }
    }
}