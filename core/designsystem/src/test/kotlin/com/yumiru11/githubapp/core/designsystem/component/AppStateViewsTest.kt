package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppEmptyState]/[AppErrorState]/[AppLoadingState] 语义断言（不建截图基线，#84 决策 Q3）。
 *
 * [AppLoadingState] 自 M3 Expressive 接入起内部改用 `LoadingIndicator`（形变加载指示，
 * 替代 `CircularProgressIndicator`）—— 见 ADR-0008；此处断言其**进度语义**未丢失。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppStateViewsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appEmptyState_fullConfig_rendersAndTriggersAction() {
        var clicks = 0
        composeRule.setContent {
            AppTheme {
                AppEmptyState(
                    icon = AppDevOcticons.Repo,
                    title = "Nothing here",
                    message = "Create your first repo",
                    actionLabel = "New repository",
                    onAction = { clicks++ },
                )
            }
        }
        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Create your first repo").assertIsDisplayed()
        composeRule.onNodeWithText("New repository").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun appEmptyState_titleOnly_rendersWithoutCrash() {
        composeRule.setContent {
            AppTheme { AppEmptyState(icon = AppDevOcticons.Star, title = "No stars") }
        }
        composeRule.onNodeWithText("No stars").assertIsDisplayed()
    }

    @Test
    fun appErrorState_retryAction_triggersCallback() {
        var retries = 0
        composeRule.setContent {
            AppTheme {
                AppErrorState(
                    title = "Failed to load",
                    message = "Network unavailable",
                    actionLabel = "Retry",
                    onAction = { retries++ },
                )
            }
        }
        composeRule.onNodeWithText("Failed to load").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    /**
     * 逃生舱契约：`action` 槽非空时**替代**内置按钮（`actionLabel` 被忽略）。
     *
     * 锁定「厚包装 + 逃生舱」的优先级语义，防止将来重构把两种行动区叠在一起渲染。
     */
    @Test
    fun appEmptyState_customActionSlot_replacesBuiltInButton() {
        var customClicks = 0
        composeRule.setContent {
            AppTheme {
                AppEmptyState(
                    icon = AppDevOcticons.Repo,
                    title = "Nothing here",
                    actionLabel = "Built-in action",
                    onAction = {},
                    action = {
                        TextButton(onClick = { customClicks++ }) {
                            Text(text = "Custom action")
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithText("Custom action").assertIsDisplayed()
        composeRule.onNodeWithText("Built-in action").assertDoesNotExist()
        composeRule.onNodeWithText("Custom action").performClick()
        assertEquals(1, customClicks)
    }

    /** 逃生舱契约：错误态同款 `action` 槽（替代内置重试按钮），单测与空态对称。 */
    @Test
    fun appErrorState_customActionSlot_replacesBuiltInButton() {
        var customClicks = 0
        composeRule.setContent {
            AppTheme {
                AppErrorState(
                    title = "Failed to load",
                    actionLabel = "Built-in retry",
                    onAction = {},
                    action = {
                        TextButton(onClick = { customClicks++ }) {
                            Text(text = "Custom retry")
                        }
                    },
                )
            }
        }
        composeRule.onNodeWithText("Custom retry").assertIsDisplayed()
        composeRule.onNodeWithText("Built-in retry").assertDoesNotExist()
        composeRule.onNodeWithText("Custom retry").performClick()
        assertEquals(1, customClicks)
    }

    @Test
    fun appLoadingState_withLabel_rendersLabel() {
        composeRule.setContent {
            AppTheme { AppLoadingState(label = "Loading…") }
        }
        composeRule.onNodeWithText("Loading…").assertIsDisplayed()
    }

    @Test
    fun appLoadingState_withoutLabel_rendersWithoutCrash() {
        composeRule.setContent {
            AppTheme { AppLoadingState() }
        }
        composeRule.waitForIdle()
    }

    /**
     * M3 Expressive 接入后仍须暴露**进度语义**（读屏能播报"加载中"）。
     *
     * `LoadingIndicator` 与旧的 `CircularProgressIndicator` 都会往
     * `SemanticsProperties.ProgressBarRangeInfo` 写值（alpha18 字节码实测：
     * `LoadingIndicatorImpl` 内 `setProgressBarRangeInfo`），本断言把这层契约钉死——
     * 若将来换成纯装饰性图形（无 progress 语义）会立刻失败。
     */
    @Test
    fun appLoadingState_rendersIndeterminateProgressSemantics() {
        composeRule.setContent {
            AppTheme { AppLoadingState() }
        }
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertIsDisplayed()
    }
}
