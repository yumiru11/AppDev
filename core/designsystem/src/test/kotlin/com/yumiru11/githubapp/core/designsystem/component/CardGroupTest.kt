package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [cardGroupSegmentCorners] 圆角解析纯函数断言（#87）：
 * 首末外圆角 / 中段内圆角 / 按压整段鼓起 / 单条目恒外圆角。
 */
class CardGroupSegmentCornersTest {
    @Test
    fun cardGroupSegmentCorners_singleItem_allCornersOuter() {
        val corners = cardGroupSegmentCorners(count = 1, index = 0, pressed = false)
        assertEquals(GroupSegmentCorners(20.dp, 20.dp, 20.dp, 20.dp), corners)
    }

    @Test
    fun cardGroupSegmentCorners_firstItem_topOuterBottomInner() {
        val corners = cardGroupSegmentCorners(count = 3, index = 0, pressed = false)
        assertEquals(
            GroupSegmentCorners(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
            corners,
        )
    }

    @Test
    fun cardGroupSegmentCorners_middleItem_allCornersInner() {
        val corners = cardGroupSegmentCorners(count = 3, index = 1, pressed = false)
        assertEquals(GroupSegmentCorners(4.dp, 4.dp, 4.dp, 4.dp), corners)
    }

    @Test
    fun cardGroupSegmentCorners_lastItem_topInnerBottomOuter() {
        val corners = cardGroupSegmentCorners(count = 3, index = 2, pressed = false)
        assertEquals(
            GroupSegmentCorners(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
            corners,
        )
    }

    @Test
    fun cardGroupSegmentCorners_pressedMiddleItem_expandsAllCornersOuter() {
        val corners = cardGroupSegmentCorners(count = 3, index = 1, pressed = true)
        assertEquals(GroupSegmentCorners(20.dp, 20.dp, 20.dp, 20.dp), corners)
    }

    @Test
    fun cardGroupSegmentCorners_pressedFirstItem_bottomExpandsToOuter() {
        val corners = cardGroupSegmentCorners(count = 2, index = 0, pressed = true)
        assertEquals(GroupSegmentCorners(20.dp, 20.dp, 20.dp, 20.dp), corners)
    }
}

/** [CardGroup] 渲染与点击语义断言（Robolectric compose-test，不建截图基线，#84 Q3 同款口径）。 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CardGroupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardGroup_multipleItems_renderAllContent() {
        composeRule.setContent {
            AppTheme {
                CardGroup {
                    item { androidx.compose.material3.Text("外观项") }
                    item { androidx.compose.material3.Text("通用项") }
                }
            }
        }
        composeRule.onNodeWithText("外观项").assertIsDisplayed()
        composeRule.onNodeWithText("通用项").assertIsDisplayed()
    }

    @Test
    fun cardGroup_clickableItem_invokesOnClick() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                CardGroup {
                    item(onClick = { clicks++ }) { androidx.compose.material3.Text("关于") }
                }
            }
        }
        composeRule.onNodeWithText("关于").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun cardGroup_nonClickableItem_clickDoesNotInvoke() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                CardGroup {
                    item(onClick = null) { androidx.compose.material3.Text("开关行") }
                    item(onClick = { clicks++ }) { androidx.compose.material3.Text("可点行") }
                }
            }
        }
        composeRule.onNodeWithText("开关行").performClick()
        composeRule.onNodeWithText("可点行").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
