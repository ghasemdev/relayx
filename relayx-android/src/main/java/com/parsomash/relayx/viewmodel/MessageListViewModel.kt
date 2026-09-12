package com.parsomash.relayx.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parsomash.relayx.data.local.OutboxMessageDao
import com.parsomash.relayx.domain.model.MessageDetail
import com.parsomash.relayx.domain.model.MessageFilter
import com.parsomash.relayx.domain.model.MessageListItem
import com.parsomash.relayx.domain.model.toDetail
import com.parsomash.relayx.domain.model.toListItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

data class MessageListUiState(
    val messages: List<MessageListItem> = emptyList(),
    val totalCount: Int = 0,
    val selectedFilter: MessageFilter = MessageFilter.ALL,
    val searchQuery: String = "",
    val selectedMessage: MessageDetail? = null,
    val isRetrying: Boolean = false
)

@KoinViewModel
class MessageListViewModel(
    private val outboxMessageDao: OutboxMessageDao
) : ViewModel() {

    val selectedFilter: StateFlow<MessageFilter>
        field = MutableStateFlow(MessageFilter.ALL)

    val searchQuery: StateFlow<String>
        field = MutableStateFlow("")

    val selectedMessage: StateFlow<MessageDetail?>
        field = MutableStateFlow<MessageDetail?>(null)

    val isRetrying: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val uiState: StateFlow<MessageListUiState> = combine(
        outboxMessageDao.observeAllMessages(),
        selectedFilter,
        searchQuery,
        selectedMessage,
        isRetrying
    ) { allMessages, filter, query, selectedMsg, retrying ->
            val filteredByTab = when (filter) {
                MessageFilter.ALL -> allMessages
                MessageFilter.PENDING -> allMessages.filter { it.status.equals("PENDING", ignoreCase = true) }
                MessageFilter.FORWARDED -> allMessages.filter { it.status.equals("DELIVERED", ignoreCase = true) }
                MessageFilter.FAILED -> allMessages.filter { it.status.equals("FAILED", ignoreCase = true) }
                MessageFilter.FILTERED -> allMessages.filter { it.status.equals("FILTERED", ignoreCase = true) }
            }

            val filteredBySearch = if (query.isBlank()) {
                filteredByTab
            } else {
                val q = query.trim().lowercase()
                filteredByTab.filter { msg ->
                    msg.sender.lowercase().contains(q) || msg.id.lowercase().contains(q)
                }
            }

            val listItems = filteredBySearch.map { it.toListItem() }

            val currentDetail = selectedMsg?.let { current ->
                allMessages.find { it.id == current.id }?.toDetail() ?: current
            }

            MessageListUiState(
                messages = listItems,
                totalCount = allMessages.size,
                selectedFilter = filter,
                searchQuery = query,
                selectedMessage = currentDetail,
                isRetrying = retrying
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MessageListUiState()
        )

    fun setInitialFilter(filterStr: String) {
        selectedFilter.value = MessageFilter.fromString(filterStr)
    }

    fun selectFilter(filter: MessageFilter) {
        selectedFilter.value = filter
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun clearSearch() {
        searchQuery.value = ""
    }

    fun selectMessage(item: MessageListItem) {
        viewModelScope.launch {
            val entity = outboxMessageDao.getById(item.id)
            selectedMessage.value = entity?.toDetail()
        }
    }

    fun dismissMessageDetail() {
        selectedMessage.value = null
    }

    fun retryMessage(messageId: String, onScheduleDispatch: (() -> Unit)? = null) {
        viewModelScope.launch {
            isRetrying.value = true
            try {
                val now = System.currentTimeMillis()
                outboxMessageDao.resetForRetry(messageId, now)
                onScheduleDispatch?.invoke()
            } finally {
                isRetrying.value = false
            }
        }
    }
}
