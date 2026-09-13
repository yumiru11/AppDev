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
import com.yumiru11.githubapp.core.editor.MarkdownComposer
import com.yumiru11.githubapp.core.editor.MarkdownEditorView
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
        // 编辑/预览 + md 工具栏收敛在 core:editor 的 MarkdownComposer（#166 / UI05）：
        // 同一套交互同时服务本页与 issue 评论输入 Sheet，避免两处各维护一份工具栏。
        // 预览用 WebView（长文档，与 README 渲染一致）；评论那侧用原生 viewer（短文本铁律）。
        MarkdownComposer(
            text = uiState.text,
            isPreview = uiState.isPreview,
            onTogglePreview = { viewModel.setPreview(it) },
            onTextChanged = { viewModel.onTextChanged(it) },
            onEditorReady = { viewModel.onEditorReady(it) },
            onToolbarAction = { viewModel.applySyntax(it) },
            themeTokens = editorTokens,
            preview = {
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
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

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

    /** Mermaid 渲染结果：编辑器预览无 CI 断言锚点（README 主路径在 feature:repo 打点），保留空实现。 */
    override fun onMermaidResult(
        rendered: Int,
        failed: Int,
        engineSupported: Boolean,
    ) = Unit
}
