package com.parsomash.relayx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
    suspend fun insert(rule: RuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RuleEntity>)

    @Query("UPDATE filter_rules SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE filter_rules SET priority = :priority, updated_at = :updatedAt WHERE id = :id")
    suspend fun updatePriority(id: String, priority: Int, updatedAt: Long)

    @Query("DELETE FROM filter_rules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM filter_rules")
    suspend fun countAll(): Int
}
