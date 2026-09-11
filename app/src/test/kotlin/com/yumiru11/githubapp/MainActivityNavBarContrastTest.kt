package com.yumiru11.githubapp

import android.view.Window
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [disableNavigationBarContrastScrim] 的契约测试（P0 系统栏 insets 修复）。
 *
 * ## 为什么断言 Window 标志位
 *
 * 「系统是否在三键导航栏上叠半透明 scrim」在窗口层就是 `isNavigationBarContrastEnforced`
 * 这一个布尔：`enableEdgeToEdge()` 置 `true` → 系统画 scrim（官方 edge-to-edge 指南原文
 * 「For three-button navigation bar, set `Window.setNavigationBarContrastEnforced` to false
 * otherwise there will be a translucent scrim applied」）。断言它等价于断言系统不再压暗
 * 延伸进导航栏区域的底栏玻璃；像素级差异（(14,18,23)→(7,9,12)）在 Robolectric 里渲染不出，
 * 且 CI 截图链路不渲染系统栏（见 `SystemBarInsets` KDoc）。
 *
 * ## 为什么不用 `buildActivity(MainActivity)`
 *
 * `MainActivity` 的 `@AndroidEntryPoint` 注入链会构造 `EncryptedTokenStorage`
 * （AndroidKeyStore 在纯 JVM 不可用）——所以本测试直接对**裸 Activity 的 Window** 调
 * 生产扩展函数，路径与 `MainActivity.onCreate` 中那一行完全一致。
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityNavBarContrastTest {
    @Test
    @Config(sdk = [35])
    fun disableNavigationBarContrastScrim_threeButtonNav_setsFlagFalse() {
        val window = bareWindow()

        window.isNavigationBarContrastEnforced = true // enableEdgeToEdge 的默认效果
        window.disableNavigationBarContrastScrim()

        assertFalse(
            "API 35 上必须把 isNavigationBarContrastEnforced 置 false，" +
                "否则系统会在三键导航栏区域叠半透明 scrim、压暗延伸进该区域的底栏玻璃",
            window.isNavigationBarContrastEnforced,
        )
    }

    @Test
    @Config(sdk = [29])
    fun disableNavigationBarContrastScrim_api29_setsFlagFalse() {
        // 开关自 API 29 引入；29 是官方文档口径下的最低生效版本
        val window = bareWindow()

        window.isNavigationBarContrastEnforced = true
        window.disableNavigationBarContrastScrim()

        assertFalse("API 29 起该开关必须生效", window.isNavigationBarContrastEnforced)
    }

    @Test
    @Config(sdk = [28])
    fun disableNavigationBarContrastScrim_api28_isNoOp() {
        // API 28 无此字段（`Window.isNavigationBarContrastEnforced` 自 API 29 引入）：
        // 生产代码的版本守卫必须拦住它，否则真机会 NoSuchMethodError。
        // 注意：API 28 沙箱里连读取属性都会 NoSuchMethodError，故这里**只**断言调用不抛异常。
        val window = bareWindow()

        window.disableNavigationBarContrastScrim()
    }

    @Test
    @Config(sdk = [35])
    fun window_navigationBarContrastFlag_isWritable() {
        // 观测力护栏：若标志位在 Robolectric 下读不出真实值，上面的断言全是假绿
        val window = bareWindow()

        window.isNavigationBarContrastEnforced = true

        assertTrue(
            "Window.isNavigationBarContrastEnforced 必须可读写，否则本文件的断言无观测力",
            window.isNavigationBarContrastEnforced,
        )
    }

    private fun bareWindow(): Window =
        Robolectric
            .buildActivity(androidx.activity.ComponentActivity::class.java)
            .create()
            .get()
            .window
}
