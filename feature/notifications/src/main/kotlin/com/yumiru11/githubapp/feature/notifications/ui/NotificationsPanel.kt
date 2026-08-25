@file:Suppress("LongParameterList")
// - LongParameterList：面板内容装配的组合回调天然多（关闭/筛选/已读/删除/导航/登录），
//   与 AppNavHost 宿主注入槽位同构；拆散反损可读性，精准抑制。

package com.yumiru11.githubapp.feature.notifications.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yumiru11.githubapp.core.designsystem.component.AppEmptyState
import com.yumiru11.githubapp.core.designsystem.component.AppErrorState
import com.yumiru11.githubapp.core.designsystem.component.AppLoadingState
import com.yumiru11.githubapp.core.designsystem.component.GlassSurface
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.navigation.link.GitHubLinkParser
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.notifications.NotificationsErrorType
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelUiState
import com.yumiru11.githubapp.feature.notifications.NotificationsPanelViewModel
import com.yumiru11.githubapp.feature.notifications.R
import com.yumiru11.githubapp.feature.notifications.model.NotificationFilter
import com.yumiru11.githubapp.feature.notifications.model.NotificationGroup
import com.yumiru11.githubapp.feature.notifications.model.NotificationItem
import java.time.Instant
import java.time.ZoneId

/** 面板滑入时长基准（§3.4「300ms 左右」，经 [AppMotion.scaledDuration] 受动效缩放约束） */
private const val PANEL_ENTER_MILLIS = 300

/** 面板滑出时长（快进慢出节奏，与页面退出 200ms 一致） */
private const val PANEL_EXIT_MILLIS = 200

/** 遮罩浓度（§3.4 拍板「中浅遮罩约 50%」） */
private const val PANEL_SCRIM_ALPHA = 0.5f

/**
 * 通知面板完整形态（#88，docs/ui-design.md §3.4）：顶栏铃铛触发的右侧滑入全屏面板。
 *
 * - 结构：中浅遮罩（拦截背后交互、点击关闭）+ 全屏玻璃面板（[GlassSurface] backdrop blur）
 * - 动效：右侧 slide + fade（Emphasized 族 ~300ms，动效缩放感知）；返回键关闭
 * - 内容：按仓库分组折叠列表、右滑标已读回弹、左滑 done 删除、筛选 chips、三态占位
 */
@Composable
fun NotificationsPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onNotificationClick: (ParsedUrl) -> Unit,
    blurEnabled: Boolean,
    modifier: Modifier = Modifier,
    viewModel: NotificationsPanelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    BackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(
        visible = visible,
        enter =
            slideInHorizontally(
                animationSpec =
                    tween(
                        AppMotion.scaledDuration(PANEL_ENTER_MILLIS),
                        easing = AppMotion.EmphasizedDecelerate,
                    ),
                initialOffsetX = { it },
            ) + fadeIn(tween(AppMotion.scaledDuration(PANEL_ENTER_MILLIS))),
        exit =
            slideOutHorizontally(
                animationSpec =
                    tween(
                        AppMotion.scaledDuration(PANEL_EXIT_MILLIS),
                        easing = AppMotion.EmphasizedAccelerate,
                    ),
                targetOffsetX = { it },
            ) + fadeOut(tween(AppMotion.scaledDuration(PANEL_EXIT_MILLIS))),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 遮罩：点击关闭；背后不可交互为 §3.4 拍板
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = PANEL_SCRIM_ALPHA))
                        .clickable(interactionSource = null, indication = null) { onDismiss() },
            )
            GlassSurface(
                modifier = Modifier.fillMaxSize(),
                windowInsets = WindowInsets.systemBars,
                blurEnabled = blurEnabled,
            ) {
                NotificationsPanelContent(
                    uiState = uiState,
                    filter = filter,
                    onDismiss = onDismiss,
                    onMarkAllRead = viewModel::markAllRead,
                    onSelectFilter = viewModel::selectFilter,
                    onToggleGroup = viewModel::toggleGroup,
                    onMarkRead = viewModel::markRead,
                    onDelete = viewModel::delete,
                    onRetry = viewModel::retry,
                    onLoginClick = onLoginClick,
                    onNotificationClick = onNotificationClick,
                )
            }
        }
    }
}

/**
 * 面板内容（无动画包装与遮罩，供截图测试直接使用）。
 * 标题栏（通知 | 筛选 | 全部已读 | 关闭）+ 可展开筛选 chips + 状态分支内容。
 *
 * LongMethod 抑制原因：标题白字为用户拍板的一行色值补充后恰超 80 行阈值
 * （精准抑制，同 RepoFilesViewModel 先例）。
 */
