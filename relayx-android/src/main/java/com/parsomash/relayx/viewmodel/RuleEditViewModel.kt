package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.domain.engine.RegexValidator
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.domain.usecase.GetRuleByIdUseCase
import com.parsomash.relayx.domain.usecase.GetSmsSendersUseCase
import com.parsomash.relayx.domain.usecase.SaveRuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class RuleEditUiState(
    val isEditing: Boolean = false,
    val ruleId: String? = null,
    val name: String = "",
    val senderPattern: String = "",
    val senderMatchType: SenderMatchType = SenderMatchType.EXACT,
    val contentPattern: String = "",
    val action: RuleAction = RuleAction.FORWARD_RAW,
    val transformPattern: String = "",
    val priority: Int = 100,
    val enabled: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,

    // SMS Senders Bottom Sheet state
    val availableSenders: List<String> = emptyList(),
    val isLoadingSenders: Boolean = false,
    val senderSearchQuery: String = ""
) {
    val contentRegexError: String?
        get() = RegexValidator.validatePattern(contentPattern)

    val transformRegexError: String?
        get() = if (action == RuleAction.FORWARD_TRANSFORMED) {
            RegexValidator.validatePattern(transformPattern)
        } else {
            null
        }

    val isFormValid: Boolean
        get() = name.isNotBlank() &&
            contentRegexError == null &&
            transformRegexError == null &&
            (action != RuleAction.FORWARD_TRANSFORMED || transformPattern.isNotBlank()) &&
            (senderMatchType == SenderMatchType.ANY || senderPattern.isNotBlank())

    val filteredSenders: List<String>
        get() = if (senderSearchQuery.isBlank()) {
            availableSenders
        } else {
            availableSenders.filter { it.contains(senderSearchQuery, ignoreCase = true) }
        }
}

@KoinViewModel
class RuleEditViewModel(
    private val getRuleByIdUseCase: GetRuleByIdUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val getSmsSendersUseCase: GetSmsSendersUseCase
) : ViewModel() {

    val uiState: StateFlow<RuleEditUiState>
        field = MutableStateFlow(RuleEditUiState())

    fun loadRule(ruleId: String?) {
        if (ruleId == null) {
            uiState.update { RuleEditUiState() }
            loadSenders()
            return
        }
        viewModelScope.launch {
            uiState.update { it.copy(isLoading = true) }
            val rule = getRuleByIdUseCase(ruleId)
            if (rule != null) {
                uiState.update {
                    it.copy(
                        isEditing = true,
                        ruleId = rule.id,
                        name = rule.name,
                        senderPattern = rule.senderPattern,
                        senderMatchType = rule.senderMatchType,
                        contentPattern = rule.contentPattern ?: "",
                        action = rule.action,
                        transformPattern = rule.transformPattern ?: "",
                        priority = rule.priority,
                        enabled = rule.enabled,
                        isLoading = false
                    )
                }
            } else {
                uiState.update { it.copy(isLoading = false) }
            }
            loadSenders()
        }
    }

    fun onNameChanged(name: String) = uiState.update { it.copy(name = name) }
    fun onSenderPatternChanged(pattern: String) = uiState.update { it.copy(senderPattern = pattern) }
    fun onSenderMatchTypeChanged(type: SenderMatchType) = uiState.update { it.copy(senderMatchType = type) }
    fun onContentPatternChanged(pattern: String) = uiState.update { it.copy(contentPattern = pattern) }
    fun onActionChanged(action: RuleAction) = uiState.update { it.copy(action = action) }
    fun onTransformPatternChanged(pattern: String) = uiState.update { it.copy(transformPattern = pattern) }
    fun onPriorityChanged(priority: Int) = uiState.update { it.copy(priority = priority) }
    fun onEnabledChanged(enabled: Boolean) = uiState.update { it.copy(enabled = enabled) }

    fun onSenderSelected(sender: String) {
        uiState.update { it.copy(senderPattern = sender) }
    }

    fun onSenderSearchQueryChanged(query: String) {
        uiState.update { it.copy(senderSearchQuery = query) }
    }

    fun loadSenders() {
        viewModelScope.launch {
            uiState.update { it.copy(isLoadingSenders = true) }
            val senders = getSmsSendersUseCase()
            uiState.update { it.copy(availableSenders = senders, isLoadingSenders = false) }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun saveRule(onSuccess: () -> Unit) {
        val state = uiState.value
        if (!state.isFormValid) return

        viewModelScope.launch {
            val rule = Rule(
                id = state.ruleId ?: Uuid.random().toString(),
                name = state.name.trim(),
                senderPattern = if (state.senderMatchType == SenderMatchType.ANY) "" else state.senderPattern.trim(),
                senderMatchType = state.senderMatchType,
                contentPattern = state.contentPattern.trim().ifEmpty { null },
                action = state.action,
                transformPattern = if (state.action == RuleAction.FORWARD_TRANSFORMED) {
                    state.transformPattern.trim().ifEmpty { null }
                } else null,
                priority = state.priority,
                enabled = state.enabled,
                updatedAt = System.currentTimeMillis()
            )
            val result = saveRuleUseCase(rule)
            if (result.isSuccess) {
                uiState.update { it.copy(isSaved = true) }
                onSuccess()
            } else {
                uiState.update { it.copy(errorMessage = result.exceptionOrNull()?.message) }
            }
        }
    }
}
