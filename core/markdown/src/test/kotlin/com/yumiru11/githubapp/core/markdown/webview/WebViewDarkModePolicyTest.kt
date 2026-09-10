package com.yumiru11.githubapp.core.markdown.webview

import android.webkit.WebSettings
import io.mockk.mockk
import org.junit.Test

/**
 * WebView 暗化策略装配（#170）。
 *
 * 核心断言只有一个：**在不支持该特性的 WebView 上，装配过程不得抛异常**。
 * 这正是 2026-08-16 模拟器 logcat 里的 FATAL EXCEPTION，也是模块截图门禁首次真跑时
 * 抓到的 feature:issue 失败点。
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
 * - 特性支持位是函数参数（见 WebViewDarkModePolicy 的注释），无需真实探测
 * - WebSettings 用 mockk 造（core:markdown 已开 isReturnDefaultValues，Android 桩
 *   返回默认值而不是抛 "not mocked"）
 * - WebSettingsCompat 内部无论抛什么，都被函数里的 runCatching 吃掉 —— 而"吃掉异常"
 *   正是本测试要断言的语义
 */
class WebViewDarkModePolicyTest {
    @Test
    fun applyWebViewDarkModePolicy_featuresReportedSupported_doesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)

        // 最坏组合：版本检查说"支持"，但真实调用仍抛 UnsupportedOperationException
        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = true,
            supportsForceDarkStrategy = true,
        )
    }

    @Test
    fun applyWebViewDarkModePolicy_featuresReportedUnsupported_doesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)

        // 旧 WebView / API<31 路径：一次 WebSettingsCompat 都不该调，也不该抛
        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = false,
            supportsForceDarkStrategy = false,
        )
    }

    @Test
    fun applyWebViewDarkModePolicy_realFeatureProbe_doesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)

        // 走**默认参数**（即真实探测 WebViewFeature.isFeatureSupported）。
        // 纯 JVM 环境下 SDK_INT 读桩默认值 0，探测结果必然是"不支持"，正好覆盖
        // "旧 WebView"这条生产路径 —— 真机上的探测分支由模块截图/真机走查兜底。
        applyWebViewDarkModePolicy(settings)
    }

    @Test
    fun applyWebViewDarkModePolicy_mixedSupport_doesNotThrow() {
        val settings = mockk<WebSettings>(relaxed = true)

        // 只有其中一项受支持（真实世界的常见组合：FORCE_DARK_STRATEGY 比
        // ALGORITHMIC_DARKENING 早一个版本落地）
        applyWebViewDarkModePolicy(
            settings = settings,
            supportsAlgorithmicDarkening = false,
            supportsForceDarkStrategy = true,
        )
    }
}
