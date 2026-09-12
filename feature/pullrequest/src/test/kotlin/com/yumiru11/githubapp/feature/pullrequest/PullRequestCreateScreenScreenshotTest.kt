package com.yumiru11.githubapp.feature.pullrequest

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 创建 PR 页（T23）截图基准（light / dark 两帧，表单填齐态）。
 *
 * 基准 PNG：feature/pullrequest/src/test/screenshots/PullRequestCreateScreen_{light,dark}.png
 * （入库，只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与 PR 域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 [pullRequestCreateScreenshotViewModel]：真实 VM + 仓库桩，分支候选/写权限
 * 就绪、标题正文已填 → 顶栏 Create 可用；Loading 态含形变加载动画，故不取 Loading 帧。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PullRequestCreateScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestCreateScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestCreateScreen_light", darkTheme = false) {
            pullRequestCreateUnderTest()
        }
    }

    @Test
    fun pullRequestCreateScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestCreateScreen_dark", darkTheme = true) {
            pullRequestCreateUnderTest()
        }
    }

    @Composable
    private fun pullRequestCreateUnderTest() {
        PullRequestCreateScreen(
            owner = "octocat",
            repo = "Hello-World",
            onBackClick = {},
            onCreated = { _, _, _ -> },
            viewModel = pullRequestCreateScreenshotViewModel(),
        )
    }
}
