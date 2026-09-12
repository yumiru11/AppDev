@file:Suppress("TooManyFunctions", "LongMethod")
// 屏幕文件：多个小 Composable 是 Compose 惯用结构，拆文件反损可读性（T3 先例）；
// RepoDetailScreen 主入口装配（T23 新增分支入口参数后 81 行）结构固有，拆散反损可读性（BranchesScreen 先例）

package com.yumiru11.githubapp.feature.repo

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Add
import com.composables.icons.materialsymbols.rounded.Call_split
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Open_in_new
import com.composables.icons.materialsymbols.rounded.Share
import com.composables.icons.materialsymbols.rounded.Tag
import com.composables.icons.materialsymbols.rounded.Visibility
import com.composables.icons.materialsymbols.rounded.Visibility_off
import com.yumiru11.githubapp.core.data.model.Release
import com.yumiru11.githubapp.core.data.model.Repository
import com.yumiru11.githubapp.core.designsystem.component.labelChipContainerColor
import com.yumiru11.githubapp.core.designsystem.component.labelChipContentColor
import com.yumiru11.githubapp.core.designsystem.token.AppMotion
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl
import com.yumiru11.githubapp.core.ui.AppImageOverlay
import com.yumiru11.githubapp.core.ui.LocalRepoDetailActions
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import com.yumiru11.githubapp.core.ui.sharedTransitionElement
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val TAG = "ReadmeRender"

/** 剪贴板条目名（文件编辑「保留本地更改」） */
private const val CLIP_LABEL_FILE_EDIT = "file-edit"

/** Star 弹跳峰值缩放（#167 / UI07：§4.2 H2-7 用户确认「星形弹跳可以」） */
private const val STAR_BOUNCE_SCALE = 1.25f

