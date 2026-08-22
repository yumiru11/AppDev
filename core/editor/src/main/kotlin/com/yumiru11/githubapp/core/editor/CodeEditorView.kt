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
 * Sora Editor 代码视图（plan.md §8.1/§8.2：TextMate 高亮、行号、横向滚动、可编辑）。
 *
 * - 默认只读（T11 代码浏览）：禁编辑/撤销；行号开启；禁软换行（横向滚动）
 * - [editable] = true（T22 文件编辑提交）：可编辑 + undo/redo，文本变更经
 *   [onTextChanged] 上报宿主（编辑器是文本唯一事实源，宿主据此同步提交状态）
 * - 高亮/主题与只读共用：[CodeLanguageDetector] 选语法资产，M3 派生主题
 *
 * @param content 文件文本（已解码，保留原 CRLF；编辑模式仅用于初始化/外部重置）
 * @param grammarFileName TextMate 语法资产文件名（assets/grammars/ 下；null = 纯文本）
 * @param themeTokens M3 编辑器令牌（[rememberM3EditorThemeTokens]）
 * @param editable 是否可编辑（false = 只读浏览，T11 默认行为）
 * @param onEditorReady 编辑器控制句柄就绪回调（搜索/跳转行/撤销重做等外部控制用）
 * @param onTextChanged 文本变更回调（编辑模式同步宿主状态；[CodeEditorController.onTextChanged]）
 */
@Composable
fun CodeEditorView(
    content: String,
    grammarFileName: String?,
    themeTokens: EditorThemeTokens,
    editable: Boolean = false,
    onEditorReady: (CodeEditorController) -> Unit = {},
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
        remember(grammarFileName, themeSource) {
            if (grammarFileName == null) {
                null
            } else {
                runCatching { createTextMateLanguage(context, grammarFileName, themeSource) }.getOrNull()
            }
        }

    val currentOnEditorReady by rememberUpdatedState(onEditorReady)
    val currentOnTextChanged by rememberUpdatedState(onTextChanged)

    // 控制器只创建一次（注册内容监听器，重复创建会累积监听器）；onTextChanged 每次重组刷新
    var controller by remember { mutableStateOf<CodeEditorController?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            CodeEditor(ctx).apply {
                setEditable(editable)
                isLineNumberEnabled = true
                isWordwrap = false
                setTabWidth(4)
                setTextSize(EDITOR_TEXT_SIZE_SP)
                setUndoEnabled(editable)
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
                controller = CodeEditorController(editor)
            }
            controller?.let { c ->
                c.onTextChanged = currentOnTextChanged
                currentOnEditorReady(c)
            }
        },
        onReset = {
            // 视图重置时销毁旧控制器（移除监听器），防止内存泄漏
            controller?.destroy()
            controller = null
        },
        onRelease = {
            // 视图释放时销毁控制器（移除监听器），防止内存泄漏
            controller?.destroy()
            controller = null
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
