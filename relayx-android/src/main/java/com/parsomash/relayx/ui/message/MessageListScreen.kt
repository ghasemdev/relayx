@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parsomash.relayx.ui.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.parsomash.relayx.R
import com.parsomash.relayx.data.worker.MessageDispatchWorker
import com.parsomash.relayx.domain.model.DeliveryStatus
import com.parsomash.relayx.domain.model.MessageFilter
import com.parsomash.relayx.viewmodel.MessageListViewModel
import kotlinx.coroutines.launch

@Composable
fun MessageListScreen(
    viewModel: MessageListViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialFilter: String = "ALL"
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val filters = remember { MessageFilter.entries }
    val initialPageIndex = remember(initialFilter) {
        val target = MessageFilter.fromString(initialFilter)
        filters.indexOf(target).coerceAtLeast(0)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { filters.size }
    )

    val listStates = remember {
        List(filters.size) { LazyListState() }
    }

    val currentListState = listStates.getOrElse(pagerState.currentPage) { listStates.first() }

    val isScrolled by remember {
        derivedStateOf {
            currentListState.firstVisibleItemIndex > 0 || currentListState.firstVisibleItemScrollOffset > 40
        }
    }

    var isSearchExplicitlyExpanded by remember { mutableStateOf(false) }

    // Search bar is visible when user is near the top, when a query is entered, or when explicitly tapped
    val showInlineSearchBar = !isScrolled || state.searchQuery.isNotEmpty() || isSearchExplicitlyExpanded

    LaunchedEffect(initialFilter) {
        val targetIndex = filters.indexOf(MessageFilter.fromString(initialFilter)).coerceAtLeast(0)
        if (pagerState.currentPage != targetIndex) {
            pagerState.scrollToPage(targetIndex)
        }
        viewModel.setInitialFilter(initialFilter)
    }

    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectFilter(filters[pagerState.currentPage])
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.message_list_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    // Parallax/collapse action: Search icon in top bar when inline search bar is collapsed
                    AnimatedVisibility(
                        visible = !showInlineSearchBar,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        IconButton(
                            onClick = {
                                isSearchExplicitlyExpanded = true
                                coroutineScope.launch {
                                    currentListState.animateScrollToItem(0)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search_messages_hint)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Collapsible Animated Search Bar
            AnimatedVisibility(
                visible = showInlineSearchBar,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    placeholder = { Text(stringResource(R.string.search_messages_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    viewModel.clearSearch()
                                    isSearchExplicitlyExpanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_search)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors()
                )
            }

            // Horizontal Filter Tabs
            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                filters.forEachIndexed { index, filter ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(filter.titleResId),
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // Swipeable HorizontalPager between filter categories
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val filter = filters[pageIndex]
                val pageItems = remember(state.allMessages, filter) {
                    when (filter) {
                        MessageFilter.ALL -> state.allMessages
                        MessageFilter.PENDING -> state.allMessages.filter { it.status == DeliveryStatus.PENDING }
                        MessageFilter.FORWARDED -> state.allMessages.filter { it.status == DeliveryStatus.DELIVERED }
                        MessageFilter.FAILED -> state.allMessages.filter { it.status == DeliveryStatus.FAILED }
                        MessageFilter.FILTERED -> state.allMessages.filter { it.status == DeliveryStatus.FILTERED }
                    }
                }
                val listState = listStates[pageIndex]

                if (pageItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 32.dp,
                                end = 32.dp,
                                top = 32.dp,
                                bottom = 32.dp + innerPadding.calculateBottomPadding()
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.empty_messages_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = if (state.totalCount == 0) {
                                    stringResource(R.string.no_messages_in_db)
                                } else {
                                    stringResource(R.string.empty_messages_desc)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 12.dp,
                            end = 16.dp,
                            bottom = 16.dp + innerPadding.calculateBottomPadding()
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = pageItems,
                            key = { it.id }
                        ) { item ->
                            MessageItemCard(
                                item = item,
                                onClick = { viewModel.selectMessage(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for inspecting selected message
    state.selectedMessage?.let { detail ->
        MessageDetailBottomSheet(
            detail = detail,
            onDismissRequest = { viewModel.dismissMessageDetail() },
            onRetryClick = { messageId ->
                viewModel.retryMessage(messageId) {
                    MessageDispatchWorker.enqueue(context)
                }
            },
            isRetrying = state.isRetrying
        )
    }
}
