package com.yumiru11.githubapp.feature.notifications.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.designsystem.component.AppChevron
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppFilterChipRow
import com.yumiru11.githubapp.core.designsystem.component.AppIconButton
import com.yumiru11.githubapp.core.designsystem.component.AppListSkeleton
import com.yumiru11.githubapp.core.designsystem.component.AppScaffold
import com.yumiru11.githubapp.core.designsystem.component.AppSectionHeader
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelUiState
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelViewModel
import com.yumiru11.githubapp.feature.notifications.R
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import com.yumiru11.githubapp.feature.notifications.model.NotificationSortOrder

/**
 * 通知整页（2026-10 UI 重构：由「顶栏铃铛 → 右侧滑入面板」改为**导航整页**）。
 *
 * 为什么改：面板形态在玻璃降级路径上要额外处理"下层透印"，且与其余页面（返回键/手势返回、
 * 顶栏语言、滚动避让）不同构；整页后与仓库/议题等页面同构，交互动效也统一（导航过渡）。
 *
 * 复用既有能力（逻辑零改动）：[NotificationsPanelViewModel] 的过滤/排序/乐观已读/删除回滚；
 * 行组件与手势（右滑标已读、左滑删除）沿用面板内的 [SwipeableNotificationRow]
 * （同文件内 internal 复用，避免第二套手势实现漂移）。
 */
@Composable
fun NotificationsScreen(
    onBackClick: () -> Unit,
    onNotificationClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
    onLoginClick: () -> Unit = {},
    viewModel: NotificationsPanelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    BackHandler { onBackClick() }

    AppScaffold(
        modifier = modifier,
        topBar = {
            NotificationsTopBar(
                onBackClick = onBackClick,
                onMarkAllRead = viewModel::markAllRead,
                sortOrder = sortOrder,
                onToggleSortOrder = {
                    viewModel.selectSortOrder(
                        if (sortOrder == NotificationSortOrder.NEWEST_FIRST) {
                            NotificationSortOrder.OLDEST_FIRST
                        } else {
                            NotificationSortOrder.NEWEST_FIRST
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            NotificationsFilterRow(filter = filter, onSelectFilter = viewModel::selectFilter)

            when (val state = uiState) {
                is NotificationsPanelUiState.Loading -> {
                    AppListSkeleton(rows = 6, rowHeight = 84.dp)
                }

                is NotificationsPanelUiState.Unauthenticated -> {
                    AppEmptyState(
                        icon = AppIcons.Lock.rounded,
                        title = stringResource(R.string.notification_require_login),
                        actionLabel = stringResource(R.string.notification_login),
                        onAction = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsPanelUiState.Error -> {
                    AppErrorState(
                        title = stringResource(R.string.notification_error_network),
                        actionLabel = stringResource(R.string.notification_retry),
                        onAction = viewModel::retry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsPanelUiState.Success -> {
                    if (state.groups.isEmpty()) {
                        AppEmptyState(
                            icon = AppIcons.Notifications.rounded,
                            title = stringResource(R.string.notification_empty),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        NotificationsGroupedList(
                            groups = state.groups,
                            collapsedRepos = state.collapsedRepos,
                            onToggleGroup = viewModel::toggleGroup,
                            onMarkRead = viewModel::markRead,
                            onDelete = viewModel::delete,
                            onItemClick = { item -> handleItemClick(item, onNotificationClick) },
                        )
                    }
                }
            }
        }
    }
}

/** 过滤 chip 行（全部 / 参与 / 提及）：文案与选中态映射集中在此，页面主体只留结构。 */
@Composable
private fun NotificationsFilterRow(
    filter: NotificationFilter,
    onSelectFilter: (NotificationFilter) -> Unit,
) {
    AppFilterChipRow(
        options =
            NotificationFilter.entries.map { entry ->
                when (entry) {
                    NotificationFilter.ALL -> stringResource(R.string.notification_filter_all)
                    NotificationFilter.PARTICIPATING -> stringResource(R.string.notification_filter_participating)
                    NotificationFilter.MENTION -> stringResource(R.string.notification_filter_mention)
                }
            },
        selectedIndex = NotificationFilter.entries.indexOf(filter).coerceAtLeast(0),
        onSelect = { index -> onSelectFilter(NotificationFilter.entries[index]) },
        contentPadding = PaddingValues(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.s),
    )
}

/** 页顶栏：返回 + 标题 + 排序切换 + 全部已读（与仓库页顶栏同一语言，不做玻璃，避免遮挡列表）。 */
@Composable
private fun NotificationsTopBar(
    onBackClick: () -> Unit,
    onMarkAllRead: () -> Unit,
    sortOrder: NotificationSortOrder,
    onToggleSortOrder: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.spacing.s,
                    end = AppDimens.spacing.s,
                    top = AppDimens.spacing.s,
                    bottom = AppDimens.spacing.xs,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(
            icon = AppIcons.ArrowBack.rounded,
            contentDescription = stringResource(R.string.notification_back),
            onClick = onBackClick,
        )
        Spacer(Modifier.width(AppDimens.spacing.xs))
        Text(
            text = stringResource(R.string.notification_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggleSortOrder) {
            Text(
                text =
                    stringResource(
                        if (sortOrder == NotificationSortOrder.NEWEST_FIRST) {
                            R.string.notification_sort_newest
                        } else {
                            R.string.notification_sort_oldest
                        },
                    ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        TextButton(onClick = onMarkAllRead) {
            Text(text = stringResource(R.string.notification_mark_all_read), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * 分组列表：仓库标题 + 该仓库下的通知行（M3 Expressive 列表语言：圆形图标徽标 + 两行文本）。
 *
 * 折叠态沿用 VM 的 collapsedRepos（面板时期的语义不变）。
 */
@Composable
private fun NotificationsGroupedList(
    groups: List<com.yumiru11.githubapp.feature.notifications.model.NotificationGroup>,
    collapsedRepos: Set<String>,
    onToggleGroup: (String) -> Unit,
    onMarkRead: (NotificationItem) -> Unit,
    onDelete: (NotificationItem) -> Unit,
    onItemClick: (NotificationItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = AppDimens.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppDimens.listSpacing),
    ) {
        groups.forEach { group ->
            val collapsed = group.repoFullName in collapsedRepos
            item(key = "header-" + group.repoFullName) {
                AppSectionHeader(
                    title = group.repoFullName,
                    subtitle = group.items.size.toString(),
                    action = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text =
                                    stringResource(
                                        if (collapsed) {
                                            R.string.notification_group_expand
                                        } else {
                                            R.string.notification_group_collapse
                                        },
                                    ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            AppChevron()
                        }
                    },
                    modifier = Modifier.clickable { onToggleGroup(group.repoFullName) },
                )
            }
            if (!collapsed) {
                items(group.items, key = { it.id }) { item ->
                    SwipeableNotificationRow(
                        item = item,
                        onMarkRead = onMarkRead,
                        onDelete = onDelete,
                        onClick = { onItemClick(item) },
                        modifier = Modifier.padding(horizontal = AppDimens.spacing.l),
                    )
                }
            }
        }
    }
}