/**
 * 仓库详情页（T9 README 浏览 tracer bullet + T12 仓库管理）。
 *
 * 顶部：仓库元数据（名称/描述/星/分叉/语言 + 登录态 Star/Watch/Fork 按钮 + 语言栏）
 * 下方：分区 Tab（README / 文件 / Releases）
 *
 * T12：Star/Watch 乐观更新（失败回滚 + Snackbar）；Fork 权限控制；Releases/Tags 列表
 * 与 Release 详情（页内展开，不进导航）；语言栏按 Linguist 数据渲染。
 *
 * 链接分发（T9 验收第 3 条）：WebView bridge 的链接统一经
 * [RepoDetailActions] 处理——内部链接应用内导航，外部链接 CustomTabs，纯锚点忽略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    owner: String,
    repo: String,
    onBackClick: () -> Unit = {},
    initialRef: String? = null,
    /** TREE 深链：初始落「文件」分区（无路径也直接看到文件浏览而非 README） */
    initialShowFiles: Boolean = false,
    /** TREE 深链：文件分区自动展开到的仓库内路径（空 = 只到根树） */
    initialTreePath: String = "",
    /** RELEASE 深链：初始落「Releases」分区 */
    initialShowReleases: Boolean = false,
    /** RELEASE 深链：Releases 分区展开该 tag 的 Release 详情（空 = 只到列表） */
    initialReleaseTag: String = "",
    onBranchesClick: (owner: String, repo: String, currentRef: String?) -> Unit = { _, _, _ -> },
    /** L05：新建 Release 表单页（Releases Tab 入口） */
    onCreateRelease: (owner: String, repo: String) -> Unit = { _, _ -> },
    /** L06：点击 Topic chip → 搜索页（query = topic:xxx） */
    onTopicClick: (topic: String) -> Unit = {},
    viewModel: RepoDetailViewModel = hiltViewModel(),
    // 与 viewModel 同形参化：屏幕级测试可直接注入（默认 hiltViewModel() 保持生产接线不变）
    filesViewModel: RepoFilesViewModel = hiltViewModel(),
    actions: RepoDetailActions = LocalRepoDetailActions.current,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filesState by filesViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    RepoEventSnackbar(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onDeleted = onBackClick,
    )
    // T22：文件编辑事件（提交/删除成功、保留本地剪贴板、失败文案）
    FileEditEventSnackbar(viewModel = filesViewModel, snackbarHostState = snackbarHostState)

    // L04：删除仓库入口仅 owner/admin 可见（permissions 缺失 → 保守隐藏）
    val canDeleteRepo = (uiState as? RepoDetailUiState.Success)?.canDeleteRepo == true
    val deleteInProgress = (uiState as? RepoDetailUiState.Success)?.pendingAction == RepoAction.DELETE

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            RepoTopBar(
                owner = owner,
                repo = repo,
                onBackClick = onBackClick,
                canDelete = canDeleteRepo,
                onDeleteClick = { showDeleteDialog = true },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is RepoDetailUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 整页首载 → M3 Expressive 形变加载指示（ADR-0008：alpha18 无 opt-in 门控）
                        LoadingIndicator()
                    }
                }

                is RepoDetailUiState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retry() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is RepoDetailUiState.Success -> {
                    if (filesState.editState !is FileEditState.Idle) {
                        // T22：文件编辑全屏覆盖（编辑/预览/提交/删除/冲突对话框；返回键回查看器）
                        FileEditScreen(
                            editState = filesState.editState,
                            filePath = filesState.selectedPath,
                            baseRepoUrl = buildRepoUrl(state.repo),
                            defaultRef = state.repo.defaultBranch ?: DEFAULT_REF,
                            viewModel = filesViewModel,
                            onClose = { filesViewModel.dismissEdit() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (filesState.selectedPath != null) {
                        // T11：文件查看器全屏覆盖（树/README 内容隐藏，返回键回文件树）
                        FileViewerScreen(
                            fileState = filesState.fileState,
                            selectedPath = filesState.selectedPath.orEmpty(),
                            ref = state.repo.defaultBranch ?: DEFAULT_REF,
                            viewModel = filesViewModel,
                            actions = actions,
                            baseRepoUrl = buildRepoUrl(state.repo),
                            findState = filesState.findState,
                            isFindOpen = filesState.isFindOpen,
                            editable = state.isLoggedIn,
                            onClose = { filesViewModel.closeFile() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        RepoDetailContent(
                            state = state,
                            filesState = filesState,
                            filesViewModel = filesViewModel,
                            actions = actions,
                            onRetryReadme = { viewModel.retry() },
                            initialRef = initialRef,
                            initialShowFiles = initialShowFiles,
                            initialTreePath = initialTreePath,
                            initialShowReleases = initialShowReleases,
                            initialReleaseTag = initialReleaseTag,
                            onBranchesClick = { onBranchesClick(owner, repo, filesState.currentRef) },
                            onTopicClick = onTopicClick,
                            managementCallbacks =
                                RepoManagementCallbacks(
                                    onToggleStar = { viewModel.toggleStar() },
                                    onToggleWatch = { viewModel.toggleWatch() },
                                    onFork = { viewModel.fork() },
                                    onEnsureReleasesLoaded = { viewModel.ensureReleasesLoaded() },
                                    onEnsureTagsLoaded = { viewModel.ensureTagsLoaded() },
                                    onReleaseClick = { viewModel.loadReleaseDetail(it) },
                                    onCollapseRelease = { viewModel.collapseReleaseDetail() },
                                    onDeleteRepository = { viewModel.deleteRepository() },
                                    onCreateRelease = { onCreateRelease(owner, repo) },
                                    onUploadAsset = { releaseId, fileName, content ->
                                        viewModel.uploadAsset(releaseId, fileName, content)
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteRepoDialog(
            fullName = "$owner/$repo",
            inProgress = deleteInProgress,
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteRepository()
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * 仓库管理事件 → Snackbar（UI 层 stringResource 映射，ViewModel 不产文案）。
 *
 * @param onDeleted 删除成功回调（UI 返回上一页；页面数据已不存在）
 */
@Composable
private fun RepoEventSnackbar(
    viewModel: RepoDetailViewModel,
    snackbarHostState: SnackbarHostState,
    onDeleted: () -> Unit = {},
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            val message =
                when (event) {
                    RepoEvent.Forked -> {
                        context.getString(R.string.repo_snackbar_forked)
                    }

                    RepoEvent.ForkPermissionDenied -> {
                        context.getString(R.string.repo_snackbar_fork_permission_denied)
                    }

                    RepoEvent.ForkAlreadyExists -> {
                        context.getString(R.string.repo_snackbar_fork_already_exists)
                    }

                    RepoEvent.ForkFailed -> {
                        context.getString(R.string.repo_snackbar_fork_failed)
                    }

                    RepoEvent.ToggleFailed -> {
                        context.getString(R.string.repo_snackbar_toggle_failed)
                    }

                    RepoEvent.RepositoryDeleted -> {
                        onDeleted()
                        context.getString(R.string.repo_delete_snackbar_deleted)
                    }

                    RepoEvent.RepositoryDeleteForbidden -> {
                        context.getString(R.string.repo_delete_snackbar_forbidden)
                    }

                    RepoEvent.RepositoryDeleteFailed -> {
                        context.getString(R.string.repo_delete_snackbar_failed)
                    }

                    is RepoEvent.AssetUploaded -> {
                        context.getString(R.string.repo_release_asset_uploaded, event.name)
                    }

                    RepoEvent.AssetUploadFailed -> {
                        context.getString(R.string.repo_release_asset_upload_failed)
                    }
                }
            snackbarHostState.showSnackbar(message)
        }
    }
}

/**
 * 删除仓库二次确认对话框（L04）。
 *
 * 危险操作防误触：必须逐字输入完整 `owner/repo` 才能启用「永久删除」。
 */
@Composable
private fun DeleteRepoDialog(
    fullName: String,
    inProgress: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.repo_delete_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.repo_delete_message, fullName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.repo_delete_confirm_label, fullName)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = typed.trim() == fullName && !inProgress,
            ) {
                Text(text = stringResource(R.string.repo_delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.repo_delete_cancel))
            }
        },
    )
}

/** 文件编辑事件 → Snackbar/剪贴板（UI 层 stringResource 映射，ViewModel 不产文案）。 */
@Composable
private fun FileEditEventSnackbar(
    viewModel: RepoFilesViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.editEvents.collect { event ->
            when (event) {
                is FileEditEvent.Committed -> {
                    val message =
                        if (event.isNewBranch) {
                            context.getString(
                                R.string.repo_file_snackbar_committed_new_branch,
                                event.path,
                                event.branch.orEmpty(),
                            )
                        } else {
                            context.getString(R.string.repo_file_snackbar_committed, event.path)
                        }
                    snackbarHostState.showSnackbar(message)
                }

                is FileEditEvent.Deleted -> {
                    snackbarHostState.showSnackbar(context.getString(R.string.repo_file_snackbar_deleted, event.path))
                }

                is FileEditEvent.KeepLocal -> {
                    copyToClipboard(context, event.text, CLIP_LABEL_FILE_EDIT)
                    snackbarHostState.showSnackbar(context.getString(R.string.repo_file_snackbar_keep_local))
                }

                is FileEditEvent.DraftRestored -> {
                    val result =
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.repo_file_snackbar_draft_restored),
                            actionLabel = context.getString(R.string.repo_file_snackbar_draft_discard),
                        )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.discardRestoredDraft()
                    }
                }

                is FileEditEvent.Failed -> {
                    snackbarHostState.showSnackbar(editErrorText(context, event.errorType))
                }
            }
        }
    }
}

/** 编辑错误类型 → 本地化文案（非 Composable 版本：事件收集场景不可用 stringResource）。 */
private fun editErrorText(
    context: Context,
    errorType: RepoErrorType,
): String =
    when (errorType) {
        RepoErrorType.FORBIDDEN -> context.getString(R.string.repo_error_forbidden)
        RepoErrorType.NOT_FOUND -> context.getString(R.string.repo_error_not_found)
        RepoErrorType.PATH_NOT_FOUND -> context.getString(R.string.repo_error_path_not_found)
        RepoErrorType.NETWORK -> context.getString(R.string.repo_error_network)
        RepoErrorType.UNKNOWN -> context.getString(R.string.repo_error_unknown)
    }

/**
 * 复制文本到系统剪贴板（409「保留本地更改」/ L09 复制完整 SHA）。
 *
 * @param label 剪贴板条目名（系统剪贴板 UI 展示用；非界面文案）
 */
internal fun copyToClipboard(
    context: Context,
    text: String,
    label: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** 系统分享面板（ui-design §3.8 顶栏「更多」菜单的分享项）。 */
internal fun shareText(
    context: Context,
    text: String,
    chooserTitle: String,
) {
    val sendIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}

/**
 * 顶栏：返回 + 仓库名 + 「更多」菜单（ui-design §3.8：分享 / 浏览器打开 / 复制链接；
 * L04 追加「删除仓库」）。
 *
 * 三个客户端动作（分享/外链/复制）**始终可见**——它们不依赖权限，游客也能用；
 * 「删除仓库」按 [canDelete]（游客/非 admin/permissions 缺失）隐藏，入口不可见而非禁用，
 * 避免把「可能有但没权限」暴露成可点击的空壳。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepoTopBar(
    owner: String,
    repo: String,
    onBackClick: () -> Unit,
    canDelete: Boolean = false,
    onDeleteClick: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val htmlUrl = "https://github.com/$owner/$repo"
    val shareTitle = stringResource(R.string.repo_share_chooser)
    TopAppBar(
        title = {
            Text(
                text = "$owner/$repo",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.repo_back),
                )
            }
        },
        actions = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.repo_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.repo_menu_copy_link)) },
                        leadingIcon = { Icon(MaterialSymbols.Rounded.Link, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            copyToClipboard(context, htmlUrl, owner)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.repo_menu_open_browser)) },
                        leadingIcon = { Icon(MaterialSymbols.Rounded.Open_in_new, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(htmlUrl))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.repo_menu_share)) },
                        leadingIcon = { Icon(MaterialSymbols.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            shareText(context, htmlUrl, shareTitle)
                        },
                    )
                    if (canDelete) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.repo_delete)) },
                            leadingIcon = { Icon(MaterialSymbols.Rounded.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            },
                        )
                    }
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun RepoDetailContent(
    state: RepoDetailUiState.Success,
    filesState: RepoFilesUiState,
    filesViewModel: RepoFilesViewModel,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    initialRef: String? = null,
    initialShowFiles: Boolean = false,
    initialTreePath: String = "",
    initialShowReleases: Boolean = false,
    initialReleaseTag: String = "",
    onBranchesClick: () -> Unit,
    onTopicClick: (String) -> Unit = {},
    managementCallbacks: RepoManagementCallbacks,
    modifier: Modifier = Modifier,
) {
    // 深链初始分区：TREE → 文件，RELEASE → Releases；两者同帧不会同时来自解析器，
    // 同真时文件优先（见 repoDetailInitialTab）。rememberSaveable：旋转/重建保留用户实际切到的分区。
    var tab by rememberSaveable { mutableIntStateOf(repoDetailInitialTab(initialShowFiles, initialShowReleases)) }

    // ── README 下滑收起头部（#167 / UI10，ui-design §3.8 A 版）────────────────
    // A 版口径（用户拍板）：README 下滑时，仓库头（名称/描述/统计/Star·Watch·Fork）
    // **跟手渐隐并收缩高度**，滚过一段距离后完全收起，只留下分区 Tab。
    //
    // 为什么要把 scrollY 从 WebView 里捞出来：README 正文是 WebView 主渲染，滚动发生在
    // WebView 内部，Compose 侧完全观察不到 —— 所以由 WebViewMarkdownRenderer 反向回调。
    // 这也是本渲染架构下唯一可行的联动方式（不引入嵌套滚动/手势冲突）。
    var readmeScrollY by remember { mutableIntStateOf(0) }
    val collapseDistancePx = with(LocalDensity.current) { HEADER_COLLAPSE_DISTANCE.toPx() }
    // 只在 README 分区生效：文件/Releases 分区有自己的滚动，不该被 README 的滚动位置影响
    val headerCollapse =
        if (tab == README_TAB_INDEX) (readmeScrollY / collapseDistancePx).coerceIn(0f, 1f) else 0f
    // 头部自然高度只在实际展开时更新：收起过程中的测量值会被压缩后的约束污染，
    // 若持续更新会形成"越收越小"的反馈回路。
    var headerHeightPx by remember { mutableIntStateOf(0) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // 水平 padding 移到各子段：README 段由 EnhancedMarkdownViewer 自带 37dp
                // （用户实测目标边距）接管——全局 16 + 内层 37 = 53dp 太宽（2026-08-17 真机）
                .padding(top = 16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 高度收缩 + 裁剪 = "收起"；alpha 同步淡出 = "渐隐"
                    .height(
                        with(LocalDensity.current) {
                            (headerHeightPx * (1f - headerCollapse)).toDp()
                        },
                    ).clipToBounds()
                    .padding(horizontal = 16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .onSizeChanged { size -> if (headerCollapse == 0f) headerHeightPx = size.height }
                        .graphicsLayer {
                            alpha = 1f - headerCollapse
                            // 向上位移收进裁剪区，避免"高度在收、内容却在原地下沉"的割裂感
                            translationY = -headerCollapse * headerHeightPx * HEADER_COLLAPSE_DRIFT
                        },
            ) {
                RepoHeader(
                    state = state,
                    onToggleStar = managementCallbacks.onToggleStar,
                    onToggleWatch = managementCallbacks.onToggleWatch,
                    onFork = managementCallbacks.onFork,
                    onTopicClick = onTopicClick,
                )
            }
        }

        // 头部收起时不再需要那段留白，否则会留下一块"空气隙"
        Spacer(modifier = Modifier.height(16.dp * (1f - headerCollapse)))

        Box(Modifier.padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(text = stringResource(R.string.repo_tab_readme)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(text = stringResource(R.string.repo_tab_files)) },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text(text = stringResource(R.string.repo_tab_releases)) },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (tab) {
            0 -> {
                ReadmeSection(
                    readmeState = state.readmeState,
                    actions = actions,
                    onRetryReadme = onRetryReadme,
                    baseRepoUrl = buildRepoUrl(state.repo),
                    onScrollChanged = { readmeScrollY = it },
                )
            }

            1 -> {
                FilesTab(
                    filesState = filesState,
                    filesViewModel = filesViewModel,
                    isLoggedIn = state.isLoggedIn,
                    defaultBranch = state.repo.defaultBranch,
                    currentRef = filesState.currentRef,
                    initialRef = initialRef,
                    initialTreePath = initialTreePath,
                    onBranchesClick = onBranchesClick,
                )
            }

            else -> {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    ReleasesSection(
                        state = state,
                        actions = actions,
                        callbacks = managementCallbacks,
                        initialTag = initialReleaseTag,
                    )
                }
            }
        }
    }
}

/** 文件 Tab（T22 新建文件 + T23 分支入口）：分支 Chip（当前分支） + 新建文件（登录态）+ 文件树。 */
@Composable
private fun FilesTab(
    filesState: RepoFilesUiState,
    filesViewModel: RepoFilesViewModel,
    isLoggedIn: Boolean,
    defaultBranch: String?,
    currentRef: String?,
    initialRef: String? = null,
    initialTreePath: String = "",
    onBranchesClick: () -> Unit,
) {
    Box(Modifier.padding(horizontal = 16.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                InputChip(
                    selected = false,
                    onClick = onBranchesClick,
                    label = { Text(text = currentRef ?: defaultBranch ?: stringResource(R.string.repo_branches)) },
                )
                if (isLoggedIn) {
                    TextButton(
                        onClick = { filesViewModel.startNewFile() },
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.repo_file_new))
                    }
                }
            }
            FileTreeSection(
                treeState = filesState.treeState,
                defaultBranch = defaultBranch,
                initialRef = initialRef,
                initialTreePath = initialTreePath,
                viewModel = filesViewModel,
            )
        }
    }
}

