package com.yumiru11.githubapp.core.editor

import android.content.Context
import android.graphics.Typeface
import androidx.test.core.app.ApplicationProvider
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 代码编辑器偏好 → Sora 实例的应用测试（T24 死设置接线）。
 *
 * 断言的是**API 效果**（写入的 typeface / 行号开关状态 / 幂等性），不是字体渲染 ——
 * 渲染依赖平台字体资产，截图与真机走查才是它的裁判（AGENTS：「不要在 Robolectric 里断言
 * 真实字体渲染」）。用 `assertEquals` 而非 `assertSame`：等值断言在真机与 Robolectric 下
 * 都成立，不会把测试绑死在某一实现的实例缓存策略上。
 *
 * 本类抓出过一个真缺陷（保留为回归防线）：Sora 构造后正文 `Typeface.DEFAULT`、行号槽
 * `Typeface.MONOSPACE`，只比对正文的幂等判据会让「切到系统默认」漏改行号槽。
 *
 * 能直接推进 `applyCodeEditorPreferences` 的前提是 Robolectric 下可构造 Sora 编辑器 ——
 * 这是本仓既有事实（FileEditScreen 截图基线里就有 Sora 渲染出的行号与代码）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeFontTypefaceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun applyCodeEditorPreferences_mono_setsMonospaceOnTextAndLineNumbers() {
        val editor = CodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = true)

        // 行号槽与正文同档：两处字体不同会让行号与代码列错位
        assertEquals(Typeface.MONOSPACE, editor.typefaceText)
        assertEquals(Typeface.MONOSPACE, editor.typefaceLineNumber)
    }

    @Test
    fun applyCodeEditorPreferences_system_setsDefaultTypeface() {
        val editor = CodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.SYSTEM, lineNumbers = true)

        assertEquals(Typeface.DEFAULT, editor.typefaceText)
        assertEquals(Typeface.DEFAULT, editor.typefaceLineNumber)
    }

    @Test
    fun applyCodeEditorPreferences_fontChanged_rewritesTypeface() {
        val editor = CountingCodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = true)
        editor.applyCodeEditorPreferences(codeFont = CodeFont.SYSTEM, lineNumbers = true)

        // 一档一次：MONO → SYSTEM 必须真的改写（否则设置页切换后字体不跟着变）
        assertEquals(2, editor.typefaceWriteCount)
    }

    @Test
    fun applyCodeEditorPreferences_sameFontTwice_doesNotRewriteTypeface() {
        // 幂等是性能刚需：update 块每次重组都会跑，无条件写入会每次触发 Sora 的 createLayout()
        val editor = CountingCodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = true)
        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = true)

        assertEquals(1, editor.typefaceWriteCount)
    }

    @Test
    fun applyCodeEditorPreferences_lineNumbersOff_disablesGutter() {
        val editor = CodeEditor(context)

        // Sora 构造期默认开行号（见 CodeFontFamily 文件头的 API 核实）→ 关掉必须有真实状态变化
        assertTrue("前置条件：Sora 默认开行号", editor.isLineNumberEnabled)
        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = false)

        assertFalse(editor.isLineNumberEnabled)
    }

    @Test
    fun applyCodeEditorPreferences_lineNumbersOn_reenablesGutter() {
        val editor = CodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = false)
        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = true)

        assertTrue(editor.isLineNumberEnabled)
    }

    @Test
    fun applyCodeEditorPreferences_sameLineNumberPreferenceTwice_doesNotRewriteSwitch() {
        val editor = CountingCodeEditor(context)

        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = false)
        editor.applyCodeEditorPreferences(codeFont = CodeFont.MONO, lineNumbers = false)

        assertEquals(1, editor.lineNumberWriteCount)
    }

    /**
     * 计数用编辑器：只统计**构造之后**的写入次数，用来验证幂等判据真的跳过了重复写入。
     *
     * Sora 的 `setTypefaceText` / `setLineNumberEnabled` 都是非 final 的 public 方法，可覆写；
     * 构造期父类自身也会调用它们，故构造完成后先清零。
     */
    private class CountingCodeEditor(
        context: Context,
    ) : CodeEditor(context) {
        var typefaceWriteCount = 0
        var lineNumberWriteCount = 0

        init {
            typefaceWriteCount = 0
            lineNumberWriteCount = 0
        }

        override fun setTypefaceText(typeface: Typeface?) {
            typefaceWriteCount++
            super.setTypefaceText(typeface)
        }

        override fun setLineNumberEnabled(enabled: Boolean) {
            lineNumberWriteCount++
            super.setLineNumberEnabled(enabled)
        }
    }
}
