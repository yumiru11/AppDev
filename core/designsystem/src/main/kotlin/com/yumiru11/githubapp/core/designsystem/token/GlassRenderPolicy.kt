package com.yumiru11.githubapp.core.designsystem.token

import android.os.Build

/**
 * 玻璃渲染模式判定（纯函数，JVM 可断言）。
 *
 * **为什么单独成函数**（issue #83 验收可验证性）：backdrop blur 依赖
 * RenderEffect/BlurEffect，而 Robolectric 不渲染 RenderEffect、CI 模拟器又停在
 * API 30（低于 [AppBlur.MIN_BLUR_API]）——于是「选中了哪条渲染路径」这件事在 JVM
 * 既断言不了像素、也断言不了分支，只能靠真机眼睛看（PR #92 的截图基线 diff 为空即
 * 是该缺口的体现）。把判定从 [com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
 * 里收敛出来成本函数后，四条降级路径（关开关 / 背景不可达 / API<31 / 独立 window）
 * 与生效路径全部可单测断言；真机验收卡从此只需回答「模糊看起来对不对」这一件
 * JVM 做不到的事。
 *
 * 判定顺序（docs/ui-design.md §6.1/§6.2 拍板）：模糊生效必须**同时**满足
 * ① 用户开启毛玻璃（设置页 T24 总开关 + §6.3 逐项开关）② 玻璃矩形背后确实有可采样的
 * 源内容（[backdropReachable]：上游提供了共享 HazeState 且 source 与 effect 同 window，
 * 详见 [resolveInDialogWindow]）③ 系统支持 RenderEffect（API 31+）。
 * 任一不满足 → 纯半透明 scrim，视觉与 PR #92 之前的旧 AppBlur 降级方案一致。
 */
object GlassRenderPolicy {
    /**
     * @param blurEnabled 毛玻璃开关（#167 / UI03：总开关 ∧ 该点位逐项开关，
     *   由 [GlassSettings.enabledFor] 裁决后传入）；false 强制降级
     * @param backdropReachable 玻璃矩形背后是否有**可采样的同 window 源内容**；
     *   false 有两种成因（都会让模糊每帧采样到空背景 → 真机「无效果」根因）：
     *   ① 调用方没提供
     *   [com.yumiru11.githubapp.core.designsystem.component.LocalHazeState]（内容侧没挂
     *   `hazeSource`）；② effect 与 source 分别落在两个 window（[resolveInDialogWindow]）
     * @param sdkInt 判定用 API level；默认取设备值，测试传显值以覆盖 [AppBlur.MIN_BLUR_API] 边界
     */
    fun resolve(
        blurEnabled: Boolean,
        backdropReachable: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): GlassRenderMode =
        if (blurEnabled && backdropReachable && sdkInt >= AppBlur.MIN_BLUR_API) {
            GlassRenderMode.BackdropBlur
        } else {
            GlassRenderMode.TranslucentScrim
        }

    /**
     * 弹层（`ModalBottomSheet` / Dialog）内的玻璃点位判定（issue #167 / UI22，§6.1 #4）。
     *
     * **几何结论（本函数存在的唯一理由，已机械核实）**：M3 的 `ModalBottomSheet` 是
     * **独立 window**（`ModalBottomSheetDialog` → `Dialog` → 自带 `ViewRootImpl`，
     * 非同一窗口的覆盖层）。Haze 1.6.10 在 `HazeEffectNode` 里用
     * `LocalView.current.windowId`（见 `Utils.android.kt#getWindowId`）过滤可采样区域，
     * windowId 不同的 source 会被直接剔除；且跨 window 也无共享 `GraphicsLayer` /
     * `RenderNode` 可采样（两个 window 是 SurfaceFlinger 的两层）。因此在弹层内部
     * **不存在**能真正模糊「弹层背后的 app 内容」的路径——弹层自己那层 scrim 是
     * 纯色，模糊它不产生任何视觉差异。
     *
     * 结论：弹层点位一律走**半透明 surface 降级**（[GlassRenderMode.TranslucentScrim]，
     * §6.2 API 26–30 同款路径）。开关语义仍然完整：§6.3 总开关/逐项开关/OLED/高对比的裁决
     * 结果决定该点位是否走玻璃效果，只是弹层这条路径的可见结果与栏侧一致——**同一层半透明
     * scrim、同一个 alpha 出口 [layerAlpha]**（本函数只回答「走哪条渲染路径」，「叠多厚」
     * 一律归 [layerAlpha]，全仓不再有第二条写死 alpha 的降级实现）。
     * 未来若把弹层改成「主 window 内的覆盖层」（自绘 Sheet，内容侧同 window 挂 `hazeSource`），
     * 只需改本函数的返回值并同步单测，五处调用点无需改动。
     *
     * **收敛后的角色说明**（本次 merge）：渲染路径的**生产入口**已统一到
     * [com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
     * ——薄封装 [com.yumiru11.githubapp.core.designsystem.component.GlassSheetSurface] 传
     * `backdropReachable = false`，[GlassSurface] 内部调 [resolve] 得到的
     * `TranslucentScrim` 与本函数返回值**恒等**。本函数保留为这条几何结论的
     * **规范出口与单测锚点**（`backdropReachable = false` 这个布尔量本身说不出「为什么」，
     * 「独立 window」这条事实只在这里成文；单测也直接钉它）。
     * 因此**不存在**两条实现——只有一条计算路径（[resolve]）与一处几何事实陈述（本函数）。
     * 若哪天放开弹层模糊，必须同时改这里、`GlassSheetSurface` 的 `backdropReachable`
     * 与 `GlassSheetSurfaceTest`，三者任一漏改都会被 `resolveInDialogWindow_*` 那组单测拦下。
     *
     * @param glassAllowed §6.3 在该点位的裁决结果（总开关 ∧ 逐项开关 ∧ 非 OLED/高对比，
     *   即 [GlassSettings.enabledFor] 的返回值）。参数名不叫 `blurEnabled`：弹层**不可能**真模糊，
     *   叫 blurEnabled 会让人误以为这里在做模糊开关。
     * @param sdkInt 保留参数：生效路径一旦开放，API<31 仍需降级（与 [resolve] 同源边界）
     */
    fun resolveInDialogWindow(
        glassAllowed: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): GlassRenderMode = resolve(blurEnabled = glassAllowed, backdropReachable = false, sdkInt = sdkInt)

