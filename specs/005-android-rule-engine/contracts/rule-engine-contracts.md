# Contracts: Android Rule & Filtering Engine

**Feature**: `specs/005-android-rule-engine`  
**Date**: 2026-09-14  

---

## 1. Domain Interfaces

### `RuleEngine`
Evaluates an SMS against ordered active rules.

```kotlin
interface RuleEngine {
    /**
     * Evaluates incoming SMS in deterministic priority order.
     * If no rule matches, returns default DROP policy.
     */
    fun evaluate(sender: String, body: String, rules: List<Rule>): RuleEvaluationResult

    /**
     * Validates if a regex pattern is syntactically valid and safe.
     * Returns null if valid, or human-readable error message if invalid.
     */
    fun validateRegex(pattern: String?): String?
}
```

### `RuleRepository`
Persistence and reactive data streams for filter rules.

```kotlin
interface RuleRepository {
    fun getAllRules(): Flow<List<Rule>>
    fun getActiveRules(): Flow<List<Rule>>
    suspend fun getActiveRulesDirect(): List<Rule>
    fun getActiveRulesCount(): Flow<Int>
    suspend fun getRuleById(id: String): Rule?
    suspend fun saveRule(rule: Rule)
    suspend fun toggleRule(id: String, enabled: Boolean)
    suspend fun deleteRule(id: String)
    suspend fun reorderRules(orderedIds: List<String>)
    suspend fun seedDefaultRulesIfEmpty()
}
```

---

## 2. Room DAO Contract (`RuleDao`)

```kotlin
@Dao
interface RuleDao {
    @Query("SELECT * FROM filter_rules ORDER BY priority ASC, id ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    fun getActiveRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM filter_rules WHERE enabled = 1 ORDER BY priority ASC, id ASC")
    suspend fun getActiveRulesDirect(): List<RuleEntity>

    @Query("SELECT COUNT(*) FROM filter_rules WHERE enabled = 1")
    fun getActiveRulesCount(): Flow<Int>

    @Query("SELECT * FROM filter_rules WHERE id = :id")
    suspend fun getRuleById(id: String): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(rule: RuleEntity)

    @Query("UPDATE filter_rules SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE filter_rules SET priority = :priority, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int, updatedAt: Long)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM filter_rules")
    suspend fun countAll(): Int
}
```

---

## 3. UI State & ViewModel Contract

```kotlin
data class RulesUiState(
    val rules: List<Rule> = emptyList(),
    val activeCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    
    // Testing Sandbox State
    val testSender: String = "",
    val testBody: String = "",
    val testResult: RuleEvaluationResult? = null
)

sealed interface RulesUiEvent {
    data class ToggleRule(val id: String, val enabled: Boolean) : RulesUiEvent
    data class DeleteRule(val id: String) : RulesUiEvent
    data class SaveRule(val rule: Rule) : RulesUiEvent
    data class ReorderRules(val orderedIds: List<String>) : RulesUiEvent
    data class TestInputChanged(val sender: String, val body: String) : RulesUiEvent
    data object RunTest : RulesUiEvent
    data object ClearTest : RulesUiEvent
}
```
