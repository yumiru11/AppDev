package com.yumiru11.githubapp.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.icon.AppDevOcticons
import com.yumiru11.githubapp.core.designsystem.theme.extendedColors

/**
 * 搜索限流提示条（issue #165 / L13，plan.md §9.3「限流处理」）。
 *
 * 剩余配额低于阈值时置顶于结果区：显示剩余请求数与距配额重置的相对时间。
 * - 颜色取 [extendedColors] 的 warning 语义容器（零硬编码颜色）
 * - 图标用 [AppDevOcticons]（全应用禁 emoji 图标）；装饰性图标 contentDescription = null，
 *   语义由文本承载（无障碍读屏只读文本，不重复播报）
 * - 文案全部 stringResource（en + zh-rCN 成对）
 */
@Composable
internal fun SearchRateLimitSection(
    warning: SearchRateLimitWarning,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.extendedColors.warningContainer,
        contentColor = MaterialTheme.extendedColors.onWarningContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppDevOcticons.Stop,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_rate_limit_banner, warning.remaining),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = stringResource(R.string.search_rate_limit_reset, warning.resetInMinutes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
