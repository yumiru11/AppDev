package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 连接式按钮组的形状：外缘保持胶囊，相邻边收紧。
 *
 * 纯函数（可单测）：size <= 1 恒胶囊；首尾只圆外侧；中段四角取 inner。
 */
fun appConnectedShape(
    index: Int,
    size: Int,
    height: Dp,
    inner: Dp = AppDimens.cornerSmall,
): Shape {
    val outer = height / 2
    return when {
        size <= 1 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = inner, bottomStart = outer)
        index == size - 1 -> RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = outer, bottomStart = inner)
        else -> RoundedCornerShape(inner)
    }
}

/** 连接式按钮组的一个动作。 */
data class AppConnectedAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * 连接式按钮组（Expressive 语言）：按钮之间留 [AppDimens.groupSeam] 细缝，
 * 相邻角收紧、外缘胶囊；selected 项用主题色实心表达。
 *
 * 与 [AppSegmentedButton] 的分工：本组件是动作组（点一下就执行），
 * 分段按钮是单选控件（表达互斥选择，带 radio 语义）；无障碍语义不同，不要互相替代。
 */
@Composable
fun AppConnectedButtonRow(
    actions: List<AppConnectedAction>,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.buttonHeightCompact,
    fillWidth: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.groupSeam),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            AppConnectedItem(
                action = action,
                shape = appConnectedShape(index, actions.size, height),
                height = height,
                fillWidth = fillWidth,
            )
        }
    }
}

@Composable
private fun RowScope.AppConnectedItem(
    action: AppConnectedAction,
    shape: Shape,
    height: Dp,
    fillWidth: Boolean,
) {
    val container =
        when {
            action.selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val content =
        when {
            action.selected -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier =
            Modifier
                .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                .height(height)
                .clip(shape)
                .clickable(enabled = action.enabled, onClick = action.onClick),
        shape = shape,
        color = container,
        contentColor = content,
    ) {
        Row(
            // 非 fillWidth 时必须按内容收窄：否则每个条目都吃满行宽、相邻条目互相叠压，
            // 点击会落到错误的条目上（AppExpressiveComponentsTest 锁定了这条几何契约）。
            modifier =
                Modifier
                    .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = AppDimens.spacing.l),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (action.icon != null) {
                Icon(imageVector = action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppDimens.spacing.s))
            }
            Text(text = action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
