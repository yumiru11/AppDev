package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yumiru11.githubapp.core.designsystem.token.AppBlur
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderMode
import com.yumiru11.githubapp.core.designsystem.token.GlassRenderPolicy
import com.yumiru11.githubapp.core.designsystem.token.GlassScope
import com.yumiru11.githubapp.core.designsystem.token.LocalGlassSettings

/**
 * 弹层（`ModalBottomSheet`）内的玻璃容器 —— docs/ui-design.md §6.1 第 4 项
 * 「BottomSheet 背景毛玻璃（默认开）」（issue #167 / UI22）。
 *
 * **与 [GlassSurface] 的分工**：本组件是弹层专用点位，二者不是替代关系。
 * 弹层是**独立 window**（M3 `ModalBottomSheet` → `Dialog` → 自带 `ViewRootImpl`），
 * 而 backdrop blur 只能采样**同 window** 的 `hazeSource`（Haze 1.6.10 在
 * `HazeEffectNode` 内按 `LocalView.current.windowId` 过滤采样区域），跨 window 也没有
 * 可共享的 `GraphicsLayer`/`RenderNode`。因此弹层里**不存在**能真正模糊「弹层背后 app
 * 内容」的路径——挂 `hazeEffect` 只会每帧采样到同 window 内的纯色 dialog scrim
 * （模糊纯色 = 零视觉差异），白付一次离屏 RenderNode 的代价。几何论证见
 * [GlassRenderPolicy.resolveInDialogWindow] 的 KDoc。
 *
 * 于是本组件走 §6.2 既有的**半透明 surface 降级路径**（与其它点位同一套 token、
 * 同一个判定函数），不另造一套：
 * - 开关开启 → 半透明玻璃层（`surfaceContainerLow` @[AppBlur.SCRIM_ALPHA]）：弹层背后的
 *   dialog scrim 与页面内容依稀可辨（雾面观感），正文对比度由 scrim 保证
 * - 关闭（总开关 / [GlassScope.BOTTOM_SHEET] 逐项开关）/ OLED 纯黑 / 高对比主题
 *   → 与栏侧（[GlassSurface]）完全一致：**照旧叠同一层半透明 scrim**（不把 alpha 抬到
 *   不透明）。「关闭」= 不叠玻璃的**效果**（模糊/背景图），不是把容器换一个颜色，
 *   OLED/高对比下也没有额外的模糊可禁。主题项已由 [LocalGlassSettings] 合并进总开关
 *   （`GlassSettings.withAccessibilityOverrides`），调用方无需重复判断 OLED/高对比。
 *
 * 颜色一律取自 `MaterialTheme.colorScheme.*`（底 = `surfaceContainerLow`，与 M3
 * `BottomSheetDefaults.ContainerColor` 同值），禁止硬编码颜色。
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
 * `Modifier.weight` / `Modifier.align` 语义搬进来后保持不变。
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
    // 采样基色与 M3 弹层默认 containerColor（BottomSheetDefaults.ContainerColor =
    // surfaceContainerLow）保持一致：这样「玻璃层的半透明色」与「关闭时的弹层本色」同源，
    // 开关切换只是同一底色的透明度变化，不会换色。不写 `BottomSheetDefaults.ContainerColor`
    // 是为避开 material3 的实验性 API（本组件只需同值的 colorScheme 角色）。
    val sheetBaseColor = MaterialTheme.colorScheme.surfaceContainerLow
    // 「叠不叠、叠多厚」由纯函数判定（组件内不写几何/开关分支）：弹层几何 → 恒为
    // TranslucentScrim；总开关/逐项开关/OLED/高对比各组合由 GlassRenderPolicyTest 与
    // GlassSheetSurfaceTest 断言。
    val glassAlpha = GlassRenderPolicy.sheetGlassAlpha(glassAllowed = blurEnabled)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // alpha = 1f 表示不叠层（留给未来真能模糊时 tint 由 HazeStyle 负责的分工）
                .then(
                    if (glassAlpha < 1f) {
                        Modifier.background(color = sheetBaseColor.copy(alpha = glassAlpha))
                    } else {
                        Modifier
                    },
                ),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}
