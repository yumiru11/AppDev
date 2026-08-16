@file:Suppress("TooGenericExceptionCaught") // 语法资产损坏/加载失败统一兜底为纯文本，不崩溃

package com.yumiru11.githubapp.core.editor

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 * Sora Editor 只读代码视图（plan.md §8.1：TextMate 高亮、行号、横向滚动）。
 *
 * - 只读：禁编辑/撤销；行号开启；禁软换行（横向滚动）
 * - 高亮：TextMate 语法（assets/grammars/ 资产，[CodeLanguageDetector] 选择）；
 *   语法缺失/加载失败 → 纯文本兜底，不崩溃
 * - 主题：M3 派生 VS Code 主题 JSON（[m3EditorThemeTokens] + [buildEditorThemeJson]），
 *   经全局 ThemeRegistry 注入（sora language-textmate 的 analyzer 从全局注册表取主题——
 *   本应用同时只显示一个编辑器，单例无冲突）
 *
 * @param content 文件文本（已解码，保留原 CRLF）
 * @param grammarFileName TextMate 语法资产文件名（assets/grammars/ 下；null = 纯文本）
 * @param themeTokens M3 编辑器令牌（[rememberM3EditorThemeTokens]）
 * @param onEditorReady 编辑器控制句柄就绪回调（搜索/跳转行等外部控制用）
 */
@Composable
fun CodeEditorView(
    content: String,
    grammarFileName: String?,
    themeTokens: EditorThemeTokens,
    onEditorReady: (CodeEditorController) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val themeSource =
        remember(themeTokens) {
            IThemeSource.fromString(IThemeSource.ContentType.JSON, buildEditorThemeJson("M3 Editor", themeTokens))
        }

    // 语法 + 主题语言实例：语法资产或主题变化时重建（themeSource 变化 → 重新装入全局注册表）
    val editorLanguage =
        remember(grammarFileName, themeSource) {
            if (grammarFileName == null) {
                null
            } else {
                runCatching { createTextMateLanguage(context, grammarFileName, themeSource) }.getOrNull()
            }
        }

    val currentOnEditorReady by rememberUpdatedState(onEditorReady)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                setEditable(false)
                isLineNumberEnabled = true
                isWordwrap = false
                setTabWidth(4)
                setTextSize(EDITOR_TEXT_SIZE_SP)
                setUndoEnabled(false)
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
            currentOnEditorReady(CodeEditorController(editor))
        },
    )
}

/** 从 assets 加载语法并创建 TextMate 语言（语法 JSON 损坏/不兼容时抛异常，由调用方兜底）。 */
private fun createTextMateLanguage(
    context: Context,
    grammarFileName: String,
    themeSource: IThemeSource,
): TextMateLanguage {
    val stream = context.assets.open("grammars/$grammarFileName")
    val grammarSource = IGrammarSource.fromInputStream(stream, grammarFileName, Charsets.UTF_8)
    return TextMateLanguage.createNoCompletion(grammarSource, themeSource)
}

private const val EDITOR_TEXT_SIZE_SP = 14f
