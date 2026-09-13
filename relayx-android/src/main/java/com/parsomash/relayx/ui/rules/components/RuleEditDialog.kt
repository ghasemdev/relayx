package com.parsomash.relayx.ui.rules.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.parsomash.relayx.R
import com.parsomash.relayx.domain.engine.RegexValidator
import com.parsomash.relayx.domain.model.Rule
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditDialog(
    initialRule: Rule?,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit
) {
    val isEditing = initialRule != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialRule?.name ?: "") }
    var senderPattern by remember { mutableStateOf(initialRule?.senderPattern ?: "") }
    var senderMatchType by remember {
        mutableStateOf(
            initialRule?.senderMatchType ?: SenderMatchType.ANY
        )
    }
    var contentPattern by remember { mutableStateOf(initialRule?.contentPattern ?: "") }
    var action by remember { mutableStateOf(initialRule?.action ?: RuleAction.FORWARD_RAW) }
    var transformPattern by remember { mutableStateOf(initialRule?.transformPattern ?: "") }
    var priority by remember { mutableIntStateOf(initialRule?.priority ?: 100) }
    var enabled by remember { mutableStateOf(initialRule?.enabled ?: true) }

    val contentRegexError: String? by remember(contentPattern) {
        derivedStateOf {
            RegexValidator.validatePattern(contentPattern)
        }
    }

    val transformRegexError: String? by remember(transformPattern, action) {
        derivedStateOf {
            if (action == RuleAction.FORWARD_TRANSFORMED) {
                RegexValidator.validatePattern(transformPattern)
            } else {
                null
            }
        }
    }

    val isFormValid by remember(
        name,
        contentRegexError,
        transformRegexError,
        action,
        transformPattern
    ) {
        derivedStateOf {
            name.isNotBlank() &&
                contentRegexError == null &&
                transformRegexError == null &&
                (action != RuleAction.FORWARD_TRANSFORMED || transformPattern.isNotBlank())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title & Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(if (isEditing) R.string.edit_rule else R.string.add_rule),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.cancel)
                    )
                }
            }

            HorizontalDivider()

            // Rule Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.rule_name_label)) },
                placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Sender Match Type Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.sender_match_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SenderMatchType.entries.forEach { matchType ->
                        FilterChip(
                            selected = senderMatchType == matchType,
                            onClick = { senderMatchType = matchType },
                            label = { Text(matchType.name) }
                        )
                    }
                }
            }

            // Sender Pattern (hidden if ANY)
            if (senderMatchType != SenderMatchType.ANY) {
                OutlinedTextField(
                    value = senderPattern,
                    onValueChange = { senderPattern = it },
                    label = { Text(stringResource(R.string.sender_pattern_label)) },
                    placeholder = { Text(stringResource(R.string.sender_pattern_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Content Pattern Regex
            OutlinedTextField(
                value = contentPattern,
                onValueChange = { contentPattern = it },
                label = { Text(stringResource(R.string.content_pattern_label)) },
                placeholder = { Text(stringResource(R.string.content_pattern_placeholder)) },
                isError = contentRegexError != null,
                supportingText = {
                    if (contentRegexError != null) {
                        Text(
                            text = stringResource(
                                R.string.regex_invalid,
                                contentRegexError ?: ""
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (contentPattern.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.regex_valid),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Action Policy Selection
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.action_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    RuleAction.entries.forEach { ruleAction ->
                        FilterChip(
                            selected = action == ruleAction,
                            onClick = { action = ruleAction },
                            label = { Text(ruleAction.name) }
                        )
                    }
                }
            }

            // Transform Pattern Regex (Only for FORWARD_TRANSFORMED)
            if (action == RuleAction.FORWARD_TRANSFORMED) {
                OutlinedTextField(
                    value = transformPattern,
                    onValueChange = { transformPattern = it },
                    label = { Text(stringResource(R.string.transform_pattern_label)) },
                    placeholder = { Text(stringResource(R.string.transform_pattern_placeholder)) },
                    isError = transformRegexError != null,
                    supportingText = {
                        if (transformRegexError != null) {
                            Text(
                                text = stringResource(
                                    R.string.regex_invalid,
                                    transformRegexError ?: ""
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (transformPattern.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.regex_valid),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Priority
            OutlinedTextField(
                value = priority.toString(),
                onValueChange = { priority = it.toIntOrNull() ?: 100 },
                label = { Text(stringResource(R.string.priority_label)) },
                placeholder = { Text(stringResource(R.string.priority_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Enabled Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.rule_enabled_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it }
                )
            }

            // Action Buttons: Cancel and Save
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        val rule = Rule(
                            id = initialRule?.id ?: Uuid.random().toString(),
                            name = name.trim(),
                            senderPattern = if (senderMatchType == SenderMatchType.ANY) "" else senderPattern.trim(),
                            senderMatchType = senderMatchType,
                            contentPattern = contentPattern.trim().ifEmpty { null },
                            action = action,
                            transformPattern = if (action == RuleAction.FORWARD_TRANSFORMED) {
                                transformPattern.trim().ifEmpty { null }
                            } else null,
                            priority = priority,
                            enabled = enabled,
                            createdAt = initialRule?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSave(rule)
                    },
                    enabled = isFormValid,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
