package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleEvaluationResult
import com.parsomash.relayx.domain.usecase.DeleteRuleUseCase
import com.parsomash.relayx.domain.usecase.GetActiveRulesCountUseCase
import com.parsomash.relayx.domain.usecase.GetRulesUseCase
import com.parsomash.relayx.domain.usecase.ReorderRulesUseCase
import com.parsomash.relayx.domain.usecase.SaveRuleUseCase
import com.parsomash.relayx.domain.usecase.TestRulesUseCase
import com.parsomash.relayx.domain.usecase.ToggleRuleUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

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

@KoinViewModel
class RulesViewModel(
    private val getRulesUseCase: GetRulesUseCase,
    private val getActiveRulesCountUseCase: GetActiveRulesCountUseCase,
    private val saveRuleUseCase: SaveRuleUseCase,
    private val toggleRuleUseCase: ToggleRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val reorderRulesUseCase: ReorderRulesUseCase,
    private val testRulesUseCase: TestRulesUseCase
) : ViewModel() {

    val uiState: StateFlow<RulesUiState>
        field = MutableStateFlow(RulesUiState())

    init {
        getRulesUseCase()
            .onEach { rules ->
                uiState.update { it.copy(rules = rules, isLoading = false) }
            }
            .launchIn(viewModelScope)

        getActiveRulesCountUseCase()
            .onEach { count ->
                uiState.update { it.copy(activeCount = count) }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: RulesUiEvent) {
        when (event) {
            is RulesUiEvent.ToggleRule -> {
                viewModelScope.launch {
                    toggleRuleUseCase(event.id, event.enabled)
                }
            }

            is RulesUiEvent.DeleteRule -> {
                viewModelScope.launch {
                    deleteRuleUseCase(event.id)
                }
            }

            is RulesUiEvent.SaveRule -> {
                viewModelScope.launch {
                    val result = saveRuleUseCase(event.rule)
                    if (result.isFailure) {
                        uiState.update {
                            it.copy(errorMessage = result.exceptionOrNull()?.message)
                        }
                    }
                }
            }

            is RulesUiEvent.ReorderRules -> {
                viewModelScope.launch {
                    reorderRulesUseCase(event.orderedIds)
                }
            }

            is RulesUiEvent.TestInputChanged -> {
                uiState.update {
                    it.copy(testSender = event.sender, testBody = event.body)
                }
            }

            is RulesUiEvent.RunTest -> {
                viewModelScope.launch {
                    val result = testRulesUseCase(
                        sender = uiState.value.testSender,
                        body = uiState.value.testBody
                    )
                    uiState.update { it.copy(testResult = result) }
                }
            }

            is RulesUiEvent.ClearTest -> {
                uiState.update {
                    it.copy(
                        testSender = "",
                        testBody = "",
                        testResult = null
                    )
                }
            }
        }
    }

    fun clearError() {
        uiState.update { it.copy(errorMessage = null) }
    }
}
