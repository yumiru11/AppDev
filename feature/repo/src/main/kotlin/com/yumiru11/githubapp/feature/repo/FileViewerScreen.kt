@file:Suppress("LongMethod", "CyclomaticComplexMethod", "LongParameterList")
// 查看器状态分支（CODE/MARKDOWN/大文件/二进制）+ T22 编辑入口 + 跳转对话框 + #166 UI14
// 悬浮工具层/查找面板接线，圈复杂度到达 15 阈值边界，属屏幕装配固有结构，拆散反损可读性
// （查找面板与工具条已拆为同文件私有 Composable）
// - LongParameterList：屏幕状态 hoist + 回调透传天然多参（#166 新增 findState/isFindOpen 两参），
//   与 HomeScreen / ReposScreen / IssueDetailScreen / NotificationsPanel 同款先例

package com.yumiru11.githubapp.feature.repo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Close
import com.composables.icons.materialsymbols.rounded.Data_object
import com.composables.icons.materialsymbols.rounded.Edit
import com.composables.icons.materialsymbols.rounded.Format_list_numbered
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_down
import com.composables.icons.materialsymbols.rounded.Keyboard_arrow_up
import com.composables.icons.materialsymbols.rounded.Search
import com.yumiru11.githubapp.core.editor.CodeEditorController
import com.yumiru11.githubapp.core.editor.CodeEditorView
import com.yumiru11.githubapp.core.editor.CodeLanguageDetector
import com.yumiru11.githubapp.core.editor.EditorThemeTokens
import com.yumiru11.githubapp.core.editor.FileFindState
import com.yumiru11.githubapp.core.editor.rememberM3EditorThemeTokens
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.ui.RepoDetailActions

/**
 * 文件查看器（T11 验收 2-5 条 + T22 编辑入口 + #166 UI14 文件内查找）。
 *
 * - CODE：Sora 只读高亮（行号/横向滚动）；右下角悬浮工具层（查找 / 跳行，ui-design §3.10）
 *   + 查找面板（输入 / 上下一处 / 「第 n / 共 m 项」/ 关闭）+ 编辑（T22，顶栏）
 * - MARKDOWN：Rendered（[MarkdownViewer] 原生渲染）/ Source（Sora）切换 + 编辑（T22，
 *   D1 决策：md 编辑统一走可提交编辑器，T21 无提交入口摘除）
 * - TOO_LARGE / BINARY：明确提示卡片而非尝试渲染
 *
 * 编辑器主题跟随 M3（[rememberM3EditorThemeTokens]，plan.md §8.2 映射表）。
 *
 * 查找状态（[findState] / [isFindOpen]）由 [RepoFilesViewModel] 持有（纯逻辑状态机在
 * `core:editor` 的 [FileFindState]）；真正的高亮与跳转经 [CodeEditorController] 执行——
 * 编辑器句柄只在组合期存在，不能进 ViewModel。关闭面板即清除全部高亮（验收项）。
 *
 * @param findState 文件内查找状态机快照（计数/序号/查询词）
 * @param isFindOpen 查找面板是否展开
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
    findState: FileFindState,
    isFindOpen: Boolean,
    editable: Boolean = true,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editorTokens = rememberM3EditorThemeTokens()
    val loaded = (fileState as? FileViewState.Loaded)?.data

    var editor by remember { mutableStateOf<CodeEditorController?>(null) }
    var showJumpDialog by rememberSaveable { mutableStateOf(false) }
    val findFocusRequester = remember { FocusRequester() }

    // 展开即聚焦查询框（键盘随之上推面板：insets 见 FileViewerTools）
    LaunchedEffect(isFindOpen) {
        if (isFindOpen) findFocusRequester.requestFocus()
    }

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
                    // 顶栏只留主操作（编辑）；查找/跳行移入右下角悬浮工具层（ui-design §3.10）
                    when (loaded?.kind) {
                        FileKind.CODE, FileKind.MARKDOWN -> if (editable) EditFileButton(viewModel)
                        else -> Unit
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        // 外层不消费 Scaffold padding：内容层按 padding 内缩，悬浮工具层自行避让底部 insets
        // （避免「导航栏 + 键盘」被两处重复计算）
        Box(modifier = Modifier.fillMaxSize()) {
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
                                    onEditorReady = { controller ->
                                        editor = controller
                                        // 权威结果回灌（查询词异步生效）；首次结果无选中项时补跳到首处匹配
                                        controller.onFindResult = { result ->
                                            viewModel.onFindResults(result)
                                            if (result.hasMatches && result.currentMatchIndex == FileFindState.NO_MATCH) {
                                                viewModel.onFindResults(controller.findNext())
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }

            if (loaded?.kind == FileKind.CODE) {
                FileViewerTools(
                    viewModel = viewModel,
                    editor = editor,
                    findState = findState,
                    isFindOpen = isFindOpen,
                    focusRequester = findFocusRequester,
                    onJumpToLine = { showJumpDialog = true },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
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

/**
 * UI14 悬浮工具层（右下角）：查找面板展开时**取代**「查找 / 跳行」按钮条。
 *
 * 形态决策（ui-design §3.10「悬浮跳行/搜索按钮」，issue #166 第 5 项）：
 * - 收起态 = 竖向按钮条（两个 48dp 图标按钮，M3 无障碍触区下限）：代码浏览屏无「主操作」
 *   语义，56dp FAB 会显著遮挡代码，故不用 FAB 而用悬浮按钮条
 * - 展开态 = 面板取代按钮条（同一角落不叠两层），面板浮于代码之上
 * - insets：内容层已按 Scaffold padding 内缩，本层自行避让「导航栏 ∪ 键盘」（union 取较大者），
 *   键盘弹出时面板抬到键盘上沿
 *
 * 查找动作在本层内联接线：**状态层乐观推进 + 编辑器权威回灌**必须成对出现
 * （[RepoFilesViewModel.onFindNext] 立即刷新计数，[CodeEditorController.findNext] 才是真实跳转），
 * 拆成回调参数会把这对语义拆到两处、易漏其中一半。
 */
