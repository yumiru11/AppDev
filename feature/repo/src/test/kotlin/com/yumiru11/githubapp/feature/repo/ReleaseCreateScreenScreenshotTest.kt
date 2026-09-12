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
 * 新建 Release 页（L05）截图基准（light / dark 两帧，表单填齐态）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/ReleaseCreateScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与仓库域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 [releaseCreateScreenshotViewModel]：真实 VM + relaxed 管理仓库桩，
 * Tag/目标分支/标题/说明填齐 + 预发布开 → 六字段全可见、提交按钮可用态。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReleaseCreateScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun releaseCreateScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReleaseCreateScreen_light", darkTheme = false) {
            ReleaseCreateScreen(viewModel = releaseCreateScreenshotViewModel())
        }
    }

    @Test
    fun releaseCreateScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReleaseCreateScreen_dark", darkTheme = true) {
            ReleaseCreateScreen(viewModel = releaseCreateScreenshotViewModel())
        }
    }
}
