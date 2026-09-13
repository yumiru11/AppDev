package com.yumiru11.githubapp.core.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 阅读密度令牌单测（开发契约锁）：
 *
 * - [MarkdownDensity.Literal] / [MarkdownDensity.Conservative] 的 cm → dp 直译值；
 * - 行高派生（原生 sp 与 WebView 倍数同源）；
 * - [MarkdownDensity.current] 默认 = [MarkdownDensity.Literal]（切换是唯一一行）；
 * - 两个通道的映射：原生 [MarkdownDensityTokens.bodyTextStyle] 与 WebView
 *   [MarkdownDensityTokens.toCssVariables]（注入值在 WebViewHtmlBuilder 契约测试再验一次）。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
class MarkdownDensityTest {
    @Test
    fun literalPreset_matchesCmLiteralTargets() {
        val literal = MarkdownDensity.Literal

        // 0.7cm 侧距 / 0.6cm 段距 / 0.4cm 每层缩进（61.3 dp/cm 直译：43 / 37 / 24）
        assertEquals(43.dp, literal.sides)
        assertEquals(37.dp, literal.paragraphGap)
        assertEquals(24.dp, literal.indentPerLevel)
        // 0.3cm 视觉行距 = 18dp → 行盒 16 + 18 = 34sp ≈ 2.125×
        assertEquals(18.dp, literal.lineLeading)
        assertEquals(34.sp, literal.lineHeight)
        assertEquals(2.125f, literal.lineHeightMultiplier, 1e-4f)
    }

    @Test
    fun conservativePreset_matchesSpecifiedFallback() {
        val conservative = MarkdownDensity.Conservative

        assertEquals(24.dp, conservative.sides)
        assertEquals(24.dp, conservative.paragraphGap)
        assertEquals(24.dp, conservative.indentPerLevel)
        // 行高 1.8 → 行盒 28.8sp，视觉行距 12.8dp
        assertEquals(12.8.dp, conservative.lineLeading)
        assertEquals(28.8f, conservative.lineHeight.value, 1e-4f)
        assertEquals(1.8f, conservative.lineHeightMultiplier, 1e-4f)
    }

    @Test
    fun currentPreset_isLiteralByDefault() {
        assertSame("默认预设必须是 Literal（主推）；切换只改 MarkdownDensity.current 一行", MarkdownDensity.Literal, MarkdownDensity.current)
    }

    @Test
    fun bodyTextStyle_mapsDensityTokensToNativeTypography() {
        val style = MarkdownDensity.Literal.bodyTextStyle(Color.Red)

        assertEquals("正文字号固定 16sp（与 WebView 的 16px 对齐）", 16.sp, style.fontSize)
        assertEquals("原生行高必须等于令牌派生行高（34sp = 16 + 18）", 34.sp, style.lineHeight)
        assertEquals(Color.Red, style.color)
    }

    @Test
    fun conservativeBodyTextStyle_usesShorterLineHeight() {
        val style = MarkdownDensity.Conservative.bodyTextStyle(Color.Black)

        assertEquals(16.sp, style.fontSize)
        assertEquals(28.8f, style.lineHeight.value, 1e-4f)
    }

    @Test
    fun literalTokens_cssVariables_emitInjectedValues() {
        val css = MarkdownDensity.Literal.toCssVariables()

        assertTrue("WebView 左右间距", css.contains("${MarkdownDensity.CSS_VAR_SIDES}: 43px"))
        assertTrue("WebView 段间距", css.contains("${MarkdownDensity.CSS_VAR_PARAGRAPH_GAP}: 37px"))
        assertTrue("WebView 行高倍数", css.contains("${MarkdownDensity.CSS_VAR_LINE_HEIGHT}: 2.125"))
        assertTrue("WebView 每层缩进", css.contains("${MarkdownDensity.CSS_VAR_INDENT}: 24px"))
        assertTrue("变量声明在 :root", css.contains(":root {"))
    }

    @Test
    fun conservativeTokens_cssVariables_emitUpdatedValues() {
        val css = MarkdownDensity.Conservative.toCssVariables()

        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_SIDES}: 24px"))
        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_PARAGRAPH_GAP}: 24px"))
        assertTrue("1.8 必须以最短十进制输出（不出现 1.8000）", css.contains("${MarkdownDensity.CSS_VAR_LINE_HEIGHT}: 1.8"))
        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_INDENT}: 24px"))
    }

    @Test
    fun toCssVariables_fractionalDp_keepsDecimalValue() {
        // 备选 53.7 dp/cm 假设（360dp 设备）可由调用方显式构造；小数必须保真（不四舍五入成整数）
        val alternative =
            MarkdownDensityTokens(
                sides = 37.6.dp,
                paragraphGap = 32.2.dp,
                lineLeading = 16.dp,
                indentPerLevel = 21.5.dp,
            )

        val css = alternative.toCssVariables()

        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_SIDES}: 37.6px"))
        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_PARAGRAPH_GAP}: 32.2px"))
        assertTrue(css.contains("${MarkdownDensity.CSS_VAR_INDENT}: 21.5px"))
    }
}
