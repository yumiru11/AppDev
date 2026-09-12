package com.yumiru11.githubapp.core.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.yumiru11.githubapp.core.datastore.model.ThemeMode
import com.yumiru11.githubapp.core.designsystem.theme.AppTheme
import com.yumiru11.githubapp.core.testing.screenshot.ScreenshotTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * 主题/语言矩阵补全帧（ADR-0004 主题引擎 + i18n）：OLED、高对比、zh-rCN 三个此前
 * 完全没有截图回归的维度。
 *
 * 为什么选顶栏做代表：它是 `MaterialTheme.colorScheme.*` 与玻璃层混色最密集的一处
 * （OLED 纯黑面、HC 高对比描边、搜索提示文案），且尺寸小、无动画、渲染稳定。
 *
 * 与 [AppTopBarScreenshotTest]（light/dark 基线）分开成类：本类每帧都要换
 * `AppTheme(themeMode=…)` 或语言上下文，变量是主题/语言而不是明暗。
 *
 * 维度清单（实测盘点，2026-09-12）：
 * - `ThemeMode` 共 7 档：SYSTEM / LIGHT / DARK / OLED / HIGH_CONTRAST /
 *   DYNAMIC_LIGHT / DYNAMIC_DARK。动态取色依赖 API 31+ 壁纸，Robolectric 下无壁纸可提，
 *   探针环境取固定回退色板（=light/dark），截图没有区分度 → 本矩阵只补
 *   **OLED**（`oledPalette()`，纯黑 #000000）与 **HIGH_CONTRAST**
 *   （`highContrastLightPalette()`，白底黑字 + 深蓝/深红）。
 * - 语言：资源只有 en（默认）与 zh-rCN 两套；仓库无 ar 资源，`ar-rSA` 下文案会 fallback 到 en，
 *   所以 RTL 维度用「方向注入」表达（见 [AppTopBarRtlScreenshotTest]），语言维度用 zh-rCN 表达。
 *
 * zh 帧**不用** `@Config(qualifiers = "zh-rCN")`：qualifier 只能改宿主配置，且实测
 * 组合内 `LocalConfiguration` 能读到 zh-CN、但 `LocalResources`（`stringResource`
 * 的真实数据源，compose-ui 1.11.4 字节码确认读取 `AndroidCompositionLocals.getLocalResources`）
 * 是否同步不可靠。这里在组合内显式提供 `LocalContext` / `LocalConfiguration` /
 * `LocalResources` 三者，把「文案确实来自 zh 资源」变成组合期断言。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppTopBarMatrixScreenshotTest : ScreenshotTest() {
    @Test
    fun appTopBar_oledTheme_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_oled", darkTheme = true) {
            AppTheme(themeMode = ThemeMode.OLED, darkTheme = true) {
                MatrixTopBar()
            }
        }
    }

    @Test
    fun appTopBar_highContrastTheme_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_highContrast", darkTheme = false) {
            AppTheme(themeMode = ThemeMode.HIGH_CONTRAST, darkTheme = false) {
                MatrixTopBar()
            }
        }
    }

    @Test
    fun appTopBar_zhLocale_matchesBaseline() {
        captureScreenshot(name = "AppTopBar_zh", darkTheme = false) {
            ZhLocale {
                MatrixTopBar()
            }
        }
    }
}

@Composable
private fun MatrixTopBar() {
    AppTopBar(
        onSearchClick = {},
        onNotificationClick = {},
        onProfileClick = {},
        unreadCount = 0,
    )
}

/**
 * 在组合内切换到 zh-CN 资源上下文。
 *
 * `stringResource` 读取的是 `LocalResources`（compose-ui 1.11.4 字节码实证），
 * 只换 `LocalContext` 依然会拿到 en 文案 → 必须三者一起提供。
 */
@Composable
private fun ZhLocale(content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized =
        remember(base) {
            val config =
                Configuration(base.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag("zh-CN"))
                    setLayoutDirection(Locale.forLanguageTag("zh-CN"))
                }
            base.createConfigurationContext(config)
        }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources,
    ) {
        check(
            localized.resources.configuration.locales[0]
                .language == "zh",
        ) {
            "zh 帧必须在 zh 资源上下文下拍摄（当前=${localized.resources.configuration.locales[0]}）"
        }
        // 文案确实来自 zh 资源（而不是 en fallback）——search_hint 是顶栏可见文案
        check(localized.resources.getString(R.string.search_hint) != base.getString(R.string.search_hint)) {
            "search_hint 在 zh 与 en 下解析相同 —— zh 注入未生效"
        }
        content()
    }
}
