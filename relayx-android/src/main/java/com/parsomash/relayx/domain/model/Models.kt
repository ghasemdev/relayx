package com.parsomash.relayx.domain.model

enum class DeliveryStatus {
    PENDING,
    SENDING,
    DELIVERED,
    FAILED,
    FILTERED
}

data class GatewayConfig(
    val serverHost: String = "10.0.2.2",
    val serverPort: Int = 8080,
    val useHttps: Boolean = false,
    val deviceId: String = "",
    val bearerToken: String = "",
    val forwardingEnabled: Boolean = false
) {
    val baseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            return "$scheme://$serverHost:$serverPort"
        }
}

data class QueuedMessage(
    val id: String,
    val sender: String,
    val rawBody: String,
    val transformedBody: String? = null,
    val receivedAt: Long,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val retryCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val errorMessage: String? = null
)

data class GatewayStats(
    val totalReceived: Int = 0,
    val totalForwarded: Int = 0,
    val totalFiltered: Int = 0,
    val totalFailed: Int = 0,
    val lastMessageTimestamp: Long? = null
)
