package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yumiru11.githubapp.core.designsystem.token.AppDimens

/**
 * 可切换 chip（基准 = M3E \`GhSelectableChip\`）：40dp 高、12dp 圆角。
 *
 * 选中 = secondaryContainer，未选 = surfaceContainerHigh（M3E 口径）。
 * [containerColor] / [contentColor] 可覆盖（GitHub label 用自身颜色时）。
 */
@Composable
fun AppSelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val container =
        when {
            selected -> containerColor ?: MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val content =
        when {
            selected -> contentColor ?: MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Surface(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(AppDimens.cornerSmall)).clickable(onClick = onClick),
        shape = RoundedCornerShape(AppDimens.cornerSmall),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppDimens.spacing.m + 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * 横向可滚动的筛选 chip 行（基准 = M3E \`GhFilterChipRow\`）。
 *
 * 复用设计系统的 [AppFilterChip]（六类裸控件门禁要求），统一 12dp 圆角、
 * 无边框、选中 secondaryContainer。
 */
@Composable
fun AppFilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = AppDimens.spacing.l),
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(AppDimens.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            AppFilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = label,
                shape = RoundedCornerShape(AppDimens.cornerSmall),
                border = null,
            )
        }
    }
}
