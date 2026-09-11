@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// - LongMethod：详情页装配（PrHeader + 四 Tab 分发）聚合展示逻辑，拆分反损可读性（IssueDetailScreen 同款先例）
// - CyclomaticComplexMethod：T17 事件收集（8 类写操作 Snackbar）+ ReviewSheet 状态装配，分支天然多（HomeScreen 同款先例）

package com.yumiru11.githubapp.feature.pullrequest

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yumiru11.githubapp.core.designsystem.component.AppCenteredLoadingState
import com.yumiru11.githubapp.core.designsystem.component.AppStateChip
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.component.labelChipContainerColor
import com.yumiru11.githubapp.core.designsystem.component.labelChipContentColor
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppSnackbarHost
import com.yumiru11.githubapp.core.ui.appTransientEnterAlpha
import com.yumiru11.githubapp.core.ui.gitHubStatusStateDescription
import com.yumiru11.githubapp.core.ui.time.relativeTimeText
import com.yumiru11.githubapp.feature.pullrequest.model.CheckRun
import com.yumiru11.githubapp.feature.pullrequest.model.CombinedStatus
import com.yumiru11.githubapp.feature.pullrequest.model.DiffSide
import com.yumiru11.githubapp.feature.pullrequest.model.MergeableState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequest
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestCommit
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestFile
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestLabel
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestState
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTab
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestTimelineItem
import com.yumiru11.githubapp.feature.pullrequest.model.PullRequestUser
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewComment
import com.yumiru11.githubapp.feature.pullrequest.model.ReviewThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * PR 详情页（T15）：PrHeader + 四 Tab（Conversation/Commits/Checks/Files changed）。
 *
 * 状态由 [PullRequestDetailViewModel] 驱动；PR 正文经 WebViewMarkdownRenderer（离线 GFM）渲染，
 * 评论/Review 正文保持 MarkdownViewer 原生渲染，事件以单行次要文本呈现。
 * 所有文案经 stringResource 本地化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRequestDetailScreen(
    owner: String,
    repo: String,
    number: Int,
    onBackClick: () -> Unit,
    onInternalLink: (ParsedUrl) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PullRequestDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // 会话评论 Sheet 开关声明在事件收集之前：CommentPosted 事件要在这里把它关掉
    var showCommentSheet by remember { mutableStateOf(false) }
    // #166：正在编辑/待删除的会话评论（null = 无对话框）
    var editingComment by remember { mutableStateOf<PullRequestTimelineItem.Comment?>(null) }
    var deletingComment by remember { mutableStateOf<PullRequestTimelineItem.Comment?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PullRequestDetailEvent.CommentFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_line_comment_failed))
                }

                // 会话评论发布成功（#166）：关掉 Sheet 让用户看到时间线里的新评论
                PullRequestDetailEvent.CommentPosted -> {
                    showCommentSheet = false
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_comment_posted))
                }

                PullRequestDetailEvent.CommentUpdated -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_comment_updated))
                }

                PullRequestDetailEvent.CommentDeleted -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_comment_deleted))
                }

                PullRequestDetailEvent.ReviewFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_review_failed))
                }

                PullRequestDetailEvent.MergeSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_merge_succeeded))
                }

                PullRequestDetailEvent.MergeFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_merge_failed))
                }

                PullRequestDetailEvent.UpdateBranchSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_update_branch_succeeded))
                }

                PullRequestDetailEvent.UpdateBranchFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_update_branch_failed))
                }

                PullRequestDetailEvent.DeleteBranchSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_delete_branch_succeeded))
                }

                PullRequestDetailEvent.DeleteBranchFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_delete_branch_failed))
                }

                PullRequestDetailEvent.EditSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_edit_succeeded))
                }

                PullRequestDetailEvent.EditFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_edit_failed))
                }

                PullRequestDetailEvent.CloseSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_close_succeeded))
                }

                PullRequestDetailEvent.CloseFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_close_failed))
                }

                PullRequestDetailEvent.ReopenSucceeded -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_reopen_succeeded))
                }

                PullRequestDetailEvent.ReopenFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_reopen_failed))
                }
            }
        }
    }
    val currentUrl = (uiState as? PullRequestDetailUiState.Success)?.pullRequest?.htmlUrl

    // 评论 BottomSheet 状态

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // T17：Review 提交 BottomSheet 状态
    var showReviewSheet by remember { mutableStateOf(false) }
    // #163 L03：编辑 PR 对话框 / 关闭 PR 二次确认
    var showEditPrDialog by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    val successState = uiState as? PullRequestDetailUiState.Success

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
                            contentDescription = stringResource(R.string.pull_request_back),
                        )
                    }
                },
                actions = {
                    if (currentUrl != null || successState != null) {
                        PullRequestMoreMenu(
                            url = currentUrl,
                            // #163 L03：编辑/关闭/重开（WRITE 权限才可见；已合并不可关闭重开）
                            editActions =
                                PullRequestEditMenuActions(
                                    canEditPr = successState?.canEditPr == true,
                                    canCloseReopenPr = successState?.canCloseReopenPr == true,
                                    isClosed = successState?.pullRequest?.state == PullRequestState.CLOSED,
                                    pending = successState?.pendingAction != null,
                                    onEditPr = { showEditPrDialog = true },
                                    onClosePr = { showCloseConfirm = true },
                                    onReopenPr = viewModel::reopenPullRequest,
                                ),
                            snackbarHostState = snackbarHostState,
                            scope = scope,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            // 评论属写操作：仅详情加载成功后提供入口（Loading/Error 态无目标可评）
            if (uiState is PullRequestDetailUiState.Success) {
                ExtendedFloatingActionButton(
                    onClick = { showCommentSheet = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null, // 文本「评论」已表意，避免 TalkBack 重复朗读
                        )
                    },
                    text = { Text(text = stringResource(R.string.pull_request_comment)) },
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
                is PullRequestDetailUiState.Loading -> {
                    AppCenteredLoadingState(modifier = Modifier.fillMaxSize())
                }

                is PullRequestDetailUiState.Error -> {
                    PullRequestErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is PullRequestDetailUiState.Success -> {
                    SuccessContent(
                        pullRequest = state.pullRequest,
                        timeline = state.timeline,
                        canEditComment = state::canEditComment,
                        onEditComment = { editingComment = it },
                        onDeleteComment = { deletingComment = it },
                        commits = state.commits,
                        files = state.files,
                        checkRuns = state.checkRuns,
                        combinedStatus = state.combinedStatus,
                        selectedTab = viewModel.selectedTab.collectAsStateWithLifecycle().value,
                        onTabSelected = viewModel::selectTab,
                        expandedCheckIds = viewModel.expandedCheckIds.collectAsStateWithLifecycle().value,
                        onToggleCheckExpanded = viewModel::toggleCheckExpanded,
                        expandedCommitShas = viewModel.expandedCommitShas.collectAsStateWithLifecycle().value,
                        onToggleCommitExpanded = viewModel::toggleCommitExpanded,
                        expandedFileNames = viewModel.expandedFileNames.collectAsStateWithLifecycle().value,
                        onToggleFileExpanded = viewModel::toggleFileExpanded,
                        reviewComments = state.reviewComments,
                        reviewThreads = state.reviewThreads,
                        conversationActions =
                            ConversationActions(
                                state = state.pullRequest.state,
                                mergeableState = state.pullRequest.mergeableState,
                                canReview = state.canReview,
                                canMerge = state.canMerge,
                                canMergeBox = state.canMergeBox,
                                canDeleteHeadBranch = state.canDeleteHeadBranch,
                                headSameRepo = state.headSameRepo,
                                pendingAction = state.pendingAction,
                                prTitle = state.pullRequest.title,
                                onOpenReview = { showReviewSheet = true },
                                onMerge = viewModel::mergePullRequest,
                                onUpdateBranch = viewModel::updateBranch,
                                onDeleteBranch = viewModel::deleteBranch,
                            ),
                        onLineComment = viewModel::openLineComment,
                        onInternalLink = onInternalLink,
                        baseRepoUrl = "https://github.com/$owner/$repo",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 评论输入 BottomSheet
            // 编辑会话评论（#166）：标题 + 正文，保存走乐观更新
            editingComment?.let { comment ->
                EditCommentDialog(
                    initialBody = comment.body.orEmpty(),
                    onDismiss = { editingComment = null },
                    onSave = { body ->
                        editingComment = null
                        viewModel.updateComment(comment.id, body)
                    },
                )
            }

            // 删除会话评论确认（#166）
            deletingComment?.let { comment ->
                AlertDialog(
                    onDismissRequest = { deletingComment = null },
                    title = { Text(text = stringResource(R.string.pull_request_comment_delete_title)) },
                    text = { Text(text = stringResource(R.string.pull_request_comment_delete_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                deletingComment = null
                                viewModel.deleteComment(comment.id)
                            },
                        ) {
                            Text(text = stringResource(R.string.pull_request_comment_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { deletingComment = null }) {
                            Text(text = stringResource(R.string.pull_request_comment_cancel))
                        }
                    },
                )
            }

            if (showCommentSheet) {
                CommentBottomSheet(
                    onDismiss = { showCommentSheet = false },
                    // 发布失败时**不关闭** Sheet：已输入的内容不能丢（失败提示由事件通道给出）
                    onSubmit = viewModel::submitComment,
                    sheetState = sheetState,
                )
            }

            // 行评论 BottomSheet（T16：新增/回复 + 会话解析）
            val lineCommentTarget by viewModel.lineCommentTarget.collectAsStateWithLifecycle()
            lineCommentTarget?.let { target ->
                LineCommentSheet(
                    target = target,
                    canResolve = (uiState as? PullRequestDetailUiState.Success)?.canResolveThreads ?: false,
                    onDismiss = { viewModel.dismissLineComment() },
                    onSubmit = { anchor, body, inReplyToId -> viewModel.submitLineComment(anchor, body, inReplyToId) },
                    onToggleResolve = { thread -> viewModel.toggleThreadResolved(thread) },
                )
            }

            // #163 L03：编辑 PR（标题 + 正文）
            if (showEditPrDialog && successState?.canEditPr == true) {
                EditPullRequestDialog(
                    pullRequest = successState.pullRequest,
                    onDismiss = { showEditPrDialog = false },
                    onSubmit = { title, body ->
                        showEditPrDialog = false
                        viewModel.editPullRequest(title, body)
                    },
                )
            }

            // #163 L03：关闭 PR 二次确认
            if (showCloseConfirm) {
                AlertDialog(
                    onDismissRequest = { showCloseConfirm = false },
                    title = { Text(text = stringResource(R.string.pull_request_close_confirm_title)) },
                    text = { Text(text = stringResource(R.string.pull_request_close_confirm_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showCloseConfirm = false
                                viewModel.closePullRequest()
                            },
                        ) {
                            Text(text = stringResource(R.string.pull_request_close))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCloseConfirm = false }) {
                            Text(text = stringResource(R.string.cancel))
                        }
                    },
                )
            }

            // Review 提交 BottomSheet（T17）
            if (showReviewSheet) {
                val canApprove = (uiState as? PullRequestDetailUiState.Success)?.canApprove ?: false
                ReviewSheet(
                    canApprove = canApprove,
                    onDismiss = { showReviewSheet = false },
                    onSubmit = { conclusion, body ->
                        viewModel.submitReview(conclusion, body)
                        showReviewSheet = false
                    },
                )
            }
        }
    }
}

/**
 * #163 L03：更多菜单的编辑/关闭/重开入口（权限位 + 回调聚合，避免菜单签名膨胀——
 * [ConversationActions] 同款做法）。
 */
internal data class PullRequestEditMenuActions(
    val canEditPr: Boolean = false,
    val canCloseReopenPr: Boolean = false,
    val isClosed: Boolean = false,
    val pending: Boolean = false,
    val onEditPr: () -> Unit = {},
    val onClosePr: () -> Unit = {},
    val onReopenPr: () -> Unit = {},
)

/**
 * TopAppBar 更多菜单：分享 / 浏览器打开 / 复制链接（复制成功后 Snackbar 反馈）
 * + #163 L03 编辑 PR / 关闭 PR / 重开 PR（WRITE 权限且未合并时才显示）。
 */
@Composable
private fun PullRequestMoreMenu(
    url: String?,
    editActions: PullRequestEditMenuActions,
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
                contentDescription = stringResource(R.string.pull_request_menu_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (url != null) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.pull_request_share)) },
                    onClick = {
                        expanded = false
                        shareUrl(context, url)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.pull_request_open_in_browser)) },
                    onClick = {
                        expanded = false
                        openInBrowser(context, url)
                    },
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.pull_request_copy_link)) },
                    onClick = {
                        expanded = false
                        copyLink(context, url)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.pull_request_link_copied)) }
                    },
                )
            }
            if (editActions.canEditPr) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.pull_request_edit)) },
                    onClick = {
                        expanded = false
                        editActions.onEditPr()
                    },
                )
            }
            if (editActions.canCloseReopenPr) {
                // 关闭走二次确认；重开直接执行（写操作进行中禁用，防连点）
                if (editActions.isClosed) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.pull_request_reopen)) },
                        enabled = !editActions.pending,
                        onClick = {
                            expanded = false
                            editActions.onReopenPr()
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.pull_request_close)) },
                        enabled = !editActions.pending,
                        onClick = {
                            expanded = false
                            editActions.onClosePr()
                        },
                    )
                }
            }
        }
    }
}

