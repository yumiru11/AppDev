package com.yumiru11.githubapp.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 应用统一 Scaffold（设计系统 Batch 4，`docs/design-system/implementation-plan.md` §3.1①）。
 *
 * ## 像素等价红线（ADR-0010 决策 3）
 *
 * 默认值逐项等于 M3 [Scaffold] 的默认值（`containerColor` = `colorScheme.background`、
 * `contentColor` = `contentColorFor(containerColor)`、`contentWindowInsets` =
 * [ScaffoldDefaults.contentWindowInsets]、`floatingActionButtonPosition` = `FabPosition.End`），
 * 实现是 M3 [Scaffold] 的**逐参数直接转发**。因此同 props 下与裸 `Scaffold(` 渲染逐像素一致
 * ——迁移批不产生任何视觉变化。
 *
 * ## 逃生舱
 *
 * [containerColor] / [contentColor] / [contentWindowInsets] / [floatingActionButtonPosition]
 * 全量透传；五个内容槽（[topBar] / [bottomBar] / [snackbarHost] / [floatingActionButton] /
 * [content]）语义与 M3 完全一致。
 *
 * ## 迁移要点
 *
 * 既有调用点把 `snackbarHost = { SnackbarHost(state) }` 一并换成
 * `{ AppSnackbarHost(state) }`（`core/ui/AppSnackbarHost.kt`，UI17 动效令牌宿主），
 * 本组件不替调用方决定宿主实现——宿主是内容槽。
 *
 * @param floatingActionButtonPosition 与 M3 同名同义（计划 §3.1① 的签名漏列，属逃生舱补齐）
 * @param content 内容槽；收到的是 M3 计算后的 [PaddingValues]（含顶栏/底栏/insets 避让）
 */
@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}
