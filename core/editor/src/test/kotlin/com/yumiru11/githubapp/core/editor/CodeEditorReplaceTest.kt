package com.yumiru11.githubapp.core.editor

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [CodeEditorController] 替换能力测试（EDITOR-1；Robolectric 下推进真实 Sora 编辑器）。
 *
 * 关键断言（本票硬要求）：**replace-all 在 Sora 撤销栈里只占一步** —— 替换一次 `undo()`
 * 必须回到原文且不能再撤销。替换实现走「纯逻辑算新全文 + 单次 `Content.replace`」，
 * 本测试是该设计的回归防线。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeEditorReplaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val searchSettleTimeoutMs = 5_000L

    // ── replace-all ────────────────────────────────────────────────────────

    @Test
    fun replaceAll_allMatches_replacesEveryOccurrence() {
        val (editor, controller) = editableEditor("a b a b a")
        armReplaceQuery(editor, controller, "a")

        val count = controller.replaceAll("X")

        assertEquals(3, count)
        assertEquals("X b X b X", editor.text.toString())
    }

    @Test
    fun replaceAll_afterReplace_undoRestoresOriginalInSingleStep() {
        val (editor, controller) = editableEditor("a b a b a")
        armReplaceQuery(editor, controller, "a")
        controller.replaceAll("X")

        assertTrue("替换后应可撤销", editor.canUndo())
        editor.undo()

        assertEquals("a b a b a", editor.text.toString())
        assertFalse("整次 replace-all 必须只占一步撤销（undo 后无可撤销动作）", editor.canUndo())
    }

    @Test
    fun replaceAll_noMatch_returnsZeroAndKeepsText() {
        val (editor, controller) = editableEditor("abc")
        armReplaceQuery(editor, controller, "z")

        val count = controller.replaceAll("X")

        assertEquals(0, count)
        assertEquals("abc", editor.text.toString())
    }

    @Test
    fun replaceAll_emptyQuery_returnsZeroAndKeepsText() {
        val (editor, controller) = editableEditor("abc")

        val count = controller.replaceAll("X")

        assertEquals(0, count)
        assertEquals("abc", editor.text.toString())
    }

    @Test
    fun replaceAll_readOnlyEditor_returnsZeroAndKeepsText() {
        val editor = CodeEditor(context)
        editor.setEditable(false)
        editor.setText("a a")
        layoutEditor(editor)
        val controller = CodeEditorController(editor)
        armReplaceQuery(editor, controller, "a")

        assertEquals(0, controller.replaceAll("X"))
        assertEquals("a a", editor.text.toString())
    }

    @Test
    fun replaceAll_multiLineText_replacesAcrossLines() {
        val (editor, controller) = editableEditor("foo line\nbar line\nfoo end")
        armReplaceQuery(editor, controller, "line")

        val count = controller.replaceAll("row")

        assertEquals(2, count)
        assertEquals("foo row\nbar row\nfoo end", editor.text.toString())
    }

    // ── replace-one ────────────────────────────────────────────────────────

    @Test
    fun replaceCurrent_selectionOnSecondMatch_replacesOnlyThatMatch() {
        val (editor, controller) = editableEditor("a b a b a")
        armReplaceQuery(editor, controller, "a")
        // 选中第二处匹配（行 0，列 4..5）
        editor.setSelectionRegion(0, 4, 0, 5)

        val replaced = controller.replaceCurrent("X")

        assertTrue(replaced)
        assertEquals("a b X b a", editor.text.toString())
    }

    @Test
    fun replaceCurrent_cursorBetweenMatches_picksNextMatchAtOrAfterCursor() {
        val (editor, controller) = editableEditor("a b a")
        armReplaceQuery(editor, controller, "a")
        // 光标在列 2（不落在任何匹配上）→ 取其后的首处匹配（字符 4..5）
        editor.setSelection(0, 2)

        controller.replaceCurrent("X")

        assertEquals("a b X", editor.text.toString())
    }

    @Test
    fun replaceCurrent_emptyReplacement_deletesSelectedMatch() {
        val (editor, controller) = editableEditor("a b a")
        armReplaceQuery(editor, controller, "a")
        editor.setSelectionRegion(0, 0, 0, 1)

        controller.replaceCurrent("")

        assertEquals(" b a", editor.text.toString())
    }

    @Test
    fun replaceCurrent_noMatch_returnsFalseAndKeepsText() {
        val (editor, controller) = editableEditor("abc")
        armReplaceQuery(editor, controller, "z")

        assertFalse(controller.replaceCurrent("X"))
        assertEquals("abc", editor.text.toString())
    }

    @Test
    fun replaceCurrent_afterReplace_undoRestoresMatch() {
        val (editor, controller) = editableEditor("a b a")
        armReplaceQuery(editor, controller, "a")
        editor.setSelectionRegion(0, 0, 0, 1)
        controller.replaceCurrent("XYZ")

        editor.undo()

        assertEquals("a b a", editor.text.toString())
    }

    /**
     * 设好替换用的查询词，并把 Sora 侧的后台扫描停掉。
     *
     * 为什么必须停：`EditorSearcher` 在**每次内容变更**后都会重扫（订阅 ContentChangeEvent），
     * 扫描线程与测试线程并发读写 Content/布局时，Robolectric 下 `LineBreakLayout.afterDelete`
     * 会偶发越界（CI 与本地各复现过一次）。停掉 pattern 只影响 Sora 的高亮/计数，
     * 控制器自己的查询词镜像保留 → 替换路径照常生效（替换不依赖 Sora 的匹配表）。
     */
    private fun armReplaceQuery(
        editor: CodeEditor,
        controller: CodeEditorController,
        query: String,
    ) {
        controller.findText(query)
        val deadline = System.currentTimeMillis() + searchSettleTimeoutMs
        while (controller.currentFindState().matchCount == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        // 排空主 looper 上的回灌，再停掉 pattern（此后内容变更不再触发重扫）
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        editor.searcher.stopSearch()
    }

    /** 可编辑编辑器 + 控制器（编辑器是文本唯一事实源，控制器镜像查询词）。 */
    private fun editableEditor(text: String): Pair<CodeEditor, CodeEditorController> {
        val editor = CodeEditor(context)
        editor.setEditable(true)
        editor.setUndoEnabled(true)
        editor.setText(text)
        layoutEditor(editor)
        return editor to CodeEditorController(editor)
    }

    /**
     * 先量一次再替换：Sora 的 `CodeEditor.afterDelete` 会同步转发给内部布局
     * （`LineBreakLayout.afterDelete`），无尺寸时其行表为空 → 内容变更会越界。
     * 真机上布局必然已就绪；Robolectric 里由本函数补上这一步。
     */
    private fun layoutEditor(editor: CodeEditor) {
        editor.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        editor.layout(0, 0, 1080, 1920)
    }
}
