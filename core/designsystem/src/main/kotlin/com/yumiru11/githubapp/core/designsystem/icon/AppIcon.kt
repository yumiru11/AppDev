package com.yumiru11.githubapp.core.designsystem.icon

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.yumiru11.githubapp.core.datastore.model.IconStyle
import com.yumiru11.githubapp.core.designsystem.token.AppIcon
import com.yumiru11.githubapp.core.designsystem.token.LocalIconStyle

/**
 * 应用统一图标入口（ui-design §5.3，issue #168 / UI12）。
 *
 * 按 [LocalIconStyle]（由 app 层 AppThemeHost 从 UserPreferencesRepository.iconStyle 注入）
 * 从 [spec] 解析出对应风格变体，替换散落的 material-icons 直接引用——**风格切换即时生效**，
 * 消费方无需各自处理风格分支。
 *
 * 用法：
 * ```kotlin
 * AppIcon(spec = AppIcons.Home, contentDescription = null, selected = isSelected)
 * ```
 *
 * 无障碍：图标语义由 [contentDescription] 或周围文本承载（同 M3 [Icon] 约定）；
 * 纯装饰图标（如底栏带文字的 Tab 图标）传 `null`，避免与 label 重复播报。
 *
 * @param spec 语义图标规格（[AppIcons] 目录或 [AppIconSpec.single] 的 Octicons）
 * @param contentDescription 无障碍描述（i18n，装饰性传 null）
 * @param selected 选中态 → 实心变体（ui-design §5.1）
 * @param style 显式风格（设置页预览用）；默认取 [LocalIconStyle]
 * @param tint 着色；默认 [LocalContentColor]（同 M3）
 * @param size 尺寸档位；默认 [AppIcon.iconMedium]（24dp，M3 基准）
 */
@Composable
fun AppIcon(
    spec: AppIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    style: IconStyle = LocalIconStyle.current,
    tint: Color = LocalContentColor.current,
    size: Dp = AppIcon.iconMedium,
) {
    Icon(
        imageVector = spec.vectorFor(style = style, selected = selected),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint,
    )
}
