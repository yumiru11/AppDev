package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `AppTypography` 令牌契约（plan.md §5.4）。
 *
 * 两类断言，各自防一种事故：
 * 1. **baseline 锁**：15 档必须**逐字段**等于 `androidx.compose.material3` 的 `Typography()`
 *    官方默认值（含 `platformStyle` / `lineHeightStyle` 这两个不影响字号、却影响排版度量的
 *    字段——本票首版就漏了它们，正是此测试抓出来的）。有人「顺手」改字号 → 此测试立刻红；
 *    真要偏离 baseline，必须同时改这里，于是 patch 里必然留下「有意偏离官方字阶」的痕迹。
 * 2. **校准点锁**：[AppTypography.markdownHeading1]..[markdownCode] 的现状值 =
 *    ui-design §3.11 GitHub 网页基准（正文 16sp / 1.6 行距），也是 core/markdown 现有硬编码值。
 *    校准（FEEDBACK #11/#23「md 标题字号偏大」）时改令牌 + 改这里，两侧同步更新。
 */
class AppTypographyTest {
    @Test
    fun from_material3Defaults_equalsEveryTier() {
        val material3 = Typography()

        assertEquals(material3.displayLarge, AppTypography.displayLarge)
        assertEquals(material3.displayMedium, AppTypography.displayMedium)
        assertEquals(material3.displaySmall, AppTypography.displaySmall)
        assertEquals(material3.headlineLarge, AppTypography.headlineLarge)
        assertEquals(material3.headlineMedium, AppTypography.headlineMedium)
        assertEquals(material3.headlineSmall, AppTypography.headlineSmall)
        assertEquals(material3.titleLarge, AppTypography.titleLarge)
        assertEquals(material3.titleMedium, AppTypography.titleMedium)
        assertEquals(material3.titleSmall, AppTypography.titleSmall)
        assertEquals(material3.bodyLarge, AppTypography.bodyLarge)
        assertEquals(material3.bodyMedium, AppTypography.bodyMedium)
        assertEquals(material3.bodySmall, AppTypography.bodySmall)
        assertEquals(material3.labelLarge, AppTypography.labelLarge)
        assertEquals(material3.labelMedium, AppTypography.labelMedium)
        assertEquals(material3.labelSmall, AppTypography.labelSmall)
    }

    @Test
    fun baselineTiers_metricFields_mirrorMaterial3() {
        // 字号对得上 ≠ 排版度量对得上：includeFontPadding / 行高分配漏了就是「看着一样、
        // 实际不一样」的视觉回归。此处把这两个字段单独钉住，避免只在整档相等断言里被掩盖。
        val material3 = Typography()

        assertEquals(
            PlatformTextStyle(includeFontPadding = false),
            AppTypography.bodyLarge.platformStyle,
        )
        assertEquals(material3.bodyLarge.lineHeightStyle, AppTypography.bodyLarge.lineHeightStyle)
        assertEquals(material3.displayLarge.lineHeightStyle, AppTypography.displayLarge.lineHeightStyle)
        assertEquals(material3.labelSmall.lineHeightStyle, AppTypography.labelSmall.lineHeightStyle)
    }

    @Test
    fun from_defaultTypo_exposesTokensInEveryTier() {
        val typography = AppTypography.from()

        assertEquals(AppTypography.displayLarge, typography.displayLarge)
        assertEquals(AppTypography.displayMedium, typography.displayMedium)
        assertEquals(AppTypography.displaySmall, typography.displaySmall)
        assertEquals(AppTypography.headlineLarge, typography.headlineLarge)
        assertEquals(AppTypography.headlineMedium, typography.headlineMedium)
        assertEquals(AppTypography.headlineSmall, typography.headlineSmall)
        assertEquals(AppTypography.titleLarge, typography.titleLarge)
        assertEquals(AppTypography.titleMedium, typography.titleMedium)
        assertEquals(AppTypography.titleSmall, typography.titleSmall)
        assertEquals(AppTypography.bodyLarge, typography.bodyLarge)
        assertEquals(AppTypography.bodyMedium, typography.bodyMedium)
        assertEquals(AppTypography.bodySmall, typography.bodySmall)
        assertEquals(AppTypography.labelLarge, typography.labelLarge)
        assertEquals(AppTypography.labelMedium, typography.labelMedium)
        assertEquals(AppTypography.labelSmall, typography.labelSmall)
    }

