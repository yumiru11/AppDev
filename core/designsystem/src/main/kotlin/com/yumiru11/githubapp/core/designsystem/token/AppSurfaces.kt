package com.yumiru11.githubapp.core.designsystem.token

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 应用容器色（全局背景图 / 毛玻璃的**唯一**裁决点）。
 *
 * ## 为什么需要这一层（真实缺陷根因）
 *
 * 背景图（ui-design §7.4 / #167 UI04）此前铺在主题之内、内容之外，但**内容侧每一层容器
 * 都是不透明的**：`MainTabPager` 的裸 `Scaffold` 与各屏 `AppScaffold` 默认
 * `containerColor = colorScheme.background`，一张不透明色板铺满全屏，把背景图整层盖死；
 * 毛玻璃的 `hazeSource` 采到的也是这层不透明内容 —— 「背景图不管用」与「玻璃只能糊自己」
 * 是同一个根因。修法不是再叠一层图，而是让容器色在**有背景图时半透明**。
 *
 * ## 策略（纯函数，单测锁定）
 *
 * - [backgroundImageEnabled] == false（默认）→ 全部不透明：零观感变化，与既有基线一致。
 * - OLED（纯黑面板）/ 高对比（高对比文字）→ **强制不透明**：这两项是无障碍诉求，
 *   半透明会把它们直接破坏（与 `GlassSettings.withAccessibilityOverrides` 同一原则）。
 * - 有图且非上面两项 → 屏幕/卡片/栏按 [alpha] 半透明；**BottomSheet / Dialog 恒不透明**
 *   （叠印风险最高的一层，#201 P0 的教训）。
 *
 * 用户通过设置页「背景图不透明度」控制露图程度（0 = 不露），因此默认值取保守档：
 * 观感上仍是"淡淡的壁纸"，拉高不透明度才明显。
 */
@Immutable
data class AppSurfaces(
    /** Scaffold 容器色（屏幕底）。 */
    val screen: Color,
    /** 卡片容器色（列表卡片 / 表单卡片）。 */
    val card: Color,
    /** 高一级卡片容器色（强调块 / 表单分组）。 */
    val cardHigh: Color,
    /** 顶栏/底栏玻璃底色（`GlassSurface.baseColor`）。 */
    val bar: Color,
    /** BottomSheet / Dialog 容器色（恒不透明）。 */
    val sheet: Color,
    /** 是否处于半透明（背景图生效）档位。 */
    val translucent: Boolean,
) {
    companion object {
        /** 无背景图 / 无障碍强制档：与 M3 默认逐项相同（像素等价红线）。 */
        fun opaque(colorScheme: ColorScheme): AppSurfaces =
            AppSurfaces(
                screen = colorScheme.background,
                card = colorScheme.surfaceContainerLow,
                cardHigh = colorScheme.surfaceContainerHigh,
                bar = colorScheme.surface,
                sheet = colorScheme.surfaceContainerLow,
                translucent = false,
            )
    }
}

/**
 * 半透明档的 alpha（背景图生效时）。
 *
 * 取值口径：卡片与栏要保证文字对比度（≥85% / ≥78% 底色），屏幕底最薄（55%）——
 * 背景图主要在卡片之间的缝隙与页面留白处透出，这是"有壁纸但不影响阅读"的常见做法。
 */
object AppSurfaceAlpha {
    const val SCREEN: Float = 0.55f
    const val CARD: Float = 0.85f
    const val CARD_HIGH: Float = 0.90f
    const val BAR: Float = 0.78f
}

/**
 * 解析当前容器色档位（纯函数：输入决定输出，供表驱动单测与红→绿验证）。
 *
 * @param backgroundImageEnabled 用户是否设置了全局背景图（`backgroundImageUri` 非空）
 * @param oled OLED 纯黑模式（无障碍：强制不透明）
 * @param highContrast 高对比模式（无障碍：强制不透明）
 */
fun resolveAppSurfaces(
    colorScheme: ColorScheme,
    backgroundImageEnabled: Boolean,
    oled: Boolean = false,
    highContrast: Boolean = false,
): AppSurfaces {
    if (!backgroundImageEnabled || oled || highContrast) {
        return AppSurfaces.opaque(colorScheme)
    }
    return AppSurfaces(
        screen = colorScheme.background.copy(alpha = AppSurfaceAlpha.SCREEN),
        card = colorScheme.surfaceContainerLow.copy(alpha = AppSurfaceAlpha.CARD),
        cardHigh = colorScheme.surfaceContainerHigh.copy(alpha = AppSurfaceAlpha.CARD_HIGH),
        bar = colorScheme.surface.copy(alpha = AppSurfaceAlpha.BAR),
        // sheet / dialog 恒不透明：#201 P0（降级路径下层文字同像素叠印）不得复发
        sheet = colorScheme.surfaceContainerLow,
        translucent = true,
    )
}

/**
 * 当前容器色（由 app 层 `AppThemeHost` 依背景图/OLED/高对比状态注入）。
 *
 * 消费侧只读本值：`AppScaffold` 默认容器色、`AppCard` 默认容器色、
 * `GlassSurface.baseColor` 都从这里取数，改策略只改一处。
 */
val LocalAppSurfaces = staticCompositionLocalOf { AppSurfaces.opaque(lightColorScheme()) }
