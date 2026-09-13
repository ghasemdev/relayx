package com.parsomash.relayx.domain.repository

import com.parsomash.relayx.domain.model.Rule
import kotlinx.coroutines.flow.Flow

interface RuleRepository {
    fun getAllRules(): Flow<List<Rule>>
    fun getActiveRules(): Flow<List<Rule>>
    suspend fun getActiveRulesDirect(): List<Rule>
    fun getActiveRulesCount(): Flow<Int>
    suspend fun getRuleById(id: String): Rule?
    suspend fun saveRule(rule: Rule)
    suspend fun toggleRule(id: String, enabled: Boolean)
    suspend fun deleteRule(id: String)
    suspend fun updateRulePriority(id: String, priority: Int)
    suspend fun reorderRules(orderedIds: List<String>)
    suspend fun seedDefaultRulesIfEmpty()
}
