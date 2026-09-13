package com.parsomash.relayx.domain.model

enum class RuleAction {
    FORWARD_RAW,
    FORWARD_TRANSFORMED,
    DROP
}

enum class SenderMatchType {
    EXACT,
    PREFIX,
    CONTAINS,
    REGEX,
    ANY
}

data class Rule(
    val id: String,
    val name: String,
    val senderPattern: String = "",
    val senderMatchType: SenderMatchType = SenderMatchType.EXACT,
    val contentPattern: String? = null,
    val action: RuleAction = RuleAction.FORWARD_RAW,
    val transformPattern: String? = null,
    val priority: Int = 100,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
