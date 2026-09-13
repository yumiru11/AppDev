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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.datastore.draft.DraftText
import com.yumiru11.githubapp.core.designsystem.component.AppCenteredLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppStateChip
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.component.GlassSheetSurface
import com.yumiru11.githubapp.core.designsystem.component.labelChipContainerColor
import com.yumiru11.githubapp.core.designsystem.component.labelChipContentColor
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.editor.MarkdownComposer
import com.yumiru11.githubapp.core.editor.MarkdownEditorController
import com.yumiru11.githubapp.core.editor.rememberM3EditorThemeTokens
import com.yumiru11.githubapp.core.markdown.MarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppImageOverlay
import com.yumiru11.githubapp.core.ui.AppSnackbarHost
import com.yumiru11.githubapp.core.ui.appTransientEnterAlpha
import com.yumiru11.githubapp.core.ui.gitHubStatusStateDescription
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.issue.model.Issue
import com.yumiru11.githubapp.feature.issue.model.IssueLabel
import com.yumiru11.githubapp.feature.issue.model.IssueMilestone
import com.yumiru11.githubapp.feature.issue.model.IssueReactions
import com.yumiru11.githubapp.feature.issue.model.IssueState
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineEventType
import com.yumiru11.githubapp.feature.issue.model.IssueTimelineItem
import com.yumiru11.githubapp.feature.issue.model.IssueUser
import com.yumiru11.githubapp.feature.issue.ui.ReactionChip
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
 *
 * #163：HeaderCard 操作区补 Subscribe/Unsubscribe（登录态可见，pending 防连点）与
 * Labels/Assignees/Milestone 编辑 Sheet（TRIAGE+ 可见，Sheet 状态由 ViewModel 的
 * [IssueDetailViewModel.editState] 驱动）。
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
    val resources = LocalResources.current
    val currentUrl = (uiState as? IssueDetailUiState.Success)?.issue?.htmlUrl

    val editState by viewModel.editState.collectAsStateWithLifecycle()
    val commentDraftText by viewModel.commentDraft.text.collectAsStateWithLifecycle()

    var showCommentSheet by remember { mutableStateOf(false) }
    var editingIssue by remember { mutableStateOf(false) }
    var editingComment by remember { mutableStateOf<IssueTimelineItem.Comment?>(null) }
    var deletingComment by remember { mutableStateOf<IssueTimelineItem.Comment?>(null) }

    // 写操作事件通道 → Snackbar（ViewModel 只产类型，文案本地化）
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is IssueDetailEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(resources.getString(event.message.toRes()))
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
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
                    AppCenteredLoadingState(modifier = Modifier.fillMaxSize())
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
                        onEditIssue = {
                            viewModel.openEditIssue(state.issue.body.orEmpty())
                            editingIssue = true
                        },
                        onEditMeta = viewModel::openMetaEditor,
                        onToggleSubscription = viewModel::toggleSubscription,
                        onToggleIssueReaction = viewModel::toggleIssueReaction,
                        onToggleCommentReaction = viewModel::toggleCommentReaction,
                        onEditComment = {
                            viewModel.openEditComment(it.id, it.body.orEmpty())
                            editingComment = it
                        },
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
            text = commentDraftText,
            onTextChange = viewModel.commentDraft::onChanged,
            onDismiss = {
                viewModel.commentDraft.saveNow()
                showCommentSheet = false
            },
            onSubmit = { body ->
                showCommentSheet = false
                viewModel.addComment(body)
            },
        )
    }

    // 编辑 Issue 对话框
    val editIssueDraft = viewModel.editIssueDraft.collectAsStateWithLifecycle().value
    if (editingIssue && state?.canEditIssue == true && editIssueDraft != null) {
        EditIssueDialog(
            issue = state.issue,
            draft = editIssueDraft,
            onDismiss = {
                viewModel.closeEditIssue()
                editingIssue = false
            },
            onSubmit = { title, body ->
                editingIssue = false
                viewModel.updateIssue(title, body)
            },
        )
    }

    // #163 L02：Labels/Assignees/Milestone 编辑 Sheet（状态由 ViewModel 持有，保存成功自动关闭）
    editState?.let { edit ->
        IssueMetaEditSheet(
            state = edit,
            onDismiss = viewModel::dismissMetaEditor,
            onToggleLabel = viewModel::toggleLabelSelection,
            onToggleAssignee = viewModel::toggleAssigneeSelection,
            onSelectMilestone = viewModel::selectMilestone,
            onRetry = viewModel::openMetaEditor,
            onSave = viewModel::saveIssueMeta,
        )
    }

    // 编辑评论对话框
    val editCommentDraft = viewModel.editCommentDraft.collectAsStateWithLifecycle().value
    editingComment?.let { comment ->
        if (state?.canEditComment(comment) == true && editCommentDraft != null) {
            EditCommentDialog(
                draft = editCommentDraft,
                onDismiss = {
                    viewModel.closeEditComment()
                    editingComment = null
                },
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
    val resources = LocalResources.current
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
                    scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.issue_link_copied)) }
                },
            )
        }
    }
}

