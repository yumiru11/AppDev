@file:Suppress("DEPRECATION") // 与 WebViewDarkModePolicy 同因：WebSettingsCompat 暗化 API 上游 deprecated

package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Test

/**
 * WebView 暗化策略装配（#170；断言补全 = backlog GATE-1/TEST-2 同一批审计）。
 *
 * 要守的契约不止「不抛异常」一条（原 4 个测试只有调用、没有 verify，空转也能绿）：
 * 1. **支持位 → 调用**：`supports* = true` 时必须真的调用对应的 WebSettingsCompat 接口，
 *    且**恰好一次**（漏调用 = 暗化静默失效，页面在深色主题下不跟随）；
 * 2. **不支持 → 零调用**：`supports* = false`（旧 WebView / API<31）时一次都不能调 ——
 *    调了就会抛 `UnsupportedOperationException`（2026-08-16 模拟器 logcat 的 FATAL EXCEPTION）；
 * 3. **兜底吞噬**：即使支持位为 true 而 compat 调用仍抛异常（androidx.webkit 已知行为），
 *    也必须被 `runCatching` 吞掉，装配过程绝不外抛。
 *
 * 断言手段：`WebSettingsCompat` / `WebViewFeature` 都是静态工具类，用 `mockkStatic` 把
 * 「调用与否」与「调用是否抛异常」两个维度都变成可断言对象（`verify(exactly = ...)`）。
 *
 * ## 为什么这个测试**必须**是纯 JVM 测试（不加 @RunWith(RobolectricTestRunner)）
 *
 * 实测（本地 + CI 一致）：**被 Robolectric 沙箱加载的类不会产出 JaCoCo 覆盖数据**
 * ——Robolectric 用自己的 SandboxClassLoader 重新定义字节码，JaCoCo agent 的
 * ClassFileTransformer 在那条路径上不生效。表现是：测试全绿，但覆盖率报告里该类 0/N。
 * 同模块的 PrivateImageInterceptor（Robolectric 测试）就是这种"有测试无覆盖"的状态。
 *
 * 后果很实际：diff 覆盖率硬门禁会判定"新增代码 0% 覆盖"而直接红。所以凡是希望被
 * 覆盖率门禁认账的逻辑，都必须能在纯 JVM 下测。
 *
 * 这里能这么做的原因：
 * - 特性支持位是函数参数（见 WebViewDarkModePolicy 的注释），无需真实探测；默认参数里的
 *   真实探测（`WebViewFeature.isFeatureSupported`）用 mockkStatic 精确复现「探测抛异常」
 *   与「探测逐项返回支持」两条路径，不再依赖运行环境的偶然行为。
 * - WebSettings 用 mockk 造（core:markdown 已开 isReturnDefaultValues，Android 桩
 *   返回默认值而不是抛 "not mocked"）
 * - WebSettingsCompat 调用被 mockkStatic 接管，可精确复现"检查通过但调用不支持"的组合
 */
class WebViewDarkModePolicyTest {
    @After
    fun tearDown() = unmockkAll()

    @Test
    fun applyWebViewDarkModePolicy_featuresReportedSupported_attemptsEachCallOnceAndDoesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)
        mockkStatic(WebSettingsCompat::class)
        // 最坏组合：版本检查说"支持"，但真实调用仍抛 UnsupportedOperationException
        // （Robolectric / 部分 stub WebView 上的已实测行为）→ 必须被 runCatching 吞掉。
        every { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) } throws
            UnsupportedOperationException("stub: WebView APK 不支持算法暗化")
        every { WebSettingsCompat.setForceDarkStrategy(any(), any()) } throws
            UnsupportedOperationException("stub: WebView APK 不支持 force-dark 策略")

        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = true,
            supportsForceDarkStrategy = true,
        )

        // 两次调用都必须真的发生（否则"吞异常"无从谈起），参数必须正确，且各恰好一次。
        verify(exactly = 1) { WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false) }
        verify(exactly = 1) {
            WebSettingsCompat.setForceDarkStrategy(settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
        }
    }

    @Test
    fun applyWebViewDarkModePolicy_featuresReportedUnsupported_makesNoCompatCalls() {
        val settings = mockk<WebSettings>(relaxed = true)
        mockkStatic(WebSettingsCompat::class)
        every { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) } just Runs
        every { WebSettingsCompat.setForceDarkStrategy(any(), any()) } just Runs

        // 旧 WebView / API<31 路径
        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = false,
            supportsForceDarkStrategy = false,
        )

        // 一次 WebSettingsCompat 都不该调（调了就会抛 → 崩溃）。
        verify(exactly = 0) { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) }
        verify(exactly = 0) { WebSettingsCompat.setForceDarkStrategy(any(), any()) }
    }

    @Test
    fun applyWebViewDarkModePolicy_realFeatureProbeThrows_treatedAsUnsupportedWithoutCompatCalls() {
        val settings = mockk<WebSettings>(relaxed = true)
        mockkStatic(WebViewFeature::class)
        mockkStatic(WebSettingsCompat::class)
        // 纯 JVM / 无 WebView APK 环境下 isFeatureSupported 会【抛异常】而不是返回 false，
        // 而它原本在默认参数位置 → 异常会在进入函数体之前抛出（#170 实测）。探测失败必须按
        // "不支持"处理：不崩溃、且一次 WebSettingsCompat 都不调。
        every { WebViewFeature.isFeatureSupported(any()) } throws
            IllegalStateException("stub: 无 WebView APK")
        every { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) } just Runs
        every { WebSettingsCompat.setForceDarkStrategy(any(), any()) } just Runs

        applyWebViewDarkModePolicy(settings) // 默认参数 = 真实探测入口

        verify(exactly = 0) { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) }
        verify(exactly = 0) { WebSettingsCompat.setForceDarkStrategy(any(), any()) }
    }

    @Test
    fun applyWebViewDarkModePolicy_realFeatureProbeReportsSupported_attemptsOnlyThatCall() {
        val settings = mockk<WebSettings>(relaxed = true)
        mockkStatic(WebViewFeature::class)
        mockkStatic(WebSettingsCompat::class)
        // 逐项探测：ALGORITHMIC_DARKENING 支持、FORCE_DARK_STRATEGY 不支持（真实世界的常见组合，
        // 后者比前者早一个版本落地）→ 默认参数的接线必须把两个支持位分别传给对应分支。
        every { WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) } returns true
        every { WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY) } returns false
        every { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) } just Runs

        applyWebViewDarkModePolicy(settings)

        verify(exactly = 1) { WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false) }
        verify(exactly = 0) { WebSettingsCompat.setForceDarkStrategy(any(), any()) }
    }

    @Test
    fun applyWebViewDarkModePolicy_mixedSupport_attemptsOnlySupportedCall() {
        val settings = mockk<WebSettings>(relaxed = true)
        val webThemeDarkeningOnly = WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
        mockkStatic(WebSettingsCompat::class)
        every { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) } just Runs
        every { WebSettingsCompat.setForceDarkStrategy(any(), any()) } just Runs

        // 只有其中一项受支持
        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = false,
            supportsForceDarkStrategy = true,
        )

        // 只有受支持的那一项被调用；策略必须是已拍板的 WEB_THEME_DARKENING_ONLY
        // （防"算法暗化 + UA 暗化"双重变暗），不能被换成别的常量。
        verify(exactly = 0) { WebSettingsCompat.setAlgorithmicDarkeningAllowed(any(), any()) }
        verify(exactly = 1) { WebSettingsCompat.setForceDarkStrategy(settings, webThemeDarkeningOnly) }
    }
}
