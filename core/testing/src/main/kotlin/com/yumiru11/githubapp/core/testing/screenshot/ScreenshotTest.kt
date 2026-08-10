package com.yumiru11.githubapp.core.testing.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi 截图基准测试基类（Robolectric Native Graphics，纯 JVM 免模拟器）。
 *
 * 用法：
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * @Config(sdk = [35])
 * class XxxScreenshotTest : ScreenshotTest() {
 *     @Test
 *     fun xxxScreen_lightTheme_matchesBaseline() {
 *         captureScreenshot("XxxScreen_light", darkTheme = false) { XxxScreen() }
 *     }
 * }
 * ```
 *
 * 约定：
 * - 基类已带 Robolectric/Roborazzi 注解，子类可省略；显式重复声明亦可（不影响）
 * - 基准 PNG 输出到消费模块的 `src/test/screenshots/`（入库；build/ 不进版本库）
 * - record：`./gradlew :模块:recordRoborazziDebug`；verify：`./gradlew :模块:verifyRoborazziDebug`
 * - 主题统一包一层 MaterialTheme（light/dark 默认 colorScheme，零硬编码颜色）；
 *   需要自定义主题的模块可在 content 内自行再包主题
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
abstract class ScreenshotTest {
    /** 截图基准目录（相对消费模块项目根），子类可覆盖 */
    protected open val screenshotDir: String = "src/test/screenshots"

    /**
     * 捕获一张截图基准。
     *
     * @param name 基准文件名（不含扩展名），建议 `XxxScreen_light` / `XxxScreen_dark`
     * @param darkTheme 是否使用暗色 colorScheme
     * @param content 被测 Composable（文案一律 stringResource，颜色一律 MaterialTheme.colorScheme）
     */
    protected fun captureScreenshot(
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        captureRoboImage("$screenshotDir/$name.png") {
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                content()
            }
        }
    }
}
