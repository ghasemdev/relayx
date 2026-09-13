package com.parsomash.relayx.domain.usecase

import com.parsomash.relayx.domain.engine.RuleEngine
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleEvaluationResult
import com.parsomash.relayx.domain.repository.RuleRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Factory

@Factory
class GetRulesUseCase(
    private val ruleRepository: RuleRepository
) {
    operator fun invoke(): Flow<List<Rule>> = ruleRepository.getAllRules()
}

@Factory
class GetActiveRulesCountUseCase(
    private val ruleRepository: RuleRepository
) {
    operator fun invoke(): Flow<Int> = ruleRepository.getActiveRulesCount()
}

@Factory
class SaveRuleUseCase(
    private val ruleRepository: RuleRepository,
    private val ruleEngine: RuleEngine
) {
    suspend operator fun invoke(rule: Rule): Result<Unit> {
        val patternError = ruleEngine.validateRegex(rule.contentPattern)
        if (patternError != null) {
            return Result.failure(IllegalArgumentException("Invalid content pattern regex: $patternError"))
        }

        val transformError = ruleEngine.validateRegex(rule.transformPattern)
        if (transformError != null) {
            return Result.failure(IllegalArgumentException("Invalid transform pattern regex: $transformError"))
        }

        ruleRepository.saveRule(rule)
        return Result.success(Unit)
    }
}

@Factory
class ToggleRuleUseCase(
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(id: String, enabled: Boolean) {
        ruleRepository.toggleRule(id, enabled)
    }
}

@Factory
class DeleteRuleUseCase(
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(id: String) {
        ruleRepository.deleteRule(id)
    }
}

@Factory
class ReorderRulesUseCase(
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(orderedIds: List<String>) {
        ruleRepository.reorderRules(orderedIds)
    }
}

@Factory
class TestRulesUseCase(
    private val ruleEngine: RuleEngine,
    private val ruleRepository: RuleRepository
) {
    suspend operator fun invoke(sender: String, body: String): RuleEvaluationResult {
        val activeRules = ruleRepository.getActiveRulesDirect()
        return ruleEngine.evaluate(sender, body, activeRules)
    }
}
