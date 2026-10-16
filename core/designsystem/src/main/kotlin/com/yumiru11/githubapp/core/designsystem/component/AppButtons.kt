package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 应用标准按钮（M3 Expressive 胶囊，基准 = GitHubM3E \`GhButton\`）。
 *
 * - 默认 56dp 高、全胶囊圆角（\`height / 2\`），跟着 [AppDimens.buttonHeight] 走；
 *   行内/紧凑场景传 [AppDimens.buttonHeightCompact]（48dp）。
 * - 四档样式与 M3 语义一一对应，颜色全部取 \`colorScheme\`（零硬编码颜色）。
 * - [icon] 纯装饰：文案由 [text] 承载，图标不重复播报（contentDescription = null）。
 */
enum class AppButtonStyle { Filled, Tonal, Outlined, Text }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = AppDimens.buttonHeight,
    fillWidth: Boolean = false,
    shape: Shape? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
) {
    val resolvedShape = shape ?: RoundedCornerShape(height / 2)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(AppDimens.spacing.s))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
    val sized = modifier.height(height).then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    val contentPadding = PaddingValues(horizontal = AppDimens.spacing.xl)
    when (style) {
        AppButtonStyle.Filled -> {
            Button(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = resolvedShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = containerColor ?: MaterialTheme.colorScheme.primary,
                        contentColor = contentColor ?: MaterialTheme.colorScheme.onPrimary,
                    ),
                contentPadding = contentPadding,
                content = content,
            )
        }

        AppButtonStyle.Tonal -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = resolvedShape,
                colors =
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = containerColor ?: MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                contentPadding = contentPadding,
                content = content,
            )
        }

        AppButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = resolvedShape,
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = contentColor ?: MaterialTheme.colorScheme.primary,
                    ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = contentPadding,
                content = content,
            )
        }

        AppButtonStyle.Text -> {
            TextButton(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = resolvedShape,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = contentColor ?: MaterialTheme.colorScheme.primary,
                    ),
                contentPadding = PaddingValues(horizontal = AppDimens.spacing.l),
                content = content,
            )
        }
    }
}

/** 全宽按钮（表单提交 / 空态行动）；默认描边档，符合 M3E 的次要行动口径。 */
@Composable
fun AppWideButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Outlined,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = AppDimens.buttonHeight,
) {
    AppButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        style = style,
        icon = icon,
        enabled = enabled,
        height = height,
        fillWidth = true,
    )
}

/**
 * 应用图标按钮：48dp 触区（M3 无障碍基线），可选柔和底 [tonal] / 实心底 [filled]。
 *
 * 无障碍：图标语义由 [contentDescription] 承载；纯装饰（旁边已有文字）传 \`null\`。
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true,
    size: Dp = AppDimens.minTouchTarget,
    iconSize: Dp = AppIconDefaultSize,
    tint: Color? = null,
) {
    val container =
        when {
            filled -> MaterialTheme.colorScheme.primary
            tonal -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        }
    val contentTint =
        tint ?: when {
            filled -> MaterialTheme.colorScheme.onPrimary
            tonal -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 50))
                .background(container),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.iconButtonColors(contentColor = contentTint),
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

/** 连接式动作组里的一项。 */
data class AppConnectedAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * 连接组的形状：外缘胶囊、相邻角收紧（M3 Expressive connected button group）。
 */
fun connectedShape(
    index: Int,
    size: Int,
    height: Dp,
): Shape {
    val outer = height / 2
    val inner = AppDimens.cornerExtraSmall
    return when {
        size <= 1 -> {
            RoundedCornerShape(outer)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = outer,
                bottomStart = outer,
                topEnd = inner,
                bottomEnd = inner,
            )
        }

        index == size - 1 -> {
            RoundedCornerShape(
                topStart = inner,
                bottomStart = inner,
                topEnd = outer,
                bottomEnd = outer,
            )
        }

        else -> {
            RoundedCornerShape(inner)
        }
    }
}

/**
 * 连接式动作组（Star / Watch / Fork 这类同族动作）：3dp 细缝、外缘胶囊、内缘 8dp。
 *
 * [fillWidth] = true 时各项等宽铺满（详情页头部动作条）。
 */
@Composable
fun AppConnectedButtonRow(
    actions: List<AppConnectedAction>,
    modifier: Modifier = Modifier,
    height: Dp = AppDimens.buttonHeight,
    fillWidth: Boolean = false,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.groupSeam),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            val shape = connectedShape(index, actions.size, height)
            val container =
                if (action.selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            val content =
                if (action.selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppDimens.spacing.l),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (action.icon != null) {
                        Icon(action.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(AppDimens.spacing.s))
                    }
                    Text(text = action.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/** M3 图标默认尺寸（24dp）。 */
private val AppIconDefaultSize: Dp = 24.dp
