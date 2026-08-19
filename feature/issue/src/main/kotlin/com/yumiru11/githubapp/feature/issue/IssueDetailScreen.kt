package com.yumiru11.githubapp.feature.issue

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.util.relativeTimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Issue 详情页（T13）：header + 正文 + 时间线（评论/事件）+ TopAppBar 更多菜单（分享/浏览器/复制链接）。
 *
 * 状态由 [IssueDetailViewModel] 驱动；Issue 正文经 [WebViewMarkdownRenderer]（离线 GFM）渲染，
 * 评论正文保持 [MarkdownViewer] 原生渲染，事件以单行次要文本呈现。
 * 所有文案经 stringResource 本地化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    owner: String,
    repo: String,
    number: Int,
    onBackClick: () -> Unit,
    onInternalLink: (ParsedUrl) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentUrl = (uiState as? IssueDetailUiState.Success)?.issue?.htmlUrl

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = "$owner/$repo #$number") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.issue_back),
                        )
                    }
                },
                actions = {
                    if (currentUrl != null) {
                        IssueMoreMenu(
                            url = currentUrl,
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                        )
                    }
                },
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
                is IssueDetailUiState.Loading -> {
                    IssueLoadingContent(modifier = Modifier.fillMaxSize())
                }

                is IssueDetailUiState.Error -> {
                    IssueErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is IssueDetailUiState.Success -> {
                    SuccessContent(
                        issue = state.issue,
                        timeline = state.timeline,
                        onInternalLink = onInternalLink,
                        baseRepoUrl = "https://github.com/$owner/$repo",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** TopAppBar 更多菜单：分享 / 浏览器打开 / 复制链接（复制成功后 Snackbar 反馈） */
@Composable
private fun IssueMoreMenu(
    url: String,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.issue_menu_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.issue_share)) },
                onClick = {
                    expanded = false
                    shareUrl(context, url)
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.issue_open_in_browser)) },
                onClick = {
                    expanded = false
                    openInBrowser(context, url)
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.issue_copy_link)) },
                onClick = {
                    expanded = false
                    copyLink(context, url)
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.issue_link_copied)) }
                },
            )
        }
    }
}

