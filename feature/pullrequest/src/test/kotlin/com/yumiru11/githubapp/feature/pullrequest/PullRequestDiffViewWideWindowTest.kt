package com.yumiru11.githubapp.feature.pullrequest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-1：宽窗口（≥ 600dp）保持 side-by-side —— 切换按钮可见、切换后真的渲染两栏。
 *
 * 这是宽度门禁的反向守卫：若把阈值判定写反（宽窗口反而隐藏），
 * 两条测试都必红；若切段未生效（content 没换），[diffView_wideWindow_switchingToSideBySide_rendersTwoColumns]
 * 会停在 context 行只出现 1 次 → 必红（side-by-side 双栏各渲染一次）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp")
class PullRequestDiffViewWideWindowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setDiffContent() {
        composeRule.setContent {
            AppTheme(darkTheme = false) {
                Box(modifier = Modifier.size(width = 800.dp, height = 1280.dp)) {
                    PullRequestDiffView(
                        path = DIFF_FIXTURE_PATH,
                        diff = diffViewFixtureLines(),
                        comments = emptyList(),
                        threads = emptyList(),
                        onLineComment = { _, _, _ -> },
                    )
                }
            }
        }
    }

    @Test
    fun diffView_wideWindow_offersSideBySideToggle() {
        setDiffContent()

        composeRule.onNodeWithText("Unified").assertExists()
        composeRule.onNodeWithText("Side-by-side").assertExists()
    }

    @Test
    fun diffView_wideWindow_switchingToSideBySide_rendersBothColumns() {
        setDiffContent()
        // 默认 unified：context 行只渲染一次
        composeRule.onAllNodesWithText(CONTEXT_DIFF_LINE).assertCountEquals(1)

        composeRule.onNodeWithText("Side-by-side").performClick()
        composeRule.mainClock.advanceTimeBy(1_000) // Crossfade 300ms：等过渡走完再看节点数
        composeRule.waitForIdle()

        // side-by-side：context 行在新旧两栏各渲染一次
        composeRule.onAllNodesWithText(CONTEXT_DIFF_LINE).assertCountEquals(2)
    }
}
