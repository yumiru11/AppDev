@file:Suppress(
    "LongMethod",
    "CyclomaticComplexMethod",
    "TooManyFunctions",
    "LongParameterList",
)
// - LongMethod/CyclomaticComplexMethod：IssueDetailScreen 聚合写操作 UI 装配（对话框/Sheet/事件收集），
//   拆分收益低于装配强内聚（同 MainActivity 先例）
// - TooManyFunctions：文件含 24 个 UI 组件（header/评论/反应/事件分发），均为独立可组合单元
// - LongParameterList：SuccessContent/IssueHeader/CommentItem 为 UI 装配回调透传，默认参数豁免不适用

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Issue 详情页（T13 读 + T14 写）。
 *
 * 写操作（T14）：关闭/重开、编辑标题正文、评论增改删、反应 toggle、任务列表 checkbox
 * 反向同步（WebView bridge → ViewModel）。操作可见性按 viewerPermission 决定
 * （[IssueDetailUiState.Success.canEditIssue]/[canCloseReopen]/[canComment]）。
 * 写失败经事件通道 Snackbar 提示（乐观更新 + 回滚在 ViewModel）。
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
    val context = LocalContext.current
    val currentUrl = (uiState as? IssueDetailUiState.Success)?.issue?.htmlUrl

    var showCommentSheet by remember { mutableStateOf(false) }
    var editingIssue by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<IssueTimelineItem.Comment?>(null) }
    var deletingComment by remember { mutableStateOf<IssueTimelineItem.Comment?>(null) }

    // 写操作事件通道 → Snackbar（ViewModel 只产类型，文案本地化）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IssueDetailEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(context.getString(event.message.toRes()))
                }
            }
        }
    }

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
        floatingActionButton = {
            val state = uiState as? IssueDetailUiState.Success
            if (state?.canComment == true) {
                ExtendedFloatingActionButton(
                    onClick = { showCommentSheet = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Create,
                            contentDescription = null,
                        )
                    },
                    text = { Text(text = stringResource(R.string.issue_comment)) },
                )
            }
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
                        state = state,
                        onInternalLink = onInternalLink,
                        baseRepoUrl = "https://github.com/$owner/$repo",
                        onCloseReopen = { if (state.issue.state == IssueState.OPEN) viewModel.closeIssue() else viewModel.reopenIssue() },
                        onEditIssue = { editingIssue = true },
                        onToggleIssueReaction = viewModel::toggleIssueReaction,
                        onToggleCommentReaction = viewModel::toggleCommentReaction,
                        onEditComment = { editingComment = it },
                        onDeleteComment = { deletingComment = it },
                        onCheckboxClick = viewModel::toggleTaskListItem,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // 评论输入 BottomSheet（ui-design §3.9：评论按钮 → 上滑输入框）
    val state = uiState as? IssueDetailUiState.Success
    if (showCommentSheet && state?.canComment == true) {
        CommentInputSheet(
            onDismiss = { showCommentSheet = false },
            onSubmit = { body ->
                showCommentSheet = false
                viewModel.addComment(body)
            },
        )
    }

    // 编辑 Issue 对话框
    if (editingIssue && state?.canEditIssue == true) {
        EditIssueDialog(
            issue = state.issue,
            onDismiss = { editingIssue = false },
            onSubmit = { title, body ->
                editingIssue = false
                viewModel.updateIssue(title, body)
            },
        )
    }

    // 编辑评论对话框
    editingComment?.let { comment ->
        if (state?.canEditComment(comment) == true) {
            EditCommentDialog(
                comment = comment,
                onDismiss = { editingComment = null },
                onSubmit = { body ->
                    editingComment = null
                    viewModel.updateComment(comment.id, body)
                },
            )
        }
    }

    // 删除评论确认
    deletingComment?.let { comment ->
        if (state?.canEditComment(comment) == true) {
            AlertDialog(
                onDismissRequest = { deletingComment = null },
                title = { Text(text = stringResource(R.string.issue_delete_comment_title)) },
                text = { Text(text = stringResource(R.string.issue_delete_comment_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            deletingComment = null
                            viewModel.deleteComment(comment.id)
                        },
                    ) {
                        Text(text = stringResource(R.string.issue_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deletingComment = null }) {
                        Text(text = stringResource(R.string.issue_cancel))
                    }
                },
            )
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
    state: IssueDetailUiState.Success,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    onCloseReopen: () -> Unit,
    onEditIssue: () -> Unit,
    onToggleIssueReaction: (String) -> Unit,
    onToggleCommentReaction: (Long, String) -> Unit,
    onEditComment: (IssueTimelineItem.Comment) -> Unit,
    onDeleteComment: (IssueTimelineItem.Comment) -> Unit,
    onCheckboxClick: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val issue = state.issue
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            IssueHeader(
                issue = issue,
                canCloseReopen = state.canCloseReopen,
                canEditIssue = state.canEditIssue,
                canReact = state.canComment,
                myReactions = state.myReactions[issue.id].orEmpty(),
                onCloseReopen = onCloseReopen,
                onEditIssue = onEditIssue,
                onToggleReaction = onToggleIssueReaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!issue.body.isNullOrBlank()) {
            item(key = "body") {
                WebViewMarkdownRenderer(
                    sanitizedHtml = issue.body,
                    tokenProvider = { null },
                    bridgeCallback = createIssueBridgeCallback(onInternalLink, onCheckboxClick),
                    baseRepoUrl = baseRepoUrl,
                    // Issue 无服务端 HTML API → 离线 GFM + 融合样式（WebView 内 markdown-it 渲染）
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.timeline.isEmpty()) {
            item(key = "timeline_empty") {
                Text(
                    text = stringResource(R.string.issue_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(items = state.timeline, key = { it.id.toString() }) { item ->
                when (item) {
                    is IssueTimelineItem.Comment -> {
                        CommentItem(
                            item = item,
                            canReact = state.canComment,
                            canEdit = state.canEditComment(item),
                            myReactions = state.myReactions[item.id].orEmpty(),
                            onInternalLink = onInternalLink,
                            baseRepoUrl = baseRepoUrl,
                            onToggleReaction = { content -> onToggleCommentReaction(item.id, content) },
                            onEdit = { onEditComment(item) },
                            onDelete = { onDeleteComment(item) },
                        )
                    }

                    is IssueTimelineItem.Event -> {
                        EventItem(event = item)
                    }
                }
            }
        }
    }
}

/** WebView 正文 bridge：内部链接 → 应用内导航；外部链接 → 浏览器；checkbox → 任务列表反向同步。 */
@Suppress("EmptyFunctionBlock") // onCodeCopy/onImageClick/onHeightChanged 为预留占位
@Composable
private fun createIssueBridgeCallback(
    onInternalLink: (ParsedUrl) -> Unit,
    onCheckboxClick: (Int, Boolean) -> Unit,
): MarkdownBridgeCallback {
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
        ) {
            onCheckboxClick(index, checked)
        }

        override fun onHeightChanged(heightPx: Int) {}
    }
}

@Composable
private fun IssueHeader(
    issue: Issue,
    canCloseReopen: Boolean,
    canEditIssue: Boolean,
    canReact: Boolean,
    myReactions: Map<String, Long>,
    onCloseReopen: () -> Unit,
    onEditIssue: () -> Unit,
    onToggleReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(state = issue.state)
            if (canCloseReopen) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onCloseReopen) {
                    Text(
                        text =
                            if (issue.state == IssueState.OPEN) {
                                stringResource(R.string.issue_close)
                            } else {
                                stringResource(R.string.issue_reopen)
                            },
                    )
                }
            }
            if (canEditIssue) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onEditIssue) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.issue_edit))
                }
            }
        }
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
        if (canReact) {
            Spacer(modifier = Modifier.height(8.dp))
            ReactionBar(
                reactions = issue.reactions,
                myReactions = myReactions,
                onToggle = onToggleReaction,
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

/**
 * 反应条：8 种 GitHub 反应（+1/-1/laugh/hooray/confused/heart/rocket/eyes）文本 chip + 计数。
 * 已反应（viewer 添加过）→ primary 容器；点击 toggle 增删。
 */
@Composable
private fun ReactionBar(
    reactions: IssueReactions,
    myReactions: Map<String, Long>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = REACTION_CONTENTS.filter { reactions.counts[it] ?: 0 > 0 || myReactions.containsKey(it) }
    if (visible.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { content ->
            val count = reactions.counts[content] ?: 0
            val reacted = myReactions.containsKey(content)
            Surface(
                shape = MaterialTheme.shapes.small,
                color =
                    if (reacted) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                onClick = { onToggle(content) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = content,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                            if (reacted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (reacted) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }
    }
}

/** 评论条目：列表项风格（非重卡片），作者头像 + 登录名 + 相对时间 + Markdown 正文 + 反应/编辑删除 */
@Composable
private fun CommentItem(
    item: IssueTimelineItem.Comment,
    canReact: Boolean,
    canEdit: Boolean,
    myReactions: Map<String, Long>,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    onToggleReaction: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
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
                Column(modifier = Modifier.weight(1f)) {
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
                if (canEdit) {
                    CommentMenu(onEdit = onEdit, onDelete = onDelete)
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
            if (canReact) {
                Spacer(modifier = Modifier.height(8.dp))
                ReactionBar(
                    reactions = item.reactions,
                    myReactions = myReactions,
                    onToggle = onToggleReaction,
                )
            }
        }
    }
}

/** 评论操作菜单（编辑/删除，仅评论作者可见） */
@Composable
private fun CommentMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.issue_comment_menu),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.issue_edit)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.issue_delete)) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** 评论输入 BottomSheet（ui-design §3.9：圆角 + 输入区 + 提交） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentInputSheet(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var body by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
                    .imePadding(),
        ) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(text = stringResource(R.string.issue_comment_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onSubmit(body) },
                enabled = body.isNotBlank(),
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = stringResource(R.string.issue_comment_submit))
            }
        }
    }
}

