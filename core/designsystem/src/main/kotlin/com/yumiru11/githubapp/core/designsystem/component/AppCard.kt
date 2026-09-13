package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape

/**
 * 应用统一卡片（设计系统 Batch 1，`docs/design-system/implementation-plan.md` §3.1③）。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * 默认值逐项等于 [CardDefaults]，实现是**同一个 M3 [Card] 重载的直接转发**：
 * - [onClick] == null → 非可点击 `Card`（容器语义）；
 * - [onClick] != null → 可点击 `Card` 重载（RIpple + enabled 语义）。
 *
 * 因此同 props 下与裸 `Card(` / `Card(onClick = ...)` 渲染逐像素一致——迁移批不产生任何
 * 视觉变化（迁移证据见对应 PR）。
 *
 * ## 逃生舱
 *
 * [shape] / [colors] / [elevation] 全量透传底层 `CardDefaults` 类型；调用方需要
 * `border` / `enabled` / `interactionSource` 等特例时，仍可直接使用 M3 `Card`。
 *
 * @param onClick 非空时整卡可点击（[Card] 的可点击重载），null 时纯容器
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (onClick == null) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            content = content,
        )
    }
}