/**
 * [SuccessContent] 列表 contentPadding（纯函数供数值断言；UI-5）。
 *
 * 评论 FAB 悬浮在列表右下：底部必须预留 [AppDimens.fabContentClearance]（96dp），
 * 否则末条时间线内容滚到底时被 FAB 压住（修复前 bottom 仅 8dp）。
 */
internal fun issueDetailContentPadding(): PaddingValues =
    PaddingValues(
        start = AppDimens.contentPadding,
        end = AppDimens.contentPadding,
        top = 8.dp,
        bottom = AppDimens.fabContentClearance,
    )

@Composable
private fun SuccessContent(
    state: IssueDetailUiState.Success,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    onCloseReopen: () -> Unit,
    onEditIssue: () -> Unit,
    onEditMeta: () -> Unit,
    onToggleSubscription: () -> Unit,
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
        contentPadding = issueDetailContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            IssueHeader(
                issue = issue,
                canCloseReopen = state.canCloseReopen,
                canEditIssue = state.canEditIssue,
                canManageMeta = state.canManageMeta,
                canSubscribe = state.canSubscribe,
                isSubscribed = state.isSubscribed,
                subscriptionPending = state.subscriptionPending,
                canReact = state.canComment,
                myReactions = state.myReactions[issue.id].orEmpty(),
                onCloseReopen = onCloseReopen,
                onEditIssue = onEditIssue,
                onEditMeta = onEditMeta,
                onToggleSubscription = onToggleSubscription,
                onToggleReaction = onToggleIssueReaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (!issue.body.isNullOrBlank()) {
            item(key = "body") {
                IssueBodyWebView(
                    body = issue.body,
                    baseRepoUrl = baseRepoUrl,
                    onInternalLink = onInternalLink,
                    onCheckboxClick = onCheckboxClick,
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

/**
 * Issue 正文 WebView + 图片全屏查看（#166 / UI11）。
 *
 * 单独包一层是为了让"正文渲染 + 图片查看状态"成对出现：状态属于这一个 item，
 * 不必上提到整页 UiState（也就不会因为滚动回收而丢失/泄漏）。
 */
@Composable
private fun IssueBodyWebView(
    body: String,
    baseRepoUrl: String,
    onInternalLink: (ParsedUrl) -> Unit,
    onCheckboxClick: (Int, Boolean) -> Unit,
) {
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    WebViewMarkdownRenderer(
        sanitizedHtml = body,
        tokenProvider = { null },
        bridgeCallback =
            createIssueBridgeCallback(onInternalLink, onCheckboxClick) { previewImageUrl = it },
        baseRepoUrl = baseRepoUrl,
        // Issue 无服务端 HTML API → 离线 GFM + 融合样式（WebView 内 markdown-it 渲染）
        renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
        modifier = Modifier.fillMaxWidth(),
    )
    AppImageOverlay(imageUrl = previewImageUrl, onDismiss = { previewImageUrl = null })
}

/** WebView 正文 bridge：内部链接 → 应用内导航；外部链接 → 浏览器；checkbox → 任务列表反向同步；代码块 → 剪贴板。 */
@Suppress("EmptyFunctionBlock") // onHeightChanged 为预留占位
@Composable
private fun createIssueBridgeCallback(
    onInternalLink: (ParsedUrl) -> Unit,
    onCheckboxClick: (Int, Boolean) -> Unit,
    onImageClick: (String) -> Unit,
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

        override fun onCodeCopy(code: String) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
        }

        override fun onImageClick(src: String) {
            onImageClick(src)
        }

        override fun onCheckboxClick(
            index: Int,
            checked: Boolean,
        ) {
            onCheckboxClick(index, checked)
        }

        override fun onHeightChanged(heightPx: Int) {}

        /** Mermaid 渲染结果：本屏无 CI 断言锚点（README 主路径在 feature:repo 打点），保留空实现。 */
        override fun onMermaidResult(
            rendered: Int,
            failed: Int,
            engineSupported: Boolean,
        ) = Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueHeader(
    issue: Issue,
    canCloseReopen: Boolean,
    canEditIssue: Boolean,
    canManageMeta: Boolean,
    canSubscribe: Boolean,
    isSubscribed: Boolean,
    subscriptionPending: Boolean,
    canReact: Boolean,
    myReactions: Map<String, Long>,
    onCloseReopen: () -> Unit,
    onEditIssue: () -> Unit,
    onEditMeta: () -> Unit,
    onToggleSubscription: () -> Unit,
    onToggleReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // 操作区随权限增减按钮，窄屏自动换行（FlowRow；RTL 由 start/end 语义保证）
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(state = issue.state)
            if (canCloseReopen) {
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
            if (canManageMeta) {
                OutlinedButton(onClick = onEditMeta) {
                    Icon(
                        imageVector = AppDevOcticons.Tag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.issue_edit_meta))
                }
            }
            if (canSubscribe) {
                IssueSubscriptionButton(
                    isSubscribed = isSubscribed,
                    pending = subscriptionPending,
                    onClick = onToggleSubscription,
                )
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

/**
 * 订阅/取消订阅按钮（#163 L01）。
 *
 * 未订阅 → OutlinedButton（与同排操作一致）；已订阅 → FilledTonalButton（状态强调，点击即退订）。
 * [pending] 期间禁用并显示进度圈（防连点），文案同步切换为进行中语义（保持可读文案）。
 */
@Composable
private fun IssueSubscriptionButton(
    isSubscribed: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
) {
    val label =
        stringResource(
            if (isSubscribed) R.string.issue_unsubscribe else R.string.issue_subscribe,
        )
    val content: @Composable RowScope.() -> Unit = {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label)
    }
    if (isSubscribed) {
        FilledTonalButton(
            onClick = onClick,
            enabled = !pending,
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = !pending,
            content = content,
        )
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
    val status =
        when (state) {
            IssueState.OPEN -> GitHubStatus.OPEN
            IssueState.CLOSED -> GitHubStatus.CLOSED
        }
    // #84 audit 缺陷 #4：四态同色 secondaryContainer → AppStateChip 语义色
    // #168 / UI26：补状态播报（TalkBack 读「Open, 该项已开启」）
    AppStateChip(
        status = status,
        label = text,
        stateDescription = gitHubStatusStateDescription(status),
    )
}

/** 标签徽标：label 原色与主题 surface 低饱和混合，无则 surfaceVariant（#85 audit #2/#20） */
@Composable
private fun LabelChip(label: IssueLabel) {
    val container =
        labelColor(label.color)
            ?.let { labelChipContainerColor(labelColor = it, surface = MaterialTheme.colorScheme.surface) }
            ?: MaterialTheme.colorScheme.surfaceVariant
    val contentColor =
        labelChipContentColor(
            container = container,
            onSurface = MaterialTheme.colorScheme.onSurface,
            surface = MaterialTheme.colorScheme.surface,
        )
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = label.name,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
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
 *
 * chip 本体见 [ReactionChip]（issue #168 / UI24：命中区 48dp、视觉不膨胀，触区断言在
 * ReactionChipTouchTargetTest）。
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
    // audit 缺陷 #18（issue #85）：已反应态对 TalkBack 播报状态描述
    val reactedStateText = stringResource(R.string.issue_reaction_reacted)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        visible.forEach { content ->
            ReactionChip(
                content = content,
                count = reactions.counts[content] ?: 0,
                reacted = myReactions.containsKey(content),
                reactedStateText = reactedStateText,
                onToggle = { onToggle(content) },
            )
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

/**
 * Labels / Assignees / Milestone 编辑 Sheet（#163 L02，ui-design §3.9 IssueHeaderCard 三区）。
 *
 * 三区：标签多选 chips（FilterChip）、Assignee 多选行（ListItem + 头像 + Checkbox）、
 * Milestone 单选卡（Surface 卡片 + RadioButton，含「无里程碑」以支持清除）。
 * 加载态/失败态由 [IssueEditUiState.loading]/[errorType] 驱动；保存 pending 时按钮禁用。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IssueMetaEditSheet(
    state: IssueEditUiState,
    onDismiss: () -> Unit,
    onToggleLabel: (String) -> Unit,
    onToggleAssignee: (String) -> Unit,
    onSelectMilestone: (Long?) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // #167 / UI17：BottomSheet 进出内容走 AppMotion 令牌（§4.1 表定 500ms）
        modifier = Modifier.appTransientEnterAlpha(),
    ) {
        // 弹层玻璃点位（#167 / UI22，ui-design §6.1 #4）：容器底交 GlassSheetSurface，开关/主题降级由
        // GlassScope.BOTTOM_SHEET 统一裁决（弹层是独立 window，几何结论见该组件 KDoc）
        GlassSheetSurface {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.issue_meta_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                when {
                    state.loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.issue_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    state.errorType != null -> {
                        Text(
                            text = stringResource(R.string.issue_meta_load_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.issue_retry))
                        }
                    }

                    else -> {
                        MetaSection(title = stringResource(R.string.issue_meta_section_labels)) {
                            if (state.labels.isEmpty()) {
                                MetaEmptyHint()
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    state.labels.forEach { label ->
                                        val selected = label.name in state.selectedLabels
                                        FilterChip(
                                            selected = selected,
                                            onClick = { onToggleLabel(label.name) },
                                            label = { Text(text = label.name) },
                                            leadingIcon =
                                                if (selected) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Filled.Check,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp),
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                        )
                                    }
                                }
                            }
                        }
                        MetaSection(title = stringResource(R.string.issue_meta_section_assignees)) {
                            if (state.assignees.isEmpty()) {
                                MetaEmptyHint()
                            } else {
                                state.assignees.forEach { user ->
                                    ListItem(
                                        headlineContent = { Text(text = user.login) },
                                        leadingContent = {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = user.login,
                                                modifier =
                                                    Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape),
                                            )
                                        },
                                        trailingContent = {
                                            Checkbox(
                                                checked = user.login in state.selectedAssignees,
                                                onCheckedChange = { onToggleAssignee(user.login) },
                                            )
                                        },
                                        modifier = Modifier.clickable { onToggleAssignee(user.login) },
                                    )
                                }
                            }
                        }
                        MetaSection(title = stringResource(R.string.issue_meta_section_milestone)) {
                            MilestoneOptionCard(
                                title = stringResource(R.string.issue_meta_milestone_none),
                                selected = state.selectedMilestone == null,
                                onClick = { onSelectMilestone(null) },
                            )
                            state.milestones.forEach { milestone ->
                                MilestoneOptionCard(
                                    title = milestone.title,
                                    dueOn = milestone.dueOn,
                                    closed = milestone.state == IssueState.CLOSED,
                                    selected = milestone.number != null && milestone.number == state.selectedMilestone,
                                    onClick = { onSelectMilestone(milestone.number) },
                                )
                            }
                        }
                        Button(
                            onClick = onSave,
                            enabled = !state.saving,
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            if (state.saving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(text = stringResource(R.string.issue_save))
                        }
                    }
                }
            }
        }
    }
}

/** Sheet 内分区：标题 + 内容（统一间距） */
@Composable
private fun MetaSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** 候选项为空提示 */
@Composable
private fun MetaEmptyHint() {
    Text(
        text = stringResource(R.string.issue_meta_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Milestone 单选卡：标题 + 截止日期/已关闭标记 + RadioButton（整卡可点） */
@Composable
private fun MilestoneOptionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    dueOn: String? = null,
    closed: Boolean = false,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                val dueText = dueOn?.let { stringResource(R.string.issue_meta_milestone_due, it) }
                val closedText = if (closed) stringResource(R.string.issue_state_closed) else null
                val caption = listOfNotNull(dueText, closedText).joinToString(" · ")
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * 评论输入 Sheet 完整形态（#166 / UI05，ui-design §3.9 D2-3 用户拍板）。
 *
 * 用户拍板形制：**右下角圆角按钮触发 → 上滑 Sheet（圆角 + 把手）+ 编辑/预览切换 +
 * 底部 md 功能按钮**。此前实现只是一个 OutlinedTextField + 提交按钮，三件套缺两件。
 *
 * 复用 core:editor 的 [MarkdownComposer]（编辑/预览双 Tab + 11 个语法动作工具栏），
 * 与全屏 Markdown 编辑页共享同一套交互 —— 不给评论单独维护一份工具栏。
 *
 * 预览刻意用**原生 [MarkdownViewer]** 而不是 WebView：本仓铁律「评论列表绝不用 WebView」
 * 同样适用于评论预览（短文本、进 Sheet 就要出画面，WebView 初始化反而慢）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentInputSheet(
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var isPreview by remember { mutableStateOf(false) }
    var editorController by remember { mutableStateOf<MarkdownEditorController?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val editorTokens = rememberM3EditorThemeTokens()
    val previewPlaceholder = stringResource(R.string.issue_comment_preview_empty)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // #167 / UI17：BottomSheet 进出内容走 AppMotion 令牌（§4.1 表定 500ms）
        modifier = Modifier.appTransientEnterAlpha(),
        // 圆角 + 顶部把手：ModalBottomSheet 自带把手，圆角走全局形状令牌
        shape =
            RoundedCornerShape(
                topStart = AppDimens.cornerExtraLarge,
                topEnd = AppDimens.cornerExtraLarge,
            ),
    ) {
        // 弹层玻璃点位（#167 / UI22，ui-design §6.1 #4）：容器底交 GlassSheetSurface，开关/主题降级由
        // GlassScope.BOTTOM_SHEET 统一裁决（弹层是独立 window，几何结论见该组件 KDoc）
        GlassSheetSurface {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppDimens.contentPadding)
                        .padding(bottom = AppDimens.contentPadding)
                        .imePadding(),
            ) {
                MarkdownComposer(
                    text = text,
                    isPreview = isPreview,
                    onTogglePreview = { isPreview = it },
                    onTextChanged = onTextChange,
                    onEditorReady = { editorController = it },
                    onToolbarAction = { editorController?.applySyntax(it) },
                    themeTokens = editorTokens,
                    preview = {
                        MarkdownViewer(
                            markdown = text.ifBlank { previewPlaceholder },
                            // Sheet 自身可滚动：预览不再开内层滚动（避免嵌套滚动手势打架）
                            scrollable = false,
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppDimens.cornerSmall),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 380.dp),
                )
                Spacer(modifier = Modifier.height(AppDimens.cornerMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.issue_comment_cancel))
                    }
                    Spacer(modifier = Modifier.width(AppDimens.cornerSmall))
                    Button(
                        onClick = { onSubmit(text) },
                        enabled = text.isNotBlank(),
                    ) {
                        Text(text = stringResource(R.string.issue_comment_submit))
                    }
                }
            }
        }
    }
}

/** 编辑 Issue 对话框（标题 + 正文） */
@Composable
private fun EditIssueDialog(
    issue: Issue,
    draft: DraftText,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(issue.title) }
    val body by draft.text.collectAsStateWithLifecycle()
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
                    onValueChange = draft::onChanged,
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
    draft: DraftText,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val body by draft.text.collectAsStateWithLifecycle()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.issue_edit_comment_title)) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = draft::onChanged,
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
        IssueSnackbarMessage.SUBSCRIBED -> R.string.issue_subscribed_snackbar
        IssueSnackbarMessage.UNSUBSCRIBED -> R.string.issue_unsubscribed_snackbar
        IssueSnackbarMessage.ISSUE_META_UPDATED -> R.string.issue_meta_updated
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
