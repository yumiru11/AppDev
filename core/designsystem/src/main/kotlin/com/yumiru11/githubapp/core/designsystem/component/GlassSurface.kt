package com.yumiru11.githubapp.core.designsystem.component

import android.os.Build
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderMode
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * 玻璃拟真容器（docs/ui-design.md §6；issue #83 改为 backdrop blur）。
 *
 * 顶栏 / 底栏等 §6.1 允许清单场景的毛玻璃背景容器：
 * - **API 31+** 且上游提供了 [LocalHazeState]（模式由 [GlassRenderPolicy] 判定）：
 *   挂 Haze `hazeEffect`（RenderEffect backdrop blur），模糊 **背后滚动内容**
 *   （内容侧须挂 `hazeSource`，由同时持有栏与内容的容器组件接线——MainTabPager /
 *   HomeScreen）。玻璃层自身与栏内文字/图标保持锐利（修复 ui-audit 缺陷 #1：
 *   旧实现 `Modifier.blur` 误模糊自身绘制内容，即 FEEDBACK #17 根因）。
 *   半透明 scrim 叠在模糊层之上保证可读性（§6.1 静止态）。
 * - **API 26–30 / 无 HazeState / [blurEnabled]=false**：纯半透明 surface 层降级
 *   （[AppBlur.SCRIM_ALPHA] alpha），不做 bitmap 模糊，性能优先（§6.2）；
 *   API<31 半透明降级策略与旧 AppBlur 方案一致。
 *   例外：全屏点位传 [opaqueWhenBlurUnavailable]=true 时降级层取全不透明 surface
 *   （#201 P0 叠印修复，见该参数说明）。
 *
 * **窗口约束**：`hazeEffect` 只能采样**同一 window** 内的 `hazeSource`（Haze 按
 * `LocalView.current.windowId` 过滤采样区域）。在独立 window 弹层（`Dialog` /
 * `ModalBottomSheet`）内应传 [backdropReachable]` = false`（[GlassSheetSurface] 已封装），
 * 而不是另写一个平行组件——弹层几何结论与理由见 `GlassRenderPolicy.resolveInDialogWindow`。
 * 反过来也成立：不在独立 window 里就**不要**传 false，否则白丢一次真实模糊的能力。
 *
 * 玻璃层颜色一律取自 `MaterialTheme.colorScheme.*`，禁止硬编码颜色。基色默认
 * `colorScheme.surface`（栏侧 / 面板），[baseColor] 可覆盖成该点位容器的本色角色——
 * 唯一用它的是 [GlassSheetSurface]（M3 `ModalBottomSheet` 的
 * `BottomSheetDefaults.ContainerColor` = `surfaceContainerLow`），使「玻璃层的半透明色」
 * 与「关掉玻璃时的弹层本色」同源：开关切换只是同一底色的透明度变化，不会换色。
 *
 * 性能约束（§6.2，调用方责任）：
 * - 只用于 §6.1 允许清单（顶栏 / 底栏 / BottomSheet / 通知面板 / 全屏查看器 /
 *   Banner 悬浮层）
 * - **禁止**列表 item 内使用；**禁止**叠加超过 2 层毛玻璃；**禁止**动态模糊
 *
 * 接线方式：
 * - 玻璃栏：本组件包住 `TopAppBar` / `NavigationBar`，后者 `containerColor` 设为
 *   `Color.Transparent`、自身 insets 归零；系统栏 insets 由 [windowInsets] 统一处理
 *   （玻璃延伸进状态栏/导航栏区域，内容按 insets 内缩）
 * - 内容侧：容器组件对「栏背后的滚动内容」挂 `Modifier.hazeSource(hazeState)`，
 *   并用 `CompositionLocalProvider(LocalHazeState provides rememberHazeState())`
 *   覆盖栏与内容的公共父级（见 MainTabPager / HomeScreen 示例）
 * - [blurEnabled] 由 `LocalGlassSettings` 按 [scope] 下发（#167 / UI03），调用方
 *   不需要自己收集偏好；显式传值只用于测试/预览
 *
 * @param modifier 应用在玻璃容器上的修饰符（尺寸 / 对齐等）
 * @param shape 玻璃容器形状；圆角时内容同样被裁剪到该形状
 * @param windowInsets 内容需要避让的系统 insets（如状态栏 / 导航栏）。
 *   玻璃背景延伸进 insets 区域（玻璃盖住状态栏），内容按 insets 内缩。
 *   默认无 insets。
 * @param scope 本点位身份（ui-design §6.1 四条允许点位，issue #167 / UI03）。
 *   开关由 [LocalGlassSettings] 按 scope 裁决，调用方不需要自己拼布尔。
 * @param blurEnabled 覆盖用开关；默认取 [LocalGlassSettings] 对 [scope] 的裁决结果。
 *   显式传值只用于测试/预览（例如截图基准要固定走降级路径）。
 * @param opaqueWhenBlurUnavailable 降级路径（关开关 / 背景不可达 / API<31）是否改用
 *   **全不透明** surface 底。默认 false = #83 既有半透明降级行为，顶栏 / 底栏 /
 *   BottomSheet 必须保持 false：它们的内容本就设计成从栏后穿过（§6.2「滚动穿越感」）。
 *   只有**铺满全屏**的点位（全屏通知面板）该传 true —— 那时遮罩被面板整块盖住，
 *   面板是唯一挡住下层内容的层，半透明降级会让下层文字以 12.5% 浓度**不模糊**地透
 *   上来，与面板自身文字同像素叠印（#201 P0：文字不可读）。判定见
 *   [GlassRenderPolicy.layerAlpha]。
 * @param backdropReachable 调用方声明「玻璃矩形背后确实有可采样的**同 window** 源内容」。
 *   默认 true（栏侧：内容与栏都在主 window，且有 HazeState 就能采样）。**独立 window
 *   弹层必须传 false**（[GlassSheetSurface] 已这么做）：M3 `ModalBottomSheet` 自带
 *   `ViewRootImpl`，Haze 按 `windowId` 过滤采样区域，弹层内挂 `hazeEffect` 只会每帧
 *   模糊同 window 内的纯色 dialog scrim —— 零视觉差异、白付一次离屏 RenderNode。
 *   传 false 后渲染模式经 [GlassRenderPolicy.resolve] 落到 `TranslucentScrim`，
 *   与栏侧同一条降级路径 / 同一个 alpha 出口（[GlassRenderPolicy.layerAlpha]）。
 *   注意 [blurEnabled] 仍如实记录用户裁决结果：日志里 `mode=TranslucentScrim
 *   backdropReachable=false` 表示「用户开着玻璃，但这个点位几何上糊不了」，
 *   不会伪装成「用户关掉了玻璃」。
 * @param baseColor 玻璃层基色（须为 `colorScheme` 角色，禁止硬编码）；默认 `surface`。
 *   会按 [GlassRenderPolicy.layerAlpha] 的 alpha 叠上去，开关切换只变透明度、不换色。
 * @param content 玻璃层之上的内容
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    windowInsets: WindowInsets = WindowInsets(0.dp),
    scope: GlassScope = GlassScope.TOP_BAR,
    blurEnabled: Boolean = LocalGlassSettings.current.enabledFor(scope),
    opaqueWhenBlurUnavailable: Boolean = false,
    backdropReachable: Boolean = true,
    baseColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalHazeState.current
    // 「玻璃矩形背后有可采样的同 window 源内容」的两个必要条件：上游确实提供了共享
    // HazeState，且调用方声明自己不在独立 window 里（[backdropReachable]，见该参数说明）。
    val backdropSampled = backdropReachable && hazeState != null
    // 渲染模式判定收敛到 GlassRenderPolicy（issue #83）：降级路径（关开关 / 背景不可达 /
    // API<31 / 独立 window）改由纯函数判定并已由单测断言——本组件的分支在
    // Robolectric 下不可像素断言（不渲染 RenderEffect），此前只能靠真机看。
    val renderMode = GlassRenderPolicy.resolve(blurEnabled = blurEnabled, backdropReachable = backdropSampled)
    val useHaze = renderMode == GlassRenderMode.BackdropBlur
    // 半透明纯色层：模糊层之上叠加，保证内容可读性（§6.1 静止态 / §6.2 降级层）；
    // 全屏点位在降级路径取全不透明（#201 P0 叠印修复，判定收敛在 GlassRenderPolicy）
    val glassColor =
        baseColor.copy(
            alpha = GlassRenderPolicy.layerAlpha(renderMode = renderMode, opaqueWhenBlurUnavailable = opaqueWhenBlurUnavailable),
        )

    // 渲染路径留档（glass-verify.yml 的机器判据）。
    // 为什么需要：Robolectric 不渲染 RenderEffect，API 30 的模拟器又低于 MIN_BLUR_API ——
    // 「这块玻璃到底走了模糊、还是静默降级成半透明」此前只能靠肉眼看截图猜。
    // 打一行日志后 CI 可以直接断言 mode=BackdropBlur，而不是"看起来差不多"。
    // #201 起同时留档叠色 alpha：全屏面板的降级路径必须是 1.0（不透明），否则叠印复现。
    // remember 保证只在路径真正变化时打一次，不随每帧重组刷屏。
    remember(scope, renderMode, blurEnabled, opaqueWhenBlurUnavailable, backdropReachable) {
        runCatching {
            Log.d(
                GLASS_LOG_TAG,
                "scope=$scope mode=$renderMode blurEnabled=$blurEnabled " +
                    "alpha=${glassColor.alpha} opaqueFallback=$opaqueWhenBlurUnavailable " +
                    "backdropReachable=$backdropSampled hasHazeState=${hazeState != null} " +
                    "sdk=${Build.VERSION.SDK_INT}",
            )
        }
        renderMode
    }

    Box(
        modifier =
            modifier
                .then(
                    if (useHaze) {
                        Modifier.hazeEffect(state = hazeState) {
                            style =
                                HazeStyle(
                                    backgroundColor = baseColor,
                                    // 无 tint：scrim 由下方 background(glassColor) 统一负责，
                                    // 保证模糊/降级两条路径视觉一致
                                    tints = emptyList(),
                                    blurRadius = AppBlur.blurRadius,
                                )
                        }
                    } else {
                        Modifier
                    },
                ).background(color = glassColor, shape = shape)
                .clip(shape),
    ) {
        Box(modifier = Modifier.windowInsetsPadding(windowInsets)) {
            content()
        }
    }
}

/**
 * 玻璃渲染路径日志 tag（glass-verify.yml 用 adb logcat -s GlassRender 消费）。
 *
 * 只在 [GlassSurface] 里打点，不打在 [GlassRenderPolicy] 里 —— 后者是纯函数，
 * 加了 Android 依赖就没法在纯 JVM 单测里断言了。
 */
private const val GLASS_LOG_TAG = "GlassRender"
