package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import com.yumiru11.githubapp.core.ui.RepoDetailActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 仓库详情页截图基准（light / dark × 文件分区 / 空 README 两屏，共 4 帧）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/RepoDetailScreen_{light,dark}.png 与
 * RepoDetailScreen_readmeEmpty_{light,dark}.png（入库，只能由 CI
 * "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：文件分区含 Paging/分页与刷新指示器等无限动画，
 * Roborazzi 的 compose 捕获路径 `idle()` 永不返回（根因见
 * [captureScreenshotDeterministic]；README WebView 无法离屏栅格化，故文件分区帧固定
 * 文件树 —— 见 RepoScreenshotFixtures 文件头）。
 *
 * UI-4 空 README 帧：空态**不走 WebView**（无栅格化问题），可以离屏拍；它同时是
 * AppEmptyState 收编的像素回归锚点（此前该状态只有非门禁的模拟器 repo-actions 帧）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class RepoDetailScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repoDetailScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "RepoDetailScreen_light", darkTheme = false) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                initialShowFiles = true,
                viewModel = repoDetailScreenshotViewModel(),
                filesViewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
            )
        }
    }

    @Test
    fun repoDetailScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "RepoDetailScreen_dark", darkTheme = true) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                initialShowFiles = true,
                viewModel = repoDetailScreenshotViewModel(),
                filesViewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
            )
        }
    }

    /** UI-4：README 分区空态（AppEmptyState：File 图标 + 标题 + 说明 + 「查看文件」行动）。 */
    @Test
    fun repoDetailScreen_readmeEmpty_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "RepoDetailScreen_readmeEmpty_light", darkTheme = false) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                initialShowFiles = false,
                viewModel = repoDetailScreenshotViewModel(),
                filesViewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
            )
        }
    }

    @Test
    fun repoDetailScreen_readmeEmpty_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "RepoDetailScreen_readmeEmpty_dark", darkTheme = true) {
            RepoDetailScreen(
                owner = "octocat",
                repo = "Hello-World",
                initialShowFiles = false,
                viewModel = repoDetailScreenshotViewModel(),
                filesViewModel = repoFilesScreenshotViewModel(),
                actions = RepoDetailActions(),
            )
        }
    }
}
