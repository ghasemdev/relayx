package com.parsomash.relayx.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRuleDao : RuleDao {
    val rules = MutableStateFlow<Map<String, RuleEntity>>(emptyMap())

    override fun getAllRules(): Flow<List<RuleEntity>> {
        return rules.map { map ->
            map.values.sortedWith(compareBy({ it.priority }, { it.id }))
        }
    }

    override fun getActiveRules(): Flow<List<RuleEntity>> {
        return rules.map { map ->
            map.values.filter { it.enabled }
                .sortedWith(compareBy({ it.priority }, { it.id }))
        }
    }

    override suspend fun getActiveRulesDirect(): List<RuleEntity> {
        return rules.value.values.filter { it.enabled }
            .sortedWith(compareBy({ it.priority }, { it.id }))
    }

    override fun getActiveRulesCount(): Flow<Int> {
        return rules.map { map -> map.values.count { it.enabled } }
    }

    override suspend fun getRuleById(id: String): RuleEntity? {
        return rules.value[id]
    }

    override suspend fun insert(rule: RuleEntity) {
        rules.value += (rule.id to rule)
    }

    override suspend fun insertAll(rules: List<RuleEntity>) {
        this.rules.value += rules.associateBy { it.id }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long) {
        val existing = rules.value[id] ?: return
        rules.value += (id to existing.copy(enabled = enabled, updatedAt = updatedAt))
    }

    override suspend fun updatePriority(id: String, priority: Int, updatedAt: Long) {
        val existing = rules.value[id] ?: return
        rules.value += (id to existing.copy(priority = priority, updatedAt = updatedAt))
    }

    override suspend fun deleteById(id: String) {
        rules.value -= id
    }

    override suspend fun countAll(): Int {
        return rules.value.size
    }
}
