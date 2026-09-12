package com.yumiru11.githubapp.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

/**
 * RTL 布局矩阵（T25 验收「RTL（ar）截图矩阵通过」，ui-design §1.2「布局一律 start/end」）。
 *
 * 为什么必须有：`start/end` 这类相对方向在 LTR 下与 `left/right` **完全等价**，写成
 * `left/right` 的回归在默认语言下永远测不出来，只有切到 RTL 才暴露（典型症状是图标与
 * 文字贴到错误的一侧、内边距塌陷）。
 *
 * 做法：**显式提供 CompositionLocal**，而不是靠 Robolectric 的 `qualifiers`。
 * 实测（`ScratchRtlProbeTest`，2026-09-12）：`@Config(qualifiers = "ar-rSA-ldrtl")` 与
 * `"+ar-rSA-ldrtl"` 都不生效——探针在 qualifier 下读到 `cfgLocale=ar-SA` 但
 * `LocalLayoutDirection.current` 仍是 `Ltr`，红蓝块坐标与默认帧逐像素相同
 * （sha `d4fd0314…`，等于一个永远绿的假测试）。改成在组合内直接
 * `LocalLayoutDirection provides LayoutDirection.Rtl`，语义明确且与宿主配置解耦。
 *
 * 两道守卫（防「假绿」回归）：
 * 1. [appTopBar_rtlLayout_matchesBaseline] 在组合期断言方向真的是 Rtl——注入被误删/改错时
 *    立刻红，而不是安静地拍出一张 LTR 图；
 * 2. [appTopBar_rtlBaseline_differsFromLtrBaseline] 断言 RTL 基线 PNG 与 LTR 基线
 *    **逐字节不同**——即使方向断言被人绕过，基线相同也会把这个假矩阵揪出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarRtlScreenshotTest : ScreenshotTest() {
    @Test
    fun appTopBar_rtlLayout_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_rtl", darkTheme = false) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                check(LocalLayoutDirection.current == LayoutDirection.Rtl) {
                    "RTL 帧必须在 RTL 组合下拍摄（当前=${LocalLayoutDirection.current}）——" +
                        "方向注入失效时此断言必须红"
                }
                AppTopBar(
                    onSearchClick = {},
                    onNotificationClick = {},
                    onProfileClick = {},
                    unreadCount = 0,
                )
            }
        }
    }

    @Test
    fun appTopBar_rtlBaseline_differsFromLtrBaseline() {
        val rtl = File(screenshotDir, "AppTopBar_rtl.png")
        val ltr = File(screenshotDir, "AppTopBar_light.png")
        assertTrue("RTL 基线缺失：$rtl（先跑 CI 录制 workflow）", rtl.isFile)
        assertTrue("LTR 基线缺失：$ltr", ltr.isFile)
        assertNotEquals(
            "RTL 基线与其 LTR 对照逐字节相同 —— RTL 矩阵是假绿（方向注入未生效）",
            sha256(ltr),
            sha256(rtl),
        )
    }

    private fun sha256(file: File): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { "%02x".format(it) }
}
