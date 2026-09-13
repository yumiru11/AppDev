package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 应用统一模态底部弹层（设计系统 Batch 4，`docs/design-system/implementation-plan.md` §3.1⑤）。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * 默认值逐项等于 M3 [ModalBottomSheet]（`sheetMaxWidth` = [BottomSheetDefaults.SheetMaxWidth]、
 * `sheetGesturesEnabled` = true、`shape` = [BottomSheetDefaults.ExpandedShape]、
 * `containerColor` = [BottomSheetDefaults.ContainerColor]、`contentColor` =
 * `contentColorFor(containerColor)`、`tonalElevation` = 0.dp、`scrimColor` =
 * [BottomSheetDefaults.ScrimColor]、`dragHandle` = [BottomSheetDefaults.DragHandle]、
 * `contentWindowInsets` = `BottomSheetDefaults.modalWindowInsets`、`properties` =
 * `ModalBottomSheetProperties()`），实现是 M3 [ModalBottomSheet] 的**逐参数直接转发**。
 * 因此同 props 下与裸 `ModalBottomSheet(` 渲染逐像素一致——迁移批不产生任何视觉变化。
 *
 * ## 逃生舱
 *
 * 全量透传 M3 的参数（含 [sheetMaxWidth] / [sheetGesturesEnabled] / [tonalElevation] /
 * [contentWindowInsets] / [properties]）。
 *
 * ⚠️ **opt-in 传播（实测）**：[sheetState] 与 [properties] 是 `@ExperimentalMaterial3Api`
 * 类型；Kotlin 的实验标记会沿签名传播，因此调用点仍需自己的 `@OptIn(ExperimentalMaterial3Api::class)`
 * （现有 6 个迁移点本来就要 `rememberModalBottomSheetState()`，均已具备）。本组件的 `@OptIn`
 * 只覆盖自身实现，不替调用方卸掉 M3 的实验性契约。
 *
 * ## 与 [GlassSheetSurface] 的关系（对计划 §3.1⑤ 一处事实修正）
 *
 * 计划书写「优先让 [GlassSheetSurface] 内部改调 [AppBottomSheet]」，但该句的前提与代码事实
 * 不符：[GlassSheetSurface] **不是** `ModalBottomSheet` 的包装，而是弹层**内容区**的玻璃底
 * （`ModalBottomSheet { GlassSheetSurface { … } }`，见其 KDoc）。让内容表面去调弹层组件会变成
 * 「Sheet 套 Sheet」。因此本批的接线是**调用点原地替换**：
 * `ModalBottomSheet(…) { GlassSheetSurface { … } }` → `AppBottomSheet(…) { GlassSheetSurface { … } }`
 * ——玻璃变体作为逃逸舱原样保留，两者的层级与渲染均不变。
 *
 * @param onDismissRequest 点击 scrim / 返回键的关闭回调
 * @param sheetState 弹层状态；默认 `rememberModalBottomSheetState()`（M3 同名默认）
 * @param dragHandle 顶部把手槽；null = 不渲染把手（M3 同语义）
 * @param content 弹层内容；作用域与 M3 一致（[ColumnScope]）
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    sheetGesturesEnabled: Boolean = true,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        sheetGesturesEnabled = sheetGesturesEnabled,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = content,
    )
}
