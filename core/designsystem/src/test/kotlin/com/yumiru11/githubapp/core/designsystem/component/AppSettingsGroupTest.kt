package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppSettingsGroup] 语义与几何断言。
 *
 * 几何断言打的是纯函数 [appSettingsGroupItemCorners]（含反例），不是像素——
 * 「首末条取外圆角、中段取内圆角、按压放大」是本组件的核心契约，必须可回归。
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppSettingsGroupTest {
    @get:Rule
    val composeRule = createComposeRule()

    // ── 纯函数：位置化圆角 ────────────────────────────────────────────────

    @Test
    fun corners_singleItem_allFourCornersAreOuter() {
        val corners = appSettingsGroupItemCorners(count = 1, index = 0, pressed = false)
        assertEquals(AppDimens.cornerCardOuter, corners.topStart)
        assertEquals(AppDimens.cornerCardOuter, corners.bottomEnd)
    }

    @Test
    fun corners_firstItem_topOuterBottomInner() {
        val corners = appSettingsGroupItemCorners(count = 3, index = 0, pressed = false)
        assertEquals(AppDimens.cornerCardOuter, corners.topStart)
        assertEquals(AppDimens.cornerCardInner, corners.bottomStart)
    }

    @Test
    fun corners_middleItem_allFourCornersAreInner() {
        val corners = appSettingsGroupItemCorners(count = 3, index = 1, pressed = false)
        assertEquals(AppDimens.cornerCardInner, corners.topStart)
        assertEquals(AppDimens.cornerCardInner, corners.bottomEnd)
    }

    @Test
    fun corners_lastItem_topInnerBottomOuter() {
        val corners = appSettingsGroupItemCorners(count = 3, index = 2, pressed = false)
        assertEquals(AppDimens.cornerCardInner, corners.topStart)
        assertEquals(AppDimens.cornerCardOuter, corners.bottomStart)
    }

    @Test
    fun corners_middleItemPressed_bulgesToOuterCorners() {
        val corners = appSettingsGroupItemCorners(count = 3, index = 1, pressed = true)
        assertEquals(AppDimens.cornerCardOuter, corners.topStart)
        assertEquals(AppDimens.cornerCardOuter, corners.topEnd)
        assertEquals(AppDimens.cornerCardOuter, corners.bottomStart)
        assertEquals(AppDimens.cornerCardOuter, corners.bottomEnd)
    }

    @Test
    fun corners_outerIsStrictlyLargerThanInner_soTheBulgeIsVisible() {
        // 反例护栏：外/内圆角若被改成同值，"按压鼓起来"会静默失效
        assert(AppDimens.cornerCardOuter > AppDimens.cornerCardInner)
    }

    // ── 渲染 + 交互 ──────────────────────────────────────────────────────

    @Test
    fun group_withTitle_rendersTitleAsHeading() {
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup(title = "外观") {
                    item(headlineContent = { Text("主题") })
                }
            }
        }
        composeRule
            .onNodeWithText("外观")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
    }

    @Test
    fun item_withOnClick_clickFires() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    item(onClick = { clicks++ }, headlineContent = { Text("偏好设置") })
                }
            }
        }
        composeRule.onNodeWithText("偏好设置").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun item_withoutOnClick_hasNoClickAction() {
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    item(headlineContent = { Text("只读条目") })
                }
            }
        }
        composeRule.onNodeWithText("只读条目").assertHasNoClickAction()
    }

    @Test
    fun item_disabled_clickDoesNotFire() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    item(onClick = { clicks++ }, enabled = false, headlineContent = { Text("禁用条目") })
                }
            }
        }
        composeRule.onNodeWithText("禁用条目").performClick()
        assertEquals(0, clicks)
    }

    /**
     * 核心无障碍契约：整行是**唯一**的可切换节点（Role.Switch），
     * 尾部 Switch 自身不可交互（onCheckedChange = null）——否则 TalkBack 会出现两个节点。
     */
    @Test
    fun switchItem_wholeRowIsTheOnlyToggleableNode() {
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    switchItem(
                        checked = false,
                        onCheckedChange = {},
                        headlineContent = { Text("毛玻璃") },
                        supportingContent = { Text("顶栏与底栏模糊") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("毛玻璃").assertIsOff()
        composeRule.onAllNodesWithText("毛玻璃").assertCountEquals(1)
        composeRule.onAllNodes(isToggleable()).assertCountEquals(1)
    }

    @Test
    fun switchItem_clickOnRow_reportsToggledValue() {
        var toggled: Boolean? = null
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    switchItem(
                        checked = false,
                        onCheckedChange = { toggled = it },
                        headlineContent = { Text("毛玻璃") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("毛玻璃").performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun switchItem_checked_stateIsReadableForTalkBack() {
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    switchItem(checked = true, onCheckedChange = {}, headlineContent = { Text("高清对比") })
                }
            }
        }
        composeRule.onNodeWithText("高清对比").assertIsOn()
    }

    @Test
    fun switchItem_disabled_clickDoesNotToggle() {
        var toggled: Boolean? = null
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    switchItem(
                        checked = false,
                        onCheckedChange = { toggled = it },
                        enabled = false,
                        headlineContent = { Text("顶部栏") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("顶部栏").performClick()
        assertEquals(null, toggled)
    }

    @Test
    fun item_indented_stillRendersHeadlineAndSupporting() {
        composeRule.setContent {
            AppTheme {
                AppSettingsGroup {
                    item(
                        indented = true,
                        headlineContent = { Text("顶部栏") },
                        supportingContent = { Text("依赖总开关") },
                    )
                }
            }
        }
        composeRule.onNodeWithText("顶部栏").assertIsDisplayed()
        composeRule.onNodeWithText("依赖总开关").assertIsDisplayed()
    }
}