@Composable
private fun RepoHeader(
    state: RepoDetailUiState.Success,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
    onTopicClick: (String) -> Unit = {},
) {
    val repo = state.repo
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = "https://github.com/${repo.ownerLogin}.png",
                    contentDescription = stringResource(R.string.repo_avatar),
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.extraLarge)
                            // #90 共享元素试点：与列表仓库行头像同 key，返回时平滑回缩
                            .sharedTransitionElement(key = "repo-avatar-${repo.ownerLogin}"),
                    contentScale = ContentScale.Crop,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    repo.description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // L06：Topics chip 行（空列表不渲染该行；点击 → 搜索页 query=topic:xxx）
            if (state.topics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TopicsRow(topics = state.topics, onTopicClick = onTopicClick)
            }

            Spacer(modifier = Modifier.height(12.dp))

            RepoStatsRow(repo = repo)

            // T12：登录态才显示操作按钮（游客只读）
            if (state.isLoggedIn) {
                Spacer(modifier = Modifier.height(12.dp))
                ManagementButtons(
                    isStarred = state.isStarred,
                    isWatching = state.isWatching,
                    pendingAction = state.pendingAction,
                    onToggleStar = onToggleStar,
                    onToggleWatch = onToggleWatch,
                    onFork = onFork,
                )
            }

            // T12：语言栏（Linguist 数据，按字节占比渲染）
            if (state.languages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                LanguageBar(languages = state.languages)
            }
        }
    }
}

