@file:Suppress("CyclomaticComplexMethod")
// reasonLabel 对 GitHub reason 枚举做穷尽映射（13 分支 + 未知兜底），分支天然多（同 AppRoute.fromParsedUrl 先例）；精准抑制。

package com.yumiru11.githubapp.feature.notifications

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * 通知页（T19，docs/ui-design.md §3.4「通知 = 全屏 slide-in 面板」决策）。
 *
 * - 顶部：标题「通知」+ 全部已读 + 返回（进入动画见 AppNavHost 的 slide-in 转场）
 * - 过滤 chips：全部 / 参与 / 提及（T19 验收第 3 条）
 * - 列表：Paging 分页（T19 验收第 1 条）；分页加载错误由 LazyPagingItems.loadState 呈现
 * - 点击条目 → GitHubLinkParser 解析 html_url → 应用内导航（T19 验收第 4 条）
 * - 未登录 → 登录引导（复用 T4 auth 状态，[NotificationsUiState.Unauthenticated]）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onNotificationClick: (ParsedUrl) -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.notification_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.notification_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.markAllRead() },
                        enabled = uiState is NotificationsUiState.Success,
                    ) {
                        Text(text = stringResource(R.string.notification_mark_all_read))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
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
                is NotificationsUiState.Loading -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                is NotificationsUiState.Unauthenticated -> {
                    UnauthenticatedContent(
                        onLoginClick = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsUiState.Success -> {
                    NotificationsContent(
                        filter = filter,
                        notifications = state.notifications,
                        onFilterSelected = { viewModel.selectFilter(it) },
                        onNotificationClick = onNotificationClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsContent(
    filter: NotificationFilter,
    notifications: Flow<PagingData<NotificationItem>>,
    onFilterSelected: (NotificationFilter) -> Unit,
    onNotificationClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FilterChipsRow(
            selected = filter,
            onFilterSelected = onFilterSelected,
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            val lazyItems = notifications.collectAsLazyPagingItems()
            when {
                lazyItems.loadState.refresh is LoadState.Error -> {
                    PagingErrorContent(
                        error = (lazyItems.loadState.refresh as LoadState.Error).error,
                        onRetry = { lazyItems.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                lazyItems.loadState.refresh is LoadState.Loading && lazyItems.itemCount == 0 -> {
                    LoadingContent(modifier = Modifier.fillMaxSize())
                }

                lazyItems.itemCount == 0 -> {
                    EmptyContent(modifier = Modifier.fillMaxSize())
                }

                else -> {
                    NotificationList(
                        lazyItems = lazyItems,
                        onNotificationClick = onNotificationClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    selected: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NotificationFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onFilterSelected(filter) },
                label = { Text(text = filterLabel(filter)) },
            )
        }
    }
}

@Composable
private fun filterLabel(filter: NotificationFilter): String =
    when (filter) {
        NotificationFilter.ALL -> stringResource(R.string.notification_filter_all)
        NotificationFilter.PARTICIPATING -> stringResource(R.string.notification_filter_participating)
        NotificationFilter.MENTION -> stringResource(R.string.notification_filter_mention)
    }

@Composable
private fun NotificationList(
    lazyItems: LazyPagingItems<NotificationItem>,
    onNotificationClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = lazyItems.itemCount,
            // key 用 index 兜底：LazyPagingItems.get() 在 key lambda 内调用是反模式
            // （未加载区域访问可能触发崩溃/回收竞争，2026-08-14 真机走查修复）
            key = { index -> index },
        ) { index ->
            val item = lazyItems[index] ?: return@items
            NotificationRow(
                item = item,
                onClick = { handleItemClick(item, onNotificationClick) },
            )
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
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
            Box(
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .size(8.dp)
                        .background(
                            color = if (item.unread) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = CircleShape,
                        ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subjectTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.repoFullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val date = formatDate(item.updatedAt)
                if (date.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reasonLabel(item.reason),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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

/** 点击条目：解析 html_url → 应用内 ParsedUrl 导航（External 不可能出现，防御性忽略） */
private fun handleItemClick(
    item: NotificationItem,
    onNotificationClick: (ParsedUrl) -> Unit,
) {
    val htmlUrl = item.htmlUrl ?: return
    val parsed = GitHubLinkParser.parseUrl(htmlUrl)
    if (parsed !is ParsedUrl.External) {
        onNotificationClick(parsed)
    }
}

/** ISO-8601 时间戳 → 本地日期（yyyy-MM-dd）；解析失败返回空串（UI 隐藏时间行） */
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.notification_require_login),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onLoginClick) {
                Text(text = stringResource(R.string.notification_login))
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
            text = stringResource(R.string.notification_empty),
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
    val errorType =
        when {
            error is retrofit2.HttpException && (error.code() == 401 || error.code() == 403) -> NotificationsErrorType.UNAUTHORIZED
            error is IOException -> NotificationsErrorType.NETWORK
            else -> NotificationsErrorType.UNKNOWN
        }
    ErrorContent(errorType = errorType, onRetry = onRetry, modifier = modifier)
}

@Composable
private fun ErrorContent(
    errorType: NotificationsErrorType,
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
                Text(text = stringResource(R.string.notification_retry))
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: NotificationsErrorType): String =
    when (errorType) {
        NotificationsErrorType.NETWORK -> stringResource(R.string.notification_error_network)
        NotificationsErrorType.UNAUTHORIZED -> stringResource(R.string.notification_error_unauthorized)
        NotificationsErrorType.UNKNOWN -> stringResource(R.string.notification_error_unknown)
    }

/** 通知原因 → 本地化文案（GitHub reason 枚举全覆盖 + 未知兜底） */
@Composable
private fun reasonLabel(reason: String): String =
    when (reason) {
        "mention" -> stringResource(R.string.notification_reason_mention)
        "assign" -> stringResource(R.string.notification_reason_assign)
        "author" -> stringResource(R.string.notification_reason_author)
        "comment" -> stringResource(R.string.notification_reason_comment)
        "ci_activity" -> stringResource(R.string.notification_reason_ci_activity)
        "invitation" -> stringResource(R.string.notification_reason_invitation)
        "manual" -> stringResource(R.string.notification_reason_manual)
        "member" -> stringResource(R.string.notification_reason_member)
        "review_requested" -> stringResource(R.string.notification_reason_review_requested)
        "security_alert" -> stringResource(R.string.notification_reason_security_alert)
        "state_change" -> stringResource(R.string.notification_reason_state_change)
        "subscribed" -> stringResource(R.string.notification_reason_subscribed)
        "team_mention" -> stringResource(R.string.notification_reason_team_mention)
        else -> stringResource(R.string.notification_reason_unknown)
    }
