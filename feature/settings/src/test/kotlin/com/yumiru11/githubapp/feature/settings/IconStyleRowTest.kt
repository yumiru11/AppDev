package com.yumiru11.githubapp.feature.settings

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 设置页「图标风格」入口语义测试（issue #168 / UI12 解禁 FEEDBACK #6 隐藏项）。
 *
 * 断言三档风格都可见、点选回调带正确枚举、选中态有 Selected 语义（TalkBack 单选）。
 * 副标题也回显当前风格名 → 用「文本 + 可点」定位预览卡本身（hasClickAction）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconStyleRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun iconStyleRow_rendersAllThreeStyles() {
        composeRule.setContent {
            AppTheme { IconStyleRow(selected = IconStyle.ROUNDED, onSelect = {}) }
        }

        styleCard("Outlined").assertExists()
        styleCard("Rounded").assertExists()
        styleCard("Filled").assertExists()
    }

    @Test
    fun iconStyleRow_selectedStyle_markedSelectedForTalkBack() {
        composeRule.setContent {
            AppTheme { IconStyleRow(selected = IconStyle.OUTLINED, onSelect = {}) }
        }

        styleCard("Outlined").assertIsSelected()
    }

    @Test
    fun iconStyleRow_tapFilledCard_reportsFilledStyle() {
        var picked: IconStyle? = null
        composeRule.setContent {
            AppTheme { IconStyleRow(selected = IconStyle.ROUNDED, onSelect = { picked = it }) }
        }

        styleCard("Filled").performClick()
        assertEquals(IconStyle.FILLED, picked)
    }

    @Test
    fun iconStyleRow_tapRoundedCard_reportsRoundedStyle() {
        var picked: IconStyle? = null
        composeRule.setContent {
            AppTheme { IconStyleRow(selected = IconStyle.FILLED, onSelect = { picked = it }) }
        }

        styleCard("Rounded").performClick()
        assertEquals(IconStyle.ROUNDED, picked)
    }

    /** 风格预览卡（可点 + 含风格名文本；副标题虽同名但没有点击动作）。 */
    private fun styleCard(label: String) = composeRule.onNode(hasText(label) and hasClickAction())
}