/**
 * Topics chip 行（L06）。
 *
 * 容器色复用 designsystem 的 [labelChipContainerColor] / [labelChipContentColor]
 * （labelColor 与 surface 按 55/45 混合 → 低饱和底 + 可控对比度）；组件本体用 M3
 * [AssistChip]——designsystem 只提供颜色令牌、没有现成 LabelChip 组件（issue #85 现状）。
 * 点击 → 搜索页 query=`topic:xxx`（宿主接线）。
 */
@Composable
private fun TopicsRow(
    topics: List<String>,
    onTopicClick: (String) -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        topics.forEach { topic ->
            val container = labelChipContainerColor(labelColor = MaterialTheme.colorScheme.primaryContainer, surface = surface)
            val contentColor =
                labelChipContentColor(
                    container = container,
                    onSurface = MaterialTheme.colorScheme.onSurface,
                    surface = surface,
                )
            val description = stringResource(R.string.repo_topic_search_cd, topic)
            AssistChip(
                onClick = { onTopicClick(topic) },
                label = {
                    Text(
                        text = topic,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = contentColor),
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

/** 仓库统计行：Star 数 / Fork 数 / 主语言。 */
@Composable
private fun RepoStatsRow(repo: Repository) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_stars, repo.stargazerCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_forks, repo.forkCount),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        repo.language?.let { lang ->
            Text(
                text = stringResource(R.string.repo_language, lang),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Star/Watch/Fork 操作按钮行（登录态显示；pendingAction 期间禁用防重入）。
 * Material You 风格：使用 FilledTonalButton 提供适中的视觉重量。
 *
 * Star 动画（#167 / UI07，ui-design §4.2 H2-7 用户确认「星形弹跳可以」+ §4.3 回弹）：
 * 状态翻转时星形做一次 **1.0 → 1.25 → 1.0 的 spring 回弹**，颜色在 primary 与
 * onSurfaceVariant 之间过渡。时长/物理曲线全部走 AppMotion 令牌（尊重系统「减弱动画」：
 * 缩放为 0 时动画即时完成，视觉上仍是"状态直接切换"，不会卡住）。
 */
@Composable
private fun ManagementButtons(
    isStarred: Boolean,
    isWatching: Boolean,
    pendingAction: RepoAction?,
    onToggleStar: () -> Unit,
    onToggleWatch: () -> Unit,
    onFork: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Star 微缩放 + 颜色过渡（UI07）：只在状态真正翻转时播一次，首次组合不播
        // （#167 / UI17 补齐：原实现首次组合也会弹一下——进详情页时星星无故自弹，
        //   且与「滚动/进场中不触发动画」的约束相悖；这里用 rememberSaveable 记账跳过首帧）
        val starScale = remember { Animatable(1f) }
        var starFirstComposition by rememberSaveable { mutableStateOf(true) }
        val starTint by
            animateColorAsState(
                targetValue =
                    if (isStarred) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                animationSpec = tween(AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE)),
                label = "star-tint",
            )
        // 减弱动画（滑轮到底 / 系统「移除动画」）→ 直接跳过弹跳，状态色照常切换
        val starBounceEnabled = AppMotion.scaledDuration(AppMotion.DURATION_SMALL_STATE_CHANGE) > 0
        LaunchedEffect(isStarred) {
            if (starFirstComposition) {
                starFirstComposition = false
            } else if (starBounceEnabled && starScale.value == 1f) {
                starScale.animateTo(
                    STAR_BOUNCE_SCALE,
                    spring(dampingRatio = AppMotion.DampingRatioHighBouncy, stiffness = AppMotion.StiffnessMedium),
                )
                starScale.animateTo(1f, spring(dampingRatio = AppMotion.DampingRatioHighBouncy, stiffness = AppMotion.StiffnessMedium))
            }
        }
        FilledTonalButton(
            onClick = onToggleStar,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = starTint,
                modifier =
                    Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = starScale.value
                            scaleY = starScale.value
                        },
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (isStarred) R.string.repo_unstar else R.string.repo_star),
                maxLines = 1,
            )
        }
        FilledTonalButton(
            onClick = onToggleWatch,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector =
                    if (isWatching) {
                        MaterialSymbols.Rounded.Visibility
                    } else {
                        MaterialSymbols.Rounded.Visibility_off
                    },
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (isWatching) R.string.repo_unwatch else R.string.repo_watch),
                maxLines = 1,
            )
        }
        FilledTonalButton(
            onClick = onFork,
            enabled = pendingAction == null,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (pendingAction == RepoAction.FORK) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Call_split,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.repo_fork),
                maxLines = 1,
            )
        }
    }
}

