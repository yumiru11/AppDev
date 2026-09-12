package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings

/**
 * 弹层（`ModalBottomSheet`）内的玻璃容器 —— docs/ui-design.md §6.1 第 4 项
 * 「BottomSheet 背景毛玻璃（默认开）」（issue #167 / UI22）。
 *
 * ## 它是独立组件，但**不是**独立实现：本组件 = [GlassSurface] 的薄封装
 *
 * 唯一渲染实现仍是 [GlassSurface]；本组件存在的理由是把这个点位的一条**几何事实**
 * 固化成类型签名，而不是多养一套渲染逻辑：
 *
 * - `ModalBottomSheet` 是**独立 window**（M3 `ModalBottomSheetDialog` → `Dialog` → 自带
 *   `ViewRootImpl`），backdrop blur 只能采样**同 window** 的 `hazeSource`（Haze 1.6.10 在
 *   `HazeEffectNode` 内按 `LocalView.current.windowId` 过滤采样区域；跨 window 也没有可共享的
 *   `GraphicsLayer`/`RenderNode`）。故弹层内挂 `hazeEffect` 只会每帧采样同 window 内的
 *   纯色 dialog scrim ——**模糊纯色 = 零视觉差异**，白付一次离屏 RenderNode 的代价。
 *   几何论证见 [GlassRenderPolicy.resolveInDialogWindow]；窗口隔离由
 *   `GlassSheetSurfaceTest.modalBottomSheet_contentWindowDiffersFromMainWindow` 用真实
 *   `ModalBottomSheet` 机械核实（不是靠读源码推断）。
 * - 该事实是**点位的固有属性，不是调用方的自由选择**：五处 `ModalBottomSheet` 若各自手写
 *   [GlassSurface]，都得重复 `scope = GlassScope.BOTTOM_SHEET` +
 *   `backdropReachable = false` + `blurEnabled = …` 三件套，漏一个就退化成「声明了模糊
 *   其实没挂」或「弹层不跟开关走」。封成组件后调用方只需说「我要弹层玻璃底」，
 *   几何与开关接线在**一处**且不可能写错。
 * - 顺带统一弹层基色：M3 `BottomSheetDefaults.ContainerColor` 的角色是
 *   `surfaceContainerLow`，而 [GlassSurface] 默认 `surface`。基色经 [GlassSurface] 的
 *   `baseColor` 传入，保证「玻璃层的半透明色」与「关掉玻璃时的弹层本色」**同源** ——
 *   开关切换只是同一底色的透明度变化，不换色。
 *
 * **降级路径与栏侧完全同源**（本次 merge 的收敛结论，消灭「同一语义两套事实来源」）：
 * 渲染模式：本组件传 `backdropReachable = false` 给 [GlassSurface]，后者照常调
 * [GlassRenderPolicy.resolve]（即 [GlassRenderPolicy.resolveInDialogWindow] 的等价入口）
 * → 恒为 `TranslucentScrim`；叠色 alpha 走 [GlassRenderPolicy.layerAlpha] —— 与通知面板 /
 * 顶栏 / 底栏**同一个**不透明降级出口。本组件内**不存在**任何自己判定 alpha、颜色或
 * 渲染分支的代码。
 *
 * 此前 UI22 曾自有一条 `GlassRenderPolicy.sheetGlassAlpha` 通道，与 #219 合入的
 * `layerAlpha` 构成两条并行降级实现（同一个「降级该叠多厚」有两个事实来源）——
 * 这正是本项目反复吃亏的模式（参见 `AppTypography` 票与 [GlassRenderPolicy] 自身的
 * 收敛史）。该函数已随本次收敛删除：判断「叠多厚」的地方全仓只剩 [GlassRenderPolicy.layerAlpha]。
 *
 * 于是弹层走的是 §6.2 既有的**半透明 surface 降级路径**（与其它点位同一套 token、
 * 同一个判定函数）：
 * - 开关开启 → 半透明玻璃层（`surfaceContainerLow` @ [AppBlur.SCRIM_ALPHA]）：弹层背后的
 *   dialog scrim 与页面内容依稀可辨（雾面观感），正文对比度由 scrim 保证
 * - 关闭（总开关 / [GlassScope.BOTTOM_SHEET] 逐项开关）/ OLED 纯黑 / 高对比主题
 *   → 与栏侧（[GlassSurface]）完全一致：**照旧叠同一层半透明 scrim**（不把 alpha 抬到
 *   不透明）。「关闭」= 不叠玻璃的**效果**（模糊/背景图），不是把容器换一个颜色，
 *   OLED/高对比下也没有额外的模糊可禁。主题项已由 [LocalGlassSettings] 合并进总开关
 *   （`GlassSettings.withAccessibilityOverrides`），调用方无需重复判断 OLED/高对比。
 * - 若哪天赋层真需要一个「降级即不透明」的变体，正确做法是给本组件加一个
 *   `opaqueWhenBlurUnavailable` 并透传给 [GlassSurface]（走 [GlassRenderPolicy.layerAlpha]），
 *   **不是**在这里新写一个 alpha 分支。
 *
 * 颜色一律取自 `MaterialTheme.colorScheme.*`，禁止硬编码颜色。
 *
 * **接线方式**（五处 `ModalBottomSheet` 统一形态）：
 * ```
 * ModalBottomSheet(onDismissRequest = …, sheetState = …, modifier = modifier) {
 *     GlassSheetSurface {              // 弹层玻璃底（全宽；圆角由 Sheet 自身 shape 裁剪）
 *         Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { …原内容… }
 *     }
 * }
 * ```
 * 内容再包一层 `Column` 而非直接铺：本组件 content 是 [ColumnScope]、
 * `ModalBottomSheet` 的 content 也是 [ColumnScope]，同为纵向作用域，原内容的
 * `Modifier.weight` / `Modifier.align` 语义搬进来后保持不变。这层 `Column` 由本组件提供
 * ——[GlassSurface] 的 content 是 `BoxScope`，弹层调用点不该被迫改成 `BoxScope`。
 *
 * @param modifier 应用在玻璃容器上的修饰符；默认全宽
 * @param blurEnabled 「该点位是否用玻璃」的覆盖开关（命名与 [GlassSurface] 对齐，语义同为
 *   §6.3 的裁决结果）；默认取 [LocalGlassSettings] 对 [GlassScope.BOTTOM_SHEET] 的裁决
 *   （总开关 ∧ 逐项开关 ∧ 非 OLED/高对比）。
 *   显式传值只用于测试/预览（例如截图基准要固定走降级路径）
 * @param content 玻璃层之上的弹层内容（纵向作用域）
 */
@Composable
fun GlassSheetSurface(
    modifier: Modifier = Modifier,
    blurEnabled: Boolean = LocalGlassSettings.current.enabledFor(GlassScope.BOTTOM_SHEET),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        scope = GlassScope.BOTTOM_SHEET,
        blurEnabled = blurEnabled,
        // 弹层恒定降级：独立 window 内不存在可采样的 backdrop（几何事实，非开关选择）。
        // 开关裁决仍如实交给 blurEnabled，故 glass-verify 日志可区分「用户关掉了玻璃」与
        // 「用户开着但该点位几何上糊不了」（backdropReachable=false）。
        backdropReachable = false,
        // 与 M3 BottomSheetDefaults.ContainerColor 同角色（不直接写 BottomSheetDefaults
        // 本身，是为避开 material3 实验性 API；本组件只需同值的 colorScheme 角色）
        baseColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        // 弹层内容作用域是 Column（同 ModalBottomSheet 的 content scope），语义不变
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}
