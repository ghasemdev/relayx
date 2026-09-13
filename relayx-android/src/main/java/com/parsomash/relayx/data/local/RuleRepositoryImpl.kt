package com.parsomash.relayx.data.local

import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.domain.repository.RuleRepository
import com.parsomash.relayx.util.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
class RuleRepositoryImpl(
    private val ruleDao: RuleDao,
    private val dispatchers: AppDispatchers = AppDispatchers()
) : RuleRepository {

    override fun getAllRules(): Flow<List<Rule>> {
        return ruleDao.getAllRules().map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveRules(): Flow<List<Rule>> {
        return ruleDao.getActiveRules().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getActiveRulesDirect(): List<Rule> = withContext(dispatchers.io) {
        ruleDao.getActiveRulesDirect().map { it.toDomain() }
    }

    override fun getActiveRulesCount(): Flow<Int> {
        return ruleDao.getActiveRulesCount()
    }

    override suspend fun getRuleById(id: String): Rule? = withContext(dispatchers.io) {
        ruleDao.getRuleById(id)?.toDomain()
    }

    override suspend fun saveRule(rule: Rule) = withContext(dispatchers.io) {
        val entity = RuleEntity.fromDomain(rule.copy(updatedAt = System.currentTimeMillis()))
        ruleDao.insert(entity)
    }

    override suspend fun toggleRule(id: String, enabled: Boolean) = withContext(dispatchers.io) {
        ruleDao.setEnabled(id, enabled, System.currentTimeMillis())
    }

    override suspend fun deleteRule(id: String) = withContext(dispatchers.io) {
        ruleDao.deleteById(id)
    }

    override suspend fun updateRulePriority(id: String, priority: Int) =
        withContext(dispatchers.io) {
            ruleDao.updatePriority(id, priority, System.currentTimeMillis())
        }

    override suspend fun reorderRules(orderedIds: List<String>) = withContext(dispatchers.io) {
        orderedIds.forEachIndexed { index, id ->
            ruleDao.updatePriority(id, (index + 1) * 10, System.currentTimeMillis())
        }
    }

    override suspend fun seedDefaultRulesIfEmpty() = withContext(dispatchers.io) {
        if (ruleDao.countAll() == 0) {
            val defaultOtpRule = Rule(
                id = Uuid.random().toString(),
                name = "Default 2FA & Verification Code",
                senderPattern = "",
                senderMatchType = SenderMatchType.ANY,
                contentPattern = "(?i)(?:code|otp|passcode|verification)[:\\s]+([0-9]{4,8})",
                action = RuleAction.FORWARD_TRANSFORMED,
                transformPattern = "(?i)(?:code|otp|passcode|verification)[:\\s]+([0-9]{4,8})",
                priority = 100,
                enabled = true
            )
            ruleDao.insert(RuleEntity.fromDomain(defaultOtpRule))
        }
    }
}