    @Test
    fun from_calledTwice_returnsSameInstance() {
        // Typography 未重写 equals（身份比较）：引用不稳定会让 LocalTypography 每次重组都变化
        assertSame(AppTypography.from(), AppTypography.from())
    }

    @Test
    fun markdownHeadings_statusQuo_matchesGitHubWebScale() {
        assertEquals(32.sp, AppTypography.markdownHeading1.fontSize)
        assertEquals(40.sp, AppTypography.markdownHeading1.lineHeight)
        assertEquals(24.sp, AppTypography.markdownHeading2.fontSize)
        assertEquals(32.sp, AppTypography.markdownHeading2.lineHeight)
        assertEquals(20.sp, AppTypography.markdownHeading3.fontSize)
        assertEquals(28.sp, AppTypography.markdownHeading3.lineHeight)
        assertEquals(16.sp, AppTypography.markdownHeading4.fontSize)
        assertEquals(24.sp, AppTypography.markdownHeading4.lineHeight)
        assertEquals(14.sp, AppTypography.markdownHeading5.fontSize)
        assertEquals(20.sp, AppTypography.markdownHeading5.lineHeight)
        assertEquals(14.sp, AppTypography.markdownHeading6.fontSize)
        assertEquals(20.sp, AppTypography.markdownHeading6.lineHeight)
    }

    @Test
    fun markdownBody_uiDesignBaseline_isSixteenSpWith1_6LineHeight() {
        // ui-design §3.11：正文 16sp / 1.6 行距
        assertEquals(16.sp, AppTypography.markdownBody.fontSize)
        assertEquals(
            1.6f,
            AppTypography.markdownBody.lineHeight.value / AppTypography.markdownBody.fontSize.value,
            LINE_HEIGHT_RATIO_TOLERANCE,
        )
    }

    @Test
    fun markdownCode_codeFontAppended_isMonospace() {
        // plan.md §5.4「代码等宽追加」
        assertEquals(FontFamily.Monospace, AppTypography.markdownCode.fontFamily)
        assertEquals(14.sp, AppTypography.markdownCode.fontSize)
        assertEquals(20.sp, AppTypography.markdownCode.lineHeight)
    }

    @Test
    fun markdownTokens_letterSpacingUnspecified_keepsConsumerRenderingUnchanged() {
        // 现状 core/markdown 未设 letterSpacing：令牌必须同样留 Unspecified，
        // 否则接线时字距会静默变化（有意加字距请在令牌里显式写值）。
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading1.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading2.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading3.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading4.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading5.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownHeading6.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownBody.letterSpacing)
        assertEquals(TextUnit.Unspecified, AppTypography.markdownCode.letterSpacing)
    }

    @Test
    fun markdownTokens_metricFieldsUnset_matchCoreMarkdownStatusQuo() {
        // core/markdown 现状的 TextStyle 不带 platformStyle / lineHeightStyle；
        // 令牌若擅自补上，接线那一刻行盒与首行内边距就会变（本票不做视觉变更）。
        assertNull(AppTypography.markdownBody.platformStyle)
        assertNull(AppTypography.markdownBody.lineHeightStyle)
        assertNull(AppTypography.markdownCode.platformStyle)
        assertNull(AppTypography.markdownHeading1.platformStyle)
    }

    private companion object {
        /** 25.6f / 16f 在 float 上不必精确等于 1.6f —— 比值断言留 1e-4 容差 */
        const val LINE_HEIGHT_RATIO_TOLERANCE = 1e-4f
    }
}
