package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

/**
 * 应用统一筛选 chip（设计系统 Batch 1，`docs/design-system/implementation-plan.md` §3.1②，
 * UI-2 选中态收口）。
 *
 * ## UI-2：选中容器色显式钉死（ADR-0010 决策 6）
 *
 * [selectedContainerColor] 显式取 `MaterialTheme.colorScheme.secondaryContainer`。
 * 本仓库 material3 1.5.0-alpha18 实测：裸 [FilterChip] 的选中容器 token
 * `FilterChipTokens.FlatSelectedContainerColor` **就是** `ColorSchemeKeyTokens.SecondaryContainer`
 * （source jar `Chip.kt` + `tokens/FilterChipTokens.kt`），因此本项对现有调用点是
 * **零视觉变化**——UI-2 是「把色决策收进设计系统」，不是改配色。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * 未传参时逐项复用 M3 默认：`shape = FilterChipDefaults.shape`、
 * `elevation = FilterChipDefaults.filterChipElevation()`、
 * `border = FilterChipDefaults.filterChipBorder(enabled, selected)`（**状态相关**默认值，
 * 不能拿 `null` 顶替）、`labelColor = Color.Unspecified`（`filterChipColors` 内部按
 * `takeOrElse` 保留默认）。
 *
 * ## 逃生舱
 *
 * [selectedContainerColor] / [containerColor] / [labelColor] / [border] 可整体覆盖色决策；
 * [leadingIconSize] 覆盖前置图标尺寸；[interactionSource] 供动效/预览消费。其余特例
 * （trailingIcon、contentPadding 等）仍可直接使用 M3 [FilterChip]。
 *
 * Batch 4 新增 [leadingIconSize]：迁移点里存在 16dp 勾选图标的旧写法
 * （Issue 元数据编辑 / 新建 Issue 的标签选择），M3 规格为 18dp
 * （[FilterChipDefaults.IconSize]）。保持 16dp 是本批**像素等价红线**的要求——
 * 统一到 18dp 属视觉变更，须走计划 §6.4 独立票 + 用户批准，不在本批内夹带。
 *
 * > 与 §3.1 契约的一处**编译期修正**：计划书写的是
 * > `containerColor = FilterChipDefaults.filterChipColors().containerColor`，但 alpha18 的
 * > [androidx.compose.material3.SelectableChipColors] 构造参数全部是 `private val`
 * > （Kotlin 编译探针实测：`Cannot access 'val containerColor': it is private`），取不到。
 * > 改为 `Color.Unspecified`：`filterChipColors(...)` 内部按 `takeOrElse` 语义保留 M3
 * > 默认（当前 = `Color.Transparent`），语义与计划书完全等价。
 *
 * @param selected 是否选中（可播报语义由 M3 提供）
 * @param label 可见文案，由调用方 [androidx.compose.ui.res.stringResource] 传入（零硬编码）
 * @param leadingIcon 可选前置图标（纯装饰，`contentDescription = null`）；尺寸见 [leadingIconSize]
 * @param leadingIconSize 前置图标尺寸；默认 [FilterChipDefaults.IconSize]（18dp，M3 规格），
 *   16dp 迁移点显式传入以保持像素等价
 * @param containerColor 未选中容器色；[Color.Unspecified] = 沿用 M3 默认（Transparent）
 * @param border 边框逃生舱；null = 按 [enabled]/[selected] 取 M3 默认边框
 */
@Composable
fun AppFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    leadingIconSize: Dp = FilterChipDefaults.IconSize,
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    containerColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified,
    border: BorderStroke? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label) },
        modifier = modifier,
        enabled = enabled,
        leadingIcon =
            leadingIcon?.let { icon ->
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(leadingIconSize),
                    )
                }
            },
        shape = FilterChipDefaults.shape,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = containerColor,
                labelColor = labelColor,
                selectedContainerColor = selectedContainerColor,
            ),
        elevation = FilterChipDefaults.filterChipElevation(),
        border = border ?: FilterChipDefaults.filterChipBorder(enabled = enabled, selected = selected),
        interactionSource = interactionSource,
    )
}
