package com.parsomash.relayx.domain.model

data class RuleEvaluationResult(
    val matchedRule: Rule?,
    val action: RuleAction,
    val transformedBody: String? = null,
    val isDropped: Boolean,
    val executionLog: String = ""
)
