package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 分支管理页截图基准（light / dark 两帧，可推送会话的列表形态）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/BranchesScreen_{light,dark}.png（入库，
 * 只能由 CI "Record screenshots (CI canonical)" workflow 录制）。
 *
 * 用 `captureScreenshotDeterministic`：与仓库域其余帧统一捕获路径（避免 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]）。
 * ViewModel 用 mockk 注入固定状态（与 [RepoDetailInitialViewTest] 同款）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BranchesScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun branchesViewModel(): BranchesViewModel =
        mockk(relaxed = true) {
            every { uiState } returns
                MutableStateFlow(
                    BranchesUiState.Success(
                        branches =
                            listOf(
                                Branch(name = "main", sha = "abc123", isProtected = true),
                                Branch(name = "feature/screenshot-expansion", sha = "def456"),
                                Branch(name = "release/v1.0", sha = "ghi789"),
                            ),
                        defaultBranch = "main",
                        canPush = true,
                        isBusy = false,
                    ),
                )
            every { events } returns emptyFlow()
        }

    @Test
    fun branchesScreen_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "BranchesScreen_light", darkTheme = false) {
            BranchesScreen(
                owner = "octocat",
                repo = "Hello-World",
                currentRef = "feature/screenshot-expansion",
                onBackClick = {},
                onBranchSelected = {},
                viewModel = branchesViewModel(),
            )
        }
    }

    @Test
    fun branchesScreen_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "BranchesScreen_dark", darkTheme = true) {
            BranchesScreen(
                owner = "octocat",
                repo = "Hello-World",
                currentRef = "feature/screenshot-expansion",
                onBackClick = {},
                onBranchSelected = {},
                viewModel = branchesViewModel(),
            )
        }
    }
}
