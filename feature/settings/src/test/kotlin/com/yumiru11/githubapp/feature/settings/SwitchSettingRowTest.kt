package com.yumiru11.githubapp.feature.settings

import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [SwitchSettingRow] 无障碍与行为测试（T24 为此新增可选 `contentDescription` 形参）。
 *
 * 背景：行标题与说明文本都是 Switch 的**兄弟节点**，TalkBack 单独停在 Switch 上只会念
 * 「开关，关闭」，用户不知道这是哪一项。T24 的「行号」开关传入行标题补名 —— 这里把该契约
 * 钉死（改为「开关必须能按行标题定位」），并断言旧调用点（不传 contentDescription）行为不变。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SwitchSettingRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun switchSettingRow_withContentDescription_switchIsLabelled() {
        composeRule.setContent {
            AppTheme {
                SwitchSettingRow(
                    title = "Line numbers",
                    description = "Show line numbers in the code editor",
                    checked = true,
                    onCheckedChange = {},
                    contentDescription = "Line numbers",
                )
            }
        }

        composeRule
            .onNode(hasContentDescription("Line numbers") and isToggleable())
            .assertIsOn()
    }

    @Test
    fun switchSettingRow_tapSwitch_reportsToggledValue() {
        var toggled: Boolean? = null
        composeRule.setContent {
            AppTheme {
                SwitchSettingRow(
                    title = "Line numbers",
                    checked = true,
                    onCheckedChange = { toggled = it },
                    contentDescription = "Line numbers",
                )
            }
        }

        composeRule.onNode(hasContentDescription("Line numbers") and isToggleable()).performClick()
        assertEquals(false, toggled)
    }

    @Test
    fun switchSettingRow_withoutContentDescription_stillRendersTitleAndSwitch() {
        // 旧调用点（外观分组其余 8 行）不传 contentDescription：标题照常渲染、开关照常可点
        var toggled: Boolean? = null
        composeRule.setContent {
            AppTheme {
                SwitchSettingRow(
                    title = "High contrast",
                    checked = false,
                    onCheckedChange = { toggled = it },
                )
            }
        }

        composeRule.onNodeWithText("High contrast").assertExists()
        composeRule.onNode(isToggleable()).performClick()
        assertEquals(true, toggled)
    }
}
