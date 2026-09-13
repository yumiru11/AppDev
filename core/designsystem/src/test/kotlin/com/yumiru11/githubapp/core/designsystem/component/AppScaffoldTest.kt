package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [AppScaffold] 默认值与逃生舱的**行为**断言（设计系统 Batch 4，计划 §3.1① / ADR-0010 决策 3）。
 *
 * 断言设计成「红色敏感」：每条都针对一处「包装层若把 M3 默认值抄错就必红」的量。
 * 反向证明（本 PR 已实测）：把 [AppScaffold] 的 `contentColor` 默认值改成 `Color.Unspecified`
 * 后 [appScaffold_defaultParams_contentSeesOnBackground] 必红；改回 `contentColorFor(containerColor)`
 * 后恢复绿——即本测试真的在检查默认值的转发，而不是「组合没崩就算过」。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppScaffoldTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 默认 `containerColor = colorScheme.background` 必须把内容色送到 `onBackground`：
     * 若包装层漏传/抄错 `contentColor`，默认态内容会是 `Unspecified` 而不是主题色。
     */
    @Test
    fun appScaffold_defaultParams_contentSeesOnBackground() {
        var expected: Color? = null
        var actual: Color? = null
        composeRule.setContent {
            AppTheme {
                expected = MaterialTheme.colorScheme.onBackground
                AppScaffold { actual = LocalContentColor.current }
            }
        }
        composeRule.waitForIdle()
        assertEquals("默认内容色必须是 onBackground（contentColorFor(background)）", expected, actual)
    }

    /**
     * 逃生舱：覆盖 `containerColor` 时，`contentColor` 默认值必须**随之重算**
     * （`contentColorFor(containerColor)`），而不是钉死在背景色。
     */
    @Test
    fun appScaffold_customContainerColor_contentColorFollowsIt() {
        var expected: Color? = null
        var actual: Color? = null
        composeRule.setContent {
            AppTheme {
                val container = MaterialTheme.colorScheme.surfaceVariant
                expected = MaterialTheme.colorScheme.onSurfaceVariant
                AppScaffold(containerColor = container) { actual = LocalContentColor.current }
            }
        }
        composeRule.waitForIdle()
        assertEquals("contentColor 默认值必须随 containerColor 重算", expected, actual)
    }

    /** topBar 槽必须真实参与测量：有顶栏时内容收到的上内边距严格大于无顶栏时。 */
    @Test
    fun appScaffold_topBar_contentGetsLargerTopPaddingThanWithout() {
        var withTopBar = PaddingValues()
        var withoutTopBar = PaddingValues()
        composeRule.setContent {
            AppTheme {
                Box {
                    AppScaffold(
                        modifier = Modifier.fillMaxWidth(),
                        topBar = { Box(Modifier.fillMaxWidth().height(48.dp)) },
                    ) { padding -> withTopBar = padding }
                    AppScaffold(modifier = Modifier.fillMaxWidth()) { padding -> withoutTopBar = padding }
                }
            }
        }
        composeRule.waitForIdle()
        assertTrue(
            "有顶栏的上内边距(${withTopBar.calculateTopPadding()}) 必须大于无顶栏(${withoutTopBar.calculateTopPadding()})",
            withTopBar.calculateTopPadding() > withoutTopBar.calculateTopPadding(),
        )
    }

    /** 内容延伸场景：`contentWindowInsets` 逃生舱必须真的透传（0 insets → 无顶栏时上内边距为 0）。 */
    @Test
    fun appScaffold_zeroWindowInsets_noTopBarTopPadding() {
        var observed: PaddingValues? = null
        composeRule.setContent {
            AppTheme {
                AppScaffold(
                    contentWindowInsets = WindowInsets(0.dp),
                ) { padding -> observed = padding }
            }
        }
        composeRule.waitForIdle()
        assertEquals("0 insets 且无顶栏时上内边距必须为 0", 0.dp, observed?.calculateTopPadding())
    }

    /** snackbarHost 槽必须被渲染（迁移批把裸 `SnackbarHost` 换成 `AppSnackbarHost` 后落在这里）。 */
    @Test
    fun appScaffold_snackbarHostSlot_rendersItsContent() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            AppTheme {
                AppScaffold(
                    snackbarHost = { if (visible) Text(text = "host-slot") },
                ) {}
            }
        }
        composeRule.onNodeWithText("host-slot").assertIsDisplayed()
        visible = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("host-slot").assertDoesNotExist()
    }
}
