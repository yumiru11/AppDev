package com.yumiru11.githubapp.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.feature.home.R
import com.yumiru11.githubapp.feature.home.model.TrendItem

/** Trending 小节标题图标尺寸 */
private val SECTION_ICON_SIZE = 18.dp

/** 语言色点直径 */
private val LANGUAGE_DOT_SIZE = 10.dp

/**
 * feed 尾部 Trending 小节（L08 / ui-design.md §2.2/§2.5）。
 *
 * 仓库名 + 语言色点 + ★ 数 + 描述（2 行）的紧凑卡片；[items] 为空时**整体不渲染**
 * （数据层失败静默降级为隐藏，不展示错误块——加分内容不得打断 feed）。
 * 点击 → 调用方解析 url 进仓库详情。
 */
@Composable
internal fun TrendingSection(
    items: List<TrendItem>,
    onItemClick: (TrendItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppDevOcticons.Flame,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(SECTION_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_trending_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items.forEach { item ->
            TrendingRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

/** 单条 Trending 卡片：仓库名 / 描述（2 行）/ 语言色点 + ★ 数 */
@Composable
private fun TrendingRow(
    item: TrendItem,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.fullName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val description = item.description
            if (description != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val language = item.language
                if (language != null) {
                    LanguageDot(language = language)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = language,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Icon(
                    imageVector = AppDevOcticons.Star,
                    contentDescription = stringResource(R.string.home_trending_stars_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.stars.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 语言色点（ui-design §3.2「语言色点」）。
 *
 * GitHub 原色是品牌色板（硬编码十六进制），与「零硬编码颜色」红线冲突，
 * 故按语言名稳定散列到 M3 主题色角色：同一语言恒定同色，且随主题/动态取色联动。
 */
@Composable
internal fun LanguageDot(
    language: String,
    modifier: Modifier = Modifier,
) {
    val colors = languageDotColors()
    Box(
        modifier =
            modifier
                .size(LANGUAGE_DOT_SIZE)
                .clip(CircleShape)
                .background(colors[languageDotIndex(language, colors.size)]),
    )
}

/** 语言名 → 色板下标（纯函数，稳定：同语言恒同色） */
internal fun languageDotIndex(
    language: String,
    paletteSize: Int,
): Int = Math.floorMod(language.hashCode(), paletteSize)

/** 色点色板：主题色角色循环（零硬编码颜色） */
@Composable
private fun languageDotColors(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.onSurfaceVariant,
    )