/** #163 L03：编辑 PR 对话框（标题 + 正文；标题必填） */
@Composable
private fun EditPullRequestDialog(
    pullRequest: PullRequest,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf(pullRequest.title) }
    var body by remember { mutableStateOf(pullRequest.body.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pull_request_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(R.string.pull_request_edit_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text(text = stringResource(R.string.pull_request_edit_body_label)) },
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
                Text(text = stringResource(R.string.pull_request_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

/** 成功态：PrHeader（固定）+ TabRow + 当前 Tab 内容 */
@Suppress("LongParameterList") // 四 Tab 数据 + 展开状态 + 回调聚合装配，分组拆散反损可读性（IssueDetailScreen 同款先例）
@Composable
private fun SuccessContent(
    pullRequest: PullRequest,
    timeline: List<PullRequestTimelineItem>,
    commits: List<PullRequestCommit>,
    files: List<PullRequestFile>,
    checkRuns: List<CheckRun>,
    combinedStatus: CombinedStatus?,
    reviewComments: List<ReviewComment>,
    reviewThreads: List<ReviewThread>,
    conversationActions: ConversationActions,
    onLineComment: (String, DiffSide, Int) -> Unit,
    selectedTab: PullRequestTab,
    onTabSelected: (PullRequestTab) -> Unit,
    expandedCheckIds: Set<Long>,
    onToggleCheckExpanded: (Long) -> Unit,
    expandedCommitShas: Set<String>,
    onToggleCommitExpanded: (String) -> Unit,
    expandedFileNames: Set<String>,
    onToggleFileExpanded: (String) -> Unit,
    onInternalLink: (ParsedUrl) -> Unit,
    baseRepoUrl: String,
    // #166：会话评论编辑/删除（作者判定 + 对话框状态由外层持有）
    canEditComment: (PullRequestTimelineItem.Comment) -> Boolean,
    onEditComment: (PullRequestTimelineItem.Comment) -> Unit,
    onDeleteComment: (PullRequestTimelineItem.Comment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PrHeader(
            pullRequest = pullRequest,
            combinedStatus = combinedStatus,
            modifier = Modifier.fillMaxWidth(),
        )
        HorizontalDivider()
        PrTabs(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected,
        )
        HorizontalDivider()
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                PullRequestTab.CONVERSATION -> {
                    ConversationTab(
                        pullRequest = pullRequest,
                        timeline = timeline,
                        conversationActions = conversationActions,
                        onInternalLink = onInternalLink,
                        baseRepoUrl = baseRepoUrl,
                        canEditComment = canEditComment,
                        onEditComment = onEditComment,
                        onDeleteComment = onDeleteComment,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PullRequestTab.COMMITS -> {
                    CommitsTab(
                        commits = commits,
                        expandedShas = expandedCommitShas,
                        onToggleExpanded = onToggleCommitExpanded,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PullRequestTab.CHECKS -> {
                    ChecksTab(
                        checkRuns = checkRuns,
                        expandedIds = expandedCheckIds,
                        onToggleExpanded = onToggleCheckExpanded,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                PullRequestTab.FILES -> {
                    FilesTab(
                        files = files,
                        expandedNames = expandedFileNames,
                        onToggleExpanded = onToggleFileExpanded,
                        reviewComments = reviewComments,
                        reviewThreads = reviewThreads,
                        onLineComment = onLineComment,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * 四 Tab 行（与网页端对齐：Conversation/Commits/Checks/Files changed）。
 *
 * C1 修复：原实现是固定 [TabRow]，4 个 tab 均分宽度（411.dp 机型上每个约 102.dp，
 * 去掉 `HorizontalTextPadding` 32.dp 后文本区仅 ~70.dp），"Conversation" / "Files changed"
 * 被省略号截断成 "Conversa…" / "Files cha…"（CI 截图 pr-conversation / pr-commits /
 * pr-actions 三张全中）——标签一旦省略就再也读不全。
 *
 * 改用 M3 1.4 的 [PrimaryScrollableTabRow]（primary tabs）：tab 宽度 =
 * max(minTabWidth, 文本宽 + 32.dp)，在滚动容器里测量宽度不受限，标签要么完整可见、
 * 要么整体滚出视口（可滚回），永不省略；宽度不够时横向滚动 + `edgePadding` 留白提示。
 */
@Composable
private fun PrTabs(
    selectedTab: PullRequestTab,
    onTabSelected: (PullRequestTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        edgePadding = TAB_ROW_EDGE_PADDING,
        minTabWidth = TAB_ROW_MIN_TAB_WIDTH,
    ) {
        PullRequestTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = tabTitle(tab),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Tab → 本地化标题 */
@Composable
private fun tabTitle(tab: PullRequestTab): String =
    when (tab) {
        PullRequestTab.CONVERSATION -> stringResource(R.string.pull_request_tab_conversation)
        PullRequestTab.COMMITS -> stringResource(R.string.pull_request_tab_commits)
        PullRequestTab.CHECKS -> stringResource(R.string.pull_request_tab_checks)
        PullRequestTab.FILES -> stringResource(R.string.pull_request_tab_files)
    }

/** PrHeader：StateChip + 标题 + 作者 + 分支信息 + Labels/Reviewers/Checks 摘要/Mergeable */
@Composable
private fun PrHeader(
    pullRequest: PullRequest,
    combinedStatus: CombinedStatus?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.padding(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(state = pullRequest.state)
            // C4 根因修复：只有「还能合并」的 PR（OPEN / DRAFT）才该显示合并性徽标。
            // GitHub 对已合并 / 已关闭的 PR 恒返回 `mergeable = null`（合并 ref 已不存在），
            // 于是徽标会永远停在 "Checking mergeability…"。CI 三张 PR 截图
            // （pr-conversation / pr-commits / pr-actions）拍的正是 PR 已 Merged 却仍挂着
            // 这句待检查文案的「死状态」——那才是"死文案"的根因。这里直接不渲染，
            // 与网页端一致（已合并的 PR 没有合并性可谈）。
            if (pullRequest.state == PullRequestState.OPEN || pullRequest.state == PullRequestState.DRAFT) {
                Spacer(modifier = Modifier.width(8.dp))
                MergeableChip(mergeableState = pullRequest.mergeableState)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pullRequest.title,
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        val author = pullRequest.author?.login.orEmpty()
        val relativeTime = pullRequest.createdAt?.let { relativeTimeText(it) }
        Text(
            text =
                if (relativeTime != null) {
                    stringResource(R.string.pull_request_author_opened, author, relativeTime)
                } else {
                    stringResource(R.string.pull_request_author, author)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val head = pullRequest.head?.ref
        val base = pullRequest.base?.ref
        if (!head.isNullOrBlank() && !base.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pull_request_branch_base_head, base, head),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pullRequest.labels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pullRequest.labels.forEach { LabelChip(label = it) }
            }
        }
        if (pullRequest.requestedReviewers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ReviewerRow(reviewers = pullRequest.requestedReviewers)
        }
        if (combinedStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            ChecksSummaryRow(combinedStatus = combinedStatus)
        }
    }
}

/** Open/Closed/Merged/Draft 状态徽标 → AppStateChip 语义色（#84 audit 缺陷 #4） */
@Composable
private fun StatusChip(state: PullRequestState) {
    val text =
        when (state) {
            PullRequestState.OPEN -> stringResource(R.string.pull_request_state_open)
            PullRequestState.CLOSED -> stringResource(R.string.pull_request_state_closed)
            PullRequestState.MERGED -> stringResource(R.string.pull_request_state_merged)
            PullRequestState.DRAFT -> stringResource(R.string.pull_request_state_draft)
        }
    val status =
        when (state) {
            PullRequestState.OPEN -> GitHubStatus.OPEN
            PullRequestState.CLOSED -> GitHubStatus.CLOSED
            PullRequestState.MERGED -> GitHubStatus.MERGED
            PullRequestState.DRAFT -> GitHubStatus.DRAFT
        }
    // #168 / UI26：补状态播报（TalkBack 读「Merged, 该 PR 已合并」）
    AppStateChip(
        status = status,
        label = text,
        stateDescription = gitHubStatusStateDescription(status),
    )
}

/**
 * Mergeable 状态徽标：可合并（primary）/ 冲突（error）/ 待检查（surfaceVariant + 进度指示）。
 * 仅由 [PrHeader] 在 PR 还可合并（OPEN / DRAFT）时调用。
 *
 * C4 修复：`MergeableState.UNKNOWN` 表示 GitHub 尚未算完合并性（`mergeable == null`），
 * 是一个**会自行消失的瞬时态**，但截图里只有一行静止的 "Checking mergeability…"，
 * 看不出还在进行中。按 MergeBox 里 pendingAction 的既有做法补一个 12.dp 的内联
 * [CircularProgressIndicator]（M3 自带组件、不引额外依赖、无自定义动画曲线）。
 * 该指示器是装饰性的（无 contentDescription）：状态语义由相邻 Text 承载，
 * 与 `ChecksSummaryRow` 的 pending 分支同款处理。
 */
@Composable
private fun MergeableChip(mergeableState: MergeableState) {
    val (text, container, content) =
        when (mergeableState) {
            MergeableState.MERGEABLE -> {
                Triple(
                    stringResource(R.string.pull_request_mergeable),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            MergeableState.CONFLICTING -> {
                Triple(
                    stringResource(R.string.pull_request_conflicting),
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            }

            MergeableState.UNKNOWN -> {
                Triple(
                    stringResource(R.string.pull_request_mergeable_pending),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (mergeableState == MergeableState.UNKNOWN) {
                CircularProgressIndicator(
                    modifier = Modifier.size(INLINE_PROGRESS_SIZE),
                    strokeWidth = INLINE_PROGRESS_STROKE,
                    color = content,
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

/** 标签徽标：label 原色与主题 surface 低饱和混合，无则 surfaceVariant（#85 audit #2/#20） */
@Composable
private fun LabelChip(label: PullRequestLabel) {
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

/** Reviewers 行：头像 + login */
@Composable
private fun ReviewerRow(reviewers: List<PullRequestUser>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.pull_request_reviewers),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        reviewers.forEach { user ->
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
 * Checks 摘要行：combined status state（success/failure/pending）。
 *
 * C4 修复：pending 原先渲染一个静止的 `Icons.Filled.Refresh` 圆箭头图标，看起来像
 * 可点的"刷新"按钮，且完全看不出检查还在跑（CI 截图三张 PR 页都是 "Checks pending"
 * 配一个死图标）。pending 分支改为内联 [CircularProgressIndicator]——语义明确
 * （进行中）、与 MergeBox 的 pendingAction 做法一致、无自定义动画。
 * success/failure 仍是状态图标，语义由文本承载，图标为纯装饰（contentDescription = null）。
 */
@Composable
private fun ChecksSummaryRow(combinedStatus: CombinedStatus) {
    val pending = combinedStatus.state != COMBINED_STATUS_SUCCESS && combinedStatus.state != COMBINED_STATUS_FAILURE
    val (text, color) =
        when {
            pending -> {
                stringResource(R.string.pull_request_checks_state_pending) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }

            combinedStatus.state == COMBINED_STATUS_SUCCESS -> {
                stringResource(R.string.pull_request_checks_state_success) to
                    MaterialTheme.colorScheme.primary
            }

            else -> {
                stringResource(R.string.pull_request_checks_state_failure) to
                    MaterialTheme.colorScheme.error
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(INLINE_PROGRESS_SIZE),
                strokeWidth = INLINE_PROGRESS_STROKE,
                color = color,
            )
        } else {
            Icon(
                imageVector = combinedStatusIcon(combinedStatus.state),
                // 文案已由相邻 Text 承载，图标纯装饰（避免 TalkBack 播报两遍）
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** combined status state → 图标（仅 success/failure 有专属图标；pending 走进度指示） */
private fun combinedStatusIcon(state: String?) =
    if (state == COMBINED_STATUS_SUCCESS) {
        Icons.Filled.CheckCircle
    } else {
        Icons.Filled.Close
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

internal fun openInBrowser(
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
private const val TAG = "PullRequestDetailScreen"

/**
 * GitHub combined status 的 state 取值（REST `/repos/{owner}/{repo}/commits/{ref}/status`）。
 * 单一取值 `pending` 之外还有 `error`，两者在 UI 上同为「未通过/进行中」的兜底分支。
 */
private const val COMBINED_STATUS_SUCCESS = "success"
private const val COMBINED_STATUS_FAILURE = "failure"

/** 状态行内联进度指示尺寸（与 MergeBox 的 18.dp 按钮内转圈区分：这里是 labelMedium 行内） */
private val INLINE_PROGRESS_SIZE = 12.dp

/** 状态行内联进度指示描边（12.dp 直径下保持 M3 视觉粗细） */
private val INLINE_PROGRESS_STROKE = 2.dp

/**
 * 四 Tab 行首尾留白（C1）。M3 语义：滚动 tab 行与首/尾 tab 之间的留白本身就是
 * 「此行可横向滚动」的视觉提示（`PrimaryScrollableTabRow` 的 `edgePadding` 文档原话）。
 * 与全 app 内容边距（AppDimens.contentPadding）同量级，与 SearchScreen 结果 Tab 行对齐。
 */
private val TAB_ROW_EDGE_PADDING = 16.dp

/**
 * 四 Tab 行 tab 最小宽度（C1）。
 *
 * 本页标签较长（en "Conversation" / "Files changed" 在 labelLarge 14sp 下约 88 / 80.dp，
 * 加 32.dp 内边距后 120 / 112.dp），实际宽度由内容决定、64.dp 下限不会生效；
 * 保留该下限是为了窄屏 / 短标签语种（zh-rCN「对话/提交/检查/文件」）下 tab 不至于挤成一团，
 * 同时远高于 Material 48.dp 的最小触摸目标。放不下时按 M3 语义横向滚动。
 */
private val TAB_ROW_MIN_TAB_WIDTH = 64.dp

/** PR 评论输入 BottomSheet（Material You 风格；写接口接入前 Submit 仅给出「暂未开放」反馈） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentBottomSheet(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    sheetState: SheetState,
) {
    var commentText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // #167 / UI17：BottomSheet 进出内容走 AppMotion 令牌（§4.1 表定 500ms）
        modifier = Modifier.appTransientEnterAlpha(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .imePadding() // 键盘弹出时抬升内容，避免输入框/按钮被遮挡
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pull_request_add_comment),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = commentText,
                onValueChange = { commentText = it },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                placeholder = { Text(text = stringResource(R.string.pull_request_comment_placeholder)) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(commentText) },
                    enabled = commentText.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.submit))
                }
            }
        }
    }
}

/** 编辑会话评论对话框（#166）：只改正文，保存前不允许空白。 */
@Composable
private fun EditCommentDialog(
    initialBody: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var body by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.pull_request_comment_edit_title)) },
        text = {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(body) },
                enabled = body.isNotBlank(),
            ) {
                Text(text = stringResource(R.string.pull_request_comment_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.pull_request_comment_cancel))
            }
        },
    )
}