/**
 * 语言栏：按 Linguist 字节占比渲染分段条 + 图例（语言名 + 百分比）。
 * 颜色循环取 MaterialTheme 色板（零硬编码颜色）。
 */
@Composable
private fun LanguageBar(languages: Map<String, Long>) {
    val total = languages.values.sum().toFloat()
    if (total <= 0f) return
    val colors = languageBarColors()

    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    // 形状令牌（ui-design §1.1-5）：4dp == AppDimens.cornerExtraSmall（scale=1 时视觉不变）
                    .clip(MaterialTheme.shapes.extraSmall),
        ) {
            languages.entries.forEachIndexed { index, (_, bytes) ->
                Box(
                    modifier =
                        Modifier
                            .weight(bytes / total)
                            .fillMaxHeight()
                            .background(colors[index % colors.size]),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            languages.entries.forEachIndexed { index, (name, bytes) ->
                val percent = (bytes * 100 / total).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors[index % colors.size]),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.repo_language_percent, name, percent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 语言栏分段色板（MaterialTheme 色板循环，零硬编码颜色）。 */
@Composable
private fun languageBarColors(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
    )

/**
 * Releases/Tags 分区（第三个 Tab）。
 *
 * 内部 FilterChip 切换 Releases/Tags 子列表；Release 点击 → 页内展开详情（不进导航）。
 * 进入分区时懒加载两个列表（ensure* 幂等：Loaded 不重复拉取）。
 */
@Composable
private fun ReleasesSection(
    state: RepoDetailUiState.Success,
    actions: RepoDetailActions,
    callbacks: RepoManagementCallbacks,
    initialTag: String = "",
) {
    LaunchedEffect(Unit) {
        callbacks.onEnsureReleasesLoaded()
        callbacks.onEnsureTagsLoaded()
    }

    // RELEASE 深链（/releases/tag/{tag}）：列表就绪后按 tag 精确匹配并展开该 Release 详情；
    // 匹配不到（tag 只有 git tag、或已删除/草稿被过滤）就停在列表 —— 深链不弹错误。
    // one-shot：用户收起详情/切 Tab 返回后不再重展开（rememberSaveable 跨配置变更保留）。
    var initialTagConsumed by rememberSaveable(initialTag) { mutableStateOf(false) }
    LaunchedEffect(state.releasesState, initialTag) {
        if (initialTagConsumed || initialTag.isBlank()) return@LaunchedEffect
        val loaded = state.releasesState as? ReleasesState.Loaded ?: return@LaunchedEffect
        val releaseId = findReleaseIdByTag(loaded.releases, initialTag) ?: return@LaunchedEffect
        initialTagConsumed = true
        callbacks.onReleaseClick(releaseId)
    }

    if (state.expandedReleaseId != null) {
        ReleaseDetailView(
            detailState = state.releaseDetailState,
            baseRepoUrl = buildRepoUrl(state.repo),
            actions = actions,
            canUpload = state.canPushRepo,
            uploading = state.pendingAssetUpload,
            onBack = callbacks.onCollapseRelease,
            onRetry = { callbacks.onReleaseClick(state.expandedReleaseId) },
            onUploadAsset = callbacks.onUploadAsset,
        )
        return
    }

    var subTab by rememberSaveable { mutableIntStateOf(0) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = subTab == 0,
                    onClick = { subTab = 0 },
                    label = { Text(text = stringResource(R.string.repo_subtab_releases)) },
                )
                FilterChip(
                    selected = subTab == 1,
                    onClick = { subTab = 1 },
                    label = { Text(text = stringResource(R.string.repo_subtab_tags)) },
                )
            }
            // L05：写权限才显示「新建 Release」（permissions 缺失 → 保守隐藏）
            if (state.canPushRepo) {
                TextButton(onClick = callbacks.onCreateRelease) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.repo_release_new))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (subTab) {
            0 -> {
                ReleasesList(
                    state = state.releasesState,
                    onReleaseClick = callbacks.onReleaseClick,
                    onRetry = callbacks.onEnsureReleasesLoaded,
                )
            }

            else -> {
                TagsList(state = state.tagsState, onRetry = callbacks.onEnsureTagsLoaded)
            }
        }
    }
}