@Composable
private fun SuccessContent(
    issue: Issue,
    timeline: List<IssueTimelineItem>,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            IssueHeader(
                issue = issue,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!issue.body.isNullOrBlank()) {
            item(key = "body") {
                WebViewMarkdownRenderer(
                    sanitizedHtml = issue.body,
                    tokenProvider = { null },
                    bridgeCallback = createIssueBridgeCallback(onInternalLink),
                    baseRepoUrl = baseRepoUrl,
                    // Issue 无服务端 HTML API → 离线 GFM + 融合样式（WebView 内 markdown-it 渲染）
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (timeline.isEmpty()) {
            item(key = "timeline_empty") {
                Text(
                    text = stringResource(R.string.issue_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = timeline, key = { it.id.toString() }) { item ->
                when (item) {
                    is IssueTimelineItem.Comment -> CommentItem(item = item, onInternalLink = onInternalLink, baseRepoUrl = baseRepoUrl)
                    is IssueTimelineItem.Event -> EventItem(event = item)
                }
            }
        }
    }
}

/** WebView 正文 bridge：内部链接 → 应用内导航；外部链接 → 浏览器；纯锚点忽略。 */
@Suppress("EmptyFunctionBlock") // onCodeCopy/onImageClick/onCheckboxClick/onHeightChanged 为预留/T14 占位
@Composable
private fun createIssueBridgeCallback(onInternalLink: (ParsedUrl) -> Unit): MarkdownBridgeCallback {
    val context = LocalContext.current
    return object : MarkdownBridgeCallback {
        override fun onExternalLink(url: String) {
            // 纯锚点（#xxx）由页面自身处理，不拦截
            if (url.startsWith("#")) return
            openInBrowser(context, url)
        }

        override fun onInternalLink(parsed: ParsedUrl) {
            onInternalLink(parsed)
        }

        override fun onCodeCopy(code: String) {}

        override fun onImageClick(src: String) {}

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {}

        override fun onHeightChanged(heightPx: Int) {}
    }
}

@Composable
private fun IssueHeader(
    issue: Issue,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        StatusChip(state = issue.state)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = issue.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val author = issue.author?.login.orEmpty()
        val relativeTime = issue.createdAt?.let { relativeTimeText(it) }
        Text(
            text =
                if (relativeTime != null) {
                    stringResource(R.string.issue_author_opened, author, relativeTime)
                } else {
                    stringResource(R.string.issue_author, author)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (issue.labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                issue.labels.forEach { LabelChip(label = it) }
            }
        }
        if (issue.assignees.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AssigneeRow(assignees = issue.assignees)
        }
        if (issue.milestone != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = issue.milestone.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (issue.reactions.totalCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.issue_reactions, issue.reactions.totalCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Open/Closed 状态徽标（tonal 容器） */
@Composable
private fun StatusChip(state: IssueState) {
    val text =
        when (state) {
            IssueState.OPEN -> stringResource(R.string.issue_state_open)
            IssueState.CLOSED -> stringResource(R.string.issue_state_closed)
        }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** 标签徽标：取 label 色（低饱和混白），无则 surfaceVariant */
@Composable
private fun LabelChip(label: IssueLabel) {
    val defaultContainer = MaterialTheme.colorScheme.surfaceVariant
    val container =
        labelColor(label.color)?.let { lerp(it, Color.White, 0.45f) } ?: defaultContainer
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = label.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Assignee 行：头像 + login */
@Composable
private fun AssigneeRow(assignees: List<IssueUser>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        assignees.forEach { user ->
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.login,
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = user.login,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

/** 评论条目：列表项风格（非重卡片），作者头像 + 登录名 + 相对时间 + Markdown 正文 */
@Composable
private fun CommentItem(
    item: IssueTimelineItem.Comment,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = item.author?.avatarUrl,
                    contentDescription = item.author?.login,
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = item.author?.login.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    item.createdAt?.let {
                        Text(
                            text = relativeTimeText(it) ?: it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!item.body.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                MarkdownViewer(
                    markdown = item.body,
                    onInternalLink = onInternalLink,
                    baseRepoUrl = baseRepoUrl,
                    scrollable = false,
                )
            }
        }
    }
}

/** 事件项：按类型分发——交叉引用/关联 PR 独立成项，其余走通用事件文本 */
@Composable
private fun EventItem(event: IssueTimelineItem.Event) {
    when (event.type) {
        IssueTimelineEventType.CROSS_REFERENCED -> {
            CrossReferenceItem(event)
        }

        IssueTimelineEventType.CONNECTED, IssueTimelineEventType.LINKED -> {
            LinkedPrItem(event)
        }

        else -> {
            EventTextItem(event)
        }
    }
}

/** 通用事件文本（closed/reopened/labeled/unlabeled/assigned/locked/unknown…） */
@Composable
private fun EventTextItem(event: IssueTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        when (event.type) {
            IssueTimelineEventType.CLOSED -> {
                stringResource(R.string.issue_event_closed, actor)
            }

            IssueTimelineEventType.REOPENED -> {
                stringResource(R.string.issue_event_reopened, actor)
            }

            IssueTimelineEventType.LABELED -> {
                stringResource(R.string.issue_event_labeled, actor)
            }

            IssueTimelineEventType.UNLABELED -> {
                stringResource(R.string.issue_event_unlabeled, actor)
            }

            IssueTimelineEventType.ASSIGNED -> {
                stringResource(R.string.issue_event_assigned, actor)
            }

            IssueTimelineEventType.LOCKED -> {
                stringResource(R.string.issue_event_locked, actor)
            }

            IssueTimelineEventType.COMMENTED, IssueTimelineEventType.UNKNOWN -> {
                stringResource(R.string.issue_event_default, actor, event.type.name.lowercase(Locale.ROOT))
            }

            // 由 EventItem 分发到 CrossReferenceItem/LinkedPrItem，此处不可达
            IssueTimelineEventType.CROSS_REFERENCED,
            IssueTimelineEventType.CONNECTED,
            IssueTimelineEventType.LINKED,
            -> {
                ""
            }
        }
    EventText(text = text)
}

/** 交叉引用项：mention 到其他 issue */
@Composable
private fun CrossReferenceItem(event: IssueTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        event.sourceIssue?.let { stringResource(R.string.issue_event_cross_referenced_number, actor, it.number) }
            ?: stringResource(R.string.issue_event_cross_referenced, actor)
    EventText(text = text)
}

/** 关联 PR 项：connected/linked */
@Composable
private fun LinkedPrItem(event: IssueTimelineItem.Event) {
    val actor = event.actor?.login.orEmpty()
    val text =
        event.linkedPullRequest?.let { stringResource(R.string.issue_event_linked_number, actor, it.number) }
            ?: stringResource(R.string.issue_event_linked, actor)
    EventText(text = text)
}

/** 事件文本统一样式 */
@Composable
private fun EventText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** GitHub 标签 hex（RRGGBB）→ Color；解析失败返回 null（走 surfaceVariant 兜底） */
private fun labelColor(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return runCatching {
        val rgb = hex.toLong(16) or FIXED_ALPHA_MASK
        Color(rgb)
    }.getOrNull()
}

private fun shareUrl(
    context: Context,
    url: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
    context.startActivity(Intent.createChooser(send, null))
}

private fun openInBrowser(
    context: Context,
    url: String,
) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // 无浏览器可处理时静默忽略
        Log.i(TAG, "No browser available to open: $url", e)
    }
}

private fun copyLink(
    context: Context,
    url: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, url))
}

private const val FIXED_ALPHA_MASK = 0xFF000000L
private const val TAG = "IssueDetailScreen"
