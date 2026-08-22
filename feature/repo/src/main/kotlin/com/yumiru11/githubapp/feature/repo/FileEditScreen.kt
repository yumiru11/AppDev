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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Delete
import com.composables.icons.materialsymbols.rounded.Edit
import com.yumiru11.githubapp.core.editor.CodeEditorView
import com.yumiru11.githubapp.core.editor.CodeLanguageDetector
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
 * @param editState 编辑状态（Idle 时本屏幕不应显示；由上层 [RepoDetailScreen] 控制可见性）
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

    // 提交表单（每次打开清空由关闭时重置）
    var commitPath by rememberSaveable { mutableStateOf("") }
    var commitMessage by rememberSaveable { mutableStateOf("") }
    var useNewBranch by rememberSaveable { mutableStateOf(false) }
    var newBranchName by rememberSaveable { mutableStateOf("") }
    var deleteMessage by rememberSaveable { mutableStateOf("") }

    Scaffold(
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
                            modifier = Modifier.padding(12.dp).size(20.dp),
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
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // ── 提交对话框：message 必填 + 分支模式 + 新建路径 ──────────────────────────
    if (showCommitDialog && editing != null) {
        val canCommit =
            commitMessage.isNotBlank() &&
                (!useNewBranch || (newBranchName.trim().isNotBlank() && !newBranchName.contains(' '))) &&
                (!isNew || commitPath.trim().isNotBlank())
        AlertDialog(
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
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        label = { Text(text = stringResource(R.string.repo_file_commit_message)) },
                        placeholder = { Text(text = stringResource(R.string.repo_file_commit_message_hint)) },
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                        Spacer(modifier = Modifier.height(8.dp))
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
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.repo_file_conflict_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.repo_file_conflict_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (c.operation == ConflictOperation.UPDATE) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.repo_file_conflict_overwrite_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = stringResource(R.string.repo_file_delete)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.repo_file_delete_confirm, displayPath),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
