package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties

/**
 * 应用统一对话框（设计系统 Batch 1，`docs/design-system/implementation-plan.md` §3.1④）。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * 默认值逐项等于 [AlertDialogDefaults]，实现是 M3 [AlertDialog] 的直接转发；未暴露的
 * `iconContentColor` / `titleContentColor` / `textContentColor` 继续走同一默认值，
 * 因此同 props 下与裸 `AlertDialog(` 渲染逐像素一致。
 *
 * ## 逃生舱
 *
 * [shape] / [containerColor] / [tonalElevation] / [properties] 全量透传；需要内容色特例
 * 时直接用 M3 `AlertDialog`。
 *
 * ## 语义与 i18n
 *
 * 按钮、标题、正文全部是内容槽：调用方传本地化 `Text(stringResource(...))`，组件侧零硬编码文案。
 *
 * @param onDismissRequest 点击外部/返回键的关闭回调（**不含** dismissButton 的点击）
 * @param confirmButton 确认按钮（事件由调用方接）
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
