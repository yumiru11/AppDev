package com.yumiru11.githubapp.feature.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.datastore.model.CodeFont
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「代码字体」入口语义测试（T24 死设置收口：消费点落地后才解禁的入口）。
 *
 * 断言两档都可见、点选回调带正确枚举、选中态有 Selected 语义（TalkBack 单选），
 * 与 [IconStyleRowTest] 同款口径。副标题也回显当前字体名 → 用「文本 + 可点」定位 chip 本身
 * （副标题同名但没有点击动作）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CodeFontRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun codeFontRow_rendersBothOptions() {
        composeRule.setContent {
            AppTheme { CodeFontRow(selected = CodeFont.MONO, onSelect = {}) }
        }

        fontChip("Monospace").assertExists()
        fontChip("System default").assertExists()
    }

    @Test
    fun codeFontRow_selectedFont_markedSelectedForTalkBack() {
        composeRule.setContent {
            AppTheme { CodeFontRow(selected = CodeFont.SYSTEM, onSelect = {}) }
        }

        fontChip("System default").assertIsSelected()
    }

    @Test
    fun codeFontRow_tapSystem_reportsSystemFont() {
        var picked: CodeFont? = null
        composeRule.setContent {
            AppTheme { CodeFontRow(selected = CodeFont.MONO, onSelect = { picked = it }) }
        }

        fontChip("System default").performClick()
        assertEquals(CodeFont.SYSTEM, picked)
    }

    @Test
    fun codeFontRow_tapMono_reportsMonoFont() {
        var picked: CodeFont? = null
        composeRule.setContent {
            AppTheme { CodeFontRow(selected = CodeFont.SYSTEM, onSelect = { picked = it }) }
        }

        fontChip("Monospace").performClick()
        assertEquals(CodeFont.MONO, picked)
    }

    /** 字体 chip（可点 + 含字体名文本；副标题虽同名但没有点击动作）。 */
    private fun fontChip(label: String) = composeRule.onNode(hasText(label) and hasClickAction())
}
