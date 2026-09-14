@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parsomash.relayx.ui.rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsomash.relayx.R
import com.parsomash.relayx.domain.model.RuleAction
import com.parsomash.relayx.domain.model.SenderMatchType
import com.parsomash.relayx.viewmodel.RuleEditViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun RuleEditScreen(
    ruleId: String?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val viewModel: RuleEditViewModel = koinViewModel()
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showSenderSheet by remember { mutableStateOf(false) }

  LaunchedEffect(ruleId) {
    viewModel.loadRule(ruleId)
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = stringResource(if (state.isEditing) R.string.edit_rule else R.string.add_rule),
            fontWeight = FontWeight.Bold
          )
        },
        navigationIcon = {
          IconButton(onClick = onNavigateBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.navigate_back)
            )
          }
        },
        actions = {
          Button(
            onClick = { viewModel.saveRule(onNavigateBack) },
            enabled = state.isFormValid && !state.isLoading,
            modifier = Modifier.padding(end = 8.dp)
          ) {
            Text(stringResource(R.string.save))
          }
        }
      )
    }
  ) { innerPadding ->
    if (state.isLoading) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    } else {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .imePadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Rule Name
        OutlinedTextField(
          value = state.name,
          onValueChange = viewModel::onNameChanged,
          label = { Text(stringResource(R.string.rule_name_label)) },
          placeholder = { Text(stringResource(R.string.rule_name_placeholder)) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Priority & Enabled Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = state.priority.toString(),
            onValueChange = { input ->
              val num = input.filter { it.isDigit() }.toIntOrNull() ?: 0
              viewModel.onPriorityChanged(num)
            },
            label = { Text(stringResource(R.string.priority_label)) },
            supportingText = { Text(stringResource(R.string.priority_helper)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = stringResource(R.string.rule_enabled_label),
              style = MaterialTheme.typography.bodyMedium
            )
            Switch(
              checked = state.enabled,
              onCheckedChange = viewModel::onEnabledChanged
            )
          }
        }

        HorizontalDivider()

        // Sender Match Type Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.sender_match_type_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            SenderMatchType.entries.forEach { matchType ->
              val labelRes = when (matchType) {
                SenderMatchType.EXACT -> R.string.match_type_exact
                SenderMatchType.PREFIX -> R.string.match_type_prefix
                SenderMatchType.CONTAINS -> R.string.match_type_contains
                SenderMatchType.REGEX -> R.string.match_type_regex
                SenderMatchType.ANY -> R.string.match_type_any
              }
              FilterChip(
                selected = state.senderMatchType == matchType,
                onClick = { viewModel.onSenderMatchTypeChanged(matchType) },
                label = { Text(stringResource(labelRes), maxLines = 1) }
              )
            }
          }
        }

        // Sender Pattern (hidden if ANY)
        if (state.senderMatchType != SenderMatchType.ANY) {
          OutlinedTextField(
            value = state.senderPattern,
            onValueChange = viewModel::onSenderPatternChanged,
            label = { Text(stringResource(R.string.sender_pattern_label)) },
            placeholder = { Text(stringResource(R.string.sender_pattern_placeholder)) },
            singleLine = true,
            trailingIcon = if (state.senderMatchType == SenderMatchType.EXACT) {
              {
                IconButton(onClick = {
                  viewModel.loadSenders()
                  showSenderSheet = true
                }) {
                  Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = stringResource(R.string.select_sender_from_sms),
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            } else null,
            modifier = Modifier.fillMaxWidth()
          )
        }

        HorizontalDivider()

        // Content Pattern Regex (Optional)
        OutlinedTextField(
          value = state.contentPattern,
          onValueChange = viewModel::onContentPatternChanged,
          label = { Text(stringResource(R.string.content_pattern_label)) },
          placeholder = { Text(stringResource(R.string.content_pattern_placeholder)) },
          isError = state.contentRegexError != null,
          supportingText = {
            if (state.contentRegexError != null) {
              Text(
                text = stringResource(
                  R.string.regex_invalid,
                  state.contentRegexError ?: ""
                ),
                color = MaterialTheme.colorScheme.error
              )
            } else if (state.contentPattern.isNotBlank()) {
              Text(
                text = stringResource(R.string.regex_valid),
                color = MaterialTheme.colorScheme.primary
              )
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        // Action Policy Selection
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.action_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            RuleAction.entries.forEach { act ->
              val labelRes = when (act) {
                RuleAction.FORWARD_RAW -> R.string.action_forward_raw
                RuleAction.FORWARD_TRANSFORMED -> R.string.action_forward_transformed
                RuleAction.DROP -> R.string.action_drop
              }
              FilterChip(
                selected = state.action == act,
                onClick = { viewModel.onActionChanged(act) },
                label = { Text(stringResource(labelRes), maxLines = 1) }
              )
            }
          }
        }

        // Transform Regex (only if FORWARD_TRANSFORMED)
        if (state.action == RuleAction.FORWARD_TRANSFORMED) {
          OutlinedTextField(
            value = state.transformPattern,
            onValueChange = viewModel::onTransformPatternChanged,
            label = { Text(stringResource(R.string.transform_pattern_label)) },
            placeholder = { Text(stringResource(R.string.transform_pattern_placeholder)) },
            isError = state.transformRegexError != null,
            supportingText = {
              if (state.transformRegexError != null) {
                Text(
                  text = stringResource(
                    R.string.regex_invalid,
                    state.transformRegexError ?: ""
                  ),
                  color = MaterialTheme.colorScheme.error
                )
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        }

        // Error message
        state.errorMessage?.let { error ->
          Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }
  }

  // Modal Bottom Sheet for picking senders from SMS
  if (showSenderSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showSenderSheet = false },
      sheetState = sheetState
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Text(
          text = stringResource(R.string.select_sender_title),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )

        // Search field
        OutlinedTextField(
          value = state.senderSearchQuery,
          onValueChange = viewModel::onSenderSearchQueryChanged,
          placeholder = { Text(stringResource(R.string.search_senders_placeholder)) },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          },
          trailingIcon = {
            if (state.senderSearchQuery.isNotEmpty()) {
              IconButton(onClick = { viewModel.onSenderSearchQueryChanged("") }) {
                Icon(imageVector = Icons.Default.Clear, contentDescription = null)
              }
            }
          },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        if (state.isLoadingSenders) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp),
            contentAlignment = Alignment.Center
          ) {
            CircularProgressIndicator()
          }
        } else if (state.filteredSenders.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(140.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = stringResource(R.string.no_senders_found),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            items(state.filteredSenders, key = { it.address }) { sender ->
              val isSelected = state.senderPattern == sender.address
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable {
                    viewModel.onSenderSelected(sender.address)
                    showSenderSheet = false
                  }
                  .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                      if (isSelected) MaterialTheme.colorScheme.primaryContainer
                      else MaterialTheme.colorScheme.surfaceVariant
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                  )
                }

                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Text(
                    text = sender.address,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (!sender.snippet.isNullOrBlank()) {
                    Text(
                      text = sender.snippet,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }

                if (isSelected) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
