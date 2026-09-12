@file:Suppress("TooGenericExceptionCaught") // 语法资产损坏/加载失败统一兜底为纯文本，不崩溃

package com.yumiru11.githubapp.core.editor

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.designsystem.token.LocalCodeEditorPreferences
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
 * - 外观偏好（T24 死设置接线）：[codeFont] / [lineNumbers] 默认取
 *   [LocalCodeEditorPreferences]（app 层 AppThemeHost 从 DataStore 注入），**组内每次变化
 *   即时下发**到已存在的 Sora 实例（见 update 块），不是只读一次初始化值。
 *
 * @param content 文件文本（已解码，保留原 CRLF；编辑模式仅用于初始化/外部重置）
 * @param grammarFileName TextMate 语法资产文件名（assets/grammars/ 下；null = 纯文本）
 * @param themeTokens M3 编辑器令牌（[rememberM3EditorThemeTokens]）
 * @param editable 是否可编辑（false = 只读浏览，T11 默认行为）
 * @param codeFont 代码字体（默认跟随设置页偏好 [LocalCodeEditorPreferences]）
 * @param lineNumbers 是否显示行号（默认跟随设置页偏好 [LocalCodeEditorPreferences]）
 * @param onEditorReady 编辑器控制句柄就绪回调（搜索/跳转行/撤销重做等外部控制用）
 * @param onTextChanged 文本变更回调（编辑模式同步宿主状态；[CodeEditorController.onTextChanged]）
 */
@Composable
fun CodeEditorView(
    content: String,
    grammarFileName: String?,
    themeTokens: EditorThemeTokens,
    editable: Boolean = false,
    codeFont: CodeFont = LocalCodeEditorPreferences.current.codeFont,
    lineNumbers: Boolean = LocalCodeEditorPreferences.current.lineNumbers,
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
                isWordwrap = false
                setTabWidth(4)
                setTextSize(EDITOR_TEXT_SIZE_SP)
                setUndoEnabled(editable)
                // 首帧就按偏好建视图：否则会先画一次默认字体/行号再被 update 纠正（可见闪动）
                applyCodeEditorPreferences(codeFont = codeFont, lineNumbers = lineNumbers)
            }
        },
        update = { editor ->
            // 偏好同步（T24）：AndroidView 的 update 每次重组都会跑，设置页改动 → 偏好重组 →
            // 此处即时下发到**已存在**的编辑器实例（helper 幂等：值没变不写，避免无谓重排）。
            editor.applyCodeEditorPreferences(codeFont = codeFont, lineNumbers = lineNumbers)
            if (editor.text.toString() != content) {
                editor.setText(content)
                // 内容被外部整体替换（切换文件 / 冲突重载）：结束查找会话。
                // Sora 的 EditorSearcher 订阅了文本变更事件，会用**旧查询词**自动重扫新内容，
                // 残留高亮会误导用户（#166 UI14 验收：查找会话随文件切换结束）。
                controller?.clearFindText()
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

/**
 * 把代码字体与行号两项偏好同步到已存在的 Sora 实例（幂等）。
 *
 * **为什么这个胶水函数住在 `*EditorView*` 文件里**：它要真实 `Typeface` 与真实 `CodeEditor`
 * 实例，属本仓排除口径中的「Sora 视图/胶水层，单测不可达」——JaCoCo 不记录 Robolectric 覆盖
 * （classloader 不带 location），留在 `CodeFontFamily.kt` 会被 diff 覆盖率门禁判成新增未覆盖行。
 * 可测的「选哪一档」映射留在 [CodeFontFamily] / [codeFontFamily]（纯 JVM 单测）。
 * 这里的 API 效果由 `CodeFontTypefaceTest`（Robolectric）守：写入的 typeface、行号开关、幂等性。
 *
 * 权威 API 依据（Rosemoe sora-editor `editor:0.23.6`，`javap -p -c` 反编译 AAR `classes.jar` 核实）：
 * - `setTypefaceText(Typeface)` → `EditorRenderer.setTypefaceText`：写 `paintGeneral`、刷
 *   `metricsText`、`invalidateRenderNodes()` + `createLayout()` + `invalidate()` ⇒ 改完立即重排重绘
 * - `getTypefaceText()` → `renderer.getPaint()`（= `paintGeneral`）`.getTypeface()` ⇒ 回读即写入值
 * - `setTypefaceLineNumber(Typeface)`：行号槽的 `paintOther`。**注意 Sora 构造后两处字体并不一致**
 *   （正文 `Typeface.DEFAULT`、行号槽 `Typeface.MONOSPACE`，实测）——只比对正文会让「切到系统
 *   默认」时行号槽留在等宽字体上，该缺陷由 `CodeFontTypefaceTest` 抓出并留作回归防线
 * - `setLineNumberEnabled(boolean)`：非软换行时只 `invalidate()`，而 `measureLineNumber()` 与
 *   `EditorRenderer.drawView()` 每次都读 `isLineNumberEnabled()` ⇒ 当帧生效，无需重建视图
 *
 * 幂等是性能刚需：调用点在 `AndroidView.update`（每次重组都会跑），无条件 `setTypefaceText`
 * 会每次触发 `createLayout()`，滚动位置与光标被反复重排（大文件上肉眼可见地卡）。
 *
 * @param codeFont 代码字体偏好（[CodeFont]）
 * @param lineNumbers 是否显示行号
 */
internal fun CodeEditor.applyCodeEditorPreferences(
    codeFont: CodeFont,
    lineNumbers: Boolean,
) {
    val typeface = codeFontFamily(codeFont).typeface()
    if (typefaceText != typeface || typefaceLineNumber != typeface) {
        // 行号槽同款字体：槽宽按 paintOther 度量，两处不同档会让行号与代码列错位
        setTypefaceText(typeface)
        setTypefaceLineNumber(typeface)
    }
    if (isLineNumberEnabled != lineNumbers) {
        setLineNumberEnabled(lineNumbers)
    }
}

/** 字形家族 → 平台 `Typeface` 单例（用平台单例而非 `Typeface.create(family, style)` 自造）。 */
internal fun CodeFontFamily.typeface(): Typeface =
    when (this) {
        CodeFontFamily.MONOSPACE -> Typeface.MONOSPACE
        CodeFontFamily.PLATFORM_DEFAULT -> Typeface.DEFAULT
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
