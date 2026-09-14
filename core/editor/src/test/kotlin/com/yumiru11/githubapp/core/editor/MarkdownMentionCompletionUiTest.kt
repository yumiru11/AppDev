package com.yumiru11.githubapp.core.editor

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.rosemoe.sora.lang.styling.StylesUtils
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `@mention` 补全的**界面行为**测试（SPEC-3 验收：输入 `@` 弹建议列表、选中即插入）。
 *
 * 与 [MarkdownCompletionProviderTest]（纯候选计算）互补：这里走完整链路——
 * [MarkdownComposer] → [MarkdownEditorView]（真实 Sora `CodeEditor` + TextMate 语言）
 * → `EditorAutoCompletion` 弹窗 → 选中插入编辑器文本。
 *
 * 为什么不用 Compose 语义树断言：Sora 的补全面板是挂在编辑器上的 `PopupWindow`
 * （不是 Compose 节点），语义树里看不到；要证明「列表真的弹出来了」只能读
 * `EditorAutoCompletion.isShowing`。同理，「输入」用 [CodeEditor.commitText] ——
 * 这是 IME 提交文本走的同一个入口（不是绕开编辑器直接改 text）。
 *
 * 红→绿双向验证：`mentions = 空` 时同一条请求路径必须**不弹**
 * （[markdownComposer_noCandidates_doesNotShowSuggestionList]），否则显示断言恒真。
 *
 * 命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class MarkdownMentionCompletionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun markdownComposer_typingAt_showsSuggestionListAndSelectingFirstInsertsLogin() {
        val editor = composeComposer(mentions = listOf("octocat", "torvalds"))

        val completion = typeAndRequestCompletion(editor, "@")

        assertTrue("输入 @ 后应弹出建议列表", completion.isShowing)
        selectFirst(completion)
        assertEquals("@octocat", editor.text.toString())
    }

    @Test
    fun markdownComposer_typingAtPrefix_filtersCandidatesAndInsertsMatchingLogin() {
        val editor = composeComposer(mentions = listOf("octocat", "torvalds"))

        val completion = typeAndRequestCompletion(editor, "@tor")

        assertTrue(completion.isShowing)
        selectFirst(completion)
        // 只有 torvalds 前缀匹配 → 首个候选即 torvalds（候选顺序 = 传入顺序）
        assertEquals("@torvalds", editor.text.toString())
    }

    @Test
    fun markdownComposer_noCandidates_doesNotShowSuggestionList() {
        val editor = composeComposer(mentions = emptyList())

        val completion = typeAndRequestCompletion(editor, "@")

        assertFalse("无候选时不应弹出空建议列表", completion.isShowing)
        assertEquals("@", editor.text.toString())
    }

    /** 组合真实的 [MarkdownComposer]（编辑态），返回其中的 Sora 编辑器实例。 */
    private fun composeComposer(mentions: List<String>): CodeEditor {
        composeRule.setContent {
            MaterialTheme {
                MarkdownComposer(
                    text = "",
                    isPreview = false,
                    onTogglePreview = {},
                    onTextChanged = {},
                    onEditorReady = {},
                    onToolbarAction = {},
                    themeTokens = rememberM3EditorThemeTokens(),
                    preview = {},
                    mentions = mentions,
                )
            }
        }
        composeRule.waitForIdle()
        return composeRule.findCodeEditor()
    }

    /** 模拟输入 [text]（IME 提交路径）并请求补全，返回补全组件（是否弹出由调用方断言）。 */
    private fun typeAndRequestCompletion(
        editor: CodeEditor,
        text: String,
    ): EditorAutoCompletion {
        composeRule.runOnUiThread {
            editor.setSelection(0, 0)
            editor.commitText(text)
        }
        composeRule.waitForIdle()
        // Sora 的 requireCompletion 在「光标处没有样式 span」时直接抑制补全
        // （StylesUtils.checkNoCompletion → true）。真实使用中编辑器早已完成 TextMate
        // 分析；测试是冷启动即输入，必须先把分析结果等出来，否则测的是竞态而非补全。
        assertTrue(
            "前置条件：TextMate 分析应产出光标处样式",
            awaitEditorAnalysis(editor),
        )

        val completion = editor.getComponent(EditorAutoCompletion::class.java)
        awaitCompletionPopup(completion)
        return completion
    }

    private fun selectFirst(completion: EditorAutoCompletion) {
        var selected = false
        composeRule.runOnUiThread { selected = completion.select(0) }
        composeRule.waitForIdle()
        assertTrue("应能选中首个候选并插入", selected)
    }

    /** 等 TextMate 分析产出样式（重跑一次分析 + 轮询光标处是否有 span）。 */
    private fun awaitEditorAnalysis(editor: CodeEditor): Boolean {
        editor.editorLanguage?.analyzeManager?.rerun()
        val deadline = System.currentTimeMillis() + ANALYZE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            pumpMainLooper()
            val styles = editor.styles
            val cursor = CharPosition(editor.cursor.leftLine, editor.cursor.leftColumn)
            if (styles != null && StylesUtils.getSpanForPosition(styles, cursor) != null) return true
            Thread.sleep(POLL_INTERVAL_MS)
        }
        return false
    }

    /**
     * 轮询等待补全面板弹出，必要时重发 [EditorAutoCompletion.requireCompletion]。
     *
     * 两个 Sora 内部时序决定了单次调用不可靠：
     * 1. `requireCompletion` 有 70ms 取消窗口（`DirectAccessProps.cancelCompletionNs`），
     *    窗口内的重复请求会**主动 hide**——故重发间隔必须大于该窗口；
     * 2. 候选在后台线程计算、弹窗经 Handler 回主 looper，且文本变更触发的重分析
     *    可能瞬时清空样式（该瞬间的请求会被 `checkNoCompletion` 抑制）——故需要重试。
     */
    private fun awaitCompletionPopup(completion: EditorAutoCompletion) {
        val deadline = System.currentTimeMillis() + COMPLETION_TIMEOUT_MS
        var nextRequestAt = System.currentTimeMillis()
        while (!completion.isShowing && System.currentTimeMillis() < deadline) {
            if (System.currentTimeMillis() >= nextRequestAt) {
                composeRule.runOnUiThread { completion.requireCompletion() }
                nextRequestAt = System.currentTimeMillis() + REQUEST_INTERVAL_MS
            }
            pumpMainLooper()
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    /** 跑完主 looper 上已排队的任务（Compose 时钟 + Sora 的 Handler 回调）。 */
    private fun pumpMainLooper() {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).runToEndOfTasks()
    }

    private fun AndroidComposeTestRule<*, *>.findCodeEditor(): CodeEditor {
        var found: CodeEditor? = null

        fun walk(view: View) {
            if (found != null) return
            if (view is CodeEditor) {
                found = view
                return
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) walk(view.getChildAt(index))
            }
        }
        walk(activity.window.decorView)
        return requireNotNull(found) { "MarkdownComposer 里应挂载 Sora CodeEditor" }
    }

    private companion object {
        const val ANALYZE_TIMEOUT_MS = 8_000L
        const val COMPLETION_TIMEOUT_MS = 8_000L

        /** 大于 `DirectAccessProps.cancelCompletionNs`（70ms），避免重发被当成取消。 */
        const val REQUEST_INTERVAL_MS = 250L

        const val POLL_INTERVAL_MS = 25L
    }
}