@Suppress("LongMethod")
@Composable
fun NotificationsPanelContent(
    uiState: NotificationsPanelUiState,
    filter: NotificationFilter,
    onDismiss: () -> Unit,
    onMarkAllRead: () -> Unit,
    onSelectFilter: (NotificationFilter) -> Unit,
    onToggleGroup: (String) -> Unit,
    onMarkRead: (NotificationItem) -> Unit,
    onDelete: (NotificationItem) -> Unit,
    onRetry: () -> Unit,
    onLoginClick: () -> Unit,
    onNotificationClick: (ParsedUrl) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersExpanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.notification_title),
                style = MaterialTheme.typography.headlineSmall,
                // 用户拍板（2026-08-25 真机反馈）：面板玻璃压深色壁纸，标题固定白色保证可读
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                Text(text = stringResource(R.string.notification_filter))
            }
            TextButton(
                onClick = onMarkAllRead,
                enabled = uiState is NotificationsPanelUiState.Success,
            ) {
                Text(text = stringResource(R.string.notification_mark_all_read))
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.notification_close),
                )
            }
        }
        AnimatedVisibility(visible = filtersExpanded) {
            FilterChipsRow(selected = filter, onFilterSelected = onSelectFilter)
        }
        Box(modifier = Modifier.weight(1f)) {
            when (val state = uiState) {
                NotificationsPanelUiState.Loading -> {
                    AppLoadingState(modifier = Modifier.fillMaxSize())
                }

                NotificationsPanelUiState.Unauthenticated -> {
                    AppEmptyState(
                        icon = AppDevOcticons.Eye,
                        title = stringResource(R.string.notification_require_login),
                        actionLabel = stringResource(R.string.notification_login),
                        onAction = onLoginClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsPanelUiState.Error -> {
                    AppErrorState(
                        title = errorMessage(state.errorType),
                        actionLabel = stringResource(R.string.notification_retry),
                        onAction = onRetry,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is NotificationsPanelUiState.Success -> {
                    if (state.groups.isEmpty()) {
                        AppEmptyState(
                            icon = AppDevOcticons.Check,
                            title = stringResource(R.string.notification_empty),
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        NotificationGroupedList(
                            state = state,
                            onToggleGroup = onToggleGroup,
                            onMarkRead = onMarkRead,
                            onDelete = onDelete,
                            onNotificationClick = onNotificationClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationGroupedList(
    state: NotificationsPanelUiState.Success,
    onToggleGroup: (String) -> Unit,
    onMarkRead: (NotificationItem) -> Unit,
    onDelete: (NotificationItem) -> Unit,
    onNotificationClick: (ParsedUrl) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 分组折叠 = 跳过该组行条目；组头/行的 animateItem 统一承载补位动画
        // （lazy 布局下 animateItem 是折叠/删除/已读重排的正确工具，替代静态容器的 animateContentSize）
        state.groups.forEach { group ->
            val collapsed = group.repoFullName in state.collapsedRepos
            item(key = "h_" + group.repoFullName, contentType = "header") {
                GroupHeader(
                    group = group,
                    collapsed = collapsed,
                    onToggle = { onToggleGroup(group.repoFullName) },
                    modifier = Modifier.animateItem(),
                )
            }
            if (!collapsed) {
                items(items = group.items, key = { it.id }, contentType = { "row" }) { entry ->
                    SwipeableNotificationRow(
                        item = entry,
                        onMarkRead = onMarkRead,
                        onDelete = onDelete,
                        onClick = { handleItemClick(entry, onNotificationClick) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** 组头：展开箭头（旋转动画）+ 仓库名 + 未读数角标；整行可点切换折叠 */
@Composable
private fun GroupHeader(
    group: NotificationGroup,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        label = "groupChevron",
    )
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = stringResource(R.string.notification_toggle_group),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = group.repoFullName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (group.unreadCount > 0) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.notification_unread_count, group.unreadCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * 左右滑操作行（M3 [SwipeToDismissBox]）：
 * 右滑（startToEnd）标已读——confirm 返回 false 复位回弹（§3.4「reset 回弹」）；
 * 左滑（endToStart）标 done 删除——confirm 放行后由列表移除 + animateItem 补位。
 * 已读行禁用右滑（无可标记项）。背景随滑动进度渐变（primary 系 / error 系）。
 */
@Composable
private fun SwipeableNotificationRow(
    item: NotificationItem,
    onMarkRead: (NotificationItem) -> Unit,
    onDelete: (NotificationItem) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // dismissState 的 confirmValueChange 闭包按首帧捕获参数，行数据乐观更新后须读最新值
    val currentItem by rememberUpdatedState(item)
    val currentOnMarkRead by rememberUpdatedState(onMarkRead)
    val currentOnDelete by rememberUpdatedState(onDelete)

    // M3 新版弃用 confirmValueChange（建议改 anchors 方案），但「右滑标已读→复位回弹」
    // 正是否决式回调的拍板语义（§3.4），anchors 无法表达动作触发；保留并抑制。
    @Suppress("DEPRECATION")
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        if (currentItem.unread) currentOnMarkRead(currentItem)
                        false // 标已读后回弹复位（行保留展示已读态）
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        currentOnDelete(currentItem)
                        true
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        false
                    }
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromStartToEnd = currentItem.unread,
        enableDismissFromEndToStart = true,
        backgroundContent = { DismissBackground(dismissState.targetValue, dismissState.progress) },
    ) {
        NotificationCard(item = currentItem, onClick = onClick)
    }
}

/** 滑动背景：方向定色系、progress 定深浅（§3.4「背景随 progress 渐变」） */
@Composable
private fun DismissBackground(
    target: SwipeToDismissBoxValue,
    progress: Float,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val backgroundColor =
        when (target) {
            SwipeToDismissBoxValue.StartToEnd -> {
                lerp(baseColor, MaterialTheme.colorScheme.primaryContainer, fraction)
            }

            else -> {
                lerp(baseColor, MaterialTheme.colorScheme.errorContainer, fraction)
            }
        }
    val fromStart = target == SwipeToDismissBoxValue.StartToEnd
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(color = backgroundColor)
                .padding(horizontal = 24.dp),
        contentAlignment = if (fromStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = if (fromStart) Icons.Default.Check else Icons.Default.Delete,
            contentDescription =
                stringResource(
                    if (fromStart) R.string.notification_action_read else R.string.notification_action_delete,
                ),
            tint =
                if (fromStart) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
        )
    }
}

/** 通知卡片：未读左缘 primary 色条（替换 T19 圆点，淡出动画）+ 摘要 + 原因/相对时间 + 事件图标（internal 供预览文件复用） */
@Composable
internal fun NotificationCard(
    item: NotificationItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barColor by animateColorAsState(
        targetValue = if (item.unread) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(AppMotion.DURATION_SMALL_STATE_CHANGE),
        label = "unreadBar",
    )
    val unreadDescription = stringResource(R.string.notification_state_unread)
    val readDescription = stringResource(R.string.notification_state_read)

    Card(
        onClick = onClick,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { stateDescription = if (item.unread) unreadDescription else readDescription },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 未读左缘色条：占满行高，已读淡出（ui-design §3.4）
            Box(
                modifier =
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(color = barColor),
            )
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
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
                    val date = notificationTimestampText(item.updatedAt)
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
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = eventIconFor(item.subjectType),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** subjectType → Octicons 事件图标（Issue/PR/Release/Discussion/CI，未知兜底 Info） */
private fun eventIconFor(subjectType: String): ImageVector =
    when {
        subjectType.equals("Issue", ignoreCase = true) -> AppDevOcticons.IssueOpened

        subjectType.equals("PullRequest", ignoreCase = true) -> AppDevOcticons.PullRequest

        subjectType.equals("Release", ignoreCase = true) -> AppDevOcticons.Tag

        subjectType.equals("Discussion", ignoreCase = true) -> AppDevOcticons.Comment

        subjectType.contains("Check", ignoreCase = true) ||
            subjectType.contains("Workflow", ignoreCase = true) -> AppDevOcticons.Flame

        else -> AppDevOcticons.Info
    }

/** 过滤 chips 行（T19 三态迁移自旧通知页） */
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
        NotificationFilter.entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                onClick = { onFilterSelected(entry) },
                label = { Text(text = filterLabel(entry)) },
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

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: NotificationsErrorType): String =
    when (errorType) {
        NotificationsErrorType.NETWORK -> stringResource(R.string.notification_error_network)
        NotificationsErrorType.UNAUTHORIZED -> stringResource(R.string.notification_error_unauthorized)
        NotificationsErrorType.UNKNOWN -> stringResource(R.string.notification_error_unknown)
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

/** ISO-8601 时间戳 → 本地日期 yyyy-MM-dd（相对时间不可用时的回退；解析失败返回空串隐藏时间行） */
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

/** 通知行时间戳：相对时间优先，回退绝对日期（缺陷 #11 时间显示统一） */
@Composable
private fun notificationTimestampText(isoTimestamp: String?): String =
    isoTimestamp?.let { relativeTimeText(it) } ?: formatDate(isoTimestamp)

/** 通知原因 → 本地化文案（GitHub reason 枚举全覆盖 + 未知兜底；穷尽映射天然多分支） */
@Composable
@Suppress("CyclomaticComplexMethod")
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
