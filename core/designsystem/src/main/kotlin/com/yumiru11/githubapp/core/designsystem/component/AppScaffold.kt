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
import com.yumiru11.githubapp.core.designsystem.token.LocalAppSurfaces

/**
 * 应用统一 Scaffold（设计系统 Batch 4，`docs/design-system/implementation-plan.md` §3.1①）。
 *
 * ## 容器色（2026-10 UI 重构：背景图半透明档）
 *
 * `containerColor` 默认取 [LocalAppSurfaces] 的 `screen`：**无背景图时逐项等于 M3 默认**
 * （= `colorScheme.background`，像素等价红线仍成立）；设置背景图后为 background 的半透明
 * 副本，让背景图透出来（这是"背景图不管用"的根因修复之一）。
 * `contentColor` 因此必须用 **ColorScheme 上的 `contentColorFor` 扩展**（未匹配返回
 * `Color.Unspecified`）并回落到 [androidx.compose.material3.ColorScheme.onBackground]；
 * 顶层同名 @Composable 版本未匹配时会回落到 `LocalContentColor.current`（= 黑），
 * 半透明档下会让正文静默变黑。
 *
 * `contentWindowInsets` = [ScaffoldDefaults.contentWindowInsets]、
 * `floatingActionButtonPosition` = `FabPosition.End`，其余仍为逐参数直接转发。
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
    containerColor: Color = LocalAppSurfaces.current.screen,
    // 这里**必须**用 ColorScheme.contentColorFor（成员函数、未匹配返回 Color.Unspecified），
    // 而不是顶层 contentColorFor：后者未匹配时回落到 LocalContentColor.current（= Color.Black），
    // 背景图开启时 containerColor 是 background 的半透明副本（精确匹配不上任何角色）→
    // 内容色会静默变黑，深色主题/深色壁纸下是正文对比度事故（本条由 AppScaffoldTest 锁定）。
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(containerColor).orOnBackground(),
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

/** 未匹配到 Material 角色色时回落到 onBackground（见 `contentColor` 形参说明）。 */
@Composable
private fun Color.orOnBackground(): Color = if (this == Color.Unspecified) MaterialTheme.colorScheme.onBackground else this
