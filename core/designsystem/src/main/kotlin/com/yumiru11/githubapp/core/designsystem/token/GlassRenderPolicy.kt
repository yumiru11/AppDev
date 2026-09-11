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
     * 结果决定该点位是否走玻璃效果，只是弹层这条路径的可见结果与栏侧一致（同一层半透明 scrim，
     * 详见 [sheetGlassAlpha]）。未来若把弹层改成「主 window 内的覆盖层」（自绘 Sheet，
     * 内容侧同 window 挂 `hazeSource`），只需改本函数的返回值并同步单测，五处调用点无需改动。
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
     * 弹层玻璃层的 alpha（[com.yumiru11.githubapp.core.designsystem.component.GlassSheetSurface]
     * 实际消费）。收敛成纯函数的理由与 [resolve] 同源：可见差异只有「叠不叠、叠多厚」这一条，
     * 断言本函数比断言 Robolectric 截图像素更精确，也不依赖渲染语义。
     *
     * **与 [com.yumiru11.githubapp.core.designsystem.component.GlassSurface] 同一条降级路径**：
     * 半透明 scrim（[AppBlur.SCRIM_ALPHA]）。开关关掉时**不**把 alpha 抬到 1f——栏侧
     * （[GlassSurface]）关掉开关照样叠同一层 scrim（否则栏内文字失去可读性），弹层沿用同款
     * 数值才能保证「同一个玻璃点位关掉后视觉一致」；`1f` 分支留给未来弹层真能模糊时的
     * tint 分工（那时 tint 由 `HazeStyle.backgroundColor` 负责，scrim 不再叠）。
     *
     * 采样基色由调用方给（弹层用 `BottomSheetDefaults.ContainerColor`
     * = `surfaceContainerLow`），本函数只管 alpha，不掺颜色。
     *
     * @param glassAllowed §6.3 在 BOTTOM_SHEET 点位的裁决结果（总开关 ∧ 逐项开关 ∧ 非 OLED/高对比，
     *   即 [GlassSettings.enabledFor]；[GlassSettings.withAccessibilityOverrides] 已把
     *   OLED/高对比合并进总开关，所以这三条降级共用本函数一条出口）
     * @see resolveInDialogWindow
     */
    fun sheetGlassAlpha(
        glassAllowed: Boolean,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Float {
        val mode = resolveInDialogWindow(glassAllowed = glassAllowed, sdkInt = sdkInt)
        // 有效果层时 tint 由 HazeStyle.backgroundColor 负责，此处不叠；降级路径（含弹层恒定路径）叠 scrim
        return if (mode == GlassRenderMode.BackdropBlur) 1f else AppBlur.SCRIM_ALPHA
    }

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
}
