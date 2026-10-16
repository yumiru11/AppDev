package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 应用统一图标按钮：48dp 触区 + 圆形底 + 主题色语义。
 *
 * 触区恒为 [AppDimens.minTouchTarget]，图标尺寸单独可调——不会出现「图标大了触区反而小」。
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AppIconButtonStyle = AppIconButtonStyle.Plain,
    enabled: Boolean = true,
    size: Dp = AppDimens.minTouchTarget,
    iconSize: Dp = 22.dp,
    tint: Color? = null,
) {
    val container =
        when (style) {
            AppIconButtonStyle.Plain -> Color.Transparent
            AppIconButtonStyle.Tonal -> MaterialTheme.colorScheme.secondaryContainer
            AppIconButtonStyle.Filled -> MaterialTheme.colorScheme.primary
        }
    val content =
        tint ?: when (style) {
            AppIconButtonStyle.Plain -> MaterialTheme.colorScheme.onSurfaceVariant
            AppIconButtonStyle.Tonal -> MaterialTheme.colorScheme.onSecondaryContainer
            AppIconButtonStyle.Filled -> MaterialTheme.colorScheme.onPrimary
        }
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(container),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(size),
            colors = IconButtonDefaults.iconButtonColors(contentColor = content),
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}
