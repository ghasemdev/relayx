package com.parsomash.relayx.domain.engine

import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.RuleEvaluationResult
import com.parsomash.relayx.domain.model.SenderMatchType
import org.koin.core.annotation.Single

interface RuleEngine {
    fun evaluate(sender: String, body: String, rules: List<Rule>): RuleEvaluationResult
    fun validateRegex(pattern: String?): String?
}

@Single
class RuleEngineImpl : RuleEngine {

    override fun validateRegex(pattern: String?): String? {
        return RegexValidator.validatePattern(pattern)
    }

    override fun evaluate(sender: String, body: String, rules: List<Rule>): RuleEvaluationResult {
        // Filter only active rules and sort deterministically by priority ASC, id ASC
        val activeRules = rules
            .filter { it.enabled }
            .sortedWith(compareBy({ it.priority }, { it.id }))

        for (rule in activeRules) {
            if (matchesSender(rule, sender) && matchesContent(rule, body)) {
                when (rule.action) {
                    RuleAction.FORWARD_RAW -> {
                        return RuleEvaluationResult(
                            matchedRule = rule,
                            action = RuleAction.FORWARD_RAW,
                            transformedBody = null,
                            isDropped = false,
                            executionLog = "Matched rule '${rule.name}' with FORWARD_RAW"
                        )
                    }

                    RuleAction.FORWARD_TRANSFORMED -> {
                        val pattern = rule.transformPattern?.takeIf { it.isNotBlank() }
                            ?: rule.contentPattern?.takeIf { it.isNotBlank() }

                        val extracted = if (pattern != null) {
                            RegexValidator.extract(pattern, body)
                        } else {
                            null // Disallow raw body leakage if transform pattern is missing
                        }

                        if (!extracted.isNullOrBlank()) {
                            return RuleEvaluationResult(
                                matchedRule = rule,
                                action = RuleAction.FORWARD_TRANSFORMED,
                                transformedBody = extracted,
                                isDropped = false,
                                executionLog = "Matched rule '${rule.name}' with FORWARD_TRANSFORMED"
                            )
                        }
                        // If transformation failed to extract anything, fall through to next rule
                    }

                    RuleAction.DROP -> {
                        return RuleEvaluationResult(
                            matchedRule = rule,
                            action = RuleAction.DROP,
                            transformedBody = null,
                            isDropped = true,
                            executionLog = "Matched rule '${rule.name}' with DROP"
                        )
                    }
                }
            }
        }

        // Constitution Principle IV: Default filter policy is DROP
        return RuleEvaluationResult(
            matchedRule = null,
            action = RuleAction.DROP,
            transformedBody = null,
            isDropped = true,
            executionLog = "Default DROP policy applied (no matching active rule)"
        )
    }

    private fun matchesSender(rule: Rule, sender: String): Boolean {
        val pattern = rule.senderPattern.trim()
        val target = sender.trim()

        return when (rule.senderMatchType) {
            SenderMatchType.ANY -> true
            SenderMatchType.EXACT -> target.equals(pattern, ignoreCase = true)
            SenderMatchType.PREFIX -> target.startsWith(pattern, ignoreCase = true)
            SenderMatchType.CONTAINS -> target.contains(pattern, ignoreCase = true)
            SenderMatchType.REGEX -> {
                try {
                    Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(target)
                } catch (_: Exception) {
                    false
                }
            }
        }
    }

    private fun matchesContent(rule: Rule, body: String): Boolean {
        val pattern = rule.contentPattern?.trim()
        if (pattern.isNullOrBlank()) return true // No content restriction

        return try {
            Regex(pattern).containsMatchIn(body)
        } catch (_: Exception) {
            // Fallback to substring matching if regex syntax is invalid
            body.contains(pattern, ignoreCase = true)
        }
    }
}
