package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 应用统一主按钮：胶囊形 + 56dp 基准高度（紧凑场景传 [AppDimens.buttonHeightCompact]）。
 *
 * 与 M3 的关系：本组件是 [Button] / [FilledTonalButton] / [OutlinedButton] / [TextButton]
 * 的厚包装——颜色仍取各自的 Defaults，因此动态取色 / OLED / 高对比 / seed 全部原生跟随；
 * 只统一四件事：形状 = height/2 胶囊、高度、图标与文案间距、默认占满宽度。
 *
 * i18n：文案由调用方经 stringResource() 传入，组件内零硬编码文案。
 *
 * @param height 按钮高度；形状按 height/2 推导胶囊，故改高度不会露出直角
 * @param fillWidth true（默认）占满父宽；false 时按内容宽度
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppButtonStyle = AppButtonStyle.Filled,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = AppDimens.buttonHeight,
    fillWidth: Boolean = true,
) {
    val shape = appPillShape(height)
    val sized = modifier.height(height).then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
    val contentPadding = PaddingValues(horizontal = AppDimens.spacing.xl)
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(AppButtonIconSize))
            Spacer(Modifier.width(AppDimens.spacing.s))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
    when (style) {
        AppButtonStyle.Filled -> {
            Button(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(),
                contentPadding = contentPadding,
                content = content,
            )
        }

        AppButtonStyle.Tonal -> {
            FilledTonalButton(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.filledTonalButtonColors(),
                contentPadding = contentPadding,
                content = content,
            )
        }

        AppButtonStyle.Outlined -> {
            OutlinedButton(
                onClick = onClick,
                modifier = sized,
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.outlinedButtonColors(),
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
                shape = shape,
                colors = ButtonDefaults.textButtonColors(),
                contentPadding = PaddingValues(horizontal = AppDimens.spacing.l),
                content = content,
            )
        }
    }
}

/** 按钮内图标尺寸（与 labelLarge 的光学对齐）。 */
private val AppButtonIconSize: Dp = 20.dp

/** 胶囊形状工厂：按钮 / 下拉 / 选中态统一走这里，避免各处重算 height/2。 */
fun appPillShape(height: Dp): Shape = RoundedCornerShape(height / 2)
