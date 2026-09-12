package com.yumiru11.githubapp.feature.notifications.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 通知面板玻璃外壳的不透明性回归锁（#201 P0：面板正文区与下层内容同像素叠印）。
 *
 * **缺陷实证**（CI release `screenshots-pr199-34600531043` 的 `notification-panel.png`）：
 * 面板铺满全屏（`fillMaxSize`）而底色只有 0.75 alpha 的 surface、遮罩（0.5）又整块被面板
 * 盖住 → 下层 Home 以 0.25 × 0.5 = **12.5% 浓度、且不模糊地**透上来：面板标题
 * `Notifications` 与下层 `Search GitHub…` 叠印、筛选行与下层 `Issues` / `Pull Requests`
 * 叠印、列表末项与底栏 `Home` 叠印（同帧实测亮度跨度 39 级，下层字形清晰可读）。
 * CI 截图模拟器是 **API 30**（`AppBlur.MIN_BLUR_API = 31`）→ 走 `TranslucentScrim`
 * 降级、完全没有模糊，所以叠印的是**可读文字**而非糊块。
 *
 * 本测试锁「接线」：`NotificationsPanelSurface` 必须把背后的内容完全遮住。若有人摘掉
 * `opaqueWhenBlurUnavailable = true`，下面断言的像素就不再等于 surface 原色。
 * 机制侧（alpha 判定 + GlassSurface 是否真画上去）由 `GlassRenderPolicyTest` /
 * `GlassSurfaceOpacityTest` 锁定。
 *
 * 像素取法：Roborazzi 同款「view 直接画进 Bitmap」（Compose 的 `captureToImage()` 在
 * Robolectric 下会 `ComposeTimeoutException`，实测）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NotificationsPanelSurfaceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun panelSurface_blurUnavailable_coversBackdropCompletely() {
        renderPanelOverPrimaryBackdrop(blurEnabled = true)

        assertEquals(
            "面板正文区必须完全遮住下层内容（P0：半透明降级 = 文字叠印不可读）",
            OPAQUE_SURFACE,
            centrePixel(),
        )
    }

    @Test
    fun panelSurface_blurDisabledByUser_coversBackdropCompletely() {
        // 设置页关掉「通知面板毛玻璃」也落在同一条降级路径 → 一样必须不透明
        renderPanelOverPrimaryBackdrop(blurEnabled = false)

        assertEquals(
            "用户关闭毛玻璃开关时降级路径同样必须不透明",
            OPAQUE_SURFACE,
            centrePixel(),
        )
    }

    /** primary 背板铺满全屏（模拟面板背后的 Home 内容）+ 面板外壳（内容为空）。 */
    private fun renderPanelOverPrimaryBackdrop(blurEnabled: Boolean) {
        composeRule.setContent {
            MaterialTheme(colorScheme = TEST_SCHEME) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary),
                ) {
                    NotificationsPanelSurface(blurEnabled = blurEnabled, onDismiss = {}) {}
                }
            }
        }
    }

    private fun centrePixel(): Color {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Robolectric 视图须已布局", view.width > 0 && view.height > 0)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(view.width / 2, view.height / 2))
    }

    private companion object {
        /**
         * 断言基准色取自**测试自己构造的同一个 scheme**（不是硬编码 hex）：
         * 「面板区像素 == scheme.surface」等价于「面板背后的内容一点没透上来」，
         * 同时不随 Material3 基线色值换代（#FFFBFE → #FEF7FF）而失效。
         */
        val TEST_SCHEME = lightColorScheme()
        val OPAQUE_SURFACE = TEST_SCHEME.surface
    }
}
