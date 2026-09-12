package com.yumiru11.githubapp.feature.repo

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.datastore.model.RepoLayoutMode
import com.yumiru11.githubapp.core.testing.MainDispatcherRule
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 「仓库」大分区（底部导航中间 Tab，#166 / UI01+UI02）截图基准（4 帧）。
 *
 * 基准 PNG：feature/repo/src/test/screenshots/：
 * - `ReposScreen_light` / `ReposScreen_dark`：通栏列表形态（light / dark）
 * - `ReposScreen_grid_light`：网格形态（UI01 布局切换的像素面）
 * - `ReposScreen_guest_light`：游客态登录引导空态
 *
 * 入库基线只能由 CI "Record screenshots (CI canonical)" workflow 录制。
 *
 * 用 `captureScreenshotDeterministic`：与仓库域其余帧统一捕获路径（规避 Roborazzi
 * compose 捕获的 `idle()` 挂死面，根因见 [captureScreenshotDeterministic]），并且
 * 捕获期间安装离线 ImageLoader（头像 `AsyncImage` 不触网，像素与网络无关）。
 * ViewModel 用 [reposScreenshotViewModel] 桩注入固定分页数据（触网的分页源不参与）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ReposScreenScreenshotTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reposScreen_listLayout_lightTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReposScreen_light", darkTheme = false) {
            reposScreenUnderTest(layoutMode = RepoLayoutMode.LIST)
        }
    }

    @Test
    fun reposScreen_listLayout_darkTheme_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReposScreen_dark", darkTheme = true) {
            reposScreenUnderTest(layoutMode = RepoLayoutMode.LIST)
        }
    }

    @Test
    fun reposScreen_gridLayout_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReposScreen_grid_light", darkTheme = false) {
            reposScreenUnderTest(layoutMode = RepoLayoutMode.GRID)
        }
    }

    @Test
    fun reposScreen_guestState_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "ReposScreen_guest_light", darkTheme = false) {
            reposScreenUnderTest(layoutMode = RepoLayoutMode.LIST, anonymous = true)
        }
    }

    @Composable
    private fun reposScreenUnderTest(
        layoutMode: RepoLayoutMode,
        anonymous: Boolean = false,
    ) {
        ReposScreen(
            onOpenRepository = { _, _ -> },
            onLoginClick = {},
            // 宿主 MainTabPager 下发的底栏总高（生产含系统导航栏 inset；离屏帧取常驻高度的代表值）
            bottomContentPadding = BOTTOM_BAR_HEIGHT,
            viewModel = reposScreenshotViewModel(layoutMode = layoutMode, anonymous = anonymous),
        )
    }

    private companion object {
        /** 底栏常驻高度代表值（MainActivity 的 `padding.calculateBottomPadding()` 同量级）。 */
        val BOTTOM_BAR_HEIGHT = 80.dp
    }
}
