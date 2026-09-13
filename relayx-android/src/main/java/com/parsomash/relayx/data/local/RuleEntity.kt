package com.parsomash.relayx.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType

@Entity(
    tableName = "filter_rules",
    indices = [
        Index(value = ["priority", "enabled"], name = "idx_rules_priority_enabled")
    ]
)
data class RuleEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "sender_pattern")
    val senderPattern: String,

    @ColumnInfo(name = "sender_match_type")
    val senderMatchType: String,

    @ColumnInfo(name = "content_pattern")
    val contentPattern: String? = null,

    @ColumnInfo(name = "action")
    val action: String,

    @ColumnInfo(name = "transform_pattern")
    val transformPattern: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    fun toDomain(): Rule {
        return Rule(
            id = id,
            name = name,
            senderPattern = senderPattern,
            senderMatchType = try {
                SenderMatchType.valueOf(senderMatchType)
            } catch (_: Exception) {
                SenderMatchType.EXACT
            },
            contentPattern = contentPattern,
            action = try {
                RuleAction.valueOf(action)
            } catch (_: Exception) {
                RuleAction.FORWARD_RAW
            },
            transformPattern = transformPattern,
            priority = priority,
            enabled = enabled,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(domain: Rule): RuleEntity {
            return RuleEntity(
                id = domain.id,
                name = domain.name,
                senderPattern = domain.senderPattern,
                senderMatchType = domain.senderMatchType.name,
                contentPattern = domain.contentPattern,
                action = domain.action.name,
                transformPattern = domain.transformPattern,
                priority = domain.priority,
                enabled = domain.enabled,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }
    }
}
