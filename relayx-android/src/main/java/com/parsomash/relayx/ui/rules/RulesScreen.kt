package com.parsomash.relayx.ui.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsomash.relayx.R
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.ui.rules.components.RuleEditDialog
import com.parsomash.relayx.ui.rules.components.RuleItemCard
import com.parsomash.relayx.ui.rules.components.RuleSandboxCard
import com.parsomash.relayx.viewmodel.RulesUiEvent
import com.parsomash.relayx.viewmodel.RulesViewModel

@Composable
fun RulesScreen(
    viewModel: RulesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var showEditDialog by remember { mutableStateOf(false) }
    var ruleToEdit by remember { mutableStateOf<Rule?>(null) }
    var ruleToDelete by remember { mutableStateOf<Rule?>(null) }

    // Track scroll direction to animate FAB
    var isScrollingDown by remember { mutableStateOf(false) }
    var previousFirstVisibleItemIndex by remember { mutableIntStateOf(0) }
    var previousFirstVisibleItemScrollOffset by remember { mutableIntStateOf(0) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val currentItemIndex = listState.firstVisibleItemIndex
        val currentScrollOffset = listState.firstVisibleItemScrollOffset

        if (currentItemIndex > previousFirstVisibleItemIndex) {
            isScrollingDown = true
        } else if (currentItemIndex < previousFirstVisibleItemIndex) {
            isScrollingDown = false
        } else {
            if (currentScrollOffset > previousFirstVisibleItemScrollOffset + 8) {
                isScrollingDown = true
            } else if (currentScrollOffset < previousFirstVisibleItemScrollOffset - 8) {
                isScrollingDown = false
            }
        }

        previousFirstVisibleItemIndex = currentItemIndex
        previousFirstVisibleItemScrollOffset = currentScrollOffset
    }

    val showFab by remember {
        derivedStateOf {
            !isScrollingDown || !listState.isScrollInProgress || listState.firstVisibleItemIndex == 0
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Unified Screen Header (Matching Dashboard and Settings hierarchy)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.rules_screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.active_rules, state.activeCount),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Interactive Rule Sandbox Card
            item {
                RuleSandboxCard(
                    sender = state.testSender,
                    body = state.testBody,
                    result = state.testResult,
                    onSenderChange = { sender ->
                        viewModel.onEvent(RulesUiEvent.TestInputChanged(sender, state.testBody))
                    },
                    onBodyChange = { body ->
                        viewModel.onEvent(RulesUiEvent.TestInputChanged(state.testSender, body))
                    },
                    onRunTest = {
                        viewModel.onEvent(RulesUiEvent.RunTest)
                    },
                    onClearTest = {
                        viewModel.onEvent(RulesUiEvent.ClearTest)
                    }
                )
            }

            // Rules Header
            item {
                Text(
                    text = "Configured Rules (${state.rules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Rules List or Empty State
            if (state.rules.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.empty_rules_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.empty_rules_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.rules,
                    key = { it.id }
                ) { rule ->
                    RuleItemCard(
                        rule = rule,
                        onToggle = { enabled ->
                            viewModel.onEvent(RulesUiEvent.ToggleRule(rule.id, enabled))
                        },
                        onEdit = {
                            ruleToEdit = rule
                            showEditDialog = true
                        },
                        onDelete = {
                            ruleToDelete = rule
                        }
                    )
                }
            }
        }

        // Floating Action Button with Animated Visibility on scroll
        AnimatedVisibility(
            visible = showFab,
            enter = scaleIn(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)),
            exit = scaleOut(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    ruleToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_rule)
                )
            }
        }

        // SnackbarHost anchored at bottom
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    // Add / Edit Rule Dialog
    if (showEditDialog) {
        RuleEditDialog(
            initialRule = ruleToEdit,
            onDismiss = {
                showEditDialog = false
                ruleToEdit = null
            },
            onSave = { updatedRule ->
                viewModel.onEvent(RulesUiEvent.SaveRule(updatedRule))
                showEditDialog = false
                ruleToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    ruleToDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text(stringResource(R.string.delete_rule)) },
            text = { Text(stringResource(R.string.delete_rule_confirm, rule.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(RulesUiEvent.DeleteRule(rule.id))
                        ruleToDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { ruleToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
