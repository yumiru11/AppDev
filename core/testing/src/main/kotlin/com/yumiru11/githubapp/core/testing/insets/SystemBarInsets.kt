package com.yumiru11.githubapp.core.testing.insets

import android.view.View
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/**
 * 系统栏 inset 注入夹具（Robolectric 下 Compose 默认读不到真实窗口 insets）。
 *
 * ## 为什么需要（存在理由）
 *
 * Robolectric 的根部窗口 insets 恒为 0：实测 `WindowInsets.navigationBars` 在
 * `@Config(sdk = [35])` 与 `[28]`、gesture 与三键 qualifier 下都返回 `0.dp`，
 * 且窗口层不会向视图树派发非 0 insets。于是**真实 insets 路径在纯 JVM 测试里不可达**，
 * 而「底栏 / 系统导航栏压住内容」这类缺陷恰恰只在**三键导航栏（inset 非 0）**下出现。
 *
 * 本夹具绕过窗口层，直接写 Compose 内部持有的 `WindowInsetsHolder`——它就是
 * `WindowInsets.navigationBars` / `statusBars` 的**唯一数据源**，被测组件读到的
 * 是平时读的同一个对象，因此这是「真值注入」而非平行假数据。
 *
 * ## 为什么用反射
 *
 * `WindowInsetsHolder` 及其 companion 在 compose-foundation 中是 `internal`，
 * 反射是唯一入口。代价是升级 compose-foundation 后内部名可能漂移；[applyTo]
 * 会逐级 `error(...)` 显式失败，不会静默变成「注入无效但测试还绿」。
 *
 * ## 用法
 *
 * ```
 * @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
 *
 * composeRule.setContent { ... }              // 宿主先渲染（holder 随 ComposeView 创建）
 * composeRule.waitForIdle()
 * composeRule.onRoot().captureComposeView()   // 或调用方自行捕获 LocalView
 * SystemBarInsets.applyTo(composeView, SystemBarInsets.THREE_BUTTON)
 * composeRule.waitForIdle()                    // 让新 insets 走完组合 / 布局
 * ```
 */
object SystemBarInsets {
    /** 手势导航：仅手势条（导航栏 inset 24dp），状态栏 24dp。 */
    val GESTURE = InsetsSpec(navigationBarDp = 24, statusBarDp = 24)

    /** 三键导航：导航栏 48dp（Android 标准），状态栏 24dp。 */
    val THREE_BUTTON = InsetsSpec(navigationBarDp = 48, statusBarDp = 24)

    /** 无系统栏（全屏 / 沉浸）。 */
    val NONE = InsetsSpec(navigationBarDp = 0, statusBarDp = 0)

    /** inset 规格（dp，密度由被测窗口决定）。 */
    data class InsetsSpec(
        val navigationBarDp: Int,
        val statusBarDp: Int,
    )

    /**
     * 把 [spec] 写进 [composeView] 的 insets holder。
     *
     * 必须在宿主已渲染（ComposeView 已创建）后调用，并在之后 `waitForIdle()`。
     */
    fun applyTo(
        composeView: View,
        spec: InsetsSpec,
    ) {
        val holder = insetsHolderFor(composeView)
        val density = composeView.resources.displayMetrics.density
        setInsets(holder, "getNavigationBars", bottomPx = (spec.navigationBarDp * density).toInt())
        setInsets(holder, "getStatusBars", topPx = (spec.statusBarDp * density).toInt())
    }

    private fun insetsHolderFor(composeView: View): Any {
        val holderClass = Class.forName(HOLDER_CLASS)
        val companion =
            holderClass
                .getDeclaredField("Companion")
                .also { it.isAccessible = true }
                .get(null)
                ?: error("$HOLDER_CLASS.Companion 取不到：compose-foundation 内部结构已变")
        val holder =
            companion.javaClass
                .getMethod("getOrCreateFor", View::class.java)
                .invoke(companion, composeView)
                ?: error("WindowInsetsHolder.getOrCreateFor 返回 null")
        return holder
    }

    private fun setInsets(
        holder: Any,
        getterName: String,
        topPx: Int = 0,
        bottomPx: Int = 0,
    ) {
        val insets =
            holder.javaClass.getMethod(getterName).invoke(holder)
                ?: error("$getterName 返回 null")
        val setter =
            insets.javaClass.methods.firstOrNull {
                it.name.startsWith("setInsets") && it.parameterTypes.size == 1
            } ?: error("AndroidWindowInsets 无 setInsets：compose-foundation 内部结构已变")
        setter.invoke(
            insets,
            androidx.core.graphics.Insets
                .of(0, topPx, 0, bottomPx),
        )
    }

    private const val HOLDER_CLASS = "androidx.compose.foundation.layout.WindowInsetsHolder"
}

/** 便捷扩展：`composeRule.setContent{}` 后调一次即可拿到 ComposeView。 */
fun ComposeContentTestRule.applySystemBarInsets(
    composeViewProvider: () -> View,
    spec: SystemBarInsets.InsetsSpec,
) {
    waitForIdle()
    SystemBarInsets.applyTo(composeViewProvider(), spec)
    waitForIdle()
}
