package com.yumiru11.githubapp.feature.issue

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
 * 创建 Issue 页（T14 / #163 L02）截图基准（light / dark 两帧）。
 *
 * 基准 PNG：feature/issue/src/test/screenshots/CreateIssueScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与 Issue 域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 [createIssueScreenshotViewModel]：真实 VM + 仓库桩，标签候选项已加载
 * （多选 chips 可见）+ 草稿正文已恢复；标题为屏内局部状态，保持空串。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CreateIssueScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createIssueScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CreateIssueScreen_light", darkTheme = false) {
            createIssueUnderTest()
        }
    }

    @Test
    fun createIssueScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CreateIssueScreen_dark", darkTheme = true) {
            createIssueUnderTest()
        }
    }

    @Composable
    private fun createIssueUnderTest() {
        CreateIssueScreen(
            owner = "octocat",
            repo = "Hello-World",
            onBackClick = {},
            onCreated = {},
            viewModel = createIssueScreenshotViewModel(),
        )
    }
}
