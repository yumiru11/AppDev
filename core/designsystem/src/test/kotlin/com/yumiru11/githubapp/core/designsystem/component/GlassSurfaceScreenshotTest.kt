package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * GlassSurface 截图基准（Robolectric Native Graphics）。
 *
 * Robolectric 限制标注：
 * - `Modifier.blur` 依赖 RenderEffect/BlurEffect（API 31+）。Robolectric 的
 *   Native Graphics 渲染路径对 RenderEffect 的支持有限，**模糊效果在截图基准中
 *   可能不可见**（视觉上仅呈现半透明 surface 层）。真机验证由 T6 完整装配承担。
 * - 因此 [glassSurface_blurEnabled_renders] 的基准图断言的是「半透明 surface +
 *   布局正确」，而非模糊像素本身；模糊分支的代码路径（[Modifier.blur] 挂载）
 *   由编译与 Robolectric 渲染不崩溃来保证。
 * - [glassSurface_api26_fallback_renders] 以 sdk=26 跑降级路径（不做模糊，
 *   纯半透明层），此路径是 Robolectric 下可完整验证的。
 * - Robolectric 不模拟系统栏 insets（通常为 0），insets 分支截图与无 insets
 *   视觉相同，仅验证代码路径可渲染。
 */
class GlassSurfaceScreenshotTest : ScreenshotTest() {

    @Test
    fun glassSurface_blurEnabled_renders() {
        captureScreenshot("GlassSurface_blurEnabled", darkTheme = false) {
            GlassBackdrop {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter),
                    blurEnabled = true,
                ) {
                    GlassBarContent()
                }
            }
        }
    }

    @Test
    fun glassSurface_blurDisabled_renders() {
        captureScreenshot("GlassSurface_blurDisabled", darkTheme = false) {
            GlassBackdrop {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter),
                    blurEnabled = false,
                ) {
                    GlassBarContent()
                }
            }
        }
    }

    @Test
    @Config(sdk = [26])
    fun glassSurface_api26_fallback_renders() {
        captureScreenshot("GlassSurface_api26_fallback", darkTheme = false) {
            GlassBackdrop {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter),
                    blurEnabled = true,
                ) {
                    GlassBarContent()
                }
            }
        }
    }

    @Test
    fun glassSurface_statusBarInsets_renders() {
        captureScreenshot("GlassSurface_statusBarInsets", darkTheme = false) {
            GlassBackdrop {
                GlassSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.TopCenter),
                    windowInsets = WindowInsets.statusBars,
                    blurEnabled = true,
                ) {
                    GlassBarContent()
                }
            }
        }
    }

    /**
     * 玻璃容器内的示意内容（无字符串；颜色一律取自 colorScheme）。
     */
    @Composable
    private fun BoxScope.GlassBarContent() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        )
    }

    /**
     * 彩色形状背板，模拟玻璃层背后滚动的多彩内容，衬托半透明/模糊层。
     */
    @Composable
    private fun GlassBackdrop(content: @Composable BoxScope.() -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .align(Alignment.BottomStart)
                    .background(MaterialTheme.colorScheme.error),
            )
            content()
        }
    }
}
