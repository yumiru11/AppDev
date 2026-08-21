package com.yumiru11.githubapp.feature.issue

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
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
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueFilter
import com.yumiru11.githubapp.feature.issue.model.IssueState
import kotlinx.coroutines.flow.Flow

/**
 * Issue 列表页（T13）：TopAppBar + Open/Closed 过滤 + 分页列表（下拉刷新）。
 *
 * - 过滤切换：FilterChip 驱动 viewModel.setFilter，重建分页流
 * - 列表：Paging 分页 + PullToRefreshBox 下拉刷新；点击条目 → onIssueClick(issue)
 * - 空/错/加载态齐全；分页加载错误由 loadState 呈现与重试
 * - 全部 UI 文案经 stringResource()/pluralStringResource() 本地化
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueListScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
    onIssueClick: (owner: String, repo: String, number: Int, isPullRequest: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onCreateIssue: () -> Unit = {},
    viewModel: IssueListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "$owner/$repo") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.issue_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.issue_search),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateIssue,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Create,
                        contentDescription = null,
                    )
                },
                text = { Text(text = stringResource(R.string.issue_create)) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            FilterRow(
                filter = filter,
                onFilterSelected = viewModel::setFilter,
            )
            when (val state = uiState) {
                is IssueListUiState.Loading -> {
                    IssueLoadingContent(modifier = Modifier.fillMaxSize())
                }

                is IssueListUiState.Error -> {
                    IssueErrorContent(
                        errorType = state.errorType,
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is IssueListUiState.Success -> {
                    IssueListContent(
                        owner = owner,
                        repo = repo,
                        issues = state.issues,
                        onIssueClick = onIssueClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: IssueFilter,
    onFilterSelected: (IssueFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = filter == IssueFilter.OPEN,
            onClick = { onFilterSelected(IssueFilter.OPEN) },
            label = { Text(text = stringResource(R.string.issue_filter_open)) },
        )
        FilterChip(
            selected = filter == IssueFilter.CLOSED,
            onClick = { onFilterSelected(IssueFilter.CLOSED) },
            label = { Text(text = stringResource(R.string.issue_filter_closed)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueListContent(
    owner: String,
    repo: String,
    issues: Flow<PagingData<Issue>>,
    onIssueClick: (owner: String, repo: String, number: Int, isPullRequest: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = issues.collectAsLazyPagingItems()
    when {
        lazyItems.loadState.refresh is LoadState.Error -> {
            PagingErrorContent(
                error = (lazyItems.loadState.refresh as LoadState.Error).error,
                onRetry = { lazyItems.retry() },
                modifier = modifier,
            )
        }

        lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
            IssueLoadingContent(modifier = modifier)
        }

        lazyItems.itemCount == 0 -> {
            PullToRefreshBox(
                isRefreshing = lazyItems.loadState.refresh is LoadState.Loading,
                onRefresh = { lazyItems.refresh() },
                modifier = modifier,
            ) {
                EmptyContent(modifier = Modifier.fillMaxSize())
            }
        }

        else -> {
            IssueList(
                owner = owner,
                repo = repo,
                lazyItems = lazyItems,
                onIssueClick = onIssueClick,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueList(
    owner: String,
    repo: String,
    lazyItems: LazyPagingItems<Issue>,
    onIssueClick: (owner: String, repo: String, number: Int, isPullRequest: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = lazyItems.loadState.refresh is LoadState.Loading,
        onRefresh = { lazyItems.refresh() },
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = lazyItems.itemCount,
                // itemKey 内部用 peek(index)（不触发页加载，未加载区回退占位 key），
                // 稳定 id 键保证翻页/刷新时已有行不重组合、滚动位置不跳变。
                key = lazyItems.itemKey { it.id },
            ) { index ->
                val issue = lazyItems[index] ?: return@items
                IssueRow(issue = issue, onClick = { onIssueClick(owner, repo, issue.number, issue.isPullRequest) })
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: Issue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = issue.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText(issue.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(issue.state),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val author = issue.author?.login
                if (!author.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.issue_author, author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = pluralStringResource(R.plurals.issue_comment_count, issue.commentCount, issue.commentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Issue 状态 → 本地化文案 */
@Composable
private fun statusText(state: IssueState): String =
    when (state) {
        IssueState.OPEN -> stringResource(R.string.issue_state_open)
        IssueState.CLOSED -> stringResource(R.string.issue_state_closed)
    }

/** Issue 状态 → 语义色（Open 绿 / Closed 红） */
@Composable
private fun statusColor(state: IssueState): Color =
    when (state) {
        IssueState.OPEN -> MaterialTheme.colorScheme.primary
        IssueState.CLOSED -> MaterialTheme.colorScheme.error
    }

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.issue_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PagingErrorContent(
    error: Throwable,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IssueErrorContent(errorType = error.toIssueErrorType(), onRetry = onRetry, modifier = modifier)
}
