package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/**
 * 应用统一单选分段按钮（设计系统 Batch 1，UI-2 配套，
 * `docs/design-system/implementation-plan.md` §3.3）。
 *
 * 接收者必须是 [SingleChoiceSegmentedButtonRowScope]：M3 的 [SegmentedButton] 是行作用域
 * 扩展（行负责重叠描边与 `selectableGroup` 语义），脱离行使用会丢布局与无障碍语义。
 * 因此本组件同样实现为行作用域扩展——迁移时在 `SingleChoiceSegmentedButtonRow { }` 内
 * 原地替换即可，行容器与按钮间距/形状全部不变。
 *
 * ## UI-2：选中容器色显式钉死（ADR-0010 决策 6）
 *
 * [selectedContainerColor] / [activeContentColor] 显式取
 * `secondaryContainer` / `onSecondaryContainer`。material3 1.5.0-alpha18 实测：
 * `OutlinedSegmentedButtonTokens.SelectedContainerColor` 就是 `SecondaryContainer`、
 * `SelectedLabelTextColor` 就是 `OnSecondaryContainer`，故本项对现有调用点是
 * **零视觉变化**。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * [shape] 默认 `itemShape(index = 0, count = 1)`（单按钮 = 完整圆角，即 M3 基线形状）；
 * 多按钮行由调用方按 index/count 传入（迁移点保持原参不变）。`border` 不显式传——
 * M3 的默认边框表达式从**传入的 colors** 计算（Kotlin 默认参数引用前序参数），
 * 因此覆盖选中色后边框色仍与 M3 默认一致。
 *
 * @param label 可见文案，由调用方 `stringResource` 传入（零硬编码）
 */
@Composable
fun SingleChoiceSegmentedButtonRowScope.AppSegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = SegmentedButtonDefaults.itemShape(index = 0, count = 1),
    selectedContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    activeContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = shape,
        modifier = modifier,
        enabled = enabled,
        colors =
            SegmentedButtonDefaults.colors(
                activeContainerColor = selectedContainerColor,
                activeContentColor = activeContentColor,
            ),
        label = { Text(text = label) },
    )
}
