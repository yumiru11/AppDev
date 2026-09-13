package com.yumiru11.githubapp.feature.pullrequest

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.yumiru11.githubapp.core.testing.screenshot.captureScreenshotDeterministic
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-1 截图基准（两帧，均需由 CI canonical `record-screenshots.yml` 录制）：
 *
 * - `PullRequestDiffNarrow_light`：411dp 手机窗口 —— 无切换按钮（unified-only），
 *   超长行按自然宽排版、视口内只见行首（可横向滚动）。
 * - `PullRequestDiffWideSideBySide_light`：800dp 宽窗口 —— 仍提供切换按钮，切段后双栏渲染
 *   （保留既有 side-by-side 行为）。
 *
 * 窄帧走共享 [captureScreenshotDeterministic]；宽帧需先点「Side-by-side」再拍，
 * 共享 helper 没有交互钩子 → 用它同一条确定性路径就地实现（见
 * [captureScreenshotAfterInteraction]）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class PullRequestDiffViewNarrowScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestDiffView_narrowWindow_unified_matchesBaseline() {
        composeRule.captureScreenshotDeterministic(name = "PullRequestDiffNarrow_light", darkTheme = false) {
            DiffViewScreenshotContent(width = 411.dp, height = 891.dp)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h1280dp")
class PullRequestDiffViewWideScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pullRequestDiffView_wideWindow_sideBySide_matchesBaseline() {
        composeRule.captureScreenshotAfterInteraction(
            name = "PullRequestDiffWideSideBySide_light",
            darkTheme = false,
            interaction = {
                composeRule.onNodeWithText("Side-by-side").performClick()
                // 拍照前的硬断言：context 行双栏各一次（unified 只一次）——切段未生效即失败，
                // 不给「看起来是 unified 的 side-by-side 帧」留缝隙。
                composeRule.onAllNodesWithText(CONTEXT_DIFF_LINE).assertCountEquals(2)
            },
        ) {
            DiffViewScreenshotContent(width = 800.dp, height = 1280.dp)
        }
    }
}

@Composable
private fun DiffViewScreenshotContent(
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
) {
    Box(modifier = Modifier.size(width = width, height = height)) {
        PullRequestDiffView(
            path = DIFF_FIXTURE_PATH,
            diff = diffViewFixtureLines(),
            comments = emptyList(),
            threads = emptyList(),
            onLineComment = { _, _, _ -> },
        )
    }
}

/**
 * 与 [captureScreenshotDeterministic] 同一条确定性捕获路径（手工 `decorView.draw` +
 * `Bitmap.captureRoboImage`），额外允许在捕获前执行一次 UI 交互。
 *
 * 与共享 helper 的差异：
 * - 交互发生在时钟**自动推进**阶段（`waitForIdle` 把点击与 Crossfade 推进到静默）——
 *   首次实现直接在冻结时钟下点击，事件不生效，断言红（已实证）；
 * - 画面静态后再 `mainClock.autoAdvance = false` 取图，录制/校验两次仍逐像素同源；
 * - 本帧无图片加载，故不安装 OfflineImageLoader。
 */
private fun AndroidComposeTestRule<*, ComponentActivity>.captureScreenshotAfterInteraction(
    name: String,
    darkTheme: Boolean,
    interaction: () -> Unit,
    content: @Composable () -> Unit,
) {
    setContent {
        MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
            content()
        }
    }
    waitForIdle()
    // 交互在 autoAdvance=true 下执行：输入事件与 Crossfade（有限动画）都靠 waitForIdle
    // 正常推进到静默（冻结时钟时点击不会生效，见本帧首次实现的失败记录）。
    // 待画面静态后再冻结时钟取图，录制/校验两次运行仍为同一像素。
    interaction()
    waitForIdle()
    mainClock.autoAdvance = false

    val view = activity.window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    bitmap.captureRoboImage("src/test/screenshots/$name.png")
}