@Composable
private fun ReleasesList(
    state: ReleasesState,
    onReleaseClick: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is ReleasesState.Idle, is ReleasesState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 分区首载 → M3 Expressive 形变加载指示（ADR-0008）
                LoadingIndicator()
            }
        }

        is ReleasesState.Error -> {
            ErrorContent(
                errorType = state.errorType,
                onRetry = onRetry,
            )
        }

        is ReleasesState.Loaded -> {
            if (state.releases.isEmpty()) {
                EmptyHint(text = stringResource(R.string.repo_releases_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.releases, key = { it.id }) { release ->
                        ReleaseCard(release = release, onClick = { onReleaseClick(release.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseCard(
    release: Release,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (release.prerelease) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ReleaseBadge(text = stringResource(R.string.repo_release_prerelease))
                }
                if (release.draft) {
                    Spacer(modifier = Modifier.width(8.dp))
                    ReleaseBadge(text = stringResource(R.string.repo_release_draft))
                }
            }
            release.name?.let { name ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = releaseMetaText(release),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Release 元信息：日期 · 作者（作者缺失时只显示日期）。 */
@Composable
private fun releaseMetaText(release: Release): String {
    val author = release.authorLogin
    val date = release.publishedAt?.let { RELEASE_DATE_FORMAT.format(it.atZone(ZoneId.systemDefault())) } ?: ""
    return when {
        date.isBlank() && author.isNullOrBlank() -> ""
        author.isNullOrBlank() -> date
        date.isBlank() -> author
        else -> stringResource(R.string.repo_release_meta, date, author)
    }
}

@Composable
private fun ReleaseBadge(text: String) {
    Box(
        modifier =
            Modifier
                // 形状令牌（ui-design §1.1-5）：4dp == AppDimens.cornerExtraSmall（scale=1 时视觉不变）
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun TagsList(
    state: TagsState,
    onRetry: () -> Unit,
) {
    when (state) {
        is TagsState.Idle, is TagsState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 分区首载 → M3 Expressive 形变加载指示（ADR-0008）
                LoadingIndicator()
            }
        }

        is TagsState.Error -> {
            ErrorContent(
                errorType = state.errorType,
                onRetry = onRetry,
            )
        }

        is TagsState.Loaded -> {
            if (state.tags.isEmpty()) {
                EmptyHint(text = stringResource(R.string.repo_tags_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.tags, key = { it.name }) { tag ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Tag,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Release 详情（页内展开，不进导航）。
 *
 * 正文用原生 [EnhancedMarkdownViewer]（短文本通道，铁律「评论列表绝不用 WebView」精神
 * 下 Release 正文同样保持原生；链接统一经 [handleParsedUrl] 分发）。
 */
@Composable
private fun ReleaseDetailView(
    detailState: ReleaseDetailState,
    baseRepoUrl: String,
    actions: RepoDetailActions,
    canUpload: Boolean = false,
    uploading: Boolean = false,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUploadAsset: (Long, String, ByteArray) -> Unit = { _, _, _ -> },
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.repo_release_back),
                )
            }
            Text(
                text = stringResource(R.string.repo_tab_releases),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (detailState) {
            is ReleaseDetailState.Idle, is ReleaseDetailState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 分区首载 → M3 Expressive 形变加载指示（ADR-0008）
                    LoadingIndicator()
                }
            }

            is ReleaseDetailState.Error -> {
                ErrorContent(
                    errorType = detailState.errorType,
                    onRetry = onRetry,
                )
            }

            is ReleaseDetailState.Loaded -> {
                ReleaseDetailContent(
                    detail = detailState,
                    baseRepoUrl = baseRepoUrl,
                    actions = actions,
                    canUpload = canUpload,
                    uploading = uploading,
                    onUploadAsset = onUploadAsset,
                )
            }
        }
    }
}

/**
 * Release 详情正文（标题/徽章/元信息/正文 Markdown + L05 附件列表）。
 *
 * 附件点击 → Custom Tabs 下载（[RepoDetailActions.onOpenExternal]，复用 T3 已落地的外部链接通道）；
 * [canUpload] 时提供「上传附件」入口（SAF 选文件 → 字节经 ViewModel 上传）。
 */
@Composable
private fun ReleaseDetailContent(
    detail: ReleaseDetailState.Loaded,
    baseRepoUrl: String,
    actions: RepoDetailActions,
    canUpload: Boolean = false,
    uploading: Boolean = false,
    onUploadAsset: (Long, String, ByteArray) -> Unit = { _, _, _ -> },
) {
    val release = detail.release
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = release.tagName,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (release.prerelease) {
                Spacer(modifier = Modifier.width(8.dp))
                ReleaseBadge(text = stringResource(R.string.repo_release_prerelease))
            }
            if (release.draft) {
                Spacer(modifier = Modifier.width(8.dp))
                ReleaseBadge(text = stringResource(R.string.repo_release_draft))
            }
        }
        release.name?.let { name ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = releaseMetaText(release),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        val body = release.body
        if (body.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.repo_release_body_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            EnhancedMarkdownViewer(
                markdown = body,
                onInternalLink = { parsed -> handleParsedUrl(parsed, actions) },
                baseRepoUrl = baseRepoUrl,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ReleaseAssetsSection(
            assets = detail.assets,
            releaseId = release.id,
            canUpload = canUpload,
            uploading = uploading,
            actions = actions,
            onUploadAsset = onUploadAsset,
        )
    }
}

/**
 * Release 附件区（L05）：名称 / 大小 / 下载数，点击 → Custom Tabs 下载；写权限时提供上传入口。
 */
@Composable
private fun ReleaseAssetsSection(
    assets: List<ReleaseAsset>,
    releaseId: Long,
    canUpload: Boolean,
    uploading: Boolean,
    actions: RepoDetailActions,
    onUploadAsset: (Long, String, ByteArray) -> Unit,
) {
    val context = LocalContext.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val bytes = readAssetBytes(context, uri)
                if (bytes != null) {
                    onUploadAsset(releaseId, queryDisplayName(context, uri), bytes)
                }
            }
        }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.repo_release_assets),
                style = MaterialTheme.typography.titleSmall,
            )
            if (canUpload) {
                TextButton(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !uploading,
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.repo_release_asset_upload))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (assets.isEmpty()) {
            EmptyHint(text = stringResource(R.string.repo_release_assets_empty))
        } else {
            assets.forEach { asset ->
                Card(
                    onClick = { asset.downloadUrl?.let(actions.onOpenExternal) },
                    // 无直链（异常数据）时不可点，避免"点了没反应"
                    enabled = asset.downloadUrl != null,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = asset.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text =
                                stringResource(
                                    R.string.repo_release_asset_meta,
                                    formatFileSize(asset.size),
                                    asset.downloadCount,
                                ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 字节数 → 人类可读大小（单位文案走 stringResource，零硬编码文案）。 */
@Composable
internal fun formatFileSize(bytes: Long): String =
    when {
        bytes < KIB -> stringResource(R.string.repo_release_asset_size_b, bytes)
        bytes < KIB * KIB -> stringResource(R.string.repo_release_asset_size_kb, bytes / KIB)
        bytes < KIB * KIB * KIB -> stringResource(R.string.repo_release_asset_size_mb, bytes / (KIB * KIB))
        else -> stringResource(R.string.repo_release_asset_size_gb, bytes / (KIB * KIB * KIB))
    }

/** 1 KiB（单位换算常量） */
private const val KIB = 1024L

/** SAF Uri → 显示文件名（OpenableColumns 查询失败时回退路径末段）。 */
internal fun queryDisplayName(
    context: Context,
    uri: Uri,
): String {
    val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
    cursor?.use { c ->
        if (c.moveToFirst() && !c.isNull(0)) {
            return c.getString(0)
        }
    }
    return uri.lastPathSegment ?: DEFAULT_ASSET_NAME
}

/** SAF Uri → 字节内容（读取失败返回 null，UI 忽略该次选择）。 */
internal fun readAssetBytes(
    context: Context,
    uri: Uri,
): ByteArray? =
    try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: IOException) {
        // SAF 读取失败（文件被删/撤销授权）：记日志后按「未选择」处理，UI 不弹错
        Log.w(TAG, "附件读取失败: ${e.message}")
        null
    }

/** 附件名兜底 */
private const val DEFAULT_ASSET_NAME = "asset"

@Composable
internal fun EmptyHint(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadmeSection(
    readmeState: ReadmeState,
    actions: RepoDetailActions,
    onRetryReadme: () -> Unit,
    baseRepoUrl: String,
    /** #167 / UI10：把 WebView 内部滚动上报给上层，用于 README 下滑收起头部 */
    onScrollChanged: (Int) -> Unit = {},
) {
    // 图片全屏查看（#166 / UI11）：README 正文里的图片此前点了没反应（onImageClick 是空桩）
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    when (readmeState) {
        is ReadmeState.Loading -> {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                // 分区首载 → M3 Expressive 形变加载指示（ADR-0008）
                LoadingIndicator()
            }
        }

        is ReadmeState.Empty -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Text(
                    text = stringResource(R.string.repo_readme_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        is ReadmeState.Loaded -> {
            // 渲染通道日志（Q7 复测锚点）：logcat 过滤 ReadmeRender；Log.i 因 vivo [log.tag]=[I] 过滤 Debug 级
            Log.i(TAG, "renderMode=${readmeState.renderMode}")
            WebViewMarkdownRenderer(
                sanitizedHtml = readmeState.content,
                tokenProvider = { null },
                bridgeCallback = createBridgeCallback(actions) { previewImageUrl = it },
                baseRepoUrl = baseRepoUrl,
                renderMode = readmeState.webViewRenderMode,
                onScrollChanged = onScrollChanged,
            )
        }

        is ReadmeState.Error -> {
            ErrorContent(
                errorType = readmeState.errorType,
                onRetry = onRetryReadme,
            )
        }
    }

    // 全屏图片查看（纯黑背景 + 向上 fade in，ui-design §3.11 9g）
    AppImageOverlay(
        imageUrl = previewImageUrl,
        onDismiss = { previewImageUrl = null },
    )
}

/**
 * 链接统一分发：内部链接 → 应用内导航；外部链接 → CustomTabs；纯锚点（#xxx）忽略
 * （WebView 内锚点由页面自身处理，原生渲染器无滚动定位，忽略即可；
 * 文件查看器的 .md Rendered 模式共用此逻辑）。
 */
internal fun handleParsedUrl(
    parsed: ParsedUrl,
    actions: RepoDetailActions,
) {
    if (parsed is ParsedUrl.External) {
        if (parsed.url.startsWith("#")) return
        actions.onOpenExternal(parsed.url)
    } else {
        actions.onNavigateToParsedUrl(parsed)
    }
}

/**
 * WebView bridge callback：链接/复制已接线（T9 验收第 3 条），
 * 图片预览自 #166 起接到 [AppImageOverlay]（此前是空桩）；任务列表写回仍留待 T14。
 *
 * @param onImageClick 图片点击 → 全屏查看（由调用方持有状态）
 */
@Suppress("EmptyFunctionBlock") // onCheckboxClick/onHeightChanged 为 T14 占位桩
@Composable
private fun createBridgeCallback(
    actions: RepoDetailActions,
    onImageClick: (String) -> Unit,
): MarkdownBridgeCallback {
    val context = LocalContext.current
    return object : MarkdownBridgeCallback {
        override fun onExternalLink(url: String) {
            // 纯锚点（#xxx）由页面自身处理，不拦截
            if (url.startsWith("#")) return
            actions.onOpenExternal(url)
        }

        override fun onInternalLink(parsed: ParsedUrl) {
            actions.onNavigateToParsedUrl(parsed)
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
        ) {}

        override fun onHeightChanged(heightPx: Int) {}
    }
}

/**
 * 加载失败态（仓库详情 / README / 文件 / Release 各分区共用）。
 *
 * **Retry 只给可重试的错误**（#201 要求 2，判定见 [RepoErrorType.isRetryable]）：
 * 404 类是确定性失败 —— [RepoErrorType.NOT_FOUND]（仓库不存在）、
 * [RepoErrorType.PATH_NOT_FOUND]（文件已删除/改名）重试必然原样再失败，
 * 旧实现却照样画一个 Retry（CI 帧 `editor.png`：文件 404 上挂着
 * 「Repository not found」+ Retry，点了几次都没用），这类状态的出口是顶栏返回。
 * 网络/超时（[RepoErrorType.NETWORK] / [RepoErrorType.UNKNOWN]）与
 * [RepoErrorType.FORBIDDEN]（403 也可能只是限流）保留重试入口。
 */
@Composable
internal fun ErrorContent(
    errorType: RepoErrorType,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage(errorType),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
            if (errorType.isRetryable) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text(text = stringResource(R.string.repo_retry))
                }
            }
        }
    }
}

/** 错误类型 → 本地化文案（ViewModel 只传类型，不产英文） */
@Composable
private fun errorMessage(errorType: RepoErrorType): String =
    when (errorType) {
        RepoErrorType.FORBIDDEN -> stringResource(R.string.repo_error_forbidden)
        RepoErrorType.NOT_FOUND -> stringResource(R.string.repo_error_not_found)
        RepoErrorType.PATH_NOT_FOUND -> stringResource(R.string.repo_error_path_not_found)
        RepoErrorType.NETWORK -> stringResource(R.string.repo_error_network)
        RepoErrorType.UNKNOWN -> stringResource(R.string.repo_error_unknown)
    }

/** Repository → GitHub 仓库页 URL（相对链接解析基址，2026-08-14 修复） */
private fun buildRepoUrl(repo: Repository): String = "https://github.com/${repo.ownerLogin}/${repo.name}"

/** Release 发布日期格式（yyyy-MM-dd，本地时区） */
private val RELEASE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

/** README 分区索引（头部收起只在 README 分区生效，见 RepoDetailContent 注释） */
private const val README_TAB_INDEX = 0

/** 文件分区索引（TREE 深链初始落点） */
private const val FILES_TAB_INDEX = 1

/** Releases 分区索引（RELEASE 深链初始落点） */
private const val RELEASES_TAB_INDEX = 2

/**
 * 深链初始分区（TREE → 文件 / RELEASE → Releases；其余 → README）。
 *
 * 两个提示同真不会来自解析器（一次只解析一个 URL）；若同真，文件优先——
 * TREE 语义更强（携带路径定位），且 showFiles 无路径时也比 Releases 更接近链接原意。
 */
internal fun repoDetailInitialTab(
    showFiles: Boolean,
    showReleases: Boolean,
): Int =
    when {
        showFiles -> FILES_TAB_INDEX
        showReleases -> RELEASES_TAB_INDEX
        else -> README_TAB_INDEX
    }

/**
 * Releases 列表里按 tag 精确匹配（GitHub tag 区分大小写；前缀相同不算命中，如 v1 ≠ v1.0）。
 * 未命中返回 null = 停在列表（该 tag 可能只有 git tag，没有对应 Release）。
 */
internal fun findReleaseIdByTag(
    releases: List<Release>,
    tag: String,
): Long? = releases.firstOrNull { it.tagName == tag }?.id

/**
 * README 下滑多少距离后头部完全收起（#167 / UI10，A 版跟手渐隐）。
 *
 * 120dp 的取法：约等于仓库头可视高度的一半 —— 太短会"一滑就没"（失去跟手感），
 * 太长则读正文时头部长期占着大半屏。
 */
private val HEADER_COLLAPSE_DISTANCE = 120.dp

/**
 * 收起过程中头部内容向上位移的比例（相对自然高度）。
 *
 * 只收缩容器高度而不位移的话，内容会"原地被裁"，看起来像被切掉而不是收起来。
 * 0.6 让内容在收起过程中向上滑走，观感更接近"折叠"。
 */
private const val HEADER_COLLAPSE_DRIFT = 0.6f
