package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens
import com.yumiru11.githubapp.core.designsystem.token.LocalAppSurfaces

/**
 * 分组列表的形状（M3 Expressive grouped list，基准 = GitHubM3E \`groupedShape\`）：
 * 首尾条目外缘 28dp，中间条目 8dp，条目之间由 [AppDimens.groupSeam] 细缝分隔。
 */
fun groupedShape(
    index: Int,
    size: Int,
    outer: Dp = AppDimens.cornerCardOuter,
    inner: Dp = AppDimens.cornerCardInner,
): Shape =
    when {
        size <= 1 -> {
            RoundedCornerShape(outer)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = outer,
                topEnd = outer,
                bottomStart = inner,
                bottomEnd = inner,
            )
        }

        index == size - 1 -> {
            RoundedCornerShape(
                topStart = inner,
                topEnd = inner,
                bottomStart = outer,
                bottomEnd = outer,
            )
        }

        else -> {
            RoundedCornerShape(inner)
        }
    }

/**
 * 分组容器：把一组行渲染成「一张卡的若干段」（设置项 / 个人页入口 / 搜索历史）。
 *
 * 容器色取 [LocalAppSurfaces]（背景图开启时半透明），细缝 = 页面底色自然露出。
 */
@Composable
fun <T> AppGroupedColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    spacing: Dp = AppDimens.groupSeam,
    horizontalPadding: Dp = AppDimens.spacing.l,
    containerColor: androidx.compose.ui.graphics.Color = LocalAppSurfaces.current.card,
    itemContent: @Composable (item: T, index: Int) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        items.forEachIndexed { index, item ->
            val shape = groupedShape(index, items.size)
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
                shape = shape,
                color = containerColor,
            ) {
                itemContent(item, index)
            }
        }
    }
}

/**
 * 分区标题（列表上方的分组标签）。
 *
 * [action] 为右端行动槽（如「全部」「清除」），由调用方传入本地化按钮。
 */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = AppDimens.cardPadding,
                    end = AppDimens.spacing.s,
                    top = AppDimens.cardPadding,
                    bottom = AppDimens.spacing.s,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

/**
 * 两行/三行列表行（M3E \`GhListRow\`）：前槽（图标徽标/头像）+ 标题 + 支持文本 + 尾槽。
 *
 * 尾槽约定：需要「可进入」语义时传 [trailingChevron] = true（渲染右箭头，装饰性）；
 * 需要行动按钮时用 [trailing] 传自己的按钮。
 */
@Composable
fun AppListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    trailingChevron: Boolean = false,
    titleMaxLines: Int = 1,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = AppDimens.spacing.l, vertical = AppDimens.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(AppDimens.spacing.l))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailing != null -> {
                Spacer(Modifier.width(AppDimens.spacing.m))
                trailing()
            }

            trailingChevron -> {
                Spacer(Modifier.width(AppDimens.spacing.m))
                AppChevron()
            }
        }
    }
}

/**
 * 「可进入」箭头（右指 chevron），随布局方向镜像（RTL 自动翻转）。
 *
 * 为什么自绘：图标库的 \`chevron_right\` 是单向矢量，不会随 RTL 镜像；本组件的箭头
 * 只需两段线，用 [Canvas] 画最省依赖且天然 RTL 安全。
 */
@Composable
fun AppChevron(
    modifier: Modifier = Modifier,
    chevronSize: Dp = AppDimens.spacing.xl,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val mirrored = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    androidx.compose.foundation.Canvas(modifier = modifier.size(chevronSize)) {
        val w = this.size.width
        val h = this.size.height
        val startX = if (mirrored) w * 0.65f else w * 0.35f
        val endX = if (mirrored) w * 0.35f else w * 0.65f
        val path =
            androidx.compose.ui.graphics.Path().apply {
                moveTo(startX, h * 0.3f)
                lineTo(endX, h * 0.5f)
                lineTo(startX, h * 0.7f)
            }
        drawPath(
            path = path,
            color = color,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
        )
    }
}

/** 细分割线（列表内部分隔）；[inset] 为左右内缩。 */
@Composable
fun AppHairline(
    modifier: Modifier = Modifier,
    inset: Dp = AppDimens.spacing.l,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = inset)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** 卡片内容槽（统一 20dp 内边距，基准 = M3E \`GhCard\` contentPadding）。 */
@Composable
fun AppCardBody(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(AppDimens.cardPadding), content = content)
}
