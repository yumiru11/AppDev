package com.yumiru11.githubapp.core.editor

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 查找结果回灌时序测试（EDITOR-1 查找/替换的 CI 偶发红根因回归防线）。
 *
 * **缺陷形态**（修复前）：Sora 的 `EditorSearcher` 在派发 `PublishSearchResultEvent` **之后**才把
 * `currentThread` 置空（`lambda$run$0` 字节码顺序，AAR `javap` 核实），而 `isResultValid()`
 * 的判据是 `currentThread == null || !currentThread.isAlive()`。于是**派发期间**读
 * `matchedPositionCount` / `getCurrentMatchedPositionIndex` 会撞上「扫描线程仍在退出」的窗口：
 * 机器负载高（CI runner 双核 + JaCoCo 插桩）时扫描线程在 post 之后被抢占，主线程先跑派发 →
 * 计数读出 0 → 查找面板显示「无匹配结果」且**在下一次查询/内容变更前不会自愈**。
 * 这正是 `FileEditScreenFindReplaceTest` 在 CI 覆盖率步骤偶发超时的原因（本地空载几乎必不复现）。
 *
 * **本测试如何做到确定性**：用 `runOneTask()` 逐条推进主 looper，精确停在「Sora 派发已执行、
 * 回灌尚未交付」这一刻 —— 此刻若回灌是派发期间同步读取（旧实现），`delivered` 已非空；
 * 修复后回灌是派发返回之后的**下一个主线程消息**，`delivered` 必为空且随后交付权威计数。
 * 线程抢占的窗口无法在测试里稳定制造，用消息边界替代时间竞速，才是可重复的判据。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeEditorFindResultDeliveryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun findText_soraPublishEvent_readsMatchTableOnlyAfterDispatchReturns() {
        val editor = editableEditor("a b a b a")
        val controller = CodeEditorController(editor)
        val delivered = mutableListOf<FileFindState>()
        controller.onFindResult = { delivered += it }

        controller.findText("a")

        // 逐条推进主 looper，直到 Sora 的「结果派发」消息执行完（权威匹配表就绪：3 处匹配）。
        // 派发前的空转由真实时钟兜底（Robolectric 的 System.currentTimeMillis 是真实时间，已实测）。
        val deadline = System.currentTimeMillis() + SEARCH_SETTLE_TIMEOUT_MS
        while (controller.currentFindState().matchCount != EXPECTED_MATCH_COUNT && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).runOneTask()
        }
        assertEquals(
            "Sora 匹配表应已就绪（派发消息已执行）",
            EXPECTED_MATCH_COUNT,
            controller.currentFindState().matchCount,
        )

        // 缺陷判据：回灌不得在派发期间同步读取匹配表（那一刻扫描线程可能仍存活 → 读到 0）。
        assertEquals("结果回灌必须在 Sora 派发返回之后交付，不得在派发期间同步读取", emptyList<FileFindState>(), delivered)

        // 下一个主线程消息 = 回灌：交付的是权威计数（3），不是竞态窗口里的 0。
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(EXPECTED_MATCH_COUNT), delivered.map { it.matchCount })
    }

    @Test
    fun clearFindText_stopSearchDispatch_deliversEmptyStateAfterDispatchReturns() {
        val editor = editableEditor("a b a b a")
        val controller = CodeEditorController(editor)
        val delivered = mutableListOf<FileFindState>()
        controller.onFindResult = { delivered += it }

        controller.findText("a")
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        delivered.clear()

        // `stopSearch` 在调用线程上同步派发结果事件：同样不得在派发期间同步读取（旧实现会当场交付）
        controller.clearFindText()
        assertEquals("同步派发（stopSearch）也不得在派发期间回灌", emptyList<FileFindState>(), delivered)

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(0), delivered.map { it.matchCount })
        assertEquals("", delivered.single().query)
    }

    /** 可编辑编辑器 + 控制器（与 `CodeEditorReplaceTest` 同款最小装配：先量一次再操作）。 */
    private fun editableEditor(text: String): CodeEditor {
        val editor = CodeEditor(context)
        editor.setEditable(true)
        editor.setUndoEnabled(true)
        editor.setText(text)
        editor.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
        )
        editor.layout(0, 0, 1080, 1920)
        return editor
    }

    private companion object {
        const val EXPECTED_MATCH_COUNT = 3
        const val SEARCH_SETTLE_TIMEOUT_MS = 5_000L
    }
}
