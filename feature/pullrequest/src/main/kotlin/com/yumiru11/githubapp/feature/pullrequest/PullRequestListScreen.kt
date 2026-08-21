package com.yumiru11.githubapp.feature.pullrequest

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFilter
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import kotlinx.coroutines.flow.Flow

/**
 * PR 列表页（T15）：TopAppBar + Open/Closed/All 过滤 + 分页列表（下拉刷新）。
 *
 * - 过滤切换：FilterChip 驱动 viewModel.setFilter，重建分页流
 * - 列表：Paging 分页 + PullToRefreshBox 下拉刷新；点击条目 → onPullRequestClick(pr)
 * - 空/错/加载态齐全；分页加载错误由 loadState 呈现与重试
 * - 全部 UI 文案经 stringResource()/pluralStringResource() 本地化
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestListScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
    onPullRequestClick: (owner: String, repo: String, number: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PullRequestListViewModel = hiltViewModel(),
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
                            contentDescription = stringResource(R.string.pull_request_back),
                        )
                    }
                },
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
                is PullRequestListUiState.Loading -> {
                    PullRequestLoadingContent(modifier = Modifier.fillMaxSize())
                }

                is PullRequestListUiState.Error -> {
                    PullRequestErrorContent(
                        errorType = state.errorType,
                        onRetry = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is PullRequestListUiState.Success -> {
                    PullRequestListContent(
                        owner = owner,
                        repo = repo,
                        pulls = state.pulls,
                        onPullRequestClick = onPullRequestClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    filter: PullRequestFilter,
    onFilterSelected: (PullRequestFilter) -> Unit,
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
            selected = filter == PullRequestFilter.OPEN,
            onClick = { onFilterSelected(PullRequestFilter.OPEN) },
            label = { Text(text = stringResource(R.string.pull_request_filter_open)) },
        )
        FilterChip(
            selected = filter == PullRequestFilter.CLOSED,
            onClick = { onFilterSelected(PullRequestFilter.CLOSED) },
            label = { Text(text = stringResource(R.string.pull_request_filter_closed)) },
        )
        FilterChip(
            selected = filter == PullRequestFilter.ALL,
            onClick = { onFilterSelected(PullRequestFilter.ALL) },
            label = { Text(text = stringResource(R.string.pull_request_filter_all)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRequestListContent(
    owner: String,
    repo: String,
    pulls: Flow<PagingData<PullRequest>>,
    onPullRequestClick: (owner: String, repo: String, number: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = pulls.collectAsLazyPagingItems()
    when {
        lazyItems.loadState.refresh is LoadState.Error -> {
            PullRequestErrorContent(
                errorType = (lazyItems.loadState.refresh as LoadState.Error).error.toPullRequestErrorType(),
                onRetry = { lazyItems.retry() },
                modifier = modifier,
            )
        }

        lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
            PullRequestLoadingContent(modifier = modifier)
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
            PullRequestList(
                owner = owner,
                repo = repo,
                lazyItems = lazyItems,
                onPullRequestClick = onPullRequestClick,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullRequestList(
    owner: String,
    repo: String,
    lazyItems: LazyPagingItems<PullRequest>,
    onPullRequestClick: (owner: String, repo: String, number: Int) -> Unit,
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
                val pullRequest = lazyItems[index] ?: return@items
                PullRequestRow(
                    pullRequest = pullRequest,
                    onClick = { onPullRequestClick(owner, repo, pullRequest.number) },
                )
            }
        }
    }
}

@Composable
private fun PullRequestRow(
    pullRequest: PullRequest,
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
                text = pullRequest.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText(pullRequest.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(pullRequest.state),
                )
                Spacer(modifier = Modifier.width(8.dp))
                val author = pullRequest.author?.login
                if (!author.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.pull_request_author, author),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = pluralStringResource(R.plurals.pull_request_comment_count, pullRequest.commentCount, pullRequest.commentCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** PR 状态 → 本地化文案 */
@Composable
private fun statusText(state: PullRequestState): String =
    when (state) {
        PullRequestState.OPEN -> stringResource(R.string.pull_request_state_open)
        PullRequestState.CLOSED -> stringResource(R.string.pull_request_state_closed)
        PullRequestState.MERGED -> stringResource(R.string.pull_request_state_merged)
        PullRequestState.DRAFT -> stringResource(R.string.pull_request_state_draft)
    }

/** PR 状态 → 语义色（Open 绿 / Merged 紫 / Closed 红 / Draft 灰） */
@Composable
private fun statusColor(state: PullRequestState): Color =
    when (state) {
        PullRequestState.OPEN -> MaterialTheme.colorScheme.primary
        PullRequestState.MERGED -> MaterialTheme.colorScheme.tertiary
        PullRequestState.CLOSED -> MaterialTheme.colorScheme.error
        PullRequestState.DRAFT -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
private fun EmptyContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.pull_request_list_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
