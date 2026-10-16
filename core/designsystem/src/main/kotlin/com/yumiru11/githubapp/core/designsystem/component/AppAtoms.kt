package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 行首圆形图标徽标（M3E \`GhIconBadge\`）：40dp 圆底 + 22dp 图标。
 *
 * 纯装饰（\`contentDescription = null\`）：语义由同一行的标题文本承载。
 */
@Composable
fun AppIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    size: Dp = AppDimens.iconBadge,
    iconSize: Dp = AppIconAtomSize,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = contentColor,
        )
    }
}

/** 元数据「图标 + 文本」对（星标数 / fork 数 / 更新时间）。 */
@Composable
fun AppMeta(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconSize: Dp = 14.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.width(AppDimens.spacing.xs))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}

/** 元数据胶囊（topic / watch 数 / 分类）。 */
@Composable
fun AppStatPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(containerColor)
                .padding(horizontal = AppDimens.spacing.s + 2.dp, vertical = AppDimens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
            Spacer(Modifier.width(AppDimens.spacing.xs))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1)
    }
}

/**
 * 提示条（游客模式 / 内联错误 / 说明）：柔和底 + 图标 + 文本 + 可选行动。
 *
 * 颜色语义由调用方决定（默认 secondaryContainer 中性档）。
 */
@Composable
fun AppNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    action: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(AppDimens.cornerSmall),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(AppDimens.spacing.m))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                modifier = Modifier.weight(1f),
            )
            if (action != null) {
                Spacer(Modifier.width(AppDimens.spacing.s))
                action()
            }
        }
    }
}

/** 语言色点（仓库语言占比图例）。 */
@Composable
fun AppLanguageDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** 横向排列的多个元数据项（默认 14dp 间距）。 */
@Composable
fun AppMetaRow(
    modifier: Modifier = Modifier,
    spacing: Dp = AppDimens.spacing.m + 2.dp,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        content()
    }
}

private val AppIconAtomSize: Dp = 22.dp
