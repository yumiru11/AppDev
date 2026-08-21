@file:OptIn(ExperimentalLayoutApi::class)

package com.yumiru11.githubapp.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.data.model.SearchCodeItem
import com.yumiru11.githubapp.core.data.model.SearchIssue
import com.yumiru11.githubapp.core.data.model.User
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.search.qualifier.QUALIFIER_SUGGESTIONS
import com.yumiru11.githubapp.feature.search.qualifier.appendQualifier
import kotlinx.coroutines.flow.Flow

/**
 * 搜索页（T18，docs/ui-design.md §3.3）。
 *
 * - 大号搜索框（自动聚焦）+ 历史记录 chips（可清空）+ qualifier 快速建议
 * - 结果 Tabs（仓库/用户/Issue/PR/代码），每 Tab 独立 Paging
 * - 代码搜索需登录：未登录展示登录引导（T18 验收第 4 条）
 * - 限流（429）与网络错误：分页错误按 GitHubError 分类展示友好文案（验收第 5 条）
 * - 点击结果 → GitHubLinkParser 解析 html_url → 应用内路由（回调由宿主接线）
 */
@Composable
fun SearchScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onResultClick: (ParsedUrl) -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SearchTopBar(
                input = input,
                onInputChange = viewModel::onQueryChange,
                onSubmit = {
                    viewModel.submitQuery(input)
                    keyboard?.hide()
                },
                onBackClick = onBackClick,
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    IdleContent(
                        history = history,
                        input = input,
                        onHistoryClick = { viewModel.submitQuery(it) },
                        onClearHistory = viewModel::clearHistory,
                        onQualifierClick = { qualifier ->
                            val next = appendQualifier(input, qualifier)
                            viewModel.onQueryChange(next)
                            viewModel.submitQuery(next)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is SearchUiState.Loading -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                is SearchUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is SearchUiState.Success -> {
                    SuccessContent(
                        state = state,
                        isLoggedIn = isLoggedIn,
                        onTabSelected = viewModel::selectTab,
                        onLoginClick = onLoginClick,
                        onResultClick = onResultClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessContent(
    state: SearchUiState.Success,
    isLoggedIn: Boolean,
    onTabSelected: (SearchTab) -> Unit,
    onLoginClick: () -> Unit,
    onResultClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ResultTabs(
            selectedTab = state.activeTab,
            onTabSelected = onTabSelected,
        )
        when (state.activeTab) {
            SearchTab.REPOSITORIES -> {
                RepositoriesContent(
                    flow = state.repositories,
                    onResultClick = onResultClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SearchTab.USERS -> {
                UsersContent(
                    flow = state.users,
                    onResultClick = onResultClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SearchTab.ISSUES -> {
                IssuesContent(
                    flow = state.issues,
                    onResultClick = onResultClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SearchTab.PULL_REQUESTS -> {
                IssuesContent(
                    flow = state.pullRequests,
                    onResultClick = onResultClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            SearchTab.CODE -> {
                if (isLoggedIn) {
                    CodeContent(
                        flow = state.code,
                        onResultClick = onResultClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CodeLoginGateContent(
                        onLoginClick = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent(
    history: List<String>,
    input: String,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    onQualifierClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (history.isNotEmpty()) {
            item {
                HistorySection(
                    history = history,
                    onHistoryClick = onHistoryClick,
                    onClearHistory = onClearHistory,
                )
            }
        }
        if (input.isEmpty()) {
            item {
                QualifierSection(onQualifierClick = onQualifierClick)
            }
        }
    }
}

@Composable
private fun HistorySection(
    history: List<String>,
    onHistoryClick: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_history),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClearHistory) {
                Text(text = stringResource(R.string.search_history_clear))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            history.forEach { query ->
                SuggestionChip(
                    onClick = { onHistoryClick(query) },
                    label = {
                        Text(
                            text = query,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun QualifierSection(
    onQualifierClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.search_qualifier_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUALIFIER_SUGGESTIONS.forEach { qualifier ->
                SuggestionChip(
                    onClick = { onQualifierClick(qualifier.value) },
                    label = {
                        Text(
                            text = qualifier.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultTabs(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = 8.dp,
    ) {
        SearchTab.entries.forEach { tab ->
            Tab(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tabLabel(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun tabLabel(tab: SearchTab): String =
    when (tab) {
        SearchTab.REPOSITORIES -> stringResource(R.string.search_tab_repositories)
        SearchTab.USERS -> stringResource(R.string.search_tab_users)
        SearchTab.ISSUES -> stringResource(R.string.search_tab_issues)
        SearchTab.PULL_REQUESTS -> stringResource(R.string.search_tab_pull_requests)
        SearchTab.CODE -> stringResource(R.string.search_tab_code)
    }

@Composable
private fun RepositoriesContent(
    flow: Flow<PagingData<Repository>>,
    onResultClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.fullName },
        modifier = modifier,
        row = { repository ->
            RepositoryRow(
                repository = repository,
                onClick = {
                    val url = "https://github.com/${repository.ownerLogin}/${repository.name}"
                    handleResultClick(url, onResultClick)
                },
            )
        },
    )
}

@Composable
private fun UsersContent(
    flow: Flow<PagingData<User>>,
    onResultClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.login },
        modifier = modifier,
        row = { user ->
            UserRow(
                user = user,
                onClick = { user.url?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

@Composable
private fun IssuesContent(
    flow: Flow<PagingData<SearchIssue>>,
    onResultClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        keyOf = { it.id },
        modifier = modifier,
        row = { issue ->
            IssueRow(
                issue = issue,
                onClick = { issue.htmlUrl?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

@Composable
private fun CodeContent(
    flow: Flow<PagingData<SearchCodeItem>>,
    onResultClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = flow.collectAsLazyPagingItems()
    SearchPagingList(
        lazyItems = lazyItems,
        // 代码搜索结果无唯一 id，用「仓库 + 文件路径」组合键（同一文件在结果集中至多一条）
        keyOf = { "${it.repoFullName}/${it.path}" },
        modifier = modifier,
        row = { item ->
            CodeRow(
                item = item,
                onClick = { item.htmlUrl?.let { handleResultClick(it, onResultClick) } },
            )
        },
    )
}

/**
 * 通用分页结果列表：刷新错误 → 全屏错误（按错误类型本地化文案）；
 * 追加错误 → 底部重试行；空结果 → 空态。
 */
@Composable
private fun <T : Any> SearchPagingList(
    lazyItems: LazyPagingItems<T>,
    keyOf: (T) -> Any,
    row: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val refreshError = lazyItems.loadState.refresh as? LoadState.Error
        when {
            refreshError != null && lazyItems.itemCount == 0 -> {
                item {
                    PagingErrorContent(
                        error = refreshError.error,
                        onRetry = lazyItems::retry,
                    )
                }
            }

            lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
                item { LoadingContent() }
            }

            lazyItems.itemCount == 0 -> {
                item { EmptyContent() }
            }

            else -> {
                items(
                    count = lazyItems.itemCount,
                    // itemKey 内部用 peek(index)（不触发页加载，未加载区回退占位 key），
                    // 稳定 id 键保证翻页/刷新时已有行不重组合、滚动位置不跳变。
                    key = lazyItems.itemKey(keyOf),
                ) { index ->
                    val item = lazyItems[index] ?: return@items
                    row(item)
                }
                if (lazyItems.loadState.append is LoadState.Error) {
                    item { AppendErrorRow(onRetry = lazyItems::retry) }
                }
            }
        }
    }
}

@Composable
private fun CodeLoginGateContent(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.search_code_require_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLoginClick) {
                Text(text = stringResource(R.string.search_login))
            }
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.search_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppendErrorRow(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.search_load_more_failed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.search_retry))
        }
    }
}

@Composable
private fun PagingErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ErrorContent(errorType = error.toSearchErrorType(), onRetry = onRetry, modifier = modifier)
}

@Composable
private fun ErrorContent(
    errorType: SearchErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.search_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel/UI 只产类型，不产英文） */
@Composable
private fun errorMessage(errorType: SearchErrorType): String =
    when (errorType) {
        SearchErrorType.NETWORK -> stringResource(R.string.search_error_network)
        SearchErrorType.RATE_LIMITED -> stringResource(R.string.search_error_rate_limited)
        SearchErrorType.UNAUTHORIZED -> stringResource(R.string.search_error_unauthorized)
        SearchErrorType.UNKNOWN -> stringResource(R.string.search_error_unknown)
    }
