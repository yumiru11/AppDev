package com.yumiru11.githubapp.core.designsystem.token

/**
 * 毛玻璃点位的**作用域标识**（ui-design §6.1 允许清单，issue #167 / UI03）。
 *
 * §6.3 拍板「全局总开关 + 设置页逐项开关」：四条允许点位各自可关。作用域不是"位置"
 * 而是"消费方的身份"——同一个 [com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
 * 组件被不同点位复用时，由调用方声明自己是谁，开关由 [GlassSettings] 统一裁决。
 *
 * 清单**封闭**（§6.1 用户逐项拍板，8 项里只有 4 项开玻璃）：
 * - [TOP_BAR] 顶栏（含首页小分区条副行，见 §6.1 #83 补充）
 * - [BOTTOM_BAR] 底部导航栏
 * - [PANEL] 通知面板
 * - [BOTTOM_SHEET] 各类 BottomSheet
 *
 * 不在清单里的（图片查看器纯黑 / FAB / 设置分组头 / 卡片本身）不得新增枚举项；
 * 要加点位必须先改 ui-design §6.1 并给出性能论证（§6.2 约束）。
 */
enum class GlassScope {
    /** 顶栏（AppTopBar，含首页小分区条副行） */
    TOP_BAR,

    /** 底部导航栏（AppBottomBar） */
    BOTTOM_BAR,

    /** 通知面板（NotificationsPanel） */
    PANEL,

    /** BottomSheet（评论输入 / 行评论 / Review / 仓库选择 …） */
    BOTTOM_SHEET,
}