@Composable
private fun FileViewerTools(
    viewModel: RepoFilesViewModel,
    editor: CodeEditorController?,
    findState: FileFindState,
    isFindOpen: Boolean,
    focusRequester: FocusRequester,
    onJumpToLine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(16.dp),
        horizontalAlignment = Alignment.End,
    ) {
        if (isFindOpen) {
            FileFindPanel(
                state = findState,
                focusRequester = focusRequester,
                onQueryChange = { query ->
                    viewModel.onFindQueryChanged(query)
                    editor?.findText(query)
                },
                onPrevious = {
                    viewModel.onFindPrevious()
                    editor?.let { viewModel.onFindResults(it.findPrevious()) }
                },
                onNext = {
                    viewModel.onFindNext()
                    editor?.let { viewModel.onFindResults(it.findNext()) }
                },
                // 关闭面板 = 清除全部匹配高亮（UI14 验收项）
                onClose = {
                    viewModel.closeFind()
                    editor?.clearFindText()
                },
            )
        } else {
            FileViewerToolRail(onOpenFind = viewModel::openFind, onJumpToLine = onJumpToLine)
        }
    }
}

/** 收起态工具条：查找 + 跳转行（图标按钮 48dp 触区；文案/描述走 stringResource）。 */
@Composable
private fun FileViewerToolRail(
    onOpenFind: () -> Unit,
    onJumpToLine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column {
            IconButton(onClick = onOpenFind) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Search,
                    contentDescription = stringResource(R.string.repo_file_search),
                )
            }
            IconButton(onClick = onJumpToLine) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Format_list_numbered,
                    contentDescription = stringResource(R.string.repo_file_jump_to_line),
                )
            }
        }
    }
}

/**
 * 查找面板：查询框 + 上一处/下一处 + 「第 n / 共 m 项」计数 + 关闭。
 *
 * 颜色全取 M3 令牌（surfaceContainerHigh / onSurfaceVariant / 透明容器）；文案全走
 * stringResource（en + zh-rCN）；无匹配时上/下一处禁用（[FileFindState.hasMatches]）。
 */
@Composable
private fun FileFindPanel(
    state: FileFindState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(min = 280.dp, max = 320.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialSymbols.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).focusRequester(focusRequester),
                    placeholder = { Text(text = stringResource(R.string.repo_file_find_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    // 键盘「搜索」键 = 下一处（无匹配时为空操作，状态机原样返回）
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Close,
                        contentDescription = stringResource(R.string.repo_file_find_close),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = state.hasMatches) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Keyboard_arrow_up,
                        contentDescription = stringResource(R.string.repo_file_find_previous),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasMatches) {
                    Icon(
                        imageVector = MaterialSymbols.Rounded.Keyboard_arrow_down,
                        contentDescription = stringResource(R.string.repo_file_find_next),
                    )
                }
                Text(
                    text = findCounterText(state),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp, end = 12.dp),
                )
            }
        }
    }
}

/**
 * 计数文案：「第 n / 共 m 项」/「无匹配结果」。
 *
 * 查询词为空或结果未回灌（[FileFindState.isSearching]）时留空——新查询词生效是异步的，
 * 立刻显示「无匹配」会闪一帧假阴性。
 */
@Composable
private fun findCounterText(state: FileFindState): String =
    when {
        !state.hasQuery || state.isSearching -> ""
        state.hasMatches -> stringResource(R.string.repo_file_find_counter, state.matchOrdinal, state.matchCount)
        else -> stringResource(R.string.repo_file_find_no_results)
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
