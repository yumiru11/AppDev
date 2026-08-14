@file:Suppress("CyclomaticComplexMethod")
// feedActionText 对事件类型 × GitHub action 枚举做穷尽映射，分支天然多（同 NotificationsScreen.reasonLabel 先例）；精准抑制。

package com.yumiru11.githubapp.feature.home

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.navigation.AppRoute
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppBottomBar
import com.yumiru11.githubapp.core.ui.AppTopBar
import com.yumiru11.githubapp.feature.home.model.FeedEventType
import com.yumiru11.githubapp.feature.home.model.FeedItem
import kotlinx.coroutines.flow.Flow
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * 首页动态流页（T10）：AppTopBar + 动态流列表 + AppBottomBar。
 *
 * - 登录态驱动：未登录 → 登录引导（T10 验收第 1 条）
 * - 列表：Paging 分页（T10 验收第 2 条）+ PullToRefreshBox 下拉刷新（T10 验收第 3 条）
 * - 点击条目 → GitHubLinkParser 解析 html_url → 应用内导航（T10 验收第 4 条）
 * - 空/错/加载态齐全（T10 验收第 5 条）；分页加载错误由 LazyPagingItems.loadState 呈现
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
    onTabSelected: (String) -> Unit,
    blurEnabled: Boolean = true,
    onLoginClick: () -> Unit = {},
    onFeedItemClick: (ParsedUrl) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                onSearchClick = onSearchClick,
                onNotificationClick = onNotificationClick,
                onProfileClick = onProfileClick,
                blurEnabled = blurEnabled,
            )
        },
        bottomBar = {
            AppBottomBar(
                selectedTab = AppRoute.HOME,
                onTabSelected = onTabSelected,
                blurEnabled = blurEnabled,
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
                is HomeUiState.Loading -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                is HomeUiState.Unauthenticated -> {
                    UnauthenticatedContent(
                        onLoginClick = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HomeUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is HomeUiState.Success -> {
                    FeedContent(
                        feed = state.feed,
                        onFeedItemClick = onFeedItemClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedContent(
    feed: Flow<PagingData<FeedItem>>,
    onFeedItemClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyItems = feed.collectAsLazyPagingItems()
    when {
        lazyItems.loadState.refresh is LoadState.Error -> {
            PagingErrorContent(
                error = (lazyItems.loadState.refresh as LoadState.Error).error,
                onRetry = { lazyItems.retry() },
                modifier = modifier,
            )
        }

        lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
            LoadingContent(modifier = modifier)
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
            FeedList(
                lazyItems = lazyItems,
                onFeedItemClick = onFeedItemClick,
                modifier = modifier,
            )
        }
    }
}

/** 动态列表：PullToRefreshBox 下拉触发 paging refresh（invalidate 重建请求） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedList(
    lazyItems: LazyPagingItems<FeedItem>,
    onFeedItemClick: (ParsedUrl) -> Unit,
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
                key = { index -> lazyItems[index]?.id ?: index },
            ) { index ->
                val item = lazyItems[index] ?: return@items
                FeedRow(
                    item = item,
                    onClick = { handleItemClick(item, onFeedItemClick) },
                )
            }
        }
    }
}

@Composable
private fun FeedRow(
    item: FeedItem,
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
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = item.actorAvatarUrl,
                contentDescription = item.actorLogin,
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feedActionText(item),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.title.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.repoFullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val date = formatDate(item.createdAt)
                    if (date.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = date,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 点击条目：解析 html_url → 应用内 ParsedUrl 导航（External 防御性忽略） */
private fun handleItemClick(
    item: FeedItem,
    onFeedItemClick: (ParsedUrl) -> Unit,
) {
    val htmlUrl = item.htmlUrl ?: return
    val parsed = GitHubLinkParser.parseUrl(htmlUrl)
    if (parsed !is ParsedUrl.External) {
        onFeedItemClick(parsed)
    }
}

/** 事件类型 + action → 本地化动作文案（ViewModel 只传原始值，不产英文） */
@Composable
private fun feedActionText(item: FeedItem): String =
    when (item.type) {
        FeedEventType.ISSUE -> {
            when (item.action) {
                ACTION_OPENED -> stringResource(R.string.feed_action_issue_opened, item.actorLogin, item.number ?: 0)
                ACTION_CLOSED -> stringResource(R.string.feed_action_issue_closed, item.actorLogin, item.number ?: 0)
                ACTION_REOPENED -> stringResource(R.string.feed_action_issue_reopened, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_issue_other, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.ISSUE_COMMENT -> {
            when (item.action) {
                ACTION_EDITED -> stringResource(R.string.feed_action_issue_comment_edited, item.actorLogin, item.number ?: 0)
                ACTION_DELETED -> stringResource(R.string.feed_action_issue_comment_deleted, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_issue_comment, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.PULL_REQUEST -> {
            when (item.action) {
                ACTION_OPENED -> stringResource(R.string.feed_action_pr_opened, item.actorLogin, item.number ?: 0)
                ACTION_CLOSED -> stringResource(R.string.feed_action_pr_closed, item.actorLogin, item.number ?: 0)
                ACTION_REOPENED -> stringResource(R.string.feed_action_pr_reopened, item.actorLogin, item.number ?: 0)
                else -> stringResource(R.string.feed_action_pr_other, item.actorLogin, item.number ?: 0)
            }
        }

        FeedEventType.PUSH -> {
            stringResource(R.string.feed_action_push, item.actorLogin, item.commitCount ?: 0)
        }

        FeedEventType.STAR -> {
            stringResource(R.string.feed_action_star, item.actorLogin)
        }

        FeedEventType.FORK -> {
            stringResource(R.string.feed_action_fork, item.actorLogin)
        }
    }

/** GitHub 事件 action 字面量 */
private const val ACTION_OPENED = "opened"
private const val ACTION_CLOSED = "closed"
private const val ACTION_REOPENED = "reopened"
private const val ACTION_EDITED = "edited"
private const val ACTION_DELETED = "deleted"

/** ISO-8601 时间戳 → 本地日期（yyyy-MM-dd）；解析失败返回空串（UI 隐藏时间） */
private fun formatDate(isoTimestamp: String?): String {
    if (isoTimestamp.isNullOrBlank()) return ""
    return runCatching {
        Instant
            .parse(isoTimestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }.getOrDefault("")
}

@Composable
private fun UnauthenticatedContent(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.feed_require_login),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.feed_require_login_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onLoginClick) {
                    Text(text = stringResource(R.string.feed_login))
                }
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
            text = stringResource(R.string.feed_empty),
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
    val errorType = if (error is IOException || error is HttpException) HomeErrorType.NETWORK else HomeErrorType.UNKNOWN
    ErrorContent(errorType = errorType, onRetry = onRetry, modifier = modifier)
}

@Composable
private fun ErrorContent(
    errorType: HomeErrorType,
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
                Text(text = stringResource(R.string.feed_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: HomeErrorType): String =
    when (errorType) {
        HomeErrorType.NETWORK -> stringResource(R.string.feed_error_network)
        HomeErrorType.UNAUTHORIZED -> stringResource(R.string.feed_error_unauthorized)
        HomeErrorType.UNKNOWN -> stringResource(R.string.feed_error_unknown)
    }
