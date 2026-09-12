package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
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
 * 新建仓库页（L04）截图基准（light / dark 两帧，表单填齐态）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/CreateRepoScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与仓库域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 [createRepoScreenshotViewModel]：真实 VM + relaxed 仓库桩，表单填齐
 * （名称/描述/私有开）→ 提交按钮可用态；不触发任何网络。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CreateRepoScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun createRepoScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CreateRepoScreen_light", darkTheme = false) {
            CreateRepoScreen(viewModel = createRepoScreenshotViewModel())
        }
    }

    @Test
    fun createRepoScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CreateRepoScreen_dark", darkTheme = true) {
            CreateRepoScreen(viewModel = createRepoScreenshotViewModel())
        }
    }
}
