@file:Suppress("LongMethod", "CyclomaticComplexMethod")
// 查看器状态分支（CODE/MARKDOWN/大文件/二进制）+ T22 编辑/搜索/跳转动作区 + 跳转对话框，
// 圈复杂度到达 15 阈值边界，属屏幕装配固有结构，拆散反损可读性

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Data_object
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Format_list_numbered
import com.composables.icons.materialsymbols.rounded.Search
import com.yumiru11.githubapp.core.editor.CodeEditorController
import com.yumiru11.githubapp.core.editor.CodeEditorView
import com.yumiru11.githubapp.core.editor.CodeLanguageDetector
import com.yumiru11.githubapp.core.editor.EditorThemeTokens
import com.yumiru11.githubapp.core.editor.rememberM3EditorThemeTokens
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.ui.RepoDetailActions

/**
 * 文件查看器（T11 验收 2-5 条 + T22 编辑入口）。
 *
 * - CODE：Sora 只读高亮（行号/横向滚动）+ 搜索（sora 内置搜索模式）+ 跳转行 + 编辑（T22）
 * - MARKDOWN：Rendered（[MarkdownViewer] 原生渲染）/ Source（Sora）切换 + 编辑（T22，
 *   D1 决策：md 编辑统一走可提交编辑器，T21 无提交入口摘除）
 * - TOO_LARGE / BINARY：明确提示卡片而非尝试渲染
 *
 * 编辑器主题跟随 M3（[rememberM3EditorThemeTokens]，plan.md §8.2 映射表）。
 *
 * @param editable 是否显示编辑入口（游客只读：[RepoDetailScreen] 按登录态传入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    fileState: FileViewState,
    selectedPath: String,
    ref: String,
    viewModel: RepoFilesViewModel,
    actions: RepoDetailActions,
    baseRepoUrl: String,
    editable: Boolean = true,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorTokens = rememberM3EditorThemeTokens()
    val loaded = (fileState as? FileViewState.Loaded)?.data

    var editor by remember { mutableStateOf<CodeEditorController?>(null) }
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedPath,
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
                    when (loaded?.kind) {
                        FileKind.CODE -> {
                            if (editable) EditFileButton(viewModel)
                            IconButton(onClick = { editor?.startSearch() }) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Search,
                                    contentDescription = stringResource(R.string.repo_file_search),
                                )
                            }
                            IconButton(onClick = { showJumpDialog = true }) {
                                Icon(
                                    imageVector = MaterialSymbols.Rounded.Format_list_numbered,
                                    contentDescription = stringResource(R.string.repo_file_jump_to_line),
                                )
                            }
                        }

                        FileKind.MARKDOWN -> {
                            // T22 D1：md 编辑统一走可提交编辑器（T21 无提交入口摘除）
                            if (editable) EditFileButton(viewModel)
                        }

                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = fileState) {
                is FileViewState.Idle,
                is FileViewState.Loading,
                -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is FileViewState.Error -> {
                    ErrorContent(
                        errorType = state.errorType,
                        onRetry = { viewModel.retryLoadFile(ref) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is FileViewState.Loaded -> {
                    val data = state.data
                    when (data.kind) {
                        FileKind.TOO_LARGE -> {
                            FilePromptCard(
                                icon = { tint ->
                                    Icon(
                                        MaterialSymbols.Rounded.Data_object,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(32.dp),
                                    )
                                },
                                text = stringResource(R.string.repo_file_too_large),
                            )
                        }

                        FileKind.BINARY -> {
                            FilePromptCard(
                                icon = { tint ->
                                    Icon(
                                        MaterialSymbols.Rounded.Data_object,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(32.dp),
                                    )
                                },
                                text = stringResource(R.string.repo_file_binary),
                            )
                        }

                        FileKind.MARKDOWN -> {
                            MarkdownFileContent(data = data, baseRepoUrl = baseRepoUrl, actions = actions, editorTokens = editorTokens)
                        }

                        FileKind.CODE -> {
                            CodeEditorView(
                                content = data.text.orEmpty(),
                                grammarFileName = CodeLanguageDetector.grammarForFile(data.fileName),
                                themeTokens = editorTokens,
                                onEditorReady = { editor = it },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJumpDialog) {
        var lineInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text(text = stringResource(R.string.repo_file_jump_to_line)) },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter(Char::isDigit).take(6) },
                    label = { Text(text = stringResource(R.string.repo_file_line_number)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lineInput.toIntOrNull()?.let { editor?.jumpToLine(it) }
                        showJumpDialog = false
                    },
                ) {
                    Text(text = stringResource(R.string.repo_file_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text(text = stringResource(R.string.repo_file_cancel))
                }
            },
        )
    }
}

/** T22：进入可提交文件编辑（CODE/MARKDOWN 文本文件）。 */
@Composable
private fun EditFileButton(viewModel: RepoFilesViewModel) {
    IconButton(onClick = { viewModel.startEdit() }) {
        Icon(
            imageVector = MaterialSymbols.Rounded.Edit,
            contentDescription = stringResource(R.string.repo_file_edit),
        )
    }
}

/** Markdown 文件内容：Rendered（原生渲染）/ Source（Sora）切换。 */
@Composable
private fun MarkdownFileContent(
    data: FileContentData,
    baseRepoUrl: String,
    actions: RepoDetailActions,
    editorTokens: EditorThemeTokens,
) {
    var showSource by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            TextButton(onClick = { showSource = false }) {
                Text(
                    text = stringResource(R.string.repo_file_rendered),
                    color = if (showSource) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = { showSource = true }) {
                Text(
                    text = stringResource(R.string.repo_file_source),
                    color = if (showSource) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showSource) {
            CodeEditorView(
                content = data.text.orEmpty(),
                grammarFileName = CodeLanguageDetector.grammarForFile(data.fileName),
                themeTokens = editorTokens,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            EnhancedMarkdownViewer(
                markdown = data.text.orEmpty(),
                onInternalLink = { parsed -> handleParsedUrl(parsed, actions) },
                baseRepoUrl = baseRepoUrl,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** 大文件/二进制提示卡片。 */
@Composable
private fun FilePromptCard(
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                icon(MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
