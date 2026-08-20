@file:Suppress("LongMethod") // 屏幕装配（顶栏/双 Tab/工具栏/编辑器/预览）结构固有，拆散反损可读性

package com.yumiru11.githubapp.feature.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Checklist
import com.composables.icons.materialsymbols.rounded.Code
import com.composables.icons.materialsymbols.rounded.Code_blocks
import com.composables.icons.materialsymbols.rounded.Format_bold
import com.composables.icons.materialsymbols.rounded.Format_h1
import com.composables.icons.materialsymbols.rounded.Format_italic
import com.composables.icons.materialsymbols.rounded.Format_list_bulleted
import com.composables.icons.materialsymbols.rounded.Format_list_numbered
import com.composables.icons.materialsymbols.rounded.Format_quote
import com.composables.icons.materialsymbols.rounded.Image
import com.composables.icons.materialsymbols.rounded.Link
import com.composables.icons.materialsymbols.rounded.Redo
import com.composables.icons.materialsymbols.rounded.Undo
import com.yumiru11.githubapp.core.editor.DEFAULT_MARKDOWN_EMOJIS
import com.yumiru11.githubapp.core.editor.MarkdownEditorView
import com.yumiru11.githubapp.core.editor.MarkdownToolbarAction
import com.yumiru11.githubapp.core.editor.rememberM3EditorThemeTokens
import com.yumiru11.githubapp.core.markdown.webview.MarkdownBridgeCallback
import com.yumiru11.githubapp.core.markdown.webview.RenderMode
import com.yumiru11.githubapp.core.markdown.webview.WebViewMarkdownRenderer
import com.yumiru11.githubapp.core.navigation.link.ParsedUrl

/**
 * Markdown 编辑器屏幕（T21，plan.md §7.1）。
 *
 * - 编辑 Tab：工具栏（加粗/斜体/行内码/代码块/标题/列表/任务列表/链接/图片/引用）
 *   + Sora 编辑器（Markdown TextMate 语法 + M3 主题 + @mention/emoji 自动补全）
 * - 预览 Tab：与展示共用主渲染管线（[WebViewMarkdownRenderer] + 离线 GFM，
 *   同 README/Issue 正文——Task B 后主渲染），保证 WYSIWYG 一致性
 * - 顶栏：返回 + 撤销/重做
 *
 * @param initialContent 初始文档内容（入口传入，如文件查看器编辑入口）
 * @param onClose 返回回调
 * @param onInternalLink 预览内 GitHub 内部链接分发（默认忽略）
 * @param onExternalLink 预览内外部链接分发（默认忽略）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownEditorScreen(
    initialContent: String,
    onClose: () -> Unit,
    onInternalLink: (ParsedUrl) -> Unit = {},
    onExternalLink: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: MarkdownEditorViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer { MarkdownEditorViewModel(initialContent) }
                },
        )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val editorTokens = rememberM3EditorThemeTokens()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Undo,
                            contentDescription = stringResource(R.string.editor_undo),
                        )
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(
                            imageVector = MaterialSymbols.Rounded.Redo,
                            contentDescription = stringResource(R.string.editor_redo),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            EditorTabRow(
                isPreview = uiState.isPreview,
                onEditClick = { if (uiState.isPreview) viewModel.togglePreview() },
                onPreviewClick = { if (!uiState.isPreview) viewModel.togglePreview() },
            )
            if (uiState.isPreview) {
                WebViewMarkdownRenderer(
                    sanitizedHtml = uiState.text,
                    tokenProvider = { null },
                    bridgeCallback =
                        remember(onInternalLink, onExternalLink) {
                            EditorPreviewBridgeCallback(onInternalLink, onExternalLink)
                        },
                    renderMode = RenderMode.OFFLINE_MARKDOWN_IT,
                    fillAvailableHeight = true,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MarkdownToolbar(onAction = { viewModel.applySyntax(it) })
                MarkdownEditorView(
                    content = uiState.text,
                    themeTokens = editorTokens,
                    mentions = emptyList(),
                    emojis = DEFAULT_MARKDOWN_EMOJIS,
                    onEditorReady = { viewModel.onEditorReady(it) },
                    onTextChanged = { viewModel.onTextChanged(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 编辑/预览双 Tab 行（选中态 primary，未选中 onSurfaceVariant）。 */
@Composable
private fun EditorTabRow(
    isPreview: Boolean,
    onEditClick: () -> Unit,
    onPreviewClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        TextButton(onClick = onEditClick) {
            Text(
                text = stringResource(R.string.editor_tab_edit),
                color =
                    if (isPreview) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
            )
        }
        TextButton(onClick = onPreviewClick) {
            Text(
                text = stringResource(R.string.editor_tab_preview),
                color =
                    if (isPreview) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/** 工具栏：横向滚动图标按钮行（11 个语法动作）。 */
@Composable
private fun MarkdownToolbar(onAction: (MarkdownToolbarAction) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
    ) {
        ToolbarButton(MaterialSymbols.Rounded.Format_bold, R.string.editor_bold) { onAction(MarkdownToolbarAction.BOLD) }
        ToolbarButton(MaterialSymbols.Rounded.Format_italic, R.string.editor_italic) { onAction(MarkdownToolbarAction.ITALIC) }
        ToolbarButton(MaterialSymbols.Rounded.Code, R.string.editor_inline_code) { onAction(MarkdownToolbarAction.INLINE_CODE) }
        ToolbarButton(MaterialSymbols.Rounded.Code_blocks, R.string.editor_code_block) { onAction(MarkdownToolbarAction.CODE_BLOCK) }
        ToolbarButton(MaterialSymbols.Rounded.Format_h1, R.string.editor_heading) { onAction(MarkdownToolbarAction.HEADING) }
        ToolbarButton(MaterialSymbols.Rounded.Format_list_bulleted, R.string.editor_unordered_list) {
            onAction(MarkdownToolbarAction.UNORDERED_LIST)
        }
        ToolbarButton(
            MaterialSymbols.Rounded.Format_list_numbered,
            R.string.editor_ordered_list,
        ) { onAction(MarkdownToolbarAction.ORDERED_LIST) }
        ToolbarButton(MaterialSymbols.Rounded.Checklist, R.string.editor_task_list) { onAction(MarkdownToolbarAction.TASK_LIST) }
        ToolbarButton(MaterialSymbols.Rounded.Link, R.string.editor_link) { onAction(MarkdownToolbarAction.LINK) }
        ToolbarButton(MaterialSymbols.Rounded.Image, R.string.editor_image) { onAction(MarkdownToolbarAction.IMAGE) }
        ToolbarButton(MaterialSymbols.Rounded.Format_quote, R.string.editor_quote) { onAction(MarkdownToolbarAction.QUOTE) }
    }
}

/** 单个工具栏图标按钮（矢量图标 + contentDescription，禁 emoji）。 */
@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescriptionRes: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(contentDescriptionRes),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 预览 WebView bridge 回调：链接分发，其余事件忽略（预览只读）。 */
private class EditorPreviewBridgeCallback(
    private val internalLinkHandler: (ParsedUrl) -> Unit,
    private val externalLinkHandler: (String) -> Unit,
) : MarkdownBridgeCallback {
    override fun onExternalLink(url: String) = externalLinkHandler(url)

    override fun onInternalLink(parsed: ParsedUrl) = internalLinkHandler(parsed)

    override fun onCodeCopy(code: String) = Unit

    override fun onImageClick(src: String) = Unit

    override fun onCheckboxClick(
        index: Int,
        checked: Boolean,
    ) = Unit

    override fun onHeightChanged(heightPx: Int) = Unit
}
