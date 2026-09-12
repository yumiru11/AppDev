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
 * COMMIT 详情页（L09）截图基准（3 帧）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/：
 * - `CommitDetailScreen_light` / `CommitDetailScreen_dark`：提交头 + 文件变更列表态
 * - `CommitDetailScreen_diff_light`：页内单文件 unified diff 态（+/- 行着色与行号）
 *
 * 入库基线只能由 CI "Record screenshots (CI canonical)" workflow 录制。
 *
 * 用 `captureScreenshotDeterministic`：与仓库域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]），捕获期间
 * 离线 ImageLoader 让作者头像不触网。ViewModel 用 [commitDetailScreenshotViewModel]
 * （真实 VM + [CommitRepository] 桩），Loading 态含形变加载动画，故不取 Loading 帧。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class CommitDetailScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun commitDetailScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CommitDetailScreen_light", darkTheme = false) {
            CommitDetailScreen(viewModel = commitDetailScreenshotViewModel())
        }
    }

    @Test
    fun commitDetailScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CommitDetailScreen_dark", darkTheme = true) {
            CommitDetailScreen(viewModel = commitDetailScreenshotViewModel())
        }
    }

    @Test
    fun commitDetailScreen_diffView_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "CommitDetailScreen_diff_light", darkTheme = false) {
            CommitDetailScreen(viewModel = commitDetailScreenshotViewModel(withDiff = true))
        }
    }
}
