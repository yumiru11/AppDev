package com.yumiru11.githubapp.core.markdown.fixture

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.yumiru11.githubapp.core.markdown.EnhancedMarkdownViewer
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * §2.3 夹具的**原生渲染路径像素基线**（Robolectric Native Graphics + Roborazzi，纯 JVM）。
 *
 * ## 这一层能证明什么
 *
 * 原生短文本链（`EnhancedMarkdownViewer`：mikepenz 0.38.1 + GFMFlavourDescriptor +
 * KotlinTextMate 高亮 + GitHubAlertCard + EnhancedHtmlBlock 等）渲染每个 §2.3 夹具的
 * **像素产物**与基线逐像素一致 → 排版/配色/间距的意外回归会被拦下。
 *
 * ## 这一层不能证明什么（诚实分层）
 *
 * - **不能**证明 WebView 路径（README/Issue 正文主通道）的渲染结果：Robolectric 下
 *   WebView 无法栅格化像素（`docs/research/screenshot-automation-alt.md`）。WebView 路径
 *   的回归见 `WebViewFixtureRenderModeTest`（产物契约）+ CI 模拟器截图（真实像素）。
 * - **不能**证明网络图片的解码：本类注入 [DeterministicImageTransformer]（固定尺寸位图），
 *   只覆盖图片组件的**布局与尺寸分支**（徽章 20dp / 普通图全宽），不覆盖 Coil 解码。
 * - **不能**证明与 GitHub 网页端的像素等价：基线是「本 App 的既有观感」，不是 GitHub 截图。
 *   与网页端的一致性靠 `SERVER_HTML` 通道（GitHub 自己渲染）+ 报告里的逐条对照。
 *
 * ## 基线录制（重要）
 *
 * 基线**只能**由 CI 的 `Record screenshots (CI canonical)` workflow 录制后提交：
 * Robolectric Native Graphics 的渲染依赖运行环境（字体/原生库/JDK），本机录的基线在 CI 上
 * verify 会全红（`docs/agents/task-audit-2026-09-06.md` / PR #181 实测）。本机禁止跑
 * `recordRoborazziDebug`（1000s+ 卡死史，见 AGENTS.md 铁律）。
 *
 * 期望基线清单见 [MarkdownGfmFixtures.baselineNames]。
 *
 * 测试命名规范：methodName_scenario_expectedBehavior。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w480dp-h1600dp")
class MarkdownFixtureScreenshotTest : ScreenshotTest() {
    @Test
    fun nativeFixtures_lightTheme_renderMatchingBaselines() {
        captureAll(MarkdownGfmFixtures.nativeFixtures(), darkTheme = false)
    }

    @Test
    fun nativeFixtures_darkThemeSubset_renderMatchingBaselines() {
        captureAll(MarkdownGfmFixtures.darkFixtures(), darkTheme = true)
    }

    /**
     * 逐个夹具捕获，**汇总**全部失败后一次性断言。
     *
     * 为什么不用「一个夹具一个 @Test」：单次 verify 要把 24 个夹具跑完才有诊断价值；
     * 若第一个 mismatched 夹具直接中断方法，后续夹具根本不会被比较（Roborazzi 比对失败
     * 抛 AssertionError），回归集就会退化成「只查第一个」。这里吞掉单个夹具的失败、
     * 继续跑完全部，最后把 24 条差异一次报出。
     */
    private fun captureAll(
        fixtures: List<MarkdownGfmFixtures.Fixture>,
        darkTheme: Boolean,
    ) {
        val failures =
            fixtures.mapNotNull { fixture ->
                val baseline = MarkdownGfmFixtures.baselineName(fixture, dark = darkTheme)
                runCatching {
                    captureScreenshot(baseline, darkTheme = darkTheme) {
                        FixtureBody(fixture.markdown(), darkTheme)
                    }
                }.exceptionOrNull()?.let { "$baseline: ${it.message ?: it::class.simpleName}" }
            }

        assertTrue(
            "夹具像素基线不一致/缺失（${fixtures.size} 个夹具已全部比较，失败 ${failures.size} 个）:\n" +
                failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    /** 单个夹具的渲染宿主：与短文本调用点同构（宽度约束 + 内建 37dp 横向 padding）。 */
    @Composable
    private fun FixtureBody(
        markdown: String,
        darkTheme: Boolean,
    ) {
        EnhancedMarkdownViewer(
            markdown = markdown,
            darkTheme = darkTheme,
            imageTransformer = DeterministicImageTransformer,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .width(480.dp),
        )
    }
}

/**
 * 测试专用图片解码桩：任何链接都返回同一张 120×60 的固定位图。
 *
 * 目的：让图片夹具（17 / 18 / 29 / 30 / 31）的像素基线**确定**且不依赖网络——
 * 生产默认的 [com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl] 会发起真实网络请求，
 * 在无网 CI 上产物不确定（且可能拖慢/挂死 Robolectric 渲染）。
 *
 * 它证明的是「图片组件的布局分支」；不证明 Coil 解码正确性（见类 KDoc 的分层说明）。
 */
private object DeterministicImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData {
        val painter: Painter =
            remember(link) {
                val bitmap = Bitmap.createBitmap(STUB_WIDTH_PX, STUB_HEIGHT_PX, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(STUB_COLOR_ARGB)
                BitmapPainter(bitmap.asImageBitmap())
            }
        return ImageData(painter = painter)
    }

    private const val STUB_WIDTH_PX = 120
    private const val STUB_HEIGHT_PX = 60
    private const val STUB_COLOR_ARGB = 0xFF9E9E9E.toInt()
}