    /**
     * 内容侧（source）门禁：是否应当给滚动内容挂 `hazeSource`。
     *
     * 与 [resolve] 同源判定（[com.yumiru11.githubapp.core.designsystem.component.GlassSurface]
     * 的 effect 侧走 resolve），避免「栏挂了 hazeEffect 而内容侧漏挂 / 反之」这种两侧漂移
     * ——那是 issue #83 真机「无效果」的成因之一。调用方是同时持有栏与内容的容器
     * （MainTabPager / HomeScreen / MainActivity），它们手里没有 HazeState 的「有没有」问题
     * （自己就是提供者），也不跨 window（都在主 window 内），故 backdropReachable 恒为 true。
     */
    fun shouldAttachHazeSource(
        blurEnabled: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = resolve(blurEnabled = blurEnabled, backdropReachable = true, sdkInt = sdkInt) == GlassRenderMode.BackdropBlur

    /**
     * 玻璃层叠色 alpha（#201 / P0 全屏面板叠印修复）。
     *
     * **为什么需要它**：全屏玻璃点位（[GlassScope.PANEL]）铺满屏幕时，遮罩整块被面板
     * 盖住，「挡住下层内容」这件事只剩面板自身这一层。此时若仍按 [AppBlur.SCRIM_ALPHA]
     * （0.75）走降级，合成结果 = 0.75×surface + 0.25×(0.5 遮罩 + 下层内容)，下层**仍有
     * 12.5% 的对比度透上来且一点没糊**（API<31 无 RenderEffect；CI 截图模拟器正是 API 30）
     * ——真机表现就是面板标题与下层顶栏文字同像素叠印（CI release
     * `screenshots-pr199-34600531043` 的 `notification-panel.png` 实测：面板滤镜行处
     * 亮度跨度 39 级，下层字形清晰可读）。
     *
     * **判定**：backdrop blur 真实生效时保持半透明玻璃——Haze 把模糊后的 backdrop
     * **不透明地**画在 effect 矩形内，透上来的只有糊掉的色块、没有可读字形（同 release
     * 的 API 31 玻璃验证帧实证：同一位置已是雾面），玻璃观感必须保留。
     * 降级路径（关开关 / 背景不可达 / API<31）不模糊 → 必须全不透明，否则就是 P0 叠印。
     *
     * **本函数是全仓唯一的不透明降级通道**（merge 后收敛结论）：薄封装
     * [com.yumiru11.githubapp.core.designsystem.component.GlassSheetSurface] 也走这里
     * （`opaqueWhenBlurUnavailable` 透传），不再有第二条写死 alpha 的降级实现——
     * 「同一语义两套事实来源」是本项目反复吃亏的模式（见 `AppTypography` 票与
     * `GlassRenderPolicy` 本身的收敛史）。
     *
     * @param renderMode [resolve] 的判定结果
     * @param opaqueWhenBlurUnavailable 调用方是否要求「降级即不透明」。只有铺满全屏、
     *   背后内容无处可露的点位（全屏通知面板）该传 true；顶栏/底栏/BottomSheet 的内容
     *   本就设计成从栏后穿过（§6.2「滚动穿越感」），必须保持半透明，故默认 false，
     *   行为与 #83 一致。
     * @return 玻璃层叠色 alpha（0..1）
     */
    fun layerAlpha(
        renderMode: GlassRenderMode,
        opaqueWhenBlurUnavailable: Boolean,
    ): Float =
        if (opaqueWhenBlurUnavailable && renderMode != GlassRenderMode.BackdropBlur) {
            OPAQUE_ALPHA
        } else {
            AppBlur.SCRIM_ALPHA
        }

    /** 全不透明（面板降级路径用；显式 1f 而非省略 alpha，保持叠色语义统一） */
    private const val OPAQUE_ALPHA = 1f
}
