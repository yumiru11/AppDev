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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.yumiru11.githubapp.core.designsystem.component.AppStateChip
import com.yumiru11.githubapp.core.designsystem.component.GitHubStatus
import com.yumiru11.githubapp.core.designsystem.component.labelChipContainerColor
import com.yumiru11.githubapp.core.designsystem.component.labelChipContentColor
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
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

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                PullRequestDetailEvent.CommentFailed -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.pull_request_line_comment_failed))
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
            }
        }
    }
    val currentUrl = (uiState as? PullRequestDetailUiState.Success)?.pullRequest?.htmlUrl

    // 评论 BottomSheet 状态
    var showCommentSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // T17：Review 提交 BottomSheet 状态
    var showReviewSheet by remember { mutableStateOf(false) }

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
                            contentDescription = stringResource(R.string.pull_request_back),
                        )
                    }
                },
                actions = {
                    if (currentUrl != null) {
                        PullRequestMoreMenu(
                            url = currentUrl,
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
                    PullRequestLoadingContent(modifier = Modifier.fillMaxSize())
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
            if (showCommentSheet) {
                val commentUnavailableMessage = stringResource(R.string.pull_request_comment_unavailable)
                CommentBottomSheet(
                    onDismiss = { showCommentSheet = false },
                    onSubmit = {
                        // 写接口尚未接入（REST 评论发布属后续票）：明确告知，不关闭 sheet 以保留已输入内容
                        scope.launch { snackbarHostState.showSnackbar(commentUnavailableMessage) }
                    },
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

/** TopAppBar 更多菜单：分享 / 浏览器打开 / 复制链接（复制成功后 Snackbar 反馈） */
@Composable
private fun PullRequestMoreMenu(
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
                contentDescription = stringResource(R.string.pull_request_menu_more),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
    }
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

/** 四 Tab 行（与网页端对齐：Conversation/Commits/Checks/Files changed） */
@Composable
private fun PrTabs(
    selectedTab: PullRequestTab,
    onTabSelected: (PullRequestTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.width(8.dp))
            MergeableChip(mergeableState = pullRequest.mergeableState)
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
    AppStateChip(
        status =
            when (state) {
                PullRequestState.OPEN -> GitHubStatus.OPEN
                PullRequestState.CLOSED -> GitHubStatus.CLOSED
                PullRequestState.MERGED -> GitHubStatus.MERGED
                PullRequestState.DRAFT -> GitHubStatus.DRAFT
            },
        label = text,
    )
}

/** Mergeable 状态徽标：可合并（primary）/ 冲突（error）/ 待检查（surfaceVariant） */
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
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
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

/** Checks 摘要行：combined status state（success/failure/pending） */
@Composable
private fun ChecksSummaryRow(combinedStatus: CombinedStatus) {
    val (text, color) =
        when (combinedStatus.state) {
            "success" -> {
                stringResource(R.string.pull_request_checks_state_success) to MaterialTheme.colorScheme.primary
            }

            "failure" -> {
                stringResource(R.string.pull_request_checks_state_failure) to MaterialTheme.colorScheme.error
            }

            else -> {
                stringResource(R.string.pull_request_checks_state_pending) to MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = combinedStatusIcon(combinedStatus.state),
            contentDescription = text,
            tint = color,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

/** combined status state → 图标（success=CheckCircle / failure=Close / 其余=Refresh） */
private fun combinedStatusIcon(state: String?) =
    when (state) {
        "success" -> Icons.Filled.CheckCircle
        "failure" -> Icons.Filled.Close
        else -> Icons.Filled.Refresh
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
