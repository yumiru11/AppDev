package com.yumiru11.githubapp.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RTL 布局矩阵（T25 验收「RTL（ar）截图矩阵通过」，ui-design §1.2「布局一律 start/end」）。
 *
 * 为什么必须有：`start/end` 这类相对方向在 LTR 下与 `left/right` **完全等价**，写成
 * `left/right` 的回归在默认语言下永远测不出来，只有切到 RTL 才暴露（典型症状是图标与
 * 文字贴到错误的一侧、内边距塌陷）。
 *
 * 做法：**显式提供 CompositionLocal**，而不是靠 Robolectric 的 `qualifiers`。
 * 实测（本 PR 踩过）：`@Config(qualifiers = "ar-rSA-ldrtl")` 与 `"+ar-rSA-ldrtl"` 都不生效——
 * Roborazzi 的 `captureRoboImage` 会另起 ActivityScenario，测试级 qualifiers 传不进那次组合，
 * 渲染结果与 LTR **逐字节相同**（等于给了一个永远绿的假测试）。改成在组合内直接
 * `LocalLayoutDirection provides LayoutDirection.Rtl`，语义明确且与宿主配置解耦。
 *
 * 覆盖范围说明：这里选顶栏作为"导航壳代表"——它是 `start/end` 用得最密的一处
 * （搜索胶囊 + 铃铛 + 头像三者排布）。全屏级 RTL 矩阵仍待真机/模拟器补（见 PR 未做项）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarRtlScreenshotTest : ScreenshotTest() {
    @Test
    fun appTopBar_rtlLayout_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTopBar(
                    onSearchClick = {},
                    onNotificationClick = {},
                    onProfileClick = {},
                    unreadCount = 0,
                )
            }
        }
    }
}
