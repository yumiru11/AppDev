package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 应用统一**胶囊下拉选择器**（设置页「少量互斥选项」的尾部控件）。
 *
 * ## 形态
 *
 * 尾部一颗胶囊（`percent = 50` 全圆角）内放「当前值 + 展开箭头」，点击弹出菜单；
 * 容器色取 `surfaceContainerLow`——比分组卡（`surfaceContainerHighest`）**暗一档**，
 * 形成「卡片浮在页面上、胶囊内凹在卡片里」的两级色调关系；层级只用色调差表达，
 * 不叠阴影、也不用 `tonalElevation`。
 *
 * ## 为什么不用 `ExposedDropdownMenu` 之外的自绘下拉
 *
 * [ExposedDropdownMenuBox] + [androidx.compose.material3.ExposedDropdownMenu] 是 M3 官方
 * 下拉组合，自带展开态语义与键盘行为，优先用它。**已知风险**（上游 issue，故此处留档）：
 * `ExposedDropdownMenuPositionProvider` 在「菜单高度 > 可见窗口高度 - 96dp」时会
 * `coerceIn(min > max)` 抛异常，小屏 + 输入法场景可能触发。本组件是非编辑型锚点
 * （`PrimaryNotEditable`，不弹输入法），触发面很小；若日后出现在小屏上崩溃的报告，
 * 降级路径是「`Box` + 普通 `DropdownMenu` + `onGloballyPositioned` 取锚点宽度」，
 * 对外 API 不变。
 *
 * @param optionLabel 选项 → 展示文本（由调用方 `stringResource` 传入，组件内零硬编码文案）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppSelect(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = AppDimens.spacing.m,
                        vertical = AppDimens.spacing.s,
                    ),
                horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionLabel(selectedOption),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AppIcon(
                    spec = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = optionLabel(option),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
