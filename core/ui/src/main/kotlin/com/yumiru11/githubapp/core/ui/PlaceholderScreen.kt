package com.yumiru11.githubapp.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppIcon
import com.yumiru11.githubapp.core.designsystem.icon.AppIconSpec
import com.yumiru11.githubapp.core.designsystem.icon.AppIcons
import com.yumiru11.githubapp.core.ui.R

/**
 * 页面级占位（T11 等未实现页面，2026-08-14 真机走查替换 SearchScreen 误占位）。
 *
 * 图标 + 标题 + 说明结构，纯信息展示。分区重构后「仓库」分区已由 ReposScreen 接管，
 * 本组件留作后续未实现页面的空态。
 *
 * 图标走 [AppIcon]/[AppIcons] 统一入口（issue #168 / UI12：空态与底栏/顶栏同一条
 * 图标风格链路）——此前默认值是 `Icons.Default.Star`，「仓库」分区曾据此把星形当仓库图标
 * （ui-audit #12 / issue #168 UI23），该遗留语义随本次切换一并清除。
 *
 * [contentPadding]（issue #83）：玻璃栏避让一律走**内容内边距**而不是把整个节点
 * 用 `Modifier.padding` 推进去——节点保持 full-bleed 才能与 Home/Profile 分区
 * 同一形状接 backdrop blur；占位页自身无可滚动内容，玻璃在其背后本就是空的，
 * 真仓库列表落地时按同一契约填 [contentPadding] 即可直接获得穿越感。
 */
@Composable
fun PlaceholderScreen(
    icon: AppIconSpec = AppIcons.Info,
    title: String = stringResource(R.string.placeholder_title),
    description: String = stringResource(R.string.placeholder_desc),
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(
            spec = icon,
            contentDescription = null,
            size = 48.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