/** 编辑 Issue 对话框（标题 + 正文） */
@Composable
private fun EditIssueDialog(
    issue: Issue,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(issue.title) }
    var body by remember { mutableStateOf(issue.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.issue_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(R.string.issue_edit_title_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(text = stringResource(R.string.issue_edit_body_label)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(title, body) },
                enabled = title.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.issue_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.issue_cancel))
            }
        },
    )
}

/** 编辑评论对话框 */
@Composable
private fun EditCommentDialog(
    comment: IssueTimelineItem.Comment,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var body by remember { mutableStateOf(comment.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.issue_edit_comment_title)) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(body) },
                enabled = body.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.issue_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.issue_cancel))
            }
        },
    )
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

/** [IssueSnackbarMessage] → 字符串资源 id（ViewModel 不产文案，UI 层本地化） */
internal fun IssueSnackbarMessage.toRes(): Int =
    when (this) {
        IssueSnackbarMessage.COMMENT_ADDED -> R.string.issue_comment_added
        IssueSnackbarMessage.COMMENT_UPDATED -> R.string.issue_comment_updated
        IssueSnackbarMessage.COMMENT_DELETED -> R.string.issue_comment_deleted
        IssueSnackbarMessage.REACTION_ADDED -> R.string.issue_reaction_added
        IssueSnackbarMessage.REACTION_REMOVED -> R.string.issue_reaction_removed
        IssueSnackbarMessage.ISSUE_CLOSED -> R.string.issue_closed_snackbar
        IssueSnackbarMessage.ISSUE_REOPENED -> R.string.issue_reopened_snackbar
        IssueSnackbarMessage.ISSUE_UPDATED -> R.string.issue_updated
        IssueSnackbarMessage.TASK_LIST_UPDATED -> R.string.issue_task_list_updated
        IssueSnackbarMessage.ERROR_NETWORK -> R.string.issue_error_network
        IssueSnackbarMessage.ERROR_FORBIDDEN -> R.string.issue_error_forbidden
        IssueSnackbarMessage.ERROR_NOT_FOUND -> R.string.issue_error_not_found
        IssueSnackbarMessage.ERROR_VALIDATION -> R.string.issue_error_validation
        IssueSnackbarMessage.ERROR_UNKNOWN -> R.string.issue_error_unknown
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

/** GitHub 反应类型（REST content 取值，顺序与 GitHub 一致） */
private val REACTION_CONTENTS = listOf("+1", "-1", "laugh", "hooray", "confused", "heart", "rocket", "eyes")

private const val FIXED_ALPHA_MASK = 0xFF000000L
private const val TAG = "IssueDetailScreen"
