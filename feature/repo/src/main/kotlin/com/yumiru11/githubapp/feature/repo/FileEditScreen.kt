@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// 屏幕装配（顶栏/编辑/预览/提交与冲突与删除对话框）结构固有，拆散反损可读性（T21/FileViewerScreen 先例）；
// 圈复杂度 35 来自三类对话框的状态分支，拆成独立函数会割裂状态流，精准抑制

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Search
import com.yumiru11.githubapp.core.designsystem.component.AppDialog
import com.yumiru11.githubapp.core.designsystem.component.AppScaffold
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.editor.CodeEditorController
import com.yumiru11.githubapp.core.editor.CodeEditorView
import com.yumiru11.githubapp.core.editor.CodeLanguageDetector
import com.yumiru11.githubapp.core.editor.TextFileFormat
import com.yumiru11.githubapp.core.editor.rememberM3EditorThemeTokens
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.ui.LocalRepoDetailActions
import com.yumiru11.githubapp.core.ui.RepoDetailActions

/**
 * 文件编辑屏幕（T22 文件编辑提交，plan.md §7.4）。
 *
 * - 顶栏：路径 + 删除（仅已有文件）+ 提交；提交进行中禁用操作并显示进度
 * - 编辑区：Sora 可编辑模式（TextMate 语法 + M3 主题，[CodeEditorView] editable）；
 *   Markdown 文件提供编辑/预览切换（预览与查看器 Rendered 态共用 [EnhancedMarkdownViewer]）
 * - 提交对话框：commit message 必填 + 分支模式（当前分支 / 新建分支——GitHub 自动创建，
 *   2026-08-22 实测）+ 新建文件路径输入
 * - 409 冲突对话框（绝不静默覆盖）：重载（拉最新）/ 覆盖（显式选择，用最新 sha 重交）/
 *   保留本地（复制剪贴板）；删除冲突：重载（回查看器刷新）/ 重试删除 / 取消
 * - 删除确认对话框：commit message 必填
 *
 * @param editState 编辑状态（Idle 时本屏幕不应显示；由上层 [FileEditHost] 控制可见性，仓库详情与 BLOB 深链两处入口共用）
 * @param filePath 当前文件路径（新建文件为 null，标题显示「新建文件」）
 * @param baseRepoUrl 仓库主页 URL（Markdown 预览相对链接基址）
 * @param defaultRef 当前查看分支（提交对话框「当前分支」文案）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEditScreen(
    editState: FileEditState,
    filePath: String?,
    baseRepoUrl: String,
    defaultRef: String,
    viewModel: RepoFilesViewModel,
    actions: RepoDetailActions = LocalRepoDetailActions.current,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorTokens = rememberM3EditorThemeTokens()
    val editing = editState as? FileEditState.Editing
    val submitting = editState as? FileEditState.Submitting
    val conflict = editState as? FileEditState.Conflict

    val isSubmitting = submitting != null
    val isNew = editing?.isNew ?: false
    val isMarkdown = editing?.isMarkdown ?: submitting?.isMarkdown ?: false
    val displayText = editing?.text ?: submitting?.text.orEmpty()
    val displayPath = filePath.orEmpty()

    var showCommitDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPreview by rememberSaveable { mutableStateOf(false) }

    // EDITOR-1：查找/替换（替换必须落在可编辑缓冲区 → 入口在编辑页；查看器保持只读 + 仅查找）；
    // 查找状态机与查询词由 RepoFilesViewModel 持有（与查看器同源），替换词是本页会话状态
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<CodeEditorController?>(null) }
    var replaceQuery by rememberSaveable { mutableStateOf("") }
    val findFocusRequester = remember { FocusRequester() }
    // 编辑器可见（Markdown 预览态的查找/替换栏无意义：编辑器不在屏上）
    val isEditing = !(isMarkdown && showPreview && !isNew)
    // EDITOR-1 状态行：打开时探测的原始格式（新建文件 = 默认 UTF-8 + LF）
    val textFormat = (uiState.fileState as? FileViewState.Loaded)?.data?.textFormat ?: TextFileFormat.DEFAULT

    // 展开查找栏即聚焦查询框（键盘随之上推面板）
    LaunchedEffect(uiState.isFindOpen) {
        if (uiState.isFindOpen) findFocusRequester.requestFocus()
    }

    // 提交表单（每次打开清空由关闭时重置）
    var commitPath by rememberSaveable { mutableStateOf("") }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var useNewBranch by rememberSaveable { mutableStateOf(false) }
    var newBranchName by rememberSaveable { mutableStateOf("") }
    var deleteMessage by rememberSaveable { mutableStateOf("") }

    AppScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) stringResource(R.string.repo_file_new) else displayPath,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.repo_back),
                        )
                    }
                },
                actions = {
                    // EDITOR-1：查找/替换面板（可编辑面）+ 软换行开关
                    if (isEditing && !isSubmitting) {
                        IconButton(
                            onClick = {
                                if (uiState.isFindOpen) {
                                    viewModel.closeFind()
                                    editor?.clearFindText()
                                } else {
                                    viewModel.openFind()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Search,
                                contentDescription = stringResource(R.string.repo_file_search),
                                tint =
                                    if (uiState.isFindOpen) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                            )
                        }
                        SoftWrapToggleButton()
                    }
                    if (!isNew && !isSubmitting) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = MaterialSymbols.Rounded.Delete,
                                contentDescription = stringResource(R.string.repo_file_delete),
                            )
                        }
                    }
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(AppDimens.spacing.m).size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = { showCommitDialog = true }) {
                            Text(text = stringResource(R.string.repo_file_commit))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Markdown 文件：编辑/预览切换（预览与查看器 Rendered 态共用渲染管线）
            if (isMarkdown && !isNew) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.xs)) {
                    TextButton(onClick = { showPreview = false }) {
                        Text(
                            text = stringResource(R.string.repo_file_edit),
                            color = if (showPreview) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                    }
                    TextButton(onClick = { showPreview = true }) {
                        Text(
                            text = stringResource(R.string.repo_file_preview),
                            color = if (showPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            // EDITOR-1：查找/替换栏（面板可编辑面才有替换行；查询词变更 → Sora 异步重扫）
            if (isEditing && uiState.isFindOpen) {
                FileFindReplaceBar(
                    state = uiState.findState,
                    replaceQuery = replaceQuery,
                    showReplace = true,
                    focusRequester = findFocusRequester,
                    onQueryChange = { query ->
                        viewModel.onFindQueryChanged(query)
                        editor?.findText(query)
                    },
                    onReplaceQueryChange = { replaceQuery = it },
                    onReplace = { editor?.replaceCurrent(replaceQuery) },
                    onReplaceAll = { editor?.replaceAll(replaceQuery) },
                    onPrevious = {
                        viewModel.onFindPrevious()
                        editor?.let { viewModel.onFindResults(it.findPrevious()) }
                    },
                    onNext = {
                        viewModel.onFindNext()
                        editor?.let { viewModel.onFindResults(it.findNext()) }
                    },
                    onClose = {
                        viewModel.closeFind()
                        editor?.clearFindText()
                    },
                    modifier = Modifier.padding(horizontal = AppDimens.spacing.m, vertical = AppDimens.spacing.xs),
                )
            }
            if (isMarkdown && showPreview && !isNew) {
                EnhancedMarkdownViewer(
                    markdown = displayText,
                    onInternalLink = { parsed -> handleParsedUrl(parsed, actions) },
                    baseRepoUrl = baseRepoUrl,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CodeEditorView(
                    content = displayText,
                    grammarFileName =
                        CodeLanguageDetector.grammarForFile(
                            displayPath.substringAfterLast('/').ifBlank { "new.txt" },
                        ),
                    themeTokens = editorTokens,
                    editable = !isSubmitting,
                    onTextChanged = { viewModel.onEditorTextChanged(it) },
                    onEditorReady = { controller ->
                        editor = controller
                        bindFindResults(viewModel, controller)
                        // 从查看器带入的查询词在新编辑器实例上重放（否则计数残留、高亮为空）
                        viewModel.uiState.value.findState.query
                            .takeIf { it.isNotEmpty() }
                            ?.let(controller::findText)
                    },
                    // weight：编辑器吃掉剩余高度，给下方格式状态行留位（fillMaxSize 会把状态行挤成 0 高）
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                // EDITOR-1 状态行：文件格式（保存按它还原行尾/字符集）
                TextFormatIndicator(textFormat)
            }
        }
    }

    // ── 提交对话框：message 必填 + 分支模式 + 新建路径 ──────────────────────────
    if (showCommitDialog && editing != null) {
        val canCommit =
            commitMessage.isNotBlank() &&
                (!useNewBranch || (newBranchName.trim().isNotBlank() && !newBranchName.contains(' '))) &&
                (!isNew || commitPath.trim().isNotBlank())
        AppDialog(
            onDismissRequest = { showCommitDialog = false },
            title = { Text(text = stringResource(R.string.repo_file_commit)) },
            text = {
                Column {
                    if (isNew) {
                        OutlinedTextField(
                            value = commitPath,
                            onValueChange = { commitPath = it },
                            label = { Text(text = stringResource(R.string.repo_file_new_path)) },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                    }
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text(text = stringResource(R.string.repo_file_commit_message)) },
                        placeholder = { Text(text = stringResource(R.string.repo_file_commit_message_hint)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = !useNewBranch, onClick = { useNewBranch = false })
                        Text(
                            text = stringResource(R.string.repo_file_branch_current, defaultRef),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = useNewBranch, onClick = { useNewBranch = true })
                        Text(
                            text = stringResource(R.string.repo_file_branch_new),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (useNewBranch) {
                        Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                        OutlinedTextField(
                            value = newBranchName,
                            onValueChange = { newBranchName = it },
                            label = { Text(text = stringResource(R.string.repo_file_new_branch_name)) },
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = canCommit,
                    onClick = {
                        viewModel.commitEdit(
                            message = commitMessage.trim(),
                            newBranchName = if (useNewBranch) newBranchName.trim() else null,
                            newFilePath = if (isNew) commitPath.trim() else null,
                        )
                        showCommitDialog = false
                        commitMessage = ""
                        newBranchName = ""
                        commitPath = ""
                        useNewBranch = false
                    },
                ) {
                    Text(text = stringResource(R.string.repo_file_commit))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommitDialog = false }) {
                    Text(text = stringResource(R.string.repo_file_cancel))
                }
            },
        )
    }

    // ── 409 冲突对话框：三选项，绝不静默覆盖 ────────────────────────────────
    conflict?.let { c ->
        BasicAlertDialog(onDismissRequest = { viewModel.dismissEdit() }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().padding(AppDimens.spacing.xl),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.repo_file_conflict_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                    Text(
                        text = stringResource(R.string.repo_file_conflict_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (c.operation == ConflictOperation.UPDATE) {
                        Spacer(modifier = Modifier.height(AppDimens.spacing.xs))
                        Text(
                            text = stringResource(R.string.repo_file_conflict_overwrite_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                    TextButton(onClick = { viewModel.reloadAfterConflict() }, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.repo_file_conflict_reload))
                    }
                    TextButton(onClick = { viewModel.overwriteAfterConflict() }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text =
                                stringResource(
                                    if (c.operation == ConflictOperation.UPDATE) {
                                        R.string.repo_file_conflict_overwrite
                                    } else {
                                        R.string.repo_file_conflict_retry_delete
                                    },
                                ),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(
                        onClick = {
                            if (c.operation == ConflictOperation.UPDATE) {
                                viewModel.keepLocalAfterConflict()
                            } else {
                                viewModel.dismissEdit()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text =
                                stringResource(
                                    if (c.operation == ConflictOperation.UPDATE) {
                                        R.string.repo_file_conflict_keep_local
                                    } else {
                                        R.string.repo_file_conflict_cancel
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }

    // ── 删除确认对话框：commit message 必填 ─────────────────────────────────
    if (showDeleteDialog && editing != null && !isNew) {
        AppDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.repo_file_delete)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.repo_file_delete_confirm, displayPath),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(AppDimens.spacing.s))
                    OutlinedTextField(
                        value = deleteMessage,
                        onValueChange = { deleteMessage = it },
                        label = { Text(text = stringResource(R.string.repo_file_commit_message)) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteMessage.isNotBlank(),
                    onClick = {
                        viewModel.deleteFile(deleteMessage.trim())
                        showDeleteDialog = false
                        deleteMessage = ""
                    },
                ) {
                    Text(
                        text = stringResource(R.string.repo_file_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(R.string.repo_file_cancel))
                }
            },
        )
    }
}

/**
 * 文件覆盖宿主（T22）：[editState] 非 [FileEditState.Idle] → 全屏 [FileEditScreen]；否则渲染 [content]。
 *
 * 为什么要共用：[RepoDetailScreen]（仓库详情文件 Tab）与 BLOB 深链路由（app 模块 `BlobRoute`）
 * 是文件查看的两个入口，都必须消费 [RepoFilesUiState.editState]。此前 BlobRoute 只渲染查看器、
 * 漏了编辑分支 —— ViewModel 已进入编辑态而界面仍停在查看器，「编辑」按钮点了没反应（深链编辑死路）。
 * 把「editState → FileEditScreen」固化在唯一组件里，两处入口不再可能各自漂移。
 *
 * @param actions 编辑区内 Markdown 预览的链接动作（缺省跟随 [LocalRepoDetailActions]）
 */
@Composable
fun FileEditHost(
    editState: FileEditState,
    filePath: String?,
    baseRepoUrl: String,
    defaultRef: String,
    viewModel: RepoFilesViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: RepoDetailActions = LocalRepoDetailActions.current,
    content: @Composable () -> Unit,
) {
    if (editState !is FileEditState.Idle) {
        FileEditScreen(
            editState = editState,
            filePath = filePath,
            baseRepoUrl = baseRepoUrl,
            defaultRef = defaultRef,
            viewModel = viewModel,
            actions = actions,
            onClose = onClose,
            modifier = modifier,
        )
    } else {
        content()
    }
}
