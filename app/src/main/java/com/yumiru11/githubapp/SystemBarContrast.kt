package com.yumiru11.githubapp

import android.os.Build
import android.view.Window

/**
 * 关闭系统在三键导航栏上叠加的半透明 scrim（P0 系统栏 insets 修复）。
 *
 * ## 为什么必须关（Android 官方口径）
 *
 * `enableEdgeToEdge()` 在 API 29+ 会把 `window.isNavigationBarContrastEnforced` 置 `true`，
 * 系统随即在**三键导航栏**区域再叠一层 ~50% 半透明黑，把 App 画在那里的内容压暗。
 * 官方文档（Compose「About system bar protection」、Views「Display content edge-to-edge」）：
 *
 * > For three-button navigation bar, set `Window.setNavigationBarContrastEnforced` to false
 * > otherwise there will be a translucent scrim applied.
 *
 * 本 App 的设计契约是玻璃**延伸进**导航栏区域——`AppBottomBar` 的 `GlassSurface`
 * `windowInsets = WindowInsets.navigationBars`，就是让顶/底栏的玻璃背景盖住系统栏
 * （`docs/ui-design.md` §6.1 NavigationBar 允许点位 + §6.2「滚动穿越感」）。
 * 系统 scrim 叠在玻璃**之上**，会把底栏最下面 48dp 压成一条黑带：
 * 既切断 Haze 模糊的连续性，也和 §6.2「栏糊掉内容、但能看到有东西在动」直接冲突。
 * 栏内文字/图标的可读性由玻璃自身的半透明 surface 层（`AppBlur.SCRIM_ALPHA`）负责，
 * 不需要系统再兜底（顶栏同理，见 `AppTopBar` KDoc）。
 *
 * ## 两种导航模式都正确
 *
 * - **三键导航（本次修复对象）**：关掉 scrim → 导航栏区域真正透明，玻璃连续铺到底，
 *   底栏内容仍按 `navigationBars` inset 内缩（`AppBottomBar` 的 GlassSurface 负责），
 *   触区不被系统导航按钮侵占。
 * - **手势导航**：系统本就不施加该 scrim，此调用是 no-op；手势条 inset（≈24dp）照旧
 *   由玻璃容器的 `windowInsetsPadding` 消费，底部预留高度随之变矮（≈80+24dp）。
 *
 * API < 29 无此开关（`isNavigationBarContrastEnforced` 为 API 29 引入），故按版本号跳过；
 * 那些版本上系统导航栏本就由厂商主题决定，App 侧不做额外处理。
 */
internal fun Window.disableNavigationBarContrastScrim() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        isNavigationBarContrastEnforced = false
    }
}
