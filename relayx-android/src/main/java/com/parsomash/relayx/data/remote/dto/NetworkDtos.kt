package com.parsomash.relayx.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthResponseDto(
    @SerialName("status") val status: String,
    @SerialName("uptime_seconds") val uptimeSeconds: Long,
    @SerialName("database") val database: String,
    @SerialName("version") val version: String
)

@Serializable
data class IngestMessageRequestDto(
    @SerialName("messageId") val messageId: String,
    @SerialName("deviceId") val deviceId: String,
    @SerialName("sender") val sender: String,
    @SerialName("body") val body: String,
    @SerialName("receivedAt") val receivedAt: Long,
    @SerialName("metadata") val metadata: Map<String, String>? = null
)

@Serializable
data class IngestMessageResponseDto(
    @SerialName("id") val id: String? = null,
    @SerialName("messageId") val messageId: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("duplicate") val duplicate: Boolean = false,
    @SerialName("error") val error: String? = null
)
