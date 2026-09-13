package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ChipColors
import androidx.compose.material3.ChipElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * 应用统一 assist chip（设计系统 Batch 1，`docs/design-system/implementation-plan.md` §3.1②：
 * 「[AppChip] 为 [AssistChip] 语义的薄包装（无 `selected`）」）。
 *
 * ## 为什么是「薄」包装
 *
 * assist 语义的全部意见已由 M3 的默认值承载（容器透明 + 描边 + labelLarge 标签），
 * 设计系统在此只统一**命名与入口**，不新增视觉决策：任何默认值改动都会破坏
 * 像素等价红线（ADR-0010 决策 3），现有唯一真实调用点（RepoDetail Topics 行）用的是
 * 自定义容器/内容色 + `labelMedium` 标签，因此 [label] 保持内容槽（`@Composable`），
 * 让调用方原样携带自己的 `Text(stringResource(...))` 与样式——组件侧零硬编码文案。
 *
 * ## 逃生舱
 *
 * [shape] / [colors] / [elevation] / [border] / [interactionSource] 全量透传。
 *
 * @param label 标签内容槽：调用方传本地化 `Text`（本组件不内嵌字符串）
 */
@Composable
fun AppChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    shape: Shape = AssistChipDefaults.shape,
    colors: ChipColors = AssistChipDefaults.assistChipColors(),
    elevation: ChipElevation? = AssistChipDefaults.assistChipElevation(),
    border: BorderStroke? = AssistChipDefaults.assistChipBorder(enabled = enabled),
    interactionSource: MutableInteractionSource? = null,
) {
    AssistChip(
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource,
    )
}
