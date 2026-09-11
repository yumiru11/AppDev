package com.yumiru11.githubapp.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextStyle
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppTheme] 的 typography 接线（本票核心断言）：令牌必须真的进到 `MaterialTheme`，
 * 而不是停在 `AppTypography` 里当摆设。
 *
 * 断言方式 = 从组合内读回 `MaterialTheme.typography` 再与 [AppTypography.from] 逐档比对；
 * 同时断言**同一实例**（`Typography` 无 `equals`，`assertSame` 才能证明没有中间复制/改写）。
 *
 * 之前 `MaterialTheme(...)` 没传 `typography` → 走 M3 内置默认值，与 [AppTypography]
 * 构成「双事实来源」（同 #9 一类的缺陷）；本测试是这条接线的回归锁。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppThemeTypographyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appTheme_defaultTheme_injectsAppTypographyTokens() {
        var provided: Typography? = null

        composeRule.setContent {
            AppTheme {
                provided = MaterialTheme.typography
            }
        }
        composeRule.waitForIdle()

        val actual = requireNotNull(provided) { "AppTheme 未组合内容" }
        assertEquals(stylesByName(AppTypography.from()), stylesByName(actual))
        assertSame(AppTypography.from(), actual)
    }

    @Test
    fun appTheme_oledTheme_injectsAppTypographyTokens() {
        var provided: Typography? = null

        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.OLED) {
                provided = MaterialTheme.typography
            }
        }
        composeRule.waitForIdle()

        val actual = requireNotNull(provided) { "AppTheme 未组合内容" }
        assertEquals(stylesByName(AppTypography.from()), stylesByName(actual))
    }

    /** 15 档按名字收成 map：断言失败时输出直接指出是哪一档，而非「第几个参数」。 */
    private fun stylesByName(typography: Typography): Map<String, TextStyle> =
        linkedMapOf(
            "displayLarge" to typography.displayLarge,
            "displayMedium" to typography.displayMedium,
            "displaySmall" to typography.displaySmall,
            "headlineLarge" to typography.headlineLarge,
            "headlineMedium" to typography.headlineMedium,
            "headlineSmall" to typography.headlineSmall,
            "titleLarge" to typography.titleLarge,
            "titleMedium" to typography.titleMedium,
            "titleSmall" to typography.titleSmall,
            "bodyLarge" to typography.bodyLarge,
            "bodyMedium" to typography.bodyMedium,
            "bodySmall" to typography.bodySmall,
            "labelLarge" to typography.labelLarge,
            "labelMedium" to typography.labelMedium,
            "labelSmall" to typography.labelSmall,
        )
}
