@file:Suppress("TooGenericExceptionCaught") // 语法资产损坏/加载失败统一兜底为纯文本，不崩溃

package com.yumiru11.githubapp.core.editor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Markdown 编辑器（T21，plan.md §7.1）。
 *
 * 编辑模式 Sora Editor：
 * - 可编辑 + undo/redo；行号开启；软换行开启（markdown 长行阅读友好）
 * - 高亮：Markdown TextMate 语法（assets/grammars/markdown.tmLanguage.json）
 * - 主题：M3 派生（[m3EditorThemeTokens]，plan.md §8.2 映射表）
 * - 自动补全：@mention / emoji（[MarkdownEditorLanguage] 包装 TextMate 语言）
 *
 * 文本同步：编辑器是文本唯一事实源；内容变化经 [onTextChanged] 上报宿主，
 * [content] 仅用于初始化/外部重置（与编辑器当前文本不同才 setText，防循环）。
 *
 * @param content 初始/外部文本
 * @param themeTokens M3 编辑器令牌（[rememberM3EditorThemeTokens]）
 * @param mentions @mention 补全候选（真实数据由上层注入）
 * @param emojis emoji 补全候选（默认 [DEFAULT_MARKDOWN_EMOJIS]）
 * @param onEditorReady 控制句柄就绪回调（工具栏/撤销重做等外部控制用）
 * @param onTextChanged 文本变更回调（预览/状态同步用）
 */
@Composable
fun MarkdownEditorView(
    content: String,
    themeTokens: EditorThemeTokens,
    mentions: List<String>,
    emojis: List<MarkdownEmoji>,
    onEditorReady: (MarkdownEditorController) -> Unit = {},
    onTextChanged: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val themeSource =
        remember(themeTokens) {
            IThemeSource.fromString(IThemeSource.ContentType.JSON, buildEditorThemeJson("M3 Editor", themeTokens))
        }

    // 语法 + 主题语言实例：语法资产或主题变化时重建（themeSource 变化 → 重新装入全局注册表）
    val editorLanguage =
        remember(MARKDOWN_GRAMMAR, themeSource, mentions, emojis) {
            runCatching {
                val textMate = createTextMateLanguage(context, MARKDOWN_GRAMMAR, themeSource)
                // markdown 无关键字补全需求：禁用 TextMate 内置补全，引用补全由包装层接管
                textMate.setAutoCompleteEnabled(false)
                MarkdownEditorLanguage(textMate, mentions, emojis)
            }.getOrNull()
        }

    val currentOnEditorReady by rememberUpdatedState(onEditorReady)
    val currentOnTextChanged by rememberUpdatedState(onTextChanged)

    // 控制器只创建一次（注册内容监听器，重复创建会累积监听器）；onTextChanged 每次重组刷新
    var controller by remember { mutableStateOf<MarkdownEditorController?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                setEditable(true)
                isLineNumberEnabled = true
                isWordwrap = true
                setTabWidth(4)
                setTextSize(EDITOR_TEXT_SIZE_SP)
                setUndoEnabled(true)
            }
        },
        update = { editor ->
            if (editor.text.toString() != content) {
                editor.setText(content)
            }
            val language = editorLanguage
            if (language != null && editor.editorLanguage !== language) {
                // 语言创建时已把 M3 主题装入全局 ThemeRegistry；配色方案跟随当前主题模型
                editor.setEditorLanguage(language)
                runCatching { editor.setColorScheme(TextMateColorScheme.create(ThemeRegistry.getInstance())) }
            }
            if (controller == null) {
                controller = MarkdownEditorController(editor)
            }
            controller?.onTextChanged = currentOnTextChanged
            controller?.let { currentOnEditorReady(it) }
        },
    )
}

/** 从 assets 加载 Markdown 语法并创建 TextMate 语言（语法 JSON 损坏/不兼容时抛异常，由调用方兜底）。 */
private fun createTextMateLanguage(
    context: Context,
    grammarFileName: String,
    themeSource: IThemeSource,
): TextMateLanguage {
    val stream = context.assets.open("grammars/$grammarFileName")
    val grammarSource = IGrammarSource.fromInputStream(stream, grammarFileName, Charsets.UTF_8)
    return TextMateLanguage.createNoCompletion(grammarSource, themeSource)
}

private const val MARKDOWN_GRAMMAR = "markdown.tmLanguage.json"
private const val EDITOR_TEXT_SIZE_SP = 14f
