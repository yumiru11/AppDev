package com.yumiru11.githubapp.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [GlassSurface] 降级路径的不透明度回归锁（#201 P0：全屏面板与下层内容叠印）。
 *
 * **为什么要像素断言**（而不是只测 [com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy]）：
 * `layerAlpha` 的正确性可以纯 JVM 断言，但「GlassSurface 到底有没有把那个 alpha 画上去」
 * 只有渲染出来才知道 —— 真机证据正是这么拿到的：CI release
 * `screenshots-pr199-34600531043` 的 `notification-panel.png` 上，面板滤镜行处亮度跨度
 * 39 级、下层 Home 的字形清晰可读（同像素叠印 = 文字不可读）。
 *
 * **为什么不用 `captureToImage()`**：它走 Compose 的 `WindowCapture.forceRedraw`，在
 * Robolectric 下等待窗口重绘会 `ComposeTimeoutException`（实测）。这里用 Roborazzi 同款
 * 路径：把 view 直接画进 Bitmap（`@GraphicsMode(NATIVE)` 下有真实像素），再取像素。
 *
 * 断言口径：背板用 `primary`（与 `surface` 高对比的主题色，遵守「零硬编码颜色」），
 * 玻璃矩形内不放内容 —— 该处像素只可能来自「背板透上来」或「玻璃自身底色」。
 * - 降级 + `opaqueWhenBlurUnavailable = true`（全屏面板）→ 必须等于 surface 原色
 * - 降级 + 默认 `false`（顶栏/底栏/BottomSheet）→ 必须**不等于** surface 原色（半透明语义保留）
 *
 * Robolectric 不渲染 RenderEffect，故这里只覆盖降级路径；API 31+ 的真模糊观感由
 * glass-verify.yml（API 31 模拟器）承担。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class GlassSurfaceOpacityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun glassSurface_opaqueFallbackRequestedWithoutHazeState_rendersFullyOpaqueSurface() {
        renderGlassOverPrimaryBackdrop(opaqueWhenBlurUnavailable = true)

        assertEquals(
            "降级路径 + opaqueWhenBlurUnavailable=true：玻璃底色必须完全遮住背板（P0 叠印）",
            OPAQUE_SURFACE,
            glassAreaPixel(),
        )
    }

    @Test
    fun glassSurface_defaultFallback_keepsTranslucentScrim() {
        // 反向对照：默认行为（顶栏/底栏/BottomSheet 的 #83 契约）必须仍是半透明 ——
        // 没有这条，上一条测试在「GlassSurface 整体改不透明」时也会通过，
        // 而那就破坏了 §6.2 的「滚动穿越感」。
        renderGlassOverPrimaryBackdrop(opaqueWhenBlurUnavailable = false)

        assertNotEquals(
            "默认降级路径必须保持半透明（§6.2：内容从栏后穿过）",
            OPAQUE_SURFACE,
            glassAreaPixel(),
        )
    }

    @Test
    @Config(sdk = [26])
    fun glassSurface_opaqueFallbackRequestedBelowBlurApi_rendersFullyOpaqueSurface() {
        // API 26–30 无 RenderEffect：即使上游提供了 HazeState 也只能降级 → 必须不透明
        renderGlassOverPrimaryBackdrop(opaqueWhenBlurUnavailable = true, hazeStateProvided = true)

        assertEquals(
            "API<31 降级路径必须不透明（CI 截图模拟器正是 API 30）",
            OPAQUE_SURFACE,
            glassAreaPixel(),
        )
    }

    /** 背板 primary 铺满全屏 + 顶部一块玻璃；玻璃内无内容，故该区域只有底色参与成像。 */
    private fun renderGlassOverPrimaryBackdrop(
        opaqueWhenBlurUnavailable: Boolean,
        hazeStateProvided: Boolean = false,
    ) {
        composeRule.setContent {
            MaterialTheme(colorScheme = TEST_SCHEME) {
                val glass: @Composable () -> Unit = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary),
                    ) {
                        GlassSurface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(GLASS_HEIGHT),
                            scope = GlassScope.PANEL,
                            blurEnabled = true,
                            opaqueWhenBlurUnavailable = opaqueWhenBlurUnavailable,
                        ) {}
                    }
                }
                if (hazeStateProvided) {
                    val hazeState = rememberHazeState()
                    CompositionLocalProvider(LocalHazeState provides hazeState) { glass() }
                } else {
                    glass()
                }
            }
        }
    }

    /** 玻璃矩形内、远离边缘的一个像素（玻璃 80dp，采样行 40px 居中）。 */
    private fun glassAreaPixel(): Color {
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Robolectric 视图须已布局", view.width > 0 && view.height > GLASS_HEIGHT_PX)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return Color(bitmap.getPixel(view.width / 2, SAMPLE_Y_PX))
    }

    private companion object {
        /**
         * 断言基准色取自**测试自己构造的同一个 scheme**（不是硬编码 hex）：
         * 「玻璃区像素 == scheme.surface」等价于「背板一点没透上来」，
         * 同时不随 Material3 基线色值换代（#FFFBFE → #FEF7FF）而失效。
         */
        val TEST_SCHEME = lightColorScheme()
        val OPAQUE_SURFACE = TEST_SCHEME.surface

        val GLASS_HEIGHT = 80.dp

        /** Robolectric 默认 density = 1 → 80dp = 80px；采样行取玻璃高度中点 */
        const val GLASS_HEIGHT_PX = 80
        const val SAMPLE_Y_PX = 40
    }
}
